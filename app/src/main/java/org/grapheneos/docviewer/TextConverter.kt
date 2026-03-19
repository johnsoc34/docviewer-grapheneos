package org.grapheneos.docviewer

import java.io.InputStream

object TextConverter {

    fun convert(inputStream: InputStream, fileName: String): String {
        val text = inputStream.bufferedReader().readText()
        val ext = fileName.substringAfterLast('.', "").lowercase()

        return when (ext) {
            "csv" -> convertCsv(text)
            "tsv" -> convertTsv(text)
            else -> convertPlainText(text)
        }
    }

    private fun convertPlainText(text: String): String {
        val body = "<pre style=\"white-space:pre-wrap;word-wrap:break-word;font-size:0.9rem;line-height:1.5\">" +
            HtmlWrapper.escapeHtml(text) + "</pre>"
        return HtmlWrapper.wrap("Text", body)
    }

    private fun convertCsv(text: String): String {
        return convertDelimited(text, ',')
    }

    private fun convertTsv(text: String): String {
        return convertDelimited(text, '\t')
    }

    private fun convertDelimited(text: String, delimiter: Char): String {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return HtmlWrapper.wrap("Spreadsheet", "<p><em>Empty file</em></p>")

        val sb = StringBuilder("<table>\n")

        for ((idx, line) in lines.withIndex()) {
            val cells = parseLine(line, delimiter)
            sb.append("<tr>")
            for (cell in cells) {
                val trimmed = cell.trim()
                val isNumeric = trimmed.toDoubleOrNull() != null
                if (idx == 0) {
                    sb.append("<th>")
                    sb.append(HtmlWrapper.escapeHtml(trimmed))
                    sb.append("</th>")
                } else if (isNumeric) {
                    sb.append("<td class=\"num\">")
                    sb.append(HtmlWrapper.escapeHtml(trimmed))
                    sb.append("</td>")
                } else {
                    sb.append("<td>")
                    sb.append(HtmlWrapper.escapeHtml(trimmed))
                    sb.append("</td>")
                }
            }
            sb.append("</tr>\n")
        }

        sb.append("</table>\n")

        val extraCss = "td.num { text-align: right; font-variant-numeric: tabular-nums; }"
        return HtmlWrapper.wrap("Spreadsheet", sb.toString(), extraCss)
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
