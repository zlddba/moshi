package dev.zlddba.moshiapp.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "summary") val summary: String? = null,
    @ColumnInfo(name = "content_html") val contentHtml: String? = null,

    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "source_uri") val sourceUri: String? = null,
    @ColumnInfo(name = "source_note") val sourceNote: String? = null,
    @ColumnInfo(name = "is_sensitive", defaultValue = "0") val isSensitive: Boolean = false,
    @ColumnInfo(name = "is_builtin", defaultValue = "0") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "is_encrypted", defaultValue = "0") val isEncrypted: Boolean = false,
    @ColumnInfo(name = "index_status", defaultValue = "PENDING") val indexStatus: String = STATUS_PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
) {
    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_PDF = "PDF"
        const val TYPE_IMAGE_OCR = "IMAGE_OCR"
        const val TYPE_AUDIO = "AUDIO"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_INDEXING = "INDEXING"
        const val STATUS_INDEXED = "INDEXED"
        const val STATUS_FAILED = "FAILED"
    }
}
