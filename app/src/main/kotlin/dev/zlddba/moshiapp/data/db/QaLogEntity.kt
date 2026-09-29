package dev.zlddba.moshiapp.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "qa_logs")
data class QaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "thinking") val thinking: String,
    @ColumnInfo(name = "sources_json") val sourcesJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
