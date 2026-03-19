package org.grapheneos.docviewer

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.text.DecimalFormat

object XlsxConverter {

    private val decimalFormat = DecimalFormat("#.##########")

    fun convert(inputStream: InputStream): String {
        val workbook = XSSFWorkbook(inputStream)
        val body = StringBuilder()
        val formulaEvaluator = workbook.creationHelper.createFormulaEvaluator()

        for (sheetIdx in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIdx)
            val sheetName = workbook.getSheetName(sheetIdx)

            body.append("<div class=\"sheet-section\">\n")

            if (workbook.numberOfSheets > 1) {
                body.append("<div class=\"sheet-name\">${HtmlWrapper.escapeHtml(sheetName)}</div>\n")
            }

            body.append(convertSheet(sheet, formulaEvaluator))
            body.append("</div>\n")
        }

        workbook.close()

        val extraCss = """
            td.num { text-align: right; font-variant-numeric: tabular-nums; }
            td.date { white-space: nowrap; }
        """.trimIndent()

        return HtmlWrapper.wrap("Spreadsheet", body.toString(), extraCss)
    }

    private fun convertSheet(sheet: Sheet, evaluator: FormulaEvaluator): String {
        val sb = StringBuilder()
        val mergedRegions = sheet.mergedRegions

        if (sheet.lastRowNum < 0) {
            sb.append("<p><em>Empty sheet</em></p>\n")
            return sb.toString()
        }

        var maxCol = 0
        for (rowIdx in sheet.firstRowNum..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            if (row.lastCellNum > maxCol) maxCol = row.lastCellNum.toInt()
        }
        maxCol = minOf(maxCol, 50)

        sb.append("<table>\n")

        for (rowIdx in sheet.firstRowNum..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx)
            sb.append("<tr>")

            for (colIdx in 0 until maxCol) {
                val mergeInfo = getMergeInfo(mergedRegions, rowIdx, colIdx)
                if (mergeInfo.hidden) continue

                val cell = row?.getCell(colIdx)
                val cellValue = getCellValue(cell, evaluator)
                val isNumeric = cell?.cellType == CellType.NUMERIC
                val isDate = cell != null && DateUtil.isCellDateFormatted(cell)

                val cssClass = when {
                    isDate -> " class=\"date\""
                    isNumeric -> " class=\"num\""
                    else -> ""
                }

                val tag = if (rowIdx == sheet.firstRowNum && looksLikeHeader(sheet)) "th" else "td"

                val mergeAttrs = StringBuilder()
                if (mergeInfo.rowSpan > 1) mergeAttrs.append(" rowspan=\"${mergeInfo.rowSpan}\"")
                if (mergeInfo.colSpan > 1) mergeAttrs.append(" colspan=\"${mergeInfo.colSpan}\"")

                sb.append("<$tag$cssClass$mergeAttrs>${HtmlWrapper.escapeHtml(cellValue)}</$tag>")
            }

            sb.append("</tr>\n")
        }

        sb.append("</table>\n")
        return sb.toString()
    }

    private fun getCellValue(cell: Cell?, evaluator: FormulaEvaluator): String {
        if (cell == null) return ""

        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue ?: ""
                CellType.NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        val date = cell.localDateTimeCellValue
                        date?.toString() ?: cell.numericCellValue.toString()
                    } else {
                        val value = cell.numericCellValue
                        if (value == value.toLong().toDouble()) {
                            value.toLong().toString()
                        } else {
                            decimalFormat.format(value)
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        val evaluated = evaluator.evaluate(cell)
                        when (evaluated.cellType) {
                            CellType.STRING -> evaluated.stringValue ?: ""
                            CellType.NUMERIC -> {
                                val v = evaluated.numberValue
                                if (v == v.toLong().toDouble()) v.toLong().toString()
                                else decimalFormat.format(v)
                            }
                            CellType.BOOLEAN -> evaluated.booleanValue.toString()
                            else -> cell.cellFormula ?: ""
                        }
                    } catch (_: Exception) {
                        "=${cell.cellFormula}"
                    }
                }
                CellType.BLANK -> ""
                CellType.ERROR -> "#ERR"
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun looksLikeHeader(sheet: Sheet): Boolean {
        val firstRow = sheet.getRow(sheet.firstRowNum) ?: return false
        var textCount = 0
        var totalCells = 0
        for (cell in firstRow) {
            totalCells++
            if (cell.cellType == CellType.STRING && cell.stringCellValue.isNotBlank()) {
                textCount++
            }
        }
        return totalCells > 0 && textCount.toFloat() / totalCells > 0.5f
    }

    private data class MergeInfo(
        val hidden: Boolean = false,
        val rowSpan: Int = 1,
        val colSpan: Int = 1
    )

    private fun getMergeInfo(
        regions: List<CellRangeAddress>,
        row: Int,
        col: Int
    ): MergeInfo {
        for (region in regions) {
            if (region.isInRange(row, col)) {
                return if (row == region.firstRow && col == region.firstColumn) {
                    MergeInfo(
                        hidden = false,
                        rowSpan = region.lastRow - region.firstRow + 1,
                        colSpan = region.lastColumn - region.firstColumn + 1
                    )
                } else {
                    MergeInfo(hidden = true)
                }
            }
        }
        return MergeInfo()
    }
}
