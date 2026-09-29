package dev.zlddba.moshiapp.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

@Database(
    entities = [NoteEntity::class, ChunkEntity::class],
    version = 2,
    exportSchema = true
)
abstract class MoshiDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun chunkDao(): ChunkDao

    companion object {

        @Volatile
        private var INSTANCE: MoshiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(db: SQLiteConnection) {
                exec(db, "ALTER TABLE notes ADD COLUMN is_builtin INTEGER NOT NULL DEFAULT 0")
                exec(
                    db,
                    "UPDATE notes SET is_builtin = 1 WHERE type = 'TEXT' " +
                        "AND (title = '默识使用说明' OR title = '使用说明')"
                )
            }

            private fun exec(db: SQLiteConnection, sql: String) {
                val statement = db.prepare(sql)
                try {
                    while (statement.step()) {
                    }
                } finally {
                    statement.close()
                }
            }
        }

        fun get(context: Context): MoshiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoshiDatabase::class.java,
                    "moshi.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
