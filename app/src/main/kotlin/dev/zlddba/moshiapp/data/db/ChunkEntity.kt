package dev.zlddba.moshiapp.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["note_id"])]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "seq") val seq: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    @ColumnInfo(name = "page_no") val pageNo: Int? = null,
    @ColumnInfo(name = "embedding_id") val embeddingId: Int? = null
)
