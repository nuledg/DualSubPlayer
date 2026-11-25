package com.dualsub.player.subtitle

/**
 * Reads subtitle tracks from Matroska and WebM files (.mkv, .webm).
 *
 * The file is a tree of elements. We walk it as a flat list: for "container" elements we step
 * inside, for everything else we jump over the data. This also works for clusters of unknown size.
 */
internal object MkvSubtitleReader {

    private const val ID_SEGMENT = 0x18538067L
    private const val ID_INFO = 0x1549A966L
    private const val ID_TIMECODE_SCALE = 0x2AD7B1L
    private const val ID_TRACKS = 0x1654AE6BL
    private const val ID_TRACK_ENTRY = 0xAEL
    private const val ID_TRACK_NUMBER = 0xD7L
    private const val ID_TRACK_TYPE = 0x83L
    private const val ID_CODEC_ID = 0x86L
    private const val ID_LANGUAGE = 0x22B59CL
    private const val ID_LANGUAGE_BCP47 = 0x22B59DL
    private const val ID_NAME = 0x536EL
    private const val ID_FLAG_DEFAULT = 0x88L
    private const val ID_FLAG_FORCED = 0x55AAL
    private const val ID_CONTENT_ENCODINGS = 0x6D80L
    private const val ID_CONTENT_ENCODING = 0x6240L
    private const val ID_CONTENT_COMPRESSION = 0x5034L
    private const val ID_COMP_ALGO = 0x4254L
    private const val ID_COMP_SETTINGS = 0x4255L
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_CLUSTER_TIMECODE = 0xE7L
    private const val ID_SIMPLE_BLOCK = 0xA3L
    private const val ID_BLOCK_GROUP = 0xA0L
    private const val ID_BLOCK = 0xA1L
    private const val ID_BLOCK_DURATION = 0x9BL

    // Other top-level elements. When one of them appears, the Tracks element is over.
    private val OTHER_LEVEL1 = setOf(0x1C53BB6BL, 0x1254C367L, 0x1043A770L, 0x1941A469L, 0x114D9B74L)

    private const val TRACK_TYPE_SUBTITLE = 17L

    private class RawTrack {
        var number = -1L
        var type = -1L
        var codecId = ""
        var language: String? = null
        var languageBcp47: String? = null
        var name: String? = null
        var isDefault = true
        var isForced = false
        var compAlgo = -1L
        var compSettings: ByteArray? = null
    }

    private class RawBlock(val startMs: Long, var durationMs: Long, val data: ByteArray)

    private class ScanResult(val tracks: List<RawTrack>, val blocks: List<RawBlock>)

    fun listTracks(source: ByteSource): List<EmbeddedTrack> {
        val scan = scan(source, targetTrack = -1L, readBlocks = false) {}
        return toPublicTracks(scan.tracks)
    }

    fun readCues(source: ByteSource, track: EmbeddedTrack, checkCancelled: () -> Unit): List<Cue> {
        val scan = scan(source, track.id, readBlocks = true, checkCancelled)
        val raw = scan.tracks.firstOrNull { it.number == track.id } ?: return emptyList()
        val codec = codecName(raw.codecId)

        val items = ArrayList<Triple<Long, Long, String>>()
        for (block in scan.blocks) {
            val payload = decompress(raw, block.data) ?: continue
            val text = decodePayload(codec, payload) ?: continue
            items.add(Triple(block.startMs, block.durationMs, text))
        }
        return EmbeddedSubtitles.fixMissingEnds(items)
    }

    // ---- Scanning ----

    private fun scan(
        source: ByteSource,
        targetTrack: Long,
        readBlocks: Boolean,
        checkCancelled: () -> Unit
    ): ScanResult {
        // Small buffer: we jump over most blocks, so a big read would only waste disk reads.
        val r = ByteReader(source, bufferSize = 1024)
        val tracks = ArrayList<RawTrack>()
        val blocks = ArrayList<RawBlock>()
        val fileSize = source.size

        var current: RawTrack? = null
        var timecodeScale = 1_000_000L
        var clusterTimecode = 0L
        var groupDuration = -1L
        var pending: RawBlock? = null
        var counter = 0

        fun finishTrack() {
            current?.let { tracks.add(it) }
            current = null
        }

        fun flush() {
            pending?.let { blocks.add(it) }
            pending = null
        }

        fun toMs(units: Long): Long = units * timecodeScale / 1_000_000L

        fun readString(size: Long): String? {
            if (size < 0 || size > 4096) return null
            return r.readBytes(size.toInt())?.toString(Charsets.UTF_8)?.trimEnd('\u0000')
        }

        loop@ while (r.position < fileSize) {
            if ((++counter and 0x1FF) == 0) checkCancelled()

            val id = r.ebmlId()
            if (id < 0) break
            val size = r.ebmlSize()
            if (size == -2L) break
            val dataStart = r.position
            val dataEnd = if (size >= 0) dataStart + size else Long.MAX_VALUE

            when (id) {
                // Containers: step inside.
                ID_SEGMENT, ID_INFO, ID_TRACKS, ID_CONTENT_ENCODINGS, ID_CONTENT_ENCODING,
                ID_CONTENT_COMPRESSION -> Unit

                ID_TRACK_ENTRY -> {
                    finishTrack()
                    current = RawTrack()
                }

                ID_CLUSTER -> {
                    flush()
                    finishTrack()
                    if (!readBlocks) break@loop // track list is complete, no need to read clusters
                    clusterTimecode = 0L
                }

                ID_BLOCK_GROUP -> {
                    flush()
                    groupDuration = -1L
                }

                ID_TIMECODE_SCALE -> {
                    val v = r.uint(size.toInt())
                    if (v > 0) timecodeScale = v
                }
                ID_CLUSTER_TIMECODE -> clusterTimecode = r.uint(size.toInt())

                ID_TRACK_NUMBER -> current?.number = r.uint(size.toInt())
                ID_TRACK_TYPE -> current?.type = r.uint(size.toInt())
                ID_FLAG_DEFAULT -> current?.isDefault = r.uint(size.toInt()) != 0L
                ID_FLAG_FORCED -> current?.isForced = r.uint(size.toInt()) != 0L
                ID_COMP_ALGO -> current?.compAlgo = r.uint(size.toInt())
                ID_CODEC_ID -> current?.codecId = readString(size) ?: ""
                ID_LANGUAGE -> current?.language = readString(size)
                ID_LANGUAGE_BCP47 -> current?.languageBcp47 = readString(size)
                ID_NAME -> current?.name = readString(size)
                ID_COMP_SETTINGS -> {
                    if (size in 0..4096) current?.compSettings = r.readBytes(size.toInt())
                }

                ID_SIMPLE_BLOCK, ID_BLOCK -> {
                    flush()
                    if (readBlocks && size > 0) {
                        val blockStart = r.position
                        val trackNumber = r.ebmlSize()
                        val relative = r.int16()
                        val flags = r.u8()
                        val headerLength = r.position - blockStart
                        val laced = flags >= 0 && (flags and 0x06) != 0
                        if (trackNumber == targetTrack && !laced && flags >= 0) {
                            val data = r.readBytes((size - headerLength).toInt())
                            if (data != null) {
                                val block = RawBlock(
                                    startMs = toMs(clusterTimecode + relative),
                                    durationMs = if (id == ID_BLOCK && groupDuration >= 0) toMs(groupDuration) else -1L,
                                    data = data
                                )
                                if (id == ID_BLOCK) pending = block else blocks.add(block)
                            }
                        }
                    }
                    if (size < 0) break@loop
                    r.position = dataEnd
                }

                ID_BLOCK_DURATION -> {
                    groupDuration = r.uint(size.toInt())
                    pending?.durationMs = toMs(groupDuration)
                }

                else -> {
                    if (id in OTHER_LEVEL1) finishTrack()
                    if (size < 0) break@loop // unknown size on an element we cannot step into
                    r.position = dataEnd
                }
            }
        }
        flush()
        finishTrack()
        return ScanResult(tracks, blocks)
    }

    // ---- Converting ----

    private fun toPublicTracks(raw: List<RawTrack>): List<EmbeddedTrack> {
        val result = ArrayList<EmbeddedTrack>()
        var index = 0
        for (t in raw) {
            if (t.type != TRACK_TYPE_SUBTITLE) continue
            val codec = codecName(t.codecId)
            result.add(
                EmbeddedTrack(
                    container = ContainerType.MKV,
                    id = t.number,
                    index = index++,
                    codec = codec,
                    language = LanguageCodes.normalize(t.languageBcp47 ?: t.language ?: "eng"),
                    title = t.name?.takeIf { it.isNotBlank() },
                    isText = codec in TEXT_CODECS,
                    isDefault = t.isDefault,
                    isForced = t.isForced
                )
            )
        }
        return result
    }

    private val TEXT_CODECS = setOf("subrip", "ass", "ssa", "webvtt")

    private fun codecName(codecId: String): String = when (codecId) {
        "S_TEXT/UTF8", "S_TEXT/ASCII" -> "subrip"
        "S_TEXT/ASS" -> "ass"
        "S_TEXT/SSA" -> "ssa"
        "S_TEXT/WEBVTT", "D_WEBVTT/SUBTITLES", "D_WEBVTT/CAPTIONS" -> "webvtt"
        "S_HDMV/PGS" -> "pgs"
        "S_VOBSUB" -> "vobsub"
        "S_DVBSUB" -> "dvbsub"
        else -> codecId.ifEmpty { "unknown" }
    }

    /** Some old files compress subtitle blocks (zlib, or header stripping). */
    private fun decompress(track: RawTrack, data: ByteArray): ByteArray? = when (track.compAlgo) {
        -1L -> data
        0L -> EmbeddedSubtitles.inflate(data)
        3L -> (track.compSettings ?: ByteArray(0)) + data
        else -> null
    }

    private fun decodePayload(codec: String, payload: ByteArray): String? {
        val text = String(payload, Charsets.UTF_8)
        return when (codec) {
            "subrip", "webvtt" -> SubtitleParser.cleanText(text).ifBlank { null }
            "ass", "ssa" -> {
                // Block format: ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text
                val fields = text.split(',', limit = 9)
                if (fields.size < 9) null else SubtitleParser.assText(fields[8])
            }
            else -> null
        }
    }
}
