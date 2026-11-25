package com.dualsub.player.subtitle

/**
 * Reads mov_text (tx3g) subtitle tracks from MP4, M4V and MOV files.
 * MP4 keeps a table for every track that says where each sample is and how long it lasts.
 */
internal object Mp4SubtitleReader {

    private class Trak {
        var id = 0L
        var handler = ""
        var timescale = 1000L
        var language: String? = null
        var format = ""
        var sttsCount = 0
        var stts: LongArray = LongArray(0) // pairs: sampleCount, sampleDelta
        var fixedSampleSize = 0L
        var sampleCount = 0
        var sizes: IntArray = IntArray(0)
        var stsc: LongArray = LongArray(0) // triples: firstChunk, samplesPerChunk, descriptionIndex
        var chunkOffsets: LongArray = LongArray(0)
    }

    fun listTracks(source: ByteSource): List<EmbeddedTrack> {
        val result = ArrayList<EmbeddedTrack>()
        var index = 0
        for (t in parse(source)) {
            if (!isSubtitleHandler(t.handler)) continue
            val isTx3g = t.format == "tx3g" || t.format == "text"
            result.add(
                EmbeddedTrack(
                    container = ContainerType.MP4,
                    id = t.id,
                    index = index++,
                    codec = if (isTx3g) "mov_text" else t.format.ifBlank { "unknown" },
                    language = LanguageCodes.normalize(t.language),
                    title = null,
                    isText = isTx3g,
                    isDefault = false,
                    isForced = false
                )
            )
        }
        return result
    }

    fun readCues(source: ByteSource, track: EmbeddedTrack): List<Cue> {
        val t = parse(source).firstOrNull { it.id == track.id } ?: return emptyList()
        val r = ByteReader(source)
        val count = t.sampleCount
        if (count == 0 || t.chunkOffsets.isEmpty()) return emptyList()

        // 1) start time and duration of every sample, from the "stts" table
        val starts = LongArray(count)
        val durations = LongArray(count)
        var si = 0
        var time = 0L
        var e = 0
        while (e < t.sttsCount && si < count) {
            val n = t.stts[e * 2]
            val delta = t.stts[e * 2 + 1]
            var k = 0L
            while (k < n && si < count) {
                starts[si] = time
                durations[si] = delta
                time += delta
                si++
                k++
            }
            e++
        }

        // 2) file offset of every sample, from "stsc" (samples per chunk), "stco" (chunk offsets), "stsz" (sizes)
        val offsets = LongArray(count)
        var sample = 0
        var stscIndex = 0
        val stscEntries = t.stsc.size / 3
        for (chunk in t.chunkOffsets.indices) {
            while (stscIndex + 1 < stscEntries && t.stsc[(stscIndex + 1) * 3] <= chunk + 1) stscIndex++
            val perChunk = t.stsc[stscIndex * 3 + 1].toInt()
            var offset = t.chunkOffsets[chunk]
            for (j in 0 until perChunk) {
                if (sample >= count) break
                offsets[sample] = offset
                offset += t.sizes[sample]
                sample++
            }
        }

        // 3) read the text of each sample: 2 bytes length, then the text
        val items = ArrayList<Triple<Long, Long, String>>()
        for (i in 0 until sample) {
            val size = t.sizes[i]
            if (size < 2) continue
            r.position = offsets[i]
            val length = r.uint(2).toInt()
            if (length <= 0) continue // empty sample = nothing on screen
            val bytes = r.readBytes(minOf(length, size - 2)) ?: continue
            val charset = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
                Charsets.UTF_16BE else Charsets.UTF_8
            val text = SubtitleParser.cleanText(String(bytes, charset))
            if (text.isBlank()) continue
            val startMs = starts[i] * 1000L / t.timescale
            val durationMs = durations[i] * 1000L / t.timescale
            items.add(Triple(startMs, durationMs, text))
        }
        return EmbeddedSubtitles.fixMissingEnds(items)
    }

    private fun isSubtitleHandler(handler: String) =
        handler == "sbtl" || handler == "text" || handler == "subt"

    // ---- Box parsing ----

    private fun parse(source: ByteSource): List<Trak> {
        val r = ByteReader(source)
        val traks = ArrayList<Trak>()
        forEachBox(r, 0L, source.size) { type, start, end ->
            if (type == "moov") {
                forEachBox(r, start, end) { t2, s2, e2 ->
                    if (t2 == "trak") traks.add(parseTrak(r, s2, e2))
                }
            }
        }
        return traks
    }

    private fun parseTrak(r: ByteReader, start: Long, end: Long): Trak {
        val t = Trak()
        forEachBox(r, start, end) { type, s, e ->
            when (type) {
                "tkhd" -> {
                    r.position = s
                    val version = r.u8()
                    r.position = s + 4 + if (version == 1) 16 else 8
                    t.id = r.uint(4)
                }
                "mdia" -> forEachBox(r, s, e) { t2, s2, e2 ->
                    when (t2) {
                        "mdhd" -> {
                            r.position = s2
                            val version = r.u8()
                            r.position = s2 + 4 + if (version == 1) 16 else 8
                            t.timescale = r.uint(4).coerceAtLeast(1L)
                            r.position += if (version == 1) 8 else 4 // duration
                            t.language = decodeLanguage(r.uint(2).toInt())
                        }
                        "hdlr" -> {
                            r.position = s2 + 8
                            t.handler = r.readBytes(4)?.toString(Charsets.ISO_8859_1) ?: ""
                        }
                        "minf" -> forEachBox(r, s2, e2) { t3, s3, e3 ->
                            if (t3 == "stbl") parseStbl(r, t, s3, e3)
                        }
                    }
                }
            }
        }
        return t
    }

    private fun parseStbl(r: ByteReader, t: Trak, start: Long, end: Long) {
        forEachBox(r, start, end) { type, s, _ ->
            when (type) {
                "stsd" -> {
                    r.position = s + 12 // version/flags, entry count, entry size
                    t.format = r.readBytes(4)?.toString(Charsets.ISO_8859_1) ?: ""
                }
                "stts" -> {
                    r.position = s + 4
                    val n = r.uint(4).toInt().coerceIn(0, 10_000_000)
                    t.sttsCount = n
                    t.stts = LongArray(n * 2)
                    for (i in 0 until n * 2) t.stts[i] = r.uint(4)
                }
                "stsz" -> {
                    r.position = s + 4
                    t.fixedSampleSize = r.uint(4)
                    val n = r.uint(4).toInt().coerceIn(0, 10_000_000)
                    t.sampleCount = n
                    t.sizes = IntArray(n) { if (t.fixedSampleSize != 0L) t.fixedSampleSize.toInt() else r.uint(4).toInt() }
                }
                "stsc" -> {
                    r.position = s + 4
                    val n = r.uint(4).toInt().coerceIn(0, 10_000_000)
                    t.stsc = LongArray(n * 3)
                    for (i in 0 until n * 3) t.stsc[i] = r.uint(4)
                }
                "stco", "co64" -> {
                    r.position = s + 4
                    val n = r.uint(4).toInt().coerceIn(0, 10_000_000)
                    val width = if (type == "co64") 8 else 4
                    t.chunkOffsets = LongArray(n) { r.uint(width) }
                }
            }
        }
    }

    /** MP4 stores the language as three 5-bit letters ("eng", "per"). */
    private fun decodeLanguage(packed: Int): String? {
        if (packed == 0) return null
        val chars = charArrayOf(
            (((packed shr 10) and 0x1F) + 0x60).toChar(),
            (((packed shr 5) and 0x1F) + 0x60).toChar(),
            ((packed and 0x1F) + 0x60).toChar()
        )
        return String(chars)
    }

    /** Calls [visit] for every box in the range. After each call the reader moves to the next box. */
    private fun forEachBox(r: ByteReader, start: Long, end: Long, visit: (String, Long, Long) -> Unit) {
        var pos = start
        while (pos + 8 <= end) {
            r.position = pos
            var size = r.uint(4)
            val type = r.readBytes(4)?.toString(Charsets.ISO_8859_1) ?: return
            var header = 8L
            if (size == 1L) {
                size = r.uint(8)
                header = 16L
            } else if (size == 0L) {
                size = end - pos
            }
            if (size < header || pos + size > end) return
            visit(type, pos + header, pos + size)
            pos += size
        }
    }
}
