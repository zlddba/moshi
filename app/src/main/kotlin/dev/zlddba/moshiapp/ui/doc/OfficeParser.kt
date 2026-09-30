package dev.zlddba.moshiapp.ui.doc

import java.io.File
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument

object OfficeParser {

    private const val MAX_SHEET_ROWS = 2000
    private const val MAX_SHEET_COLS = 60

    fun wordToHtml(file: File): String = when (file.extension.lowercase()) {
        "docx" -> docxToHtml(file)
        "doc" -> docToHtml(file)
        else -> throw UnsupportedOperationException(file.extension)
    }

    fun sheetToHtml(file: File): String = when (file.extension.lowercase()) {
        "xlsx" -> workbookToHtml(XSSFWorkbook(file.inputStream()))
        "xls" -> workbookToHtml(HSSFWorkbook(file.inputStream()))
        else -> throw UnsupportedOperationException(file.extension)
    }

    private fun docxToHtml(file: File): String {
        val document = file.inputStream().use { XWPFDocument(it) }
        return document.use { doc ->
            val builder = StringBuilder()
            for (paragraph in doc.paragraphs) {
                val text = paragraph.text.orEmpty().trim()
                if (text.isNotEmpty()) builder.append("<p>").append(HtmlRenderer.escape(text)).append("</p>")
            }
            for (table in doc.tables) {
                appendTable(builder, table.rows.map { row -> row.tableCells.map { it.text.orEmpty() } })
            }
            builder.toString().ifBlank { emptyBody() }
        }
    }

    private fun docToHtml(file: File): String {
        val document = file.inputStream().use { HWPFDocument(it) }
        return document.use { doc ->
            val builder = StringBuilder()
            val range = doc.range
            var paragraphCount = 0
            for (index in 0 until range.numParagraphs()) {
                val paragraph = range.getParagraph(index)
                val text = paragraph.text().replace("\r", "").replace("\u0007", "").trim()
                if (text.isEmpty()) continue
                builder.append("<p>").append(HtmlRenderer.escape(text)).append("</p>")
                paragraphCount++
                if (paragraphCount >= MAX_SHEET_ROWS) break
            }
            builder.toString().ifBlank { emptyBody() }
        }
    }

    private fun workbookToHtml(workbook: Workbook): String = workbook.use { book ->
        val builder = StringBuilder()
        val formatter = DataFormatter()
        val sheetCount = book.numberOfSheets
        for (index in 0 until sheetCount) {
            val sheet = book.getSheetAt(index)
            val name = sheet.sheetName.orEmpty()
            if (sheetCount > 1 || name.isNotBlank()) {
                builder.append("<div class=\"sheet-name\">")
                    .append(HtmlRenderer.escape(if (name.isBlank()) "Sheet${index + 1}" else name))
                    .append("</div>")
            }
            appendSheet(builder, sheet, formatter)
        }
        builder.toString().ifBlank { emptyBody() }
    }

    private fun appendSheet(builder: StringBuilder, sheet: Sheet, formatter: DataFormatter) {
        val firstRow = sheet.firstRowNum
        val lastRow = minOf(sheet.lastRowNum, firstRow + MAX_SHEET_ROWS - 1)
        if (lastRow < firstRow) return
        builder.append("<table>")
        for (rowIndex in firstRow..lastRow) {
            val row = sheet.getRow(rowIndex) ?: continue
            appendRow(builder, row, formatter)
        }
        builder.append("</table>")
    }

    private fun appendRow(builder: StringBuilder, row: Row, formatter: DataFormatter) {
        val lastCell = minOf(row.lastCellNum.toInt(), MAX_SHEET_COLS)
        if (lastCell <= 0) return
        builder.append("<tr>")
        for (cellIndex in 0 until lastCell) {
            val cell = row.getCell(cellIndex)
            val value = when {
                cell == null -> ""
                cell.cellType == CellType.FORMULA -> formatter.formatCellValue(cell)
                else -> formatter.formatCellValue(cell)
            }
            builder.append("<td>").append(HtmlRenderer.escape(value.orEmpty())).append("</td>")
        }
        builder.append("</tr>")
    }

    private fun appendTable(builder: StringBuilder, rows: List<List<String>>) {
        if (rows.isEmpty()) return
        builder.append("<table>")
        for (row in rows) {
            builder.append("<tr>")
            for (cell in row) {
                builder.append("<td>").append(HtmlRenderer.escape(cell.trim())).append("</td>")
            }
            builder.append("</tr>")
        }
        builder.append("</table>")
    }

    private fun emptyBody(): String = "<p class=\"empty\">无法解析文件内容</p>"
}
