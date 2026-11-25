package com.dualsub.player.subtitle

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

enum class ContainerType { MKV, MP4 }

/**
 * A subtitle track inside a video file.
 * [isText] is true for formats that carry text with timing (SRT, ASS, WebVTT, mov_text).
 * Image formats (PGS, VobSub, DVB) are false: they can be shown but give no sentence timing.
 */
data class EmbeddedTrack(
    val container: ContainerType,
    /** Track number (MKV) or track id (MP4). */
    val id: Long,
    /** Position among the subtitle tracks of the file, starting at 0. */
    val index: Int,
    val codec: String,
    val language: String?,
    val title: String?,
    val isText: Boolean,
    val isDefault: Boolean,
    val isForced: Boolean
)

/** Entry point for reading subtitle tracks out of MKV, WebM and MP4/MOV files. */
object EmbeddedSubtitles {

    /** Lists the subtitle tracks. Fast: only the file header is read. */
    fun listTracks(source: ByteSource): List<EmbeddedTrack> {
        val head = ByteArray(12)
        val n = source.readAt(0, head, 0, head.size)
        if (n < 8) return emptyList()
        return try {
            when {
                head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
                    head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() ->
                    MkvSubtitleReader.listTracks(source)
                isMp4Signature(head) -> Mp4SubtitleReader.listTracks(source)
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList() // a damaged header must not crash the player
        }
    }

    /**
     * Reads every cue of [track]. This reads through the whole file, so call it in the background.
     * [checkCancelled] is called now and then and may throw to stop the work.
     */
    fun readCues(source: ByteSource, track: EmbeddedTrack, checkCancelled: () -> Unit = {}): List<Cue> {
        require(track.isText) { "Image subtitles have no text cues" }
        return when (track.container) {
            ContainerType.MKV -> MkvSubtitleReader.readCues(source, track, checkCancelled)
            ContainerType.MP4 -> Mp4SubtitleReader.readCues(source, track)
        }
    }

    private fun isMp4Signature(head: ByteArray): Boolean {
        val type = String(head, 4, 4, Charsets.ISO_8859_1)
        return type == "ftyp" || type == "moov" || type == "mdat" || type == "free" || type == "wide"
    }

    /** Fills gaps: a cue without a known duration ends when the next one starts (at most 7 seconds). */
    internal fun fixMissingEnds(raw: List<Triple<Long, Long, String>>): List<Cue> {
        val sorted = raw.sortedBy { it.first }
        return sorted.mapIndexedNotNull { i, (start, duration, text) ->
            if (text.isBlank()) return@mapIndexedNotNull null
            val end = when {
                duration > 0 -> start + duration
                i + 1 < sorted.size -> minOf(sorted[i + 1].first, start + 7000).coerceAtLeast(start + 1)
                else -> start + 3000
            }
            Cue(start, end, text)
        }
    }

    internal fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 2)
            val chunk = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(chunk)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(chunk, 0, n)
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}
