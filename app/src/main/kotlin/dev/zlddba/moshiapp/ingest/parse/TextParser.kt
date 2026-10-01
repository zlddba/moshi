package dev.zlddba.moshiapp.ingest.parse

import android.content.Context
import android.net.Uri
import dev.zlddba.moshiapp.data.repo.IngestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TextParser : DocParser {

    private val textExtensions = setOf("txt", "text", "csv", "tsv")
    private val markdownExtensions = setOf("md", "markdown")
    private val mimeTypes = setOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "text/csv",
        "text/tab-separated-values",
        "application/csv"
    )

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        return extension in textExtensions ||
            extension in markdownExtensions ||
            normalizedMime in mimeTypes
    }

    override suspend fun parse(
        context: Context,
        uri: Uri,
        fileName: String,
        onOcrProgress: ((Int, Int) -> Unit)?
    ): ParsedDoc {
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IngestException(IngestException.Kind.IO)
            if (bytes.size > IngestRepository.MAX_FILE_BYTES) {
                throw IngestException(IngestException.Kind.TOO_LARGE)
            }
            val text = decode(bytes)
            if (text.isBlank()) throw IngestException(IngestException.Kind.EMPTY)
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val markdown = extension in markdownExtensions
            val format = when {
                markdown -> ParsedDoc.FORMAT_MD
                extension == ParsedDoc.FORMAT_CSV -> ParsedDoc.FORMAT_CSV
                else -> ParsedDoc.FORMAT_TXT
            }
            ParsedDoc(
                title = deriveTitle(fileName, text, markdown),
                text = text,
                format = format
            )
        }
    }

    private fun decode(bytes: ByteArray): String {
        val payload = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload))
                .toString()
        } catch (e: CharacterCodingException) {
            String(payload, Charset.forName("GBK"))
        }
    }
}
