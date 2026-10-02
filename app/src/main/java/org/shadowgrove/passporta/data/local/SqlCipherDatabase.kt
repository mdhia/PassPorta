package org.shadowgrove.passporta.data.local

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.util.Log
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * Everything SQLCipher-specific that has to happen before Room opens the database.
 *
 * - Loads the native library exactly once.
 * - Converts the random database key into SQLCipher's raw-key form (no PBKDF2 needed).
 * - Migrates a pre-encryption plaintext database atomically, without losing data on failure.
 * - Verifies the key before Room opens the file: SQLCipher's default error handler *deletes* a
 *   database it can't decrypt, so a key mismatch must never reach Room. Files that can't be
 *   decrypted are moved aside instead.
 */
internal object SqlCipherDatabase {

    private const val TAG = "SqlCipherDatabase"
    private const val KEY_BYTES = 32
    private const val ENCRYPTING_SUFFIX = ".encrypting"
    private const val PLAINTEXT_BACKUP_SUFFIX = ".plaintext-backup"
    private const val UNREADABLE_SUFFIX = ".unreadable-"
    private val SIDECAR_SUFFIXES = listOf("", "-wal", "-shm", "-journal")
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val HEX = "0123456789abcdef".toByteArray(Charsets.US_ASCII)
    private val UNREADABLE_CODE = Regex("""\(code (11|26)\b""")

    /** Never deletes anything: corruption is surfaced as an exception and handled here. */
    private val KEEP_FILE_ERROR_HANDLER = DatabaseErrorHandler { }

    @Volatile
    private var libraryLoaded = false

    fun ensureLibraryLoaded() {
        if (libraryLoaded) return
        synchronized(this) {
            if (!libraryLoaded) {
                System.loadLibrary("sqlcipher")
                libraryLoaded = true
            }
        }
    }

    /**
     * SQLCipher raw-key specification `x'<64 hex chars>'`.
     *
     * The key is already 256 bits of randomness, so SQLCipher's password KDF would only slow
     * down every open without adding security.
     */
    fun rawKeySpec(key: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "Database key must be $KEY_BYTES bytes" }
        val spec = ByteArray(3 + KEY_BYTES * 2)
        spec[0] = 'x'.code.toByte()
        spec[1] = '\''.code.toByte()
        key.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            spec[2 + index * 2] = HEX[value ushr 4]
            spec[3 + index * 2] = HEX[value and 0x0F]
        }
        spec[spec.lastIndex] = '\''.code.toByte()
        return spec
    }

    /**
     * Brings [database] into a state Room can open with [keySpec]:
     * resumes an interrupted migration, encrypts a plaintext database and verifies the key.
     */
    fun prepare(database: File, keySpec: ByteArray) {
        ensureLibraryLoaded()
        resumeInterruptedMigration(database)
        if (!database.isFile) return

        try {
            if (isPlaintext(database)) {
                migratePlaintext(database, keySpec)
            }
            verifyKey(database, keySpec)
        } catch (error: SQLiteException) {
            if (!error.isUnreadableDatabase()) throw error
            // Wrong key or a truly corrupt file. Keep it for manual recovery; Room starts with
            // an empty database and the user can restore a backup.
            setAside(database, error)
        }
    }

    /**
     * SQLCipher reports a wrong key as a plain [SQLiteException] with `SQLITE_NOTADB` (26), not
     * as [SQLiteDatabaseCorruptException] - so both forms (and `SQLITE_CORRUPT`, 11) count.
     */
    private fun SQLiteException.isUnreadableDatabase(): Boolean =
        this is SQLiteDatabaseCorruptException || message?.let(UNREADABLE_CODE::containsMatchIn) == true

    private fun verifyKey(database: File, keySpec: ByteArray) {
        // Read-only: doesn't touch journal mode or other settings Room manages. Opening with a
        // key already runs a query against the schema, so a wrong key fails right here.
        open(database, keySpec, SQLiteDatabase.OPEN_READONLY).close()
    }

    private fun migratePlaintext(database: File, keySpec: ByteArray) {
        val encrypted = sibling(database, ENCRYPTING_SUFFIX)
        deleteWithSidecars(encrypted)

        val expectedCounts: Map<String, Long>
        val expectedVersion: Int
        var source: SQLiteDatabase? = null
        try {
            // ATTACH inherits the open flags of this connection: without CREATE_IF_NECESSARY,
            // SQLite refuses to create the encrypted target file (SQLITE_CANTOPEN). The source
            // file itself already exists, so the flag has no effect on it.
            source = open(
                database,
                ByteArray(0),
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
            )
            if (!source.isDatabaseIntegrityOk) {
                throw SQLiteDatabaseCorruptException("Plaintext database failed the integrity check")
            }
            expectedVersion = source.version
            expectedCounts = tableCounts(source)

            source.execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf(encrypted.absolutePath, String(keySpec, Charsets.US_ASCII)),
            )
            // sqlcipher_export() is a SELECT: the statement only runs once the cursor is stepped.
            source.query("SELECT sqlcipher_export('encrypted')").use { it.moveToFirst() }
            // Room relies on user_version to pick migrations; never let it fall back to 0.
            source.execSQL("PRAGMA encrypted.user_version = $expectedVersion")
            source.execSQL("DETACH DATABASE encrypted")
        } catch (error: Exception) {
            deleteWithSidecars(encrypted)
            throw error
        } finally {
            source?.close()
        }

        try {
            open(encrypted, keySpec, SQLiteDatabase.OPEN_READWRITE).use { target ->
                check(target.isDatabaseIntegrityOk) { "Encrypted database failed the integrity check" }
                check(target.version == expectedVersion) { "Schema version was not preserved" }
                check(tableCounts(target) == expectedCounts) { "Encrypted copy is incomplete" }
            }
        } catch (error: Exception) {
            deleteWithSidecars(encrypted)
            throw error
        }

        install(encrypted, database)
        Log.i(TAG, "Plaintext database migrated to SQLCipher")
    }

    /** Swaps [encrypted] in place of [database]; keeps the plaintext until the swap succeeded. */
    private fun install(encrypted: File, database: File) {
        val backup = sibling(database, PLAINTEXT_BACKUP_SUFFIX)
        deleteWithSidecars(backup)
        check(moveWithSidecars(database, backup)) { "Could not preserve the plaintext database" }
        if (!encrypted.renameTo(database)) {
            moveWithSidecars(backup, database)
            deleteWithSidecars(encrypted)
            error("Could not install the encrypted database")
        }
        deleteWithSidecars(backup)
    }

    /**
     * Repairs the state after a process death during [install]:
     * - plaintext backup but no database: the swap didn't finish - restore and migrate again;
     * - both present: the encrypted database is installed - the backup is obsolete.
     */
    private fun resumeInterruptedMigration(database: File) {
        deleteWithSidecars(sibling(database, ENCRYPTING_SUFFIX))
        val backup = sibling(database, PLAINTEXT_BACKUP_SUFFIX)
        if (!backup.exists()) return
        if (database.exists()) {
            deleteWithSidecars(backup)
        } else {
            check(moveWithSidecars(backup, database)) { "Could not restore the plaintext database" }
        }
    }

    private fun setAside(database: File, cause: Throwable) {
        val target = sibling(database, UNREADABLE_SUFFIX + System.currentTimeMillis())
        if (moveWithSidecars(database, target)) {
            Log.e(TAG, "Database can't be decrypted; moved to ${target.name}", cause)
        } else {
            throw IllegalStateException("Database can't be decrypted and can't be moved aside", cause)
        }
    }

    private fun open(file: File, key: ByteArray, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, key, null, flags, KEEP_FILE_ERROR_HANDLER, null)

    private fun tableCounts(database: SQLiteDatabase): Map<String, Long> {
        val tables = database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return tables.associateWith { table ->
            val quoted = "\"" + table.replace("\"", "\"\"") + "\""
            database.query("SELECT COUNT(*) FROM $quoted").use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }
    }

    private fun isPlaintext(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = FileInputStream(file).use { input -> input.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private fun sibling(database: File, suffix: String): File =
        File(database.parentFile, database.name + suffix)

    /** Moves the main file and its journal files; `true` if the main file was moved. */
    private fun moveWithSidecars(from: File, to: File): Boolean {
        if (!from.renameTo(to)) return false
        SIDECAR_SUFFIXES.drop(1).forEach { suffix ->
            val sidecar = File(from.path + suffix)
            if (sidecar.exists()) sidecar.renameTo(File(to.path + suffix))
        }
        return true
    }

    private fun deleteWithSidecars(file: File) {
        SIDECAR_SUFFIXES.forEach { suffix -> File(file.path + suffix).delete() }
    }
}








