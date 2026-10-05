package dev.zlddba.moshiapp.ingest.parse

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

object SheetParser : DocParser {

    private const val MAX_ROWS = 2000
    private const val MAX_COLS = 60
    private const val CELL_SEPARATOR = " | "

    private val sheetExtensions = setOf(ParsedDoc.FORMAT_XLSX, ParsedDoc.FORMAT_XLS)

    private val sheetMimeTypes = setOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel"
    )

    override fun supports(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        return extension in sheetExtensions || normalizedMime in sheetMimeTypes
    }

    override suspend fun parse(
        context: Context,
        uri: Uri,
        fileName: String,
        onOcrProgress: ((Int, Int) -> Unit)?
    ): ParsedDoc = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IngestException(IngestException.Kind.IO)
        val workbook = try {
            stream.use { WorkbookFactory.create(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IngestException(IngestException.Kind.PARSE_FAILED)
        }
        workbook.use { book ->
            val extracted = build(book)
            if (extracted.text.isBlank()) throw IngestException(IngestException.Kind.EMPTY)
            ParsedDoc(
                title = deriveTitle(fileName, extracted.text, false),
                text = extracted.text,
                format = formatOf(book, fileName),
                pageOffsets = extracted.offsets
            )
        }
    }

    private data class Extracted(val text: String, val offsets: List<Int>)

    private fun build(workbook: Workbook): Extracted {
        val out = StringBuilder()
        val offsets = ArrayList<Int>()
        val formatter = DataFormatter()
        val evaluator = try {
            workbook.creationHelper.createFormulaEvaluator()
        } catch (e: Throwable) {
            null
        }
        val sheetCount = workbook.numberOfSheets
        for (index in 0 until sheetCount) {
            val sheet = workbook.getSheetAt(index)
            val rows = readRows(sheet, formatter, evaluator)
            if (rows.isEmpty()) continue
            offsets.add(out.length)
            out.append("# ").append(sheetName(sheet, index)).append("\n\n")
            for (row in rows) out.append(row).append('\n')
            out.append('\n')
        }
        return Extracted(out.toString(), offsets)
    }

    private fun readRows(
        sheet: Sheet,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator?
    ): List<String> {
        val firstRow = sheet.firstRowNum
        val lastRow = minOf(sheet.lastRowNum, firstRow + MAX_ROWS - 1)
        if (lastRow < firstRow) return emptyList()
        val rows = ArrayList<String>()
        for (rowIndex in firstRow..lastRow) {
            val row = sheet.getRow(rowIndex) ?: continue
            val line = rowToLine(row, formatter, evaluator)
            if (line.isNotBlank()) rows.add(line)
        }
        return rows
    }

    private fun rowToLine(
        row: Row,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator?
    ): String {
        val lastCell = minOf(row.lastCellNum.toInt(), MAX_COLS)
        if (lastCell <= 0) return ""
        val cells = ArrayList<String>(lastCell)
        for (cellIndex in 0 until lastCell) {
            cells.add(cellText(row.getCell(cellIndex), formatter, evaluator))
        }
        while (cells.isNotEmpty() && cells.last().isEmpty()) {
            cells.removeAt(cells.size - 1)
        }
        return cells.joinToString(CELL_SEPARATOR)
    }

    private fun cellText(
        cell: Cell?,
        formatter: DataFormatter,
        evaluator: FormulaEvaluator?
    ): String {
        if (cell == null) return ""
        val raw = try {
            if (evaluator == null) {
                formatter.formatCellValue(cell)
            } else {
                formatter.formatCellValue(cell, evaluator)
            }
        } catch (e: Throwable) {
            try {
                formatter.formatCellValue(cell)
            } catch (second: Throwable) {
                ""
            }
        }
        return raw.trim()
    }

    private fun sheetName(sheet: Sheet, index: Int): String {
        val name = sheet.sheetName.orEmpty().trim()
        return name.ifBlank { "Sheet${index + 1}" }
    }

    private fun formatOf(workbook: Workbook, fileName: String): String {
        if (workbook is HSSFWorkbook) return ParsedDoc.FORMAT_XLS
        if (workbook is XSSFWorkbook) return ParsedDoc.FORMAT_XLSX
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return if (extension == ParsedDoc.FORMAT_XLS) {
            ParsedDoc.FORMAT_XLS
        } else {
            ParsedDoc.FORMAT_XLSX
        }
    }
}
