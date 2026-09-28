package dev.zlddba.moshiapp.ingest.parse

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import dev.zlddba.moshiapp.ingest.vision.OcrTextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object PdfParser : DocParser {

    private const val OCR_DPI = 200f

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        return extension == "pdf" || normalizedMime == "application/pdf"
    }

    override suspend fun parse(
        context: Context,
        uri: Uri,
        fileName: String,
        onOcrProgress: ((Int, Int) -> Unit)?
    ): ParsedDoc {
        return withContext(Dispatchers.IO) {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context)
            }
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IngestException(IngestException.Kind.IO)
            val document = stream.use { input ->
                try {
                    PDDocument.load(input)
                } catch (e: IOException) {
                    throw IngestException(IngestException.Kind.PARSE_FAILED)
                }
            }
            document.use { doc ->
                if (doc.isEncrypted) throw IngestException(IngestException.Kind.PARSE_FAILED)
                val pages = doc.numberOfPages
                if (pages <= 0) throw IngestException(IngestException.Kind.EMPTY)
                val stripper = try {
                    PDFTextStripper()
                } catch (e: IOException) {
                    throw IngestException(IngestException.Kind.PARSE_FAILED)
                }
                val renderer = PDFRenderer(doc)
                val builder = StringBuilder()
                val offsets = mutableListOf<Int>()
                for (page in 1..pages) {
                    offsets.add(builder.length)
                    stripper.setStartPage(page)
                    stripper.setEndPage(page)
                    var pageText = try {
                        stripper.getText(doc)
                    } catch (e: IOException) {
                        throw IngestException(IngestException.Kind.PARSE_FAILED)
                    }
                    if (pageText.isBlank()) {
                        onOcrProgress?.invoke(page, pages)
                        val bitmap = try {
                            renderer.renderImageWithDPI(page - 1, OCR_DPI, ImageType.RGB)
                        } catch (e: IOException) {
                            null
                        }
                        if (bitmap != null) {
                            try {
                                pageText = OcrTextRecognizer.recognizeBitmap(bitmap)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    builder.append(pageText)
                    if (pageText.isNotEmpty() && !pageText.endsWith("\n")) {
                        builder.append('\n')
                    }
                    builder.append('\n')
                }
                val text = builder.toString()
                if (text.isBlank()) throw IngestException(IngestException.Kind.EMPTY)
                ParsedDoc(
                    title = deriveTitle(fileName, text, false),
                    text = text,
                    format = ParsedDoc.FORMAT_PDF,
                    pageOffsets = offsets
                )
            }
        }
    }
}
