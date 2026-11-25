package com.dualsub.player.subtitle

/** Start/end timestamps for the subtitle (or overlapping subtitle group) selected for replay. */
data class ReplayWindow(val startMs: Long, val endMs: Long)

/** Pure time-based sentence targeting. No Android classes, so the behavior is easy to test. */
object SentenceSeek {

    /**
     * Start time of the subtitle currently displayed at [t].
     *
     * When cues overlap, S1 displays all of them, so use the earliest active start to replay the
     * complete visible group. In a gap, use the most recently started cue, matching the player's
     * existing gap behavior. A time before the first cue has no sentence target.
     */
    fun currentCueTarget(cues: CueList, t: Long): Long? {
        if (cues.isEmpty()) return null

        val active = cues.cuesContaining(t)
        if (active.isNotEmpty()) return active.first().startMs

        val previousOrCurrent = cues.lastStartingAtOrBefore(t)
        return if (previousOrCurrent >= 0) cues[previousOrCurrent].startMs else null
    }

    /**
     * Rewind target for a single tap. If a new cue has only just appeared, treat that cue as
     * already passed and step back to the cue before it. Otherwise replay the cue currently
     * displayed. The grace period is measured from the newest cue that is actually visible.
     */
    fun rewindTarget(cues: CueList, t: Long, recentCueWindowMs: Long): Long? {
        val currentTarget = currentCueTarget(cues, t) ?: return null
        if (recentCueWindowMs <= 0L) return currentTarget

        val visible = cues.cuesContaining(t)
        val newestVisible = visible.maxByOrNull { it.startMs } ?: return currentTarget
        val elapsedSinceStart = t - newestVisible.startMs
        if (elapsedSinceStart < 0L || elapsedSinceStart >= recentCueWindowMs) return currentTarget

        return previousCueTarget(cues, newestVisible.startMs) ?: currentTarget
    }

    /**
     * Find a cue to replay when the user pauses. A cue is eligible when it is active at [t],
     * or it ended within [lookbackMs] before [t]. Unlike currentCueTarget, this deliberately
     * returns null for an old cue so a pause in a long subtitle-free gap stays a normal pause.
     * When cues overlap, replay starts at the earliest active cue. In a gap, use the most
     * recently ended cue that is still inside the lookback window.
     */
    fun replayBeforePauseWindow(cues: CueList, t: Long, lookbackMs: Long): ReplayWindow? {
        if (cues.isEmpty()) return null
        val active = cues.cuesContaining(t)
        // When cues overlap, replay from the earliest active start and continue until the latest
        // active end so every subtitle currently visible has completed before the player pauses.
        if (active.isNotEmpty()) {
            return ReplayWindow(active.first().startMs, active.maxOf { it.endMs })
        }

        val lowerBound = (t - lookbackMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        val recent = cues.cues.lastOrNull { it.startMs <= t && it.endMs > lowerBound }
        return recent?.let { ReplayWindow(it.startMs, it.endMs) }
    }

    /** Compatibility helper for callers/tests that need only the replay start. */
    fun replayBeforePauseTarget(cues: CueList, t: Long, lookbackMs: Long): Long? =
        replayBeforePauseWindow(cues, t, lookbackMs)?.startMs

    /**
     * Main (S1) has priority until its final cue has completely ended. If S2 continues beyond
     * S1's final timestamp, S2 becomes the preferred timeline from that exact point onward.
     * This also applies to pause-triggered replay so an ended S1 track cannot mask later S2 cues.
     */
    fun replayBeforePauseTimeline(main: CueList?, helper: CueList?, t: Long, lookbackMs: Long): Pair<CueList, ReplayWindow>? {
        if (helperTakesOverAfterMainEnds(main, helper, t)) {
            val helperCues = requireNotNull(helper)
            return replayBeforePauseWindow(helperCues, t, lookbackMs)?.let { helperCues to it }
        }

        return listOfNotNull(main, helper).firstNotNullOfOrNull { cues ->
            replayBeforePauseWindow(cues, t, lookbackMs)?.let { cues to it }
        }
    }

    /**
     * Select the preferred timeline for a rewind. Main (S1) has priority until its final cue
     * has ended. When helper (S2) contains timestamps extending beyond that final S1 cue, switch
     * to S2 once the S1 end timestamp is reached. Before that point, use S2 only when S1 cannot
     * resolve a cue at the current position (for example, before S1's first cue).
     *
     * When S2 has taken over but has no cue at/before the current position yet, return S2 anyway.
     * The caller then uses its fixed-time fallback rather than incorrectly replaying S1's final
     * cue again.
     */
    fun rewindTimeline(main: CueList?, helper: CueList?, t: Long): CueList? {
        if (helperTakesOverAfterMainEnds(main, helper, t)) return helper
        return listOfNotNull(main, helper).firstOrNull { !it.isEmpty() && rewindTarget(it, t, 0L) != null }
    }

    private fun helperTakesOverAfterMainEnds(main: CueList?, helper: CueList?, t: Long): Boolean {
        if (main == null || main.isEmpty() || helper == null || helper.isEmpty()) return false
        val mainFinalEndMs = main.cues.maxOfOrNull { it.endMs } ?: return false
        val helperFinalEndMs = helper.cues.maxOfOrNull { it.endMs } ?: return false
        return t >= mainFinalEndMs && helperFinalEndMs > mainFinalEndMs
    }

    /** Start time of the cue immediately before the cue that starts at [currentCueStartMs]. */
    fun previousCueTarget(cues: CueList, currentCueStartMs: Long): Long? {
        val previous = cues.lastStartingBefore(currentCueStartMs)
        return if (previous >= 0) cues[previous].startMs else null
    }

    /** Where a forward tap should seek: the start of the next cue, or null at the end. */
    fun forwardTarget(cues: CueList, t: Long): Long? {
        val index = cues.firstStartingAfter(t)
        return if (index >= 0) cues[index].startMs else null
    }

    /** Main (S1) has priority; use helper (S2) when S1 has no upcoming cue. */
    fun forwardTimeline(main: CueList?, helper: CueList?, t: Long): CueList? =
        listOfNotNull(main, helper).firstOrNull { !it.isEmpty() && forwardTarget(it, t) != null }
}

/**
 * Resolves a stream of rewind taps.
 *
 * The first tap always targets the cue visible at that tap's original playback position. A second
 * tap inside [windowMs] targets the cue before the first target, even if ExoPlayer has not yet
 * updated its reported position after the first seek. [rewindOffsetMs] moves the actual seek
 * position before the selected cue start, but the sequence keeps the unmodified cue start as its
 * anchor so rapid taps continue stepping through the correct cues.
 */
class RewindTapSequence {
    private var lastTapAtMs: Long? = null
    private var anchorCueStartMs: Long? = null
    private var activeCues: CueList? = null

    fun target(
        cues: CueList,
        positionMs: Long,
        nowMs: Long,
        windowMs: Long,
        recentCueWindowMs: Long = 0L,
        rewindOffsetMs: Long = 0L
    ): Long? = target(cues, null, positionMs, nowMs, windowMs, recentCueWindowMs, rewindOffsetMs)

    /** Main-first/helper-fallback overload used by the player. */
    fun target(
        mainCues: CueList?,
        helperCues: CueList?,
        positionMs: Long,
        nowMs: Long,
        windowMs: Long,
        recentCueWindowMs: Long = 0L,
        rewindOffsetMs: Long = 0L
    ): Long? {
        val previousTapAt = lastTapAtMs
        val isDoubleTap = previousTapAt != null &&
            nowMs >= previousTapAt &&
            nowMs - previousTapAt <= windowMs &&
            anchorCueStartMs != null && activeCues != null

        // Keep the first tap's timeline for the whole rapid-tap sequence. Otherwise a seek can
        // move into a gap in S1 and accidentally switch the second tap to S2.
        val cues = if (isDoubleTap) {
            activeCues
        } else {
            SentenceSeek.rewindTimeline(mainCues, helperCues, positionMs)
        } ?: run {
            reset()
            return null
        }

        val target = if (isDoubleTap) {
            val anchor = requireNotNull(anchorCueStartMs)
            SentenceSeek.previousCueTarget(cues, anchor) ?: anchor
        } else {
            SentenceSeek.rewindTarget(cues, positionMs, recentCueWindowMs)
        }

        if (target == null) {
            reset()
            return null
        }

        // Keep the logical cue start as the sequence anchor. Only the actual seek position
        // includes the offset; this ensures a second/third rapid tap steps to the previous cue,
        // not to an offset-adjusted timestamp that might fall inside another cue.
        val seekTarget = (target - rewindOffsetMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        lastTapAtMs = nowMs
        anchorCueStartMs = target
        activeCues = cues
        return seekTarget
    }

    fun reset() {
        lastTapAtMs = null
        anchorCueStartMs = null
        activeCues = null
    }
}

/**
 * Rewind mode. It starts on the first rewind tap of a series and remembers
 * where the user was (returnPoint). S2 is visible while [active] is true.
 */
class RewindSession {

    var returnPoint: Long? = null
        private set
    private var playbackMovedBack = false

    val active: Boolean get() = returnPoint != null

    /** Call on every rewind tap with the position BEFORE the tap. Only the first tap saves it. */
    fun onRewindTap(positionBeforeTap: Long) {
        if (returnPoint == null) {
            returnPoint = positionBeforeTap
            playbackMovedBack = false
        }
    }

    /**
     * Leave rewind mode only after playback has first moved behind the return point and then
     * caught up. This avoids clearing the helper subtitle on the immediate refresh that can
     * happen before ExoPlayer has applied the seek.
     */
    fun onPosition(positionMs: Long) {
        val point = returnPoint ?: return
        if (positionMs < point) playbackMovedBack = true
        else if (playbackMovedBack) {
            returnPoint = null
            playbackMovedBack = false
        }
    }

    /** Manual seek, new video, and so on. */
    fun exit() {
        returnPoint = null
        playbackMovedBack = false
    }
}

/**
 * Forward mode. It starts when a forward tap jumps to a sentence and lasts until that sentence is over.
 * The helper subtitle can be set to show during it.
 */
class ForwardSession {

    private var untilMs: Long? = null

    val active: Boolean get() = untilMs != null

    fun start(until: Long) {
        untilMs = until
    }

    fun onPosition(positionMs: Long) {
        val end = untilMs ?: return
        if (positionMs >= end) untilMs = null
    }

    fun exit() {
        untilMs = null
    }
}
