package dev.zlddba.moshiapp.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

@Database(
    entities = [NoteEntity::class, ChunkEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MoshiDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun chunkDao(): ChunkDao

    companion object {

        @Volatile
        private var INSTANCE: MoshiDatabase? = null

        fun get(context: Context): MoshiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoshiDatabase::class.java,
                    "moshi.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
