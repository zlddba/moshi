package dev.zlddba.moshiapp.data.db

import android.content.Context
import android.util.Log
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import dev.zlddba.moshiapp.domain.security.CryptoManager
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

@Database(
    entities = [
        NoteEntity::class,
        ChunkEntity::class,
        QaLogEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class
    ],
    version = 4,
    exportSchema = true
)
abstract class MoshiDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun chunkDao(): ChunkDao

    abstract fun qaLogDao(): QaLogDao

    abstract fun tagsDao(): TagsDao

    companion object {

        private const val DATABASE_NAME = "moshi.db"
        private const val TAG = "MoshiDatabase"

        @Volatile
        private var INSTANCE: MoshiDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                exec(connection, "ALTER TABLE notes ADD COLUMN is_builtin INTEGER NOT NULL DEFAULT 0")
                exec(
                    connection,
                    "UPDATE notes SET is_builtin = 1 WHERE type = 'TEXT' " +
                        "AND (title = '默识使用说明' OR title = '使用说明')"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                exec(
                    connection,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                exec(connection, "ALTER TABLE notes ADD COLUMN content_html TEXT")
                exec(
                    connection,
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL)"
                )
                exec(
                    connection,
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
                )
                exec(
                    connection,
                    "CREATE TABLE IF NOT EXISTS `note_tags` (" +
                        "`note_id` TEXT NOT NULL, " +
                        "`tag_id` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`note_id`, `tag_id`), " +
                        "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION " +
                        "ON DELETE CASCADE, " +
                        "FOREIGN KEY(`tag_id`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION " +
                        "ON DELETE CASCADE)"
                )
                exec(
                    connection,
                    "CREATE INDEX IF NOT EXISTS `index_note_tags_tag_id` ON `note_tags` (`tag_id`)"
                )
                exec(
                    connection,
                    "CREATE INDEX IF NOT EXISTS `index_note_tags_note_id` ON `note_tags` (`note_id`)"
                )
            }
        }

        private fun exec(connection: SQLiteConnection, sql: String) {
            val statement = connection.prepare(sql)
            try {
                while (statement.step()) {
                }
            } finally {
                statement.close()
            }
        }

        fun get(context: Context): MoshiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): MoshiDatabase {
            val file = context.getDatabasePath(DATABASE_NAME)
            val encrypt = CryptoManager.isEnabled(context)
            val passphrase = if (encrypt) CryptoManager.databasePassphrase(context) else null
            DatabaseCipher.prepare(file, encrypt, passphrase)
            val encryptedOnDisk = DatabaseCipher.isEncryptedOnDisk(file) ||
                (encrypt && !file.exists() && passphrase != null)
            val builder = Room.databaseBuilder(
                context,
                MoshiDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            if (encryptedOnDisk && passphrase != null && DatabaseCipher.ensureNative()) {
                builder.setDriver(
                    SQLCipherDriver(passphrase.toByteArray(Charsets.UTF_8), null, null)
                )
            } else if (encryptedOnDisk) {
                Log.e(TAG, "database on disk is encrypted but no driver could be created")
            }
            return builder.build()
        }

        fun reset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
