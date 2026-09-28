package dev.zlddba.moshiapp.ingest.parse

import android.content.Context
import android.net.Uri

interface DocParser {

    fun supports(fileName: String, mimeType: String?): Boolean

    suspend fun parse(
        context: Context,
        uri: Uri,
        fileName: String,
        onOcrProgress: ((Int, Int) -> Unit)? = null
    ): ParsedDoc

    fun deriveTitle(fileName: String, text: String, useHeadings: Boolean): String {
        if (useHeadings) {
            text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("#") }
                ?.let { line ->
                    val cleaned = line.trimStart('#').trim()
                    if (cleaned.isNotEmpty()) return cleaned.take(60)
                }
        }
        val base = fileName.substringBeforeLast('.')
        return base.ifBlank { text.take(20).trim() }.ifBlank { "未命名" }
    }
}
