package com.dualsub.player.subtitle

/** A word's UTF-16 character range in a subtitle line. */
data class SubtitleWordRange(val start: Int, val endExclusive: Int, val text: String)

/**
 * Finds words without splitting combining marks (important for Persian/Arabic) or
 * apostrophe contractions. Hyphenated compounds remain individually tappable words.
 */
object SubtitleWordTokenizer {
    private val wordPattern = Regex("[\\p{L}\\p{M}\\p{N}]+(?:[\\u200C'’][\\p{L}\\p{M}\\p{N}]+)*")

    fun words(text: String): List<SubtitleWordRange> =
        wordPattern.findAll(text).map { match ->
            SubtitleWordRange(match.range.first, match.range.last + 1, match.value)
        }.toList()
}
