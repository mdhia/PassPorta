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

    /**
     * Version 5 adds the start date - the counterpart to the expiration date. Existing passes
     * remain valid from the start (`null`).
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {

        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `passes` ADD COLUMN `start_date` INTEGER")
        }
    }

    /**
     * Version 6 stores every barcode as an ordered child row while retaining the legacy
     * barcode columns as the primary/first barcode for backwards compatibility.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {

        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pass_barcodes` (
                    `id` TEXT NOT NULL,
                    `pass_id` TEXT NOT NULL,
                    `barcode_data` TEXT NOT NULL,
                    `barcode_type` TEXT NOT NULL,
                    `barcode_alt_text` TEXT,
                    `barcode_ecc` TEXT,
                    `barcode_encoding` TEXT,
                    `position` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`pass_id`) REFERENCES `passes`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pass_barcodes_pass_id` " +
                    "ON `pass_barcodes` (`pass_id`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pass_barcodes_pass_id_position` " +
                    "ON `pass_barcodes` (`pass_id`, `position`)",
            )
            db.execSQL(
                """
                INSERT INTO `pass_barcodes` (
                    `id`, `pass_id`, `barcode_data`, `barcode_type`,
                    `barcode_alt_text`, `barcode_ecc`, `barcode_encoding`, `position`
                )
                SELECT
                    substr(lower(hex(randomblob(16))), 1, 8) || '-' ||
                        lower(hex(randomblob(4))) || '-' ||
                        '4' || substr(lower(hex(randomblob(16))), 1, 3) || '-' ||
                        substr('89ab', abs(random()) % 4 + 1, 1) ||
                        substr(lower(hex(randomblob(16))), 1, 3) || '-' ||
                        lower(hex(randomblob(12))),
                    `id`, `barcode_data`, `barcode_type`,
                    `barcode_alt_text`, `barcode_ecc`, `barcode_encoding`, 0
                FROM `passes`
                WHERE `barcode_data` IS NOT NULL AND trim(`barcode_data`) <> ''
                """.trimIndent(),
            )
        }
    }

    /** All migrations in ascending order. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )

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
