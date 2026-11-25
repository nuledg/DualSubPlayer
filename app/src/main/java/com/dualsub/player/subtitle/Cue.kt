package com.dualsub.player.subtitle

/** One subtitle line with its time range. [endMs] is exclusive. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/**
 * All cues of one subtitle file, sorted by start time, with fast time lookups.
 * Everything here works on time only. Line numbers are never used,
 * because two subtitle files do not share numbering.
 */
class CueList(source: List<Cue>) {

    val cues: List<Cue> = source.sortedBy { it.startMs }
    private val starts = LongArray(cues.size) { cues[it].startMs }
    private val maxDurationMs: Long = cues.maxOfOrNull { it.endMs - it.startMs } ?: 0L

    val size: Int get() = cues.size
    fun isEmpty(): Boolean = cues.isEmpty()
    operator fun get(index: Int): Cue = cues[index]

    /** First index whose start is greater than [t]. */
    private fun upperBound(t: Long): Int {
        var lo = 0
        var hi = starts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** First index whose start is greater than or equal to [t]. */
    private fun lowerBound(t: Long): Int {
        var lo = 0
        var hi = starts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] < t) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Index of the last cue with start <= t, or -1. This is "the cue at t, or the last one before it". */
    fun lastStartingAtOrBefore(t: Long): Int = upperBound(t) - 1

    /** Index of the last cue with start < t, or -1. */
    fun lastStartingBefore(t: Long): Int = lowerBound(t) - 1

    /** Index of the first cue with start > t, or -1. */
    fun firstStartingAfter(t: Long): Int {
        val i = upperBound(t)
        return if (i < size) i else -1
    }

    /** Every cue with start <= t < end (usually one, more for overlapping ASS lines). */
    fun cuesContaining(t: Long): List<Cue> {
        val result = ArrayList<Cue>(2)
        var i = upperBound(t) - 1
        while (i >= 0 && starts[i] >= t - maxDurationMs) {
            if (cues[i].endMs > t) result.add(cues[i])
            i--
        }
        result.reverse()
        return result
    }

    /** S1 text at time [t]. Empty when nothing is shown. */
    fun textAt(t: Long): String = joinTexts(cuesContaining(t))

    /**
     * S2 text for time [t], chosen by time overlap.
     * 1) cues that contain [t]
     * 2) otherwise a cue that starts within [leadMs] after [t], so a helper line
     *    that starts a little late does not leave a blank gap right after a rewind.
     */
    fun s2TextAt(t: Long, leadMs: Long = S2_LEAD_MS): String {
        val containing = cuesContaining(t)
        if (containing.isNotEmpty()) return joinTexts(containing)
        val next = firstStartingAfter(t)
        if (next >= 0 && cues[next].startMs - t <= leadMs) return cues[next].text
        return ""
    }

    private fun joinTexts(list: List<Cue>): String =
        list.map { it.text }.distinct().joinToString("\n")

    companion object {
        const val S2_LEAD_MS = 500L
    }
}
