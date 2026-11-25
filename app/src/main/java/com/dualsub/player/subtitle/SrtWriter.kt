package com.dualsub.player.subtitle

/** Writes cues as an SRT file. Used for the on-disk cache of cues read from inside a video. */
object SrtWriter {

    fun write(cues: List<Cue>): String {
        val sb = StringBuilder()
        cues.forEachIndexed { index, cue ->
            sb.append(index + 1).append('\n')
            sb.append(timestamp(cue.startMs)).append(" --> ").append(timestamp(cue.endMs)).append('\n')
            sb.append(cue.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun timestamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val h = total / 3_600_000
        val m = (total / 60_000) % 60
        val s = (total / 1000) % 60
        val milli = total % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }
}
