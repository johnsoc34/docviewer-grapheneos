package org.grapheneos.docviewer

import android.util.Base64
import org.apache.poi.xwpf.usermodel.*
import java.io.InputStream

object DocxConverter {

    fun convert(inputStream: InputStream): String {
        val doc = XWPFDocument(inputStream)
        val body = StringBuilder()

        for (element in doc.bodyElements) {
            when (element) {
                is XWPFParagraph -> body.append(convertParagraph(element))
                is XWPFTable -> body.append(convertTable(element))
            }
        }

        doc.close()
        return HtmlWrapper.wrap("Document", body.toString())
    }

    private fun convertParagraph(para: XWPFParagraph): String {
        if (para.runs.isEmpty() && para.text.isBlank()) {
            return "<br>\n"
        }

        val style = para.style ?: ""
        val alignment = when (para.alignment) {
            ParagraphAlignment.CENTER -> "text-align:center;"
            ParagraphAlignment.RIGHT -> "text-align:right;"
            ParagraphAlignment.BOTH, ParagraphAlignment.DISTRIBUTE -> "text-align:justify;"
            else -> ""
        }

        val inlineStyle = if (alignment.isNotEmpty()) " style=\"$alignment\"" else ""

        val isListItem = try { para.numID != null } catch (_: Exception) { false }

        val tag = when {
            style.startsWith("Heading1") || style.equals("heading 1", ignoreCase = true) -> "h1"
            style.startsWith("Heading2") || style.equals("heading 2", ignoreCase = true) -> "h2"
            style.startsWith("Heading3") || style.equals("heading 3", ignoreCase = true) -> "h3"
            style.startsWith("Heading4") || style.equals("heading 4", ignoreCase = true) -> "h4"
            style.startsWith("Title") -> "h1"
            style.startsWith("Subtitle") -> "h2"
            isListItem -> return convertListItem(para)
            else -> "p"
        }

        val content = buildRunContent(para)
        return "<$tag$inlineStyle>$content</$tag>\n"
    }

    private fun convertListItem(para: XWPFParagraph): String {
        val content = buildRunContent(para)
        val level = try {
            para.numIlvl?.toInt() ?: 0
        } catch (_: Exception) { 0 }
        val indent = if (level > 0) " style=\"margin-left:${level * 24}px\"" else ""
        return "<li$indent>$content</li>\n"
    }

    private fun buildRunContent(para: XWPFParagraph): String {
        val sb = StringBuilder()
        for (run in para.runs) {
            var text = HtmlWrapper.escapeHtml(run.text() ?: continue)

            for (pic in run.embeddedPictures) {
                val data = pic.pictureData
                val base64 = Base64.encodeToString(data.data, Base64.NO_WRAP)
                val mime = when (data.pictureType) {
                    Document.PICTURE_TYPE_PNG -> "image/png"
                    Document.PICTURE_TYPE_JPEG -> "image/jpeg"
                    Document.PICTURE_TYPE_GIF -> "image/gif"
                    else -> "image/png"
                }
                sb.append("<img src=\"data:$mime;base64,$base64\" alt=\"embedded image\">")
            }

            if (run.isBold) text = "<strong>$text</strong>"
            if (run.isItalic) text = "<em>$text</em>"
            if (run.underline != UnderlinePatterns.NONE) text = "<u>$text</u>"
            if (run.isStrikeThrough) text = "<s>$text</s>"

            val fontSize = run.fontSize
            val color = run.color

            if (fontSize > 0 || color != null) {
                val styles = mutableListOf<String>()
                if (fontSize > 0) styles.add("font-size:${fontSize}pt")
                if (color != null) styles.add("color:#$color")
                text = "<span style=\"${styles.joinToString(";")}\">$text</span>"
            }

            sb.append(text)
        }
        return sb.toString()
    }

    private fun convertTable(table: XWPFTable): String {
        val sb = StringBuilder("<table>\n")

        for ((rowIdx, row) in table.rows.withIndex()) {
            sb.append("<tr>\n")
            for (cell in row.tableCells) {
                val tag = if (rowIdx == 0) "th" else "td"
                val cellContent = StringBuilder()
                for (para in cell.paragraphs) {
                    cellContent.append(buildRunContent(para))
                    cellContent.append(" ")
                }
                sb.append("<$tag>${cellContent.toString().trim()}</$tag>\n")
            }
            sb.append("</tr>\n")
        }

        sb.append("</table>\n")
        return sb.toString()
    }
}
