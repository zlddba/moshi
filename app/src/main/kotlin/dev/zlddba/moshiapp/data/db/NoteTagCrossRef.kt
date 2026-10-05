package dev.zlddba.moshiapp.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["tag_id"]), Index(value = ["note_id"])]
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "tag_id") val tagId: Int
)
