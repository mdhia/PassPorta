package org.shadowgrove.passporta.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations of the pass database.
 *
 * Deliberately never migrated destructively: passes cannot be restored after a loss, since the
 * app has no network access at all.
 */
internal object PassMigrations {

    /**
     * Version 2 adds the fields from the full Apple and Google pass schema: additional fields
     * as their own table, expiration date, location, original file, hero image, and the
     * barcode parameters (error correction and character set).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {

        override fun migrate(db: SupportSQLiteDatabase) {
            NEW_PASS_COLUMNS.forEach { (name, type) ->
                db.execSQL("ALTER TABLE `passes` ADD COLUMN `$name` $type")
            }

            // Structure exactly as Room expects it for PassFieldEntity - otherwise the schema
            // validation would fail on the next startup.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pass_fields` (
                    `id` TEXT NOT NULL,
                    `pass_id` TEXT NOT NULL,
                    `label` TEXT,
                    `value` TEXT NOT NULL,
                    `section` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`pass_id`) REFERENCES `passes`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pass_fields_pass_id` " +
                    "ON `pass_fields` (`pass_id`)",
            )
        }
    }

    /**
     * Version 3 adds the key of the chosen symbol from the icon library.
     *
     * Existing passes remain without a symbol; they continue to show their logo or initials.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {

        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `passes` ADD COLUMN `icon_key` TEXT")
        }
    }

    /**
     * Version 4 adds the favorite marker.
     *
     * The column is `NOT NULL` and therefore needs a `DEFAULT` - existing rows would otherwise
     * have no valid value and `ALTER TABLE` would fail. The default value must exactly match
     * the entity's `defaultValue`, otherwise Room's schema check fails.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {

        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `passes` ADD COLUMN `is_favorite` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** All migrations in ascending order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    /** New, entirely nullable columns - therefore without `DEFAULT` and without data migration. */
    private val NEW_PASS_COLUMNS = listOf(
        "barcode_ecc" to "TEXT",
        "barcode_encoding" to "TEXT",
        "hero_image_path" to "TEXT",
        "original_file_path" to "TEXT",
        "original_file_name" to "TEXT",
        "original_mime_type" to "TEXT",
        "expiration_date" to "INTEGER",
        "location" to "TEXT",
        "location_latitude" to "REAL",
        "location_longitude" to "REAL",
    )
}
