package dev.zlddba.moshiapp.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

@Database(
    entities = [NoteEntity::class, ChunkEntity::class, QaLogEntity::class],
    version = 3,
    exportSchema = true
)
abstract class MoshiDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun chunkDao(): ChunkDao

    abstract fun qaLogDao(): QaLogDao

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
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(db: SQLiteConnection) {
                exec(
                    db,
                    "CREATE TABLE IF NOT EXISTS `qa_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`thinking` TEXT NOT NULL, " +
                        "`sources_json` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)"
                )
            }
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

        fun get(context: Context): MoshiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoshiDatabase::class.java,
                    "moshi.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
