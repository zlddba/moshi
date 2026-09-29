package dev.zlddba.moshiapp.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET index_status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE notes SET is_sensitive = :sensitive WHERE id = :id")
    suspend fun updateSensitive(id: String, sensitive: Boolean)

    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    suspend fun recentAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("SELECT * FROM notes WHERE index_status != 'INDEXED'")
    suspend fun notIndexed(): List<NoteEntity>
}
