package com.proj.Musicality.lyrics

import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Apple-Music-style TTML into a flat list of timed lines with optional
 * word-level timings, then serializes to extended LRC for the rest of the pipeline.
 *
 * English-only port: ignores background (`x-bg`) vocals and multi-agent tags.
 */
object TTMLParser {

    private const val TAG = "TTMLParser"
    private const val TTML_PARAMETER_NS = "http://www.w3.org/ns/ttml#parameter"

    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>,
    )

    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean = true,
    )

    private data class SpanInfo(
        val text: String,
        val startTime: Double,
        val endTime: Double,
        val hasTrailingSpace: Boolean,
    )

    private fun getAttr(el: Element, localName: String): String {
        val ttm = el.getAttribute("ttm:$localName")
        if (ttm.isNotEmpty()) return ttm
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS("http://www.w3.org/ns/ttml#metadata", localName)
    }

    private fun timingAttr(el: Element, localName: String): String {
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        val param = el.getAttributeNS(TTML_PARAMETER_NS, localName)
        if (param.isNotEmpty()) return param
        return ""
    }

    private fun findFirstSpanBegin(p: Element): String? {
        var child = p.firstChild
        var best: String? = null
        var bestSeconds = Double.POSITIVE_INFINITY
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    val b = timingAttr(child, "begin")
                    if (b.isNotEmpty()) {
                        val s = parseTime(b)
                        if (s < bestSeconds) {
                            bestSeconds = s
                            best = b
                        }
                    }
                }
            }
            child = child.nextSibling
        }
        return best
    }

    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ttml.byteInputStream())
            val root = doc.documentElement

            var globalOffset = 0.0
            val head = findChild(root, "head")
            if (head != null) {
                val meta = findChild(head, "metadata")
                if (meta != null) {
                    val audio = findChild(meta, "audio")
                    if (audio != null) {
                        globalOffset = audio.getAttribute("lyricOffset").toDoubleOrNull() ?: 0.0
                    }
                }
            }

            val body = findChild(root, "body")
            if (body != null) walk(body, lines, globalOffset)
        } catch (e: Exception) {
            Log.e(TAG, "parseTTML failed", e)
            return emptyList()
        }
        return lines
    }

    private fun findChild(parent: Element, localName: String): Element? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == localName) return child
            }
            child = child.nextSibling
        }
        return null
    }

    private fun walk(element: Element, lines: MutableList<ParsedLine>, offset: Double) {
        val name = element.localName ?: element.nodeName.substringAfterLast(':')
        if (name == "p") {
            parseP(element, lines, offset)
            return
        }
        var child = element.firstChild
        while (child != null) {
            if (child is Element) walk(child, lines, offset)
            child = child.nextSibling
        }
    }

    private fun parseP(p: Element, lines: MutableList<ParsedLine>, offset: Double) {
        var begin = p.getAttribute("begin")
        if (begin.isEmpty()) begin = p.getAttributeNS(TTML_PARAMETER_NS, "begin")
        if (begin.isEmpty()) begin = findFirstSpanBegin(p) ?: return

        val startTime = parseTime(begin) + offset
        val spanInfos = mutableListOf<SpanInfo>()
        val isPBackground = getAttr(p, "role") == "x-bg"
        if (isPBackground) return // skip BG lines entirely (no duet rendering)

        var child = p.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    val role = getAttr(child, "role")
                    when (role) {
                        "x-bg", "x-translation", "x-roman" -> {} // skip
                        else -> parseWordSpan(child, offset, spanInfos)
                    }
                }
            }
            child = child.nextSibling
        }

        val words = mergeSpansIntoWords(spanInfos)
        val lineText = if (words.isEmpty()) getDirectText(p).trim() else buildLineText(words)
        if (lineText.isNotEmpty()) lines.add(ParsedLine(lineText, startTime, words))
    }

    private fun parseWordSpan(span: Element, offset: Double, spanInfos: MutableList<SpanInfo>) {
        val begin = timingAttr(span, "begin")
        val end = timingAttr(span, "end")
        val text = span.textContent ?: ""
        if (begin.isNotEmpty() && end.isNotEmpty()) {
            val next = span.nextSibling
            val space = (text.isNotEmpty() && text.last().isWhitespace()) ||
                (next?.nodeType == Node.TEXT_NODE && next.textContent?.firstOrNull()?.isWhitespace() == true)
            spanInfos.add(SpanInfo(text, parseTime(begin) + offset, parseTime(end) + offset, space))
        }
    }

    private fun getDirectText(el: Element): String {
        val sb = StringBuilder()
        var child = el.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) sb.append(child.textContent)
            else if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                val role = getAttr(child, "role")
                if (name == "span" && role != "x-bg" && role != "x-translation" && role != "x-roman") {
                    sb.append(child.textContent)
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun buildLineText(words: List<ParsedWord>) = buildString {
        words.forEachIndexed { i, w ->
            append(w.text)
            if (w.hasTrailingSpace && !w.text.endsWith('-') && i < words.lastIndex) append(" ")
        }
    }.trim()

    private fun mergeSpansIntoWords(spanInfos: List<SpanInfo>): List<ParsedWord> {
        if (spanInfos.isEmpty()) return emptyList()
        val words = mutableListOf<ParsedWord>()
        var text = StringBuilder(spanInfos[0].text)
        var start = spanInfos[0].startTime
        var end = spanInfos[0].endTime

        for (i in 1 until spanInfos.size) {
            val prev = spanInfos[i - 1]
            val curr = spanInfos[i]
            if (prev.hasTrailingSpace && !prev.text.endsWith('-')) {
                words.add(ParsedWord(text.toString(), start, end, true))
                text = StringBuilder(curr.text)
                start = curr.startTime
                end = curr.endTime
            } else {
                text.append(curr.text)
                end = curr.endTime
            }
        }
        words.add(ParsedWord(text.toString(), start, end, spanInfos.last().hasTrailingSpace))
        return words.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
    }

    fun toLRC(lines: List<ParsedLine>): String {
        val sb = StringBuilder(lines.size * 128)
        for (line in lines) {
            sb.append(formatLrcTime(line.startTime)).append(line.text).append('\n')
            if (line.words.isNotEmpty()) {
                sb.append('<')
                line.words.forEachIndexed { i, w ->
                    sb.append(w.text).append(':').append(w.startTime).append(':').append(w.endTime)
                    if (i < line.words.lastIndex) sb.append('|')
                }
                sb.append(">\n")
            }
        }
        return sb.toString()
    }

    private fun formatLrcTime(time: Double): String {
        val ms = (time * 1000).toLong()
        val m = ms / 60000
        val s = (ms % 60000) / 1000
        val c = (ms % 1000) / 10
        val sb = StringBuilder(10)
        sb.append('[')
        if (m < 10) sb.append('0')
        sb.append(m).append(':')
        if (s < 10) sb.append('0')
        sb.append(s).append('.')
        if (c < 10) sb.append('0')
        sb.append(c).append(']')
        return sb.toString()
    }

    private fun parseTime(time: String): Double {
        val t = time.trim()
        val c1 = t.indexOf(':')
        if (c1 != -1) {
            val c2 = t.lastIndexOf(':')
            return if (c1 == c2) {
                (t.substring(0, c1).toIntOrNull() ?: 0) * 60.0 +
                    (t.substring(c1 + 1).toDoubleOrNull() ?: 0.0)
            } else {
                (t.substring(0, c1).toIntOrNull() ?: 0) * 3600.0 +
                    (t.substring(c1 + 1, c2).toIntOrNull() ?: 0) * 60.0 +
                    (t.substring(c2 + 1).toDoubleOrNull() ?: 0.0)
            }
        }
        if (t.endsWith("ms")) return (t.substring(0, t.length - 2).toDoubleOrNull() ?: 0.0) / 1000.0
        val s = if (t.endsWith("s") || t.endsWith("m") || t.endsWith("h")) t.substring(0, t.length - 1) else t
        val v = s.toDoubleOrNull() ?: 0.0
        return when {
            t.endsWith("m") -> v * 60.0
            t.endsWith("h") -> v * 3600.0
            else -> v
        }
    }
}
