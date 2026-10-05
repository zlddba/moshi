package dev.zlddba.moshiapp.ui.doc

import java.util.Locale

object DocumentRenderer {

    enum class Kind { MARKDOWN, TEXT, PDF, WORD, SHEET, IMAGE, AUDIO, UNSUPPORTED }

    private val MARKDOWN_EXT = setOf("md", "markdown")
    private val TEXT_EXT = setOf("txt", "text", "log", "csv", "json", "xml", "kt", "java")
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic")
    private val AUDIO_EXT = setOf("wav", "mp3", "m4a", "aac", "ogg", "amr", "flac")
    private val WORD_EXT = setOf("docx", "doc")
    private val SHEET_EXT = setOf("xlsx", "xls")

    private val MARKDOWN_STRONG = listOf(
        Regex("(?m)^#{1,6}\\s+\\S"),
        Regex("(?m)^\\s*```"),
        Regex("(?m)^\\s*\\|[^\\n]*\\|[^\\n]*$"),
        Regex("\\[[^\\]\\n]+\\]\\([^)\\n]+\\)"),
        Regex("\\*\\*[^*\\n]{1,80}\\*\\*")
    )

    private val MARKDOWN_WEAK = listOf(
        Regex("(?m)^\\s*[-*+]\\s+\\S"),
        Regex("(?m)^\\s*\\d+[.)]\\s+\\S"),
        Regex("(?m)^\\s*>\\s+\\S"),
        Regex("`[^`\\n]+`")
    )

    fun looksLikeMarkdown(content: String): Boolean {
        if (content.isBlank()) return false
        if (MARKDOWN_STRONG.any { it.containsMatchIn(content) }) return true
        return MARKDOWN_WEAK.count { it.containsMatchIn(content) } >= 2
    }

    fun kindOf(fileName: String?, noteType: String?, content: String = ""): Kind {
        val extension = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return when {
            extension == "pdf" -> Kind.PDF
            extension in MARKDOWN_EXT -> Kind.MARKDOWN
            extension in WORD_EXT -> Kind.WORD
            extension in SHEET_EXT -> Kind.SHEET
            extension in IMAGE_EXT -> Kind.IMAGE
            extension in AUDIO_EXT -> Kind.AUDIO
            extension in TEXT_EXT -> Kind.TEXT
            noteType == "PDF" -> Kind.PDF
            noteType == "IMAGE_OCR" -> Kind.IMAGE
            noteType == "AUDIO" -> Kind.AUDIO
            looksLikeMarkdown(content) -> Kind.MARKDOWN
            else -> Kind.TEXT
        }
    }

    fun labelOf(kind: Kind): String = when (kind) {
        Kind.MARKDOWN -> "Markdown"
        Kind.TEXT -> "文本"
        Kind.PDF -> "PDF"
        Kind.WORD -> "Word"
        Kind.SHEET -> "Excel"
        Kind.IMAGE -> "图片"
        Kind.AUDIO -> "音频"
        Kind.UNSUPPORTED -> "不支持"
    }
}
