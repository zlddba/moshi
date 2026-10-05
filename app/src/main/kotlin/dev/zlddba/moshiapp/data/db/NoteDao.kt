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

    @Query("UPDATE notes SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE notes SET summary = :summary, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT * FROM notes ORDER BY created_at DESC")
    suspend fun recentAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE is_builtin = 1")
    suspend fun builtinCount(): Int

    @Query(
        "SELECT * FROM notes WHERE index_status != 'INDEXED' " +
            "OR id IN (SELECT DISTINCT note_id FROM chunks WHERE embedding_id IS NULL)"
    )
    suspend fun notIndexed(): List<NoteEntity>

    @Query("UPDATE notes SET index_status = 'PENDING'")
    suspend fun markAllUnindexed()

    @Query(
        "SELECT n.* FROM notes n INNER JOIN note_tags nt ON nt.note_id = n.id " +
            "WHERE nt.tag_id = :tagId ORDER BY n.created_at DESC"
    )
    suspend fun byTag(tagId: Int): List<NoteEntity>
}
