package dev.zlddba.moshiapp.ingest.parse

import android.content.Context
import android.net.Uri
import android.util.Xml
import dev.zlddba.moshiapp.data.repo.IngestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

object DocxParser : DocParser {

    private const val DOCUMENT_ENTRY = "word/document.xml"
    private const val MIME_DOCX =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val BUFFER_SIZE = 8192

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        return extension == "docx" || normalizedMime == MIME_DOCX
    }

    override suspend fun parse(
        context: Context,
        uri: Uri,
        fileName: String,
        onOcrProgress: ((Int, Int) -> Unit)?
    ): ParsedDoc {
        return withContext(Dispatchers.IO) {
            val xml = readDocumentXml(context, uri)
            val text = extractText(xml)
            if (text.isBlank()) throw IngestException(IngestException.Kind.EMPTY)
            ParsedDoc(
                title = deriveTitle(fileName, text, true),
                text = text,
                format = ParsedDoc.FORMAT_DOCX
            )
        }
    }

    private fun readDocumentXml(context: Context, uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IngestException(IngestException.Kind.IO)
        val result = try {
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == DOCUMENT_ENTRY) {
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = zip.read(chunk)
                            if (read == -1) break
                            total += read
                            if (total > IngestRepository.MAX_FILE_BYTES) {
                                throw IngestException(IngestException.Kind.TOO_LARGE)
                            }
                            buffer.write(chunk, 0, read)
                        }
                        return@use buffer.toByteArray()
                    }
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: IngestException) {
            throw e
        } catch (e: IOException) {
            throw IngestException(IngestException.Kind.PARSE_FAILED)
        }
        return result ?: throw IngestException(IngestException.Kind.PARSE_FAILED)
    }

    private fun extractText(xml: ByteArray): String {
        val out = StringBuilder()
        val paragraph = StringBuilder()
        var inParagraph = false
        var inTextNode = false
        var headingLevel = 0
        var tableDepth = 0
        var rowCellCount = 0
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xml), null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "p" -> {
                            inParagraph = true
                            paragraph.setLength(0)
                            headingLevel = 0
                        }
                        "pStyle" -> if (inParagraph) {
                            headingLevel = headingLevelOf(attributeValue(parser, "val"))
                        }
                        "tbl" -> tableDepth++
                        "tr" -> rowCellCount = 0
                        "tc" -> {
                            if (rowCellCount > 0) out.append(" | ")
                            rowCellCount++
                        }
                        "tab" -> if (inParagraph) paragraph.append('\t')
                        "br" -> if (inParagraph) paragraph.append('\n')
                        "t" -> inTextNode = inParagraph
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        if (inParagraph && inTextNode) {
                            paragraph.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "p" -> {
                            if (inParagraph) {
                                if (tableDepth > 0) {
                                    out.append(paragraph)
                                } else {
                                    if (headingLevel in 1..6) {
                                        out.append("#".repeat(headingLevel)).append(' ')
                                    }
                                    out.append(paragraph).append("\n\n")
                                }
                            }
                            inParagraph = false
                            inTextNode = false
                            paragraph.setLength(0)
                            headingLevel = 0
                        }
                        "t" -> inTextNode = false
                        "tr" -> out.append('\n')
                        "tbl" -> {
                            tableDepth--
                            out.append('\n')
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: IOException) {
            throw IngestException(IngestException.Kind.PARSE_FAILED)
        } catch (e: Exception) {
            throw IngestException(IngestException.Kind.PARSE_FAILED)
        }
        return out.toString()
    }

    private fun attributeValue(parser: XmlPullParser, name: String): String? {
        for (index in 0 until parser.attributeCount) {
            if (parser.getAttributeName(index) == name) {
                return parser.getAttributeValue(index)
            }
        }
        return null
    }

    private fun headingLevelOf(style: String?): Int {
        if (style.isNullOrBlank()) return 0
        val normalized = style.trim()
        val english = Regex("(?i)heading([1-6])").find(normalized)
        if (english != null) return english.groupValues[1].toInt()
        val chinese = Regex("标题([1-6])").find(normalized)
        if (chinese != null) return chinese.groupValues[1].toInt()
        return 0
    }
}
