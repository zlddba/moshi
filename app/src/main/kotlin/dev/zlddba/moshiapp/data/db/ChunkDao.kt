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

    @Query("UPDATE chunks SET embedding_id = id WHERE id = :chunkId")
    suspend fun markEmbedded(chunkId: Int)

    @Query("UPDATE chunks SET embedding_id = NULL")
    suspend fun clearEmbeddings()

    @Query("SELECT COUNT(*) FROM chunks WHERE note_id = :noteId AND embedding_id IS NULL")
    suspend fun unembeddedCount(noteId: String): Int
}
