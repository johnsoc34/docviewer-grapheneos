package org.grapheneos.docviewer

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converter for OpenDocument Format files (.odt, .ods, .odp)
 * These are ZIP archives containing XML files.
 * We parse content.xml directly.
 */
object OdfConverter {

    fun convert(inputStream: InputStream, extension: String): String {
        val contentXml = extractContentXml(inputStream)
            ?: throw Exception("Could not read document content")

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(contentXml.byteInputStream())

        return when (extension) {
            "odt" -> convertOdt(doc.documentElement)
            "ods" -> convertOds(doc.documentElement)
            "odp" -> convertOdp(doc.documentElement)
            else -> throw Exception("Unsupported ODF format: .$extension")
        }
    }

    private fun extractContentXml(inputStream: InputStream): String? {
        val zis = ZipInputStream(inputStream)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "content.xml") {
                return zis.bufferedReader().readText()
            }
            entry = zis.nextEntry
        }
        return null
    }

    // ── ODT (Text Document) ──────────────────────────────────────────

    private fun convertOdt(root: Element): String {
        val body = StringBuilder()
        val bodyElement = findElement(root, "body")
        val textElement = bodyElement?.let { findElement(it, "text") } ?: root

        processOdtChildren(textElement, body)

        return HtmlWrapper.wrap("Document", body.toString())
    }

    private fun processOdtChildren(element: Element, sb: StringBuilder) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            val localName = el.localName ?: continue

            when (localName) {
                "h" -> {
                    val level = el.getAttributeNS(
                        "urn:oasis:names:tc:opendocument:xmlns:text:1.0", "outline-level"
                    ).toIntOrNull() ?: 1
                    val tag = "h${level.coerceIn(1, 6)}"
                    sb.append("<$tag>${getTextContent(el)}</$tag>\n")
                }
                "p" -> {
                    val text = getTextContent(el)
                    if (text.isBlank()) {
                        sb.append("<br>\n")
                    } else {
                        sb.append("<p>$text</p>\n")
                    }
                }
                "list" -> {
                    sb.append("<ul>\n")
                    processOdtList(el, sb)
                    sb.append("</ul>\n")
                }
                "table" -> {
                    sb.append(convertOdtTable(el))
                }
                "section" -> {
                    processOdtChildren(el, sb)
                }
            }
        }
    }

    private fun processOdtList(listElement: Element, sb: StringBuilder) {
        val children = listElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            if (el.localName == "list-item") {
                val itemChildren = el.childNodes
                for (j in 0 until itemChildren.length) {
                    val itemNode = itemChildren.item(j)
                    if (itemNode.nodeType != Node.ELEMENT_NODE) continue
                    val itemEl = itemNode as Element
                    when (itemEl.localName) {
                        "p" -> sb.append("<li>${getTextContent(itemEl)}</li>\n")
                        "list" -> {
                            sb.append("<ul>\n")
                            processOdtList(itemEl, sb)
                            sb.append("</ul>\n")
                        }
                    }
                }
            }
        }
    }

    private fun convertOdtTable(tableElement: Element): String {
        val sb = StringBuilder("<table>\n")
        val children = tableElement.childNodes

        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val el = node as Element
            if (el.localName == "table-row") {
                sb.append("<tr>")
                val cells = el.childNodes
                for (j in 0 until cells.length) {
                    val cellNode = cells.item(j)
                    if (cellNode.nodeType != Node.ELEMENT_NODE) continue
                    val cellEl = cellNode as Element
                    if (cellEl.localName == "table-cell") {
                        val content = getTextContent(cellEl)
                        sb.append("<td>${HtmlWrapper.escapeHtml(content)}</td>")
                    }
                }
                sb.append("</tr>\n")
            }
        }

        sb.append("</table>\n")
        return sb.toString()
    }

    // ── ODS (Spreadsheet) ────────────────────────────────────────────

    private fun convertOds(root: Element): String {
        val body = StringBuilder()
        val bodyElement = findElement(root, "body")
        val spreadsheet = bodyElement?.let { findElement(it, "spreadsheet") } ?: root

        val tables = getChildElements(spreadsheet, "table")

        for (table in tables) {
            val tableName = table.getAttributeNS(
                "urn:oasis:names:tc:opendocument:xmlns:table:1.0", "name"
            ).ifEmpty { "Sheet" }

            body.append("<div class=\"sheet-section\">\n")
            if (tables.size > 1) {
                body.append("<div class=\"sheet-name\">${HtmlWrapper.escapeHtml(tableName)}</div>\n")
            }

            body.append("<table>\n")
            val rows = getChildElements(table, "table-row")
            for (row in rows) {
                body.append("<tr>")
                val cells = getChildElements(row, "table-cell")
                for (cell in cells) {
                    // Handle repeated columns
                    val repeat = cell.getAttributeNS(
                        "urn:oasis:names:tc:opendocument:xmlns:table:1.0",
                        "number-columns-repeated"
                    ).toIntOrNull() ?: 1

                    val content = getTextContent(cell)
                    val times = if (content.isBlank()) minOf(repeat, 1) else repeat
                    for (r in 0 until times) {
                        body.append("<td>${HtmlWrapper.escapeHtml(content)}</td>")
                    }
                }
                body.append("</tr>\n")
            }
            body.append("</table>\n")
            body.append("</div>\n")
        }

        return HtmlWrapper.wrap("Spreadsheet", body.toString(),
            "td.num { text-align: right; }")
    }

    // ── ODP (Presentation) ───────────────────────────────────────────

    private fun convertOdp(root: Element): String {
        val body = StringBuilder()
        val bodyElement = findElement(root, "body")
        val presentation = bodyElement?.let { findElement(it, "presentation") } ?: root

        val pages = getChildElements(presentation, "page")

        for ((idx, page) in pages.withIndex()) {
            body.append("<div class=\"slide\">\n")
            body.append("<div class=\"slide-number\">Slide ${idx + 1} of ${pages.size}</div>\n")

            val frames = getChildElements(page, "frame")
            for (frame in frames) {
                val textBoxes = getChildElements(frame, "text-box")
                for (textBox in textBoxes) {
                    processOdtChildren(textBox, body)
                }
            }

            body.append("</div>\n")
        }

        return HtmlWrapper.wrap("Presentation", body.toString())
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun getTextContent(element: Element): String {
        val sb = StringBuilder()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            when (node.nodeType) {
                Node.TEXT_NODE -> sb.append(node.textContent)
                Node.ELEMENT_NODE -> {
                    val el = node as Element
                    when (el.localName) {
                        "s" -> sb.append(" ")   // space
                        "tab" -> sb.append("\t")
                        "line-break" -> sb.append("\n")
                        "span", "a", "bookmark", "bookmark-start", "bookmark-end" ->
                            sb.append(getTextContent(el))
                        "p" -> {
                            if (sb.isNotEmpty()) sb.append(" ")
                            sb.append(getTextContent(el))
                        }
                        else -> sb.append(getTextContent(el))
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun findElement(parent: Element, localName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val el = node as Element
                if (el.localName == localName) return el
                val found = findElement(el, localName)
                if (found != null) return found
            }
        }
        return null
    }

    private fun getChildElements(parent: Element, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && (node as Element).localName == localName) {
                result.add(node)
            }
        }
        return result
    }
}
