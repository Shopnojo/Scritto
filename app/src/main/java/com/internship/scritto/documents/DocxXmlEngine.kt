package com.internship.scritto.documents

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.w3c.dom.Node

data class DocxParagraphBlock(
    val index: Int,
    val text: String,
    val style: String
)

data class DocxTableBlock(
    val index: Int,
    val rows: List<List<String>>
)

object DocxXmlEngine {
    private const val WORD_DOCUMENT = "word/document.xml"
    private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    fun read(
        context: Context,
        uri: android.net.Uri
    ): Pair<List<DocxParagraphBlock>, List<DocxTableBlock>> {
        val documentXml = context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var bytes: ByteArray? = null
                while (entry != null) {
                    if (entry.name == WORD_DOCUMENT) {
                        bytes = zip.readBytes()
                        break
                    }
                    entry = zip.nextEntry
                }
                bytes
            }
        } ?: return emptyList<DocxParagraphBlock>() to emptyList()

        val document = parseXml(documentXml)
        val body = document.getElementsByTagNameNS(WORD_NS, "body").item(0)
            ?: return emptyList<DocxParagraphBlock>() to emptyList()

        val paragraphs = mutableListOf<DocxParagraphBlock>()
        val tables = mutableListOf<DocxTableBlock>()

        for (child in body.childElements()) {
            when (child.localName) {
                "p" -> paragraphs += DocxParagraphBlock(
                    paragraphs.size,
                    paragraphText(child),
                    paragraphStyle(child)
                )
                "tbl" -> tables += DocxTableBlock(
                    tables.size,
                    tableRows(child)
                )
            }
        }

        return paragraphs to tables
    }

    fun readText(context: Context, uri: android.net.Uri): String {
        val (paragraphs, tables) = read(context, uri)
        return buildString {
            paragraphs.forEachIndexed { index, paragraph ->
                if (index > 0) append('\n')
                append(paragraph.text)
            }
            tables.forEach { table ->
                table.rows.forEach { row ->
                    if (isNotEmpty()) append('\n')
                    append(row.joinToString("\t"))
                }
            }
        }
    }

    fun save(
        context: Context,
        sourceUri: android.net.Uri,
        paragraphs: List<DocxParagraphBlock>,
        tables: List<DocxTableBlock>,
        output: OutputStream
    ) {
        val source = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Unable to open DOCX")
        val documentXml = source.extractEntry(WORD_DOCUMENT)
            ?: error("DOCX document.xml is missing")

        val document = parseXml(documentXml)
        val body = document.getElementsByTagNameNS(WORD_NS, "body").item(0)
            ?: error("DOCX body is missing")

        val paragraphElements = body.childElements().filter { it.localName == "p" }
        paragraphs.forEach { block ->
            paragraphElements.getOrNull(block.index)?.let { replaceParagraphText(it, block.text) }
        }

        val tableElements = body.childElements().filter { it.localName == "tbl" }
        tables.forEach { block ->
            tableElements.getOrNull(block.index)?.let { table ->
                val rowElements = table.childElements().filter { it.localName == "tr" }
                block.rows.forEachIndexed { rowIndex, row ->
                    rowElements.getOrNull(rowIndex)?.let { rowElement ->
                        val cells = rowElement.childElements().filter { it.localName == "tc" }
                        row.forEachIndexed { columnIndex, value ->
                            cells.getOrNull(columnIndex)?.let { replaceCellText(it, value) }
                        }
                    }
                }
            }
        }

        val updatedXml = serializeXml(document)

        ZipInputStream(source.inputStream()).use { zip ->
            ZipOutputStream(output).use { out ->
                var entry = zip.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    out.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == WORD_DOCUMENT) {
                        out.write(updatedXml)
                    } else {
                        var read = zip.read(buffer)
                        while (read != -1) {
                            out.write(buffer, 0, read)
                            read = zip.read(buffer)
                        }
                    }
                    out.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun replaceParagraphText(paragraph: Element, text: String) {
        val textNodes = paragraph.getElementsByTagNameNS(WORD_NS, "t")
        if (textNodes.length == 0) {
            val run = paragraph.ownerDocument.createElementNS(WORD_NS, "w:r")
            val textNode = paragraph.ownerDocument.createElementNS(WORD_NS, "w:t")
            textNode.appendChild(paragraph.ownerDocument.createTextNode(text))
            run.appendChild(textNode)
            paragraph.appendChild(run)
            return
        }

        textNodes.item(0).textContent = text
        for (index in 1 until textNodes.length) {
            textNodes.item(index).textContent = ""
        }
    }

    private fun replaceCellText(cell: Element, text: String) {
        val paragraphs = cell.childElements().filter { it.localName == "p" }
        val target = paragraphs.firstOrNull()
        if (target == null) {
            val paragraph = cell.ownerDocument.createElementNS(WORD_NS, "w:p")
            val run = cell.ownerDocument.createElementNS(WORD_NS, "w:r")
            val textNode = cell.ownerDocument.createElementNS(WORD_NS, "w:t")
            textNode.appendChild(cell.ownerDocument.createTextNode(text))
            run.appendChild(textNode)
            paragraph.appendChild(run)
            cell.appendChild(paragraph)
            return
        }

        replaceParagraphText(target, text)
        paragraphs.drop(1).forEach { paragraph ->
            val nodes = paragraph.getElementsByTagNameNS(WORD_NS, "t")
            for (index in 0 until nodes.length) nodes.item(index).textContent = ""
        }
    }

    private fun paragraphText(paragraph: Element): String {
        val builder = StringBuilder()
        appendTextContent(paragraph, builder)
        return builder.toString()
    }

    private fun appendTextContent(node: Node, builder: StringBuilder) {
        var child = node.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                when (element.localName) {
                    "t" -> builder.append(element.textContent.orEmpty())
                    "tab" -> builder.append('\t')
                    "br", "cr" -> builder.append('\n')
                    else -> appendTextContent(element, builder)
                }
            }
            child = child.nextSibling
        }
    }

    private fun paragraphStyle(paragraph: Element): String =
        paragraph.getElementsByTagNameNS(WORD_NS, "pStyle")
            .item(0)
            ?.let { it as Element }
            ?.getAttributeNS(WORD_NS, "val")
            .orEmpty()

    private fun tableRows(table: Element): List<List<String>> =
        table.childElements()
            .filter { it.localName == "tr" }
            .map { row ->
                row.childElements()
                    .filter { it.localName == "tc" }
                    .map { cell -> paragraphText(cell) }
            }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun serializeXml(document: org.w3c.dom.Document): ByteArray {
        val output = ByteArrayOutputStream()
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        transformer.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        var child = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) result += child as Element
            child = child.nextSibling
        }
        return result
    }

    private fun ByteArray.extractEntry(name: String): ByteArray? {
        ZipInputStream(inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return null
    }
}
