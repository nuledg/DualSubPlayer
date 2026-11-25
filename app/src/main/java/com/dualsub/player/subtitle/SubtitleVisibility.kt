package com.dualsub.player.subtitle

/**
 * When is a subtitle (main or helper) shown? The user ticks any of these four situations, for each
 * subtitle separately. A subtitle is shown when at least one ticked situation is true right now.
 */
object SubtitleVisibility {
    const val WHILE_PLAYING = 1
    const val WHILE_PAUSED = 2
    const val AFTER_REWIND = 4
    const val AFTER_FORWARD = 8

    const val ALL = WHILE_PLAYING or WHILE_PAUSED or AFTER_REWIND or AFTER_FORWARD

    /** Main subtitle: always. Helper subtitle: only after a rewind tap. */
    fun defaultFor(slot: SubtitleSlot): Int = if (slot == SubtitleSlot.S1) ALL else AFTER_REWIND

    /**
     * [rewindActive]: a rewind tap happened and playback has not yet come back to where it was.
     * [forwardActive]: a forward tap jumped to a sentence and that sentence is still playing.
     */
    fun isVisible(mask: Int, paused: Boolean, rewindActive: Boolean, forwardActive: Boolean): Boolean {
        val normal = if (paused) mask and WHILE_PAUSED != 0 else mask and WHILE_PLAYING != 0
        return normal ||
            (rewindActive && mask and AFTER_REWIND != 0) ||
            (forwardActive && mask and AFTER_FORWARD != 0)
    }
}
