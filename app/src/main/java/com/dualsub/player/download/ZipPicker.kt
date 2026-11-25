package com.dualsub.player.download

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Subtitle providers often send a zip. This picks the right subtitle file from inside it. */
object ZipPicker {

    private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")
    private const val MAX_ENTRY_BYTES = 5 * 1024 * 1024

    class Picked(val fileName: String, val bytes: ByteArray)

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /** Returns the best subtitle in the zip for [wanted], or null when there is none. */
    fun pick(zip: ByteArray, wanted: ReleaseInfo, language: String? = null): Picked? {
        // File names with unusual characters can break the UTF-8 reader, so we try a second charset.
        for (charset in listOf(Charsets.UTF_8, Charsets.ISO_8859_1)) {
            try {
                return pickWith(zip, wanted, language, charset)
            } catch (_: IllegalArgumentException) {
                // try the next charset
            }
        }
        return null
    }

    private fun pickWith(
        zip: ByteArray,
        wanted: ReleaseInfo,
        language: String?,
        charset: java.nio.charset.Charset
    ): Picked? {
        var best: Picked? = null
        var bestScore = Int.MIN_VALUE

        ZipInputStream(ByteArrayInputStream(zip), charset).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                if (entry.isDirectory || ext !in SUBTITLE_EXTENSIONS || entry.name.contains("__MACOSX")) continue

                val entryLanguage = languageTagFromFileName(name)
                if (language != null && entryLanguage != null && entryLanguage != language) continue

                val info = ReleaseNameParser.parse(name)
                var score = 0
                // Prefer the selected language when a ZIP contains both English and Persian files.
                if (language != null && entryLanguage == language) score += 500
                if (wanted.isSeries && info.season != null && info.episode != null) {
                    // The zip may hold a whole season. Skip other episodes.
                    if (info.season != wanted.season || info.episode != wanted.episode) continue
                    score += 100
                }
                if (wanted.resolution != null && wanted.resolution == info.resolution) score += 30
                if (wanted.codec != null && wanted.codec == info.codec) score += 20
                if (wanted.source != null && wanted.source == info.source) score += 25
                if (wanted.group != null && wanted.group.equals(info.group, ignoreCase = true)) score += 35
                score += when (ext) { "srt" -> 5; "vtt" -> 2; else -> 0 }

                val bytes = readLimited(stream) ?: continue
                if (score > bestScore) {
                    bestScore = score
                    best = Picked(name, bytes)
                }
            }
        }
        return best
    }

    /** Only treat a language token at the end of a subtitle filename as its language.
     * Searching further back can mistake a title such as "The English Patient" for English.
     */
    private fun languageTagFromFileName(name: String): String? {
        val withoutExtension = name.substringBeforeLast('.', name)
        val tokens = withoutExtension.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        val last = tokens.last()
        val token = if (last.equals("hi", ignoreCase = true) && tokens.size > 1) tokens[tokens.lastIndex - 1] else last
        return com.dualsub.player.subtitle.LanguageCodes.knownOrNull(token)
            ?: if (token.equals("persian", ignoreCase = true)) "fa" else null
    }

    private fun readLimited(stream: ZipInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_ENTRY_BYTES) return null
        }
        return out.toByteArray()
    }
}
