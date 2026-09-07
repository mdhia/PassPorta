package org.shadowgrove.passporta.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the migration from schema 1 to 2.
 *
 * `runMigrationsAndValidate` compares the result of the hand-written SQL with the schema Room
 * expects (`app/schemas/.../2.json`). If even a single column, index or foreign key deviates,
 * the test fails - exactly the error that would otherwise only surface for the user as a
 * crash on app start.
 */
@RunWith(AndroidJUnit4::class)
class PassMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PassDatabase::class.java,
    )

    @Test
    fun migratesFrom1To2AndKeepsData() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO passes (
                    id, folder_name, title, subtitle, owner_name, identifier,
                    barcode_data, barcode_type, barcode_alt_text, background_color,
                    logo_path, source, created_at, updated_at
                ) VALUES (
                    'pass-1', 'Kundenkarten', 'Beispiel Club', NULL, 'Erika Mustermann', '4711',
                    'ABC-123', 'QR', NULL, ${0xFF37474F.toInt()},
                    'logos/pass-1.png', 'PKPASS', 1000, 2000
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            PassMigrations.MIGRATION_1_2,
        )

        // Existing data must survive the migration unchanged.
        db.query("SELECT title, expiration_date, location FROM passes WHERE id = 'pass-1'").use {
            assertTrue("The existing pass was lost", it.moveToFirst())
            assertEquals("Beispiel Club", it.getString(0))
            assertTrue("New columns must be empty", it.isNull(1))
            assertTrue("New columns must be empty", it.isNull(2))
        }

        // The new table must be writable and hang off the foreign key.
        db.execSQL(
            "INSERT INTO pass_fields (id, pass_id, label, value, section, position) " +
                "VALUES ('f1', 'pass-1', 'Sitzplatz', '14A', 'HEADER', 0)",
        )
        db.query("SELECT value FROM pass_fields WHERE pass_id = 'pass-1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("14A", it.getString(0))
        }
        db.close()
    }

    @Test
    fun migratesFrom1To5AcrossAllSteps() {
        helper.createDatabase(TEST_DB_ALL, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO passes (
                    id, folder_name, title, subtitle, owner_name, identifier,
                    barcode_data, barcode_type, barcode_alt_text, background_color,
                    logo_path, source, created_at, updated_at
                ) VALUES (
                    'pass-2', 'Fluege', 'Bordkarte', NULL, 'Erika Mustermann', 'LH400',
                    'XYZ-789', 'AZTEC', NULL, ${0xFF1A73E8.toInt()},
                    NULL, 'PKPASS', 1000, 2000
                )
                """.trimIndent(),
            )
        }

        // All migrations in sequence - this is also what happens for a user who skipped a
        // version. Otherwise the chained case would only surface out in the field.
        val db = helper.runMigrationsAndValidate(
            TEST_DB_ALL,
            5,
            true,
            PassMigrations.MIGRATION_1_2,
            PassMigrations.MIGRATION_2_3,
            PassMigrations.MIGRATION_3_4,
            PassMigrations.MIGRATION_4_5,
        )

        db.query("SELECT title, icon_key FROM passes WHERE id = 'pass-2'").use {
            assertTrue("The existing pass was lost", it.moveToFirst())
            assertEquals("Bordkarte", it.getString(0))
            assertTrue("Existing passes don't have a symbol yet", it.isNull(1))
        }

        db.execSQL("UPDATE passes SET icon_key = 'flight' WHERE id = 'pass-2'")
        db.query("SELECT icon_key FROM passes WHERE id = 'pass-2'").use {
            assertTrue(it.moveToFirst())
            assertEquals("flight", it.getString(0))
        }
        db.close()
    }

    /**
     * The favorite column is `NOT NULL`. Without a `DEFAULT` in the migration, existing rows
     * would have no valid value - so this explicitly checks the default value, not just that
     * the column exists.
     */
    @Test
    fun migratesFrom3To4AndSetsFavoriteDefault() {
        helper.createDatabase(TEST_DB_FAVORITES, 3).use { db ->
            db.execSQL(
                """
                INSERT INTO passes (
                    id, folder_name, title, subtitle, owner_name, identifier,
                    barcode_data, barcode_type, barcode_alt_text, barcode_ecc, barcode_encoding,
                    background_color, logo_path, icon_key, hero_image_path,
                    original_file_path, original_file_name, original_mime_type,
                    expiration_date, location, location_latitude, location_longitude,
                    source, created_at, updated_at
                ) VALUES (
                    'pass-3', 'Kundenkarten', 'Beispiel Club', NULL, 'Erika Mustermann', '4711',
                    'ABC-123', 'QR', NULL, NULL, NULL,
                    ${0xFF37474F.toInt()}, NULL, 'loyalty', NULL,
                    NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL,
                    'PKPASS', 1000, 2000
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_FAVORITES,
            4,
            true,
            PassMigrations.MIGRATION_3_4,
        )

        db.query("SELECT is_favorite FROM passes WHERE id = 'pass-3'").use {
            assertTrue("The existing pass was lost", it.moveToFirst())
            assertEquals("Existing passes must not be favorites", 0, it.getInt(0))
        }

        db.execSQL("UPDATE passes SET is_favorite = 1 WHERE id = 'pass-3'")
        db.query("SELECT is_favorite FROM passes WHERE id = 'pass-3'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    /** Version 5 adds the nullable start date - existing passes must stay `null`. */
    @Test
    fun migratesFrom4To5AndKeepsStartDateNull() {
        helper.createDatabase(TEST_DB_START_DATE, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO passes (
                    id, folder_name, is_favorite, title, subtitle, owner_name, identifier,
                    barcode_data, barcode_type, barcode_alt_text, barcode_ecc, barcode_encoding,
                    background_color, logo_path, icon_key, hero_image_path,
                    original_file_path, original_file_name, original_mime_type,
                    expiration_date, location, location_latitude, location_longitude,
                    source, created_at, updated_at
                ) VALUES (
                    'pass-4', 'Kundenkarten', 0, 'Beispiel Club', NULL, 'Erika Mustermann', '4711',
                    'ABC-123', 'QR', NULL, NULL, NULL,
                    ${0xFF37474F.toInt()}, NULL, 'loyalty', NULL,
                    NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL,
                    'PKPASS', 1000, 2000
                )
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB_START_DATE,
            5,
            true,
            PassMigrations.MIGRATION_4_5,
        )

        db.query("SELECT start_date FROM passes WHERE id = 'pass-4'").use {
            assertTrue("The existing pass was lost", it.moveToFirst())
            assertTrue("Existing passes must not have a start date", it.isNull(0))
        }

        db.execSQL("UPDATE passes SET start_date = 5000 WHERE id = 'pass-4'")
        db.query("SELECT start_date FROM passes WHERE id = 'pass-4'").use {
            assertTrue(it.moveToFirst())
            assertEquals(5000, it.getLong(0))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "passporta-migration-test.db"
        const val TEST_DB_ALL = "passporta-migration-test-all.db"
        const val TEST_DB_FAVORITES = "passporta-migration-test-favorites.db"
        const val TEST_DB_START_DATE = "passporta-migration-test-start-date.db"
    }
}


