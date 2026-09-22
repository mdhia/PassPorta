package org.shadowgrove.passporta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.shadowgrove.passporta.data.local.dao.PassDao
import org.shadowgrove.passporta.data.local.entity.PassEntity
import org.shadowgrove.passporta.data.local.entity.PassFieldEntity
import org.shadowgrove.passporta.data.local.entity.PassBarcodeEntity

/**
 * Local, purely offline database of PassPorta.
 *
 * Schemas are exported to `app/schemas` (see `room { schemaDirectory(...) }`), so later
 * migrations are versionable and testable.
 */
@Database(
    entities = [PassEntity::class, PassFieldEntity::class, PassBarcodeEntity::class],
    version = 6,
    exportSchema = true,
)
@TypeConverters(PassConverters::class)
abstract class PassDatabase : RoomDatabase() {

    abstract fun passDao(): PassDao

    companion object {

        private const val DATABASE_NAME = "passporta.db"

        @Volatile
        private var instance: PassDatabase? = null

        fun getInstance(context: Context): PassDatabase =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): PassDatabase =
            Room.databaseBuilder(context, PassDatabase::class.java, DATABASE_NAME)
                // Deliberately no destructive migration: pass data cannot be restored, so
                // every schema change is migrated explicitly.
                .addMigrations(*PassMigrations.ALL)
                .build()
    }
}
