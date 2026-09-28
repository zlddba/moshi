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

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}
