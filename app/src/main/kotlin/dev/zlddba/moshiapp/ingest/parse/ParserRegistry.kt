package dev.zlddba.moshiapp.ingest.parse

object ParserRegistry {

    private val parsers = listOf(TextParser, PdfParser, DocxParser)

    fun resolve(fileName: String, mimeType: String?): DocParser? {
        return parsers.firstOrNull { it.supports(fileName, mimeType) }
    }
}
