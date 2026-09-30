package dev.zlddba.moshiapp.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

data class NoteTagRefCount(
    val id: Int,
    val name: String,
    val refCount: Int
)

data class NoteTagLink(
    @androidx.room3.ColumnInfo(name = "note_id") val noteId: String,
    @androidx.room3.ColumnInfo(name = "tag_id") val tagId: Int
)

@Dao
interface TagsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun link(ref: NoteTagCrossRef)

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun byId(id: Int): TagEntity?

    @Query(
        "SELECT t.id AS id, t.name AS name, COUNT(nt.note_id) AS refCount FROM tags t " +
            "LEFT JOIN note_tags nt ON nt.tag_id = t.id " +
            "GROUP BY t.id, t.name ORDER BY refCount DESC, t.name ASC"
    )
    suspend fun allWithCount(): List<NoteTagRefCount>

    @Query(
        "SELECT t.id FROM tags t INNER JOIN note_tags nt ON nt.tag_id = t.id " +
            "WHERE nt.note_id = :noteId ORDER BY t.name ASC"
    )
    suspend fun tagIdsOfNote(noteId: String): List<Int>

    @Query(
        "SELECT t.* FROM tags t INNER JOIN note_tags nt ON nt.tag_id = t.id " +
            "WHERE nt.note_id = :noteId ORDER BY t.name ASC"
    )
    suspend fun tagsOfNote(noteId: String): List<TagEntity>

    @Query("SELECT note_id, tag_id FROM note_tags")
    suspend fun allLinks(): List<NoteTagLink>

    @Query("SELECT * FROM tags")
    suspend fun allTags(): List<TagEntity>

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun clearNote(noteId: String)

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId")
    suspend fun clearTag(tagId: Int)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Int)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM note_tags)")
    suspend fun deleteOrphans()

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("DELETE FROM note_tags")
    suspend fun clearAllLinks()

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}
