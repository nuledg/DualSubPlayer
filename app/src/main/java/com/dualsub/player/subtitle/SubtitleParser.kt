package com.dualsub.player.subtitle

/** Reads SRT, WebVTT and ASS/SSA text into a list of cues. Styling is removed. */
object SubtitleParser {

    // 00:01:02,345 --> 00:01:04,000   (SRT)   or   01:02.345 --> 01:04.000   (VTT)
    private val TIME_LINE = Regex(
        """((?:\d{1,3}:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d{1,3}:)?\d{1,2}:\d{2}[.,]\d{1,3})"""
    )
    private val HTML_TAGS = Regex("<[^>]*>")
    private val STYLE_BLOCKS = Regex("""\{\\[^}]*\}""") // {\an8}, {\i1}
    private val DRAWING = Regex("""\{[^}]*\\p[1-9][^}]*\}""") // ASS vector drawings

    fun parse(rawText: String, fileName: String): List<Cue> {
        val text = rawText.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isAss = ext == "ass" || ext == "ssa" ||
            (text.contains("[Events]") && text.contains("Dialogue:"))

        val cues = if (isAss) parseAss(text) else parseTimed(text)
        return cues
            .filter { it.text.isNotBlank() && it.endMs > it.startMs }
            .distinct()
            .sortedBy { it.startMs }
    }

    // ---- SRT and VTT share the same shape: a timing line followed by text lines ----

    private fun parseTimed(text: String): List<Cue> {
        val lines = text.split('\n')
        val cues = ArrayList<Cue>()
        var i = 0
        while (i < lines.size) {
            val match = TIME_LINE.find(lines[i])
            if (match == null) {
                i++
                continue
            }
            val start = parseTimestamp(match.groupValues[1])
            val end = parseTimestamp(match.groupValues[2])
            i++

            val textLines = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (TIME_LINE.containsMatchIn(lines[i])) {
                    // The blank line between cues is missing. The last line we took was
                    // really the number of the next cue, so we drop it.
                    if (textLines.isNotEmpty() && textLines.last().trim().all { it.isDigit() }) {
                        textLines.removeAt(textLines.size - 1)
                    }
                    break
                }
                textLines.add(lines[i])
                i++
            }
            cues.add(Cue(start, end, cleanText(textLines.joinToString("\n"))))
        }
        return cues
    }

    // ---- ASS / SSA ----

    private fun parseAss(text: String): List<Cue> {
        var inEvents = false
        var startIdx = 1
        var endIdx = 2
        var textIdx = 9
        var fieldCount = 10 // default: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
        val cues = ArrayList<Cue>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue

            if (line.startsWith("Format:", ignoreCase = true)) {
                val names = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                val s = names.indexOf("start")
                val e = names.indexOf("end")
                val t = names.indexOf("text")
                if (s >= 0 && e >= 0 && t >= 0) {
                    startIdx = s
                    endIdx = e
                    textIdx = t
                    fieldCount = names.size
                }
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue

            // The text is the last field and may contain commas, so we limit the split.
            val fields = line.substringAfter(':').trimStart().split(',', limit = fieldCount)
            if (fields.size < fieldCount) continue

            val cleaned = assText(fields[textIdx]) ?: continue
            cues.add(
                Cue(
                    startMs = parseTimestamp(fields[startIdx]),
                    endMs = parseTimestamp(fields[endIdx]),
                    text = cleaned
                )
            )
        }
        return cues
    }

    /**
     * Turns the text field of an ASS line into plain text.
     * Returns null for vector drawings and empty lines.
     */
    fun assText(rawField: String): String? {
        if (DRAWING.containsMatchIn(rawField)) return null
        val withBreaks = rawField.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
        return cleanText(withBreaks).ifBlank { null }
    }

    // ---- Helpers ----

    /** "00:01:02,345", "01:02.345", "0:00:01.50" -> milliseconds. */
    fun parseTimestamp(ts: String): Long {
        val parts = ts.trim().replace(',', '.').split(':')
        val secPart = parts.last()
        val seconds = secPart.substringBefore('.').toLongOrNull() ?: 0L
        val millis = secPart.substringAfter('.', "").padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        val minutes = if (parts.size >= 2) parts[parts.size - 2].toLongOrNull() ?: 0L else 0L
        val hours = if (parts.size >= 3) parts[parts.size - 3].toLongOrNull() ?: 0L else 0L
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
    }

    /** Removes HTML tags and ASS override blocks, trims lines, drops empty lines. */
    fun cleanText(raw: String): String {
        val noMarkup = raw
            .replace(HTML_TAGS, "")
            .replace(STYLE_BLOCKS, "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        return noMarkup.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
