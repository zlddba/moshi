package dev.zlddba.moshiapp.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface ChunkDao {

    @Insert
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM chunks WHERE note_id = :noteId ORDER BY seq")
    suspend fun byNote(noteId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE id = :chunkId")
    suspend fun byId(chunkId: Int): ChunkEntity?
}
