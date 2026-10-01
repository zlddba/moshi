package dev.zlddba.moshiapp.ingest.parse

data class ParsedDoc(
    val title: String,
    val text: String,
    val format: String,
    val pageOffsets: List<Int> = emptyList()
) {
    companion object {
        const val FORMAT_TXT = "txt"
        const val FORMAT_MD = "md"
        const val FORMAT_CSV = "csv"
        const val FORMAT_PDF = "pdf"
        const val FORMAT_DOCX = "docx"
        const val FORMAT_DOC = "doc"
        const val FORMAT_XLSX = "xlsx"
        const val FORMAT_XLS = "xls"
    }
}
