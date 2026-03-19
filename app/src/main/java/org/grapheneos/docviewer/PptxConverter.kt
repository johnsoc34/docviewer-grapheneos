package org.grapheneos.docviewer

import android.util.Base64
import org.apache.poi.xslf.usermodel.*
import java.io.InputStream

object PptxConverter {

    fun convert(inputStream: InputStream): String {
        val pptx = XMLSlideShow(inputStream)
        val body = StringBuilder()
        val slides = pptx.slides

        for ((idx, slide) in slides.withIndex()) {
            body.append("<div class=\"slide\">\n")
            body.append("<div class=\"slide-number\">Slide ${idx + 1} of ${slides.size}</div>\n")
            body.append(convertSlide(slide))
            body.append("</div>\n")
        }

        // Also render slide notes if present
        val notesBody = StringBuilder()
        var hasNotes = false
        for ((idx, slide) in slides.withIndex()) {
            val notes = getSlideNotes(slide)
            if (notes.isNotBlank()) {
                hasNotes = true
                notesBody.append("<div class=\"slide-note\">")
                notesBody.append("<strong>Slide ${idx + 1} Notes:</strong> ")
                notesBody.append(HtmlWrapper.escapeHtml(notes))
                notesBody.append("</div>\n")
            }
        }

        if (hasNotes) {
            body.append("<hr style=\"margin:32px 0\">\n")
            body.append("<h2>Speaker Notes</h2>\n")
            body.append(notesBody)
        }

        pptx.close()

        val extraCss = """
            .slide-note {
                padding: 8px 12px;
                margin: 4px 0;
                background: #f9f9f9;
                border-left: 3px solid #ccc;
                font-size: 0.9rem;
            }
            @media (prefers-color-scheme: dark) {
                .slide-note { background: #1e1e1e; border-left-color: #555; }
            }
        """.trimIndent()

        return HtmlWrapper.wrap("Presentation", body.toString(), extraCss)
    }

    private fun convertSlide(slide: XSLFSlide): String {
        val sb = StringBuilder()
        val shapes = try {
            slide.shapes
        } catch (_: Exception) {
            return "<p><em>Could not parse slide content</em></p>"
        }

        for (shape in shapes) {
            when (shape) {
                is XSLFTextShape -> sb.append(convertTextShape(shape))
                is XSLFTable -> sb.append(convertTable(shape))
                is XSLFPictureShape -> sb.append(convertPicture(shape))
                is XSLFGroupShape -> {
                    for (child in shape.shapes) {
                        when (child) {
                            is XSLFTextShape -> sb.append(convertTextShape(child))
                            is XSLFPictureShape -> sb.append(convertPicture(child))
                        }
                    }
                }
            }
        }

        return sb.toString()
    }

    private fun convertTextShape(shape: XSLFTextShape): String {
        val sb = StringBuilder()

        for (para in shape.textParagraphs) {
            val text = para.text?.trim() ?: continue
            if (text.isEmpty()) continue

            val isBold = para.textRuns.any { run -> run.isBold }
            val fontSize = if (para.textRuns.isNotEmpty()) {
                para.textRuns[0].fontSize ?: 12.0
            } else 12.0

            val tag = when {
                fontSize >= 28 || (isBold && fontSize >= 20) -> "h1"
                fontSize >= 22 || (isBold && fontSize >= 16) -> "h2"
                isBold && fontSize >= 14 -> "h3"
                else -> "p"
            }

            val content = buildTextRunContent(para)
            if (content.isNotBlank()) {
                sb.append("<$tag>$content</$tag>\n")
            }
        }

        return sb.toString()
    }

    private fun buildTextRunContent(para: XSLFTextParagraph): String {
        val sb = StringBuilder()

        val bulletChar = try { para.bulletCharacter } catch (_: Exception) { null }
        if (bulletChar != null && bulletChar.isNotEmpty()) {
            sb.append("$bulletChar ")
        }

        for (run in para.textRuns) {
            var text = HtmlWrapper.escapeHtml(run.rawText ?: continue)
            if (text.isBlank()) continue

            if (run.isBold) text = "<strong>$text</strong>"
            if (run.isItalic) text = "<em>$text</em>"
            if (run.isUnderlined) text = "<u>$text</u>"
            if (run.isStrikethrough) text = "<s>$text</s>"

            sb.append(text)
        }

        return sb.toString()
    }

    private fun convertTable(table: XSLFTable): String {
        val sb = StringBuilder("<table>\n")

        for ((rowIdx, row) in table.rows.withIndex()) {
            sb.append("<tr>")
            for (cell in row.cells) {
                val tag = if (rowIdx == 0) "th" else "td"
                val content = HtmlWrapper.escapeHtml(cell.text ?: "")
                sb.append("<$tag>$content</$tag>")
            }
            sb.append("</tr>\n")
        }

        sb.append("</table>\n")
        return sb.toString()
    }

    private fun convertPicture(shape: XSLFPictureShape): String {
        return try {
            val picData = shape.pictureData
            val base64 = Base64.encodeToString(picData.data, Base64.NO_WRAP)
            val mimeType = picData.contentType ?: "image/png"
            "<p><img src=\"data:$mimeType;base64,$base64\" alt=\"slide image\"></p>\n"
        } catch (_: Exception) {
            "<p><em>[Image]</em></p>\n"
        }
    }

    private fun getSlideNotes(slide: XSLFSlide): String {
        return try {
            val notes = slide.notes ?: return ""
            val sb = StringBuilder()
            for (shape in notes.shapes) {
                if (shape is XSLFTextShape) {
                    val text = shape.text?.trim() ?: continue
                    if (text.isNotEmpty() && !text.contains("Click to edit")) {
                        if (sb.isNotEmpty()) sb.append(" ")
                        sb.append(text)
                    }
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}
