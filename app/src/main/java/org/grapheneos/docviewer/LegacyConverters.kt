package org.grapheneos.docviewer

import android.util.Base64
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.hslf.usermodel.*
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.ss.usermodel.*
import java.io.InputStream
import java.text.DecimalFormat

/**
 * Converter for legacy .doc files (Word 97-2003)
 */
object DocConverter {

    fun convert(inputStream: InputStream): String {
        val doc = HWPFDocument(inputStream)
        val range = doc.range
        val body = StringBuilder()

        for (i in 0 until range.numParagraphs()) {
            val para = range.getParagraph(i)
            val text = para.text()?.trim() ?: continue
            if (text.isEmpty()) {
                body.append("<br>\n")
                continue
            }

            val escaped = HtmlWrapper.escapeHtml(text)
            val styleIdx = para.styleIndex
            val tag = when {
                para.isInList -> "li"
                styleIdx in 1..4 -> "h$styleIdx"
                else -> "p"
            }

            body.append("<$tag>$escaped</$tag>\n")
        }

        doc.close()
        return HtmlWrapper.wrap("Document", body.toString())
    }
}

/**
 * Converter for legacy .xls files (Excel 97-2003)
 */
object XlsConverter {

    private val decimalFormat = DecimalFormat("#.##########")

    fun convert(inputStream: InputStream): String {
        val workbook = HSSFWorkbook(inputStream)
        val body = StringBuilder()
        val evaluator = workbook.creationHelper.createFormulaEvaluator()

        for (sheetIdx in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIdx)
            val sheetName = workbook.getSheetName(sheetIdx)

            body.append("<div class=\"sheet-section\">\n")
            if (workbook.numberOfSheets > 1) {
                body.append("<div class=\"sheet-name\">${HtmlWrapper.escapeHtml(sheetName)}</div>\n")
            }

            body.append(convertSheet(sheet, evaluator))
            body.append("</div>\n")
        }

        workbook.close()
        return HtmlWrapper.wrap("Spreadsheet", body.toString(),
            "td.num { text-align: right; font-variant-numeric: tabular-nums; }")
    }

    private fun convertSheet(sheet: Sheet, evaluator: FormulaEvaluator): String {
        val sb = StringBuilder()

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
                val cell = row?.getCell(colIdx)
                val value = getCellValue(cell, evaluator)
                val isNum = cell?.cellType == CellType.NUMERIC &&
                        !(cell != null && DateUtil.isCellDateFormatted(cell))
                val cls = if (isNum) " class=\"num\"" else ""
                val tag = if (rowIdx == sheet.firstRowNum) "th" else "td"
                sb.append("<$tag$cls>${HtmlWrapper.escapeHtml(value)}</$tag>")
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
                        cell.localDateTimeCellValue?.toString() ?: ""
                    } else {
                        val v = cell.numericCellValue
                        if (v == v.toLong().toDouble()) v.toLong().toString()
                        else decimalFormat.format(v)
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try {
                        val ev = evaluator.evaluate(cell)
                        when (ev.cellType) {
                            CellType.STRING -> ev.stringValue ?: ""
                            CellType.NUMERIC -> {
                                val v = ev.numberValue
                                if (v == v.toLong().toDouble()) v.toLong().toString()
                                else decimalFormat.format(v)
                            }
                            CellType.BOOLEAN -> ev.booleanValue.toString()
                            else -> ""
                        }
                    } catch (_: Exception) { "=${cell.cellFormula}" }
                }
                else -> ""
            }
        } catch (_: Exception) { "" }
    }
}

/**
 * Converter for legacy .ppt files (PowerPoint 97-2003)
 */
object PptConverter {

    fun convert(inputStream: InputStream): String {
        val ppt = HSLFSlideShow(inputStream)
        val body = StringBuilder()
        val slides = ppt.slides

        for ((idx, slide) in slides.withIndex()) {
            body.append("<div class=\"slide\">\n")
            body.append("<div class=\"slide-number\">Slide ${idx + 1} of ${slides.size}</div>\n")

            for (shape in slide.shapes) {
                when (shape) {
                    is HSLFTextShape -> {
                        val text = shape.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            body.append("<p>${HtmlWrapper.escapeHtml(text)}</p>\n")
                        }
                    }
                    is HSLFPictureShape -> {
                        try {
                            val picData = shape.pictureData
                            val base64 = Base64.encodeToString(picData.data, Base64.NO_WRAP)
                            val mime = picData.contentType ?: "image/png"
                            body.append("<p><img src=\"data:$mime;base64,$base64\" alt=\"slide image\"></p>\n")
                        } catch (_: Exception) {
                            body.append("<p><em>[Image]</em></p>\n")
                        }
                    }
                    is HSLFTable -> {
                        body.append("<table>\n")
                        for (rowIdx in 0 until shape.numberOfRows) {
                            body.append("<tr>")
                            for (colIdx in 0 until shape.numberOfColumns) {
                                val cell = shape.getCell(rowIdx, colIdx)
                                val text = HtmlWrapper.escapeHtml(cell?.text ?: "")
                                val tag = if (rowIdx == 0) "th" else "td"
                                body.append("<$tag>$text</$tag>")
                            }
                            body.append("</tr>\n")
                        }
                        body.append("</table>\n")
                    }
                    is HSLFGroupShape -> {
                        for (child in shape.shapes) {
                            if (child is HSLFTextShape) {
                                val text = child.text?.trim()
                                if (!text.isNullOrEmpty()) {
                                    body.append("<p>${HtmlWrapper.escapeHtml(text)}</p>\n")
                                }
                            }
                        }
                    }
                }
            }

            body.append("</div>\n")
        }

        ppt.close()
        return HtmlWrapper.wrap("Presentation", body.toString())
    }
}
