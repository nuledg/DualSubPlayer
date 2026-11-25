package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSeekTest {

    private fun time(minutes: Long, seconds: Double = 0.0) = minutes * 60_000L + (seconds * 1000).toLong()

    // Cues 11, 12 and 13 from the acceptance example.
    private val cues = CueList(
        listOf(
            Cue(time(9, 58.0), time(10, 2.0), "cue 11"),
            Cue(time(10, 3.0), time(10, 6.0), "cue 12"),
            Cue(time(10, 7.0), time(10, 9.0), "cue 13")
        )
    )

    @Test
    fun oneTapAlwaysReplaysTheCurrentlyVisibleCueRegardlessOfHowRecentlyItStarted() {
        val cueStart = time(10, 3.0)
        assertEquals(cueStart, SentenceSeek.currentCueTarget(cues, cueStart + 1))
        assertEquals(cueStart, SentenceSeek.currentCueTarget(cues, cueStart + 300))
        assertEquals(cueStart, SentenceSeek.currentCueTarget(cues, cueStart + 2_900))
        assertEquals(cueStart, SentenceSeek.currentCueTarget(cues, time(10, 6.5))) // gap: last cue before t
    }

    @Test
    fun pauseReplayUsesCurrentCueOrCueThatEndedWithinFiveSeconds() {
        val list = CueList(listOf(
            Cue(1_000, 2_000, "old"),
            Cue(5_000, 6_000, "recent"),
            Cue(8_000, 10_000, "current")
        ))

        assertEquals(8_000L, SentenceSeek.replayBeforePauseTarget(list, 9_000, 5_000))
        assertEquals(8_000L, SentenceSeek.replayBeforePauseTarget(list, 10_500, 5_000))
        assertEquals(8_000L, SentenceSeek.replayBeforePauseTarget(list, 11_001, 5_000))
        assertNull(SentenceSeek.replayBeforePauseTarget(list, 15_001, 5_000))
    }


    @Test
    fun pauseReplayKeepsCueEndSoPlaybackCanPauseAtTheSubtitleEnd() {
        val current = Cue(time(10, 3.0), time(10, 7.0), "current")
        val list = CueList(listOf(current))

        val selected = SentenceSeek.replayBeforePauseTimeline(list, null, time(10, 5.0), 5_000)
        assertEquals(ReplayWindow(current.startMs, current.endMs), selected?.second)
    }

    @Test
    fun overlappingCurrentSubtitlesReplayUntilTheLastActiveCueEnds() {
        val list = CueList(listOf(
            Cue(3_000, 7_000, "first"),
            Cue(4_000, 8_000, "overlap")
        ))

        assertEquals(ReplayWindow(3_000, 8_000), SentenceSeek.replayBeforePauseWindow(list, 5_000, 5_000))
    }

    @Test
    fun recentlyEndedCueRetainsItsEndTimestampForReplayPause() {
        val recent = Cue(3_000, 4_000, "recent")
        val list = CueList(listOf(recent))

        val selected = SentenceSeek.replayBeforePauseTimeline(list, null, 5_000, 5_000)
        assertEquals(ReplayWindow(recent.startMs, recent.endMs), selected?.second)
    }

    @Test
    fun pauseReplayFallsBackToHelperWhenMainHasNoCueInTheFiveSecondWindow() {
        val main = CueList(listOf(Cue(1_000, 2_000, "old main")))
        val helper = CueList(listOf(Cue(7_000, 8_000, "recent helper")))

        val selected = SentenceSeek.replayBeforePauseTimeline(main, helper, 9_000, 5_000)
        assertEquals(helper, selected?.first)
        assertEquals(ReplayWindow(7_000, 8_000), selected?.second)
    }

    @Test
    fun pauseReplayReturnsNullWhenNeitherSubtitleHasARecentCue() {
        val main = CueList(listOf(Cue(1_000, 2_000, "old main")))
        val helper = CueList(listOf(Cue(2_000, 3_000, "old helper")))

        assertNull(SentenceSeek.replayBeforePauseTimeline(main, helper, 10_000, 5_000))
    }

    @Test
    fun rewindGracePeriodTreatsAJustAppearedSubtitleAsAlreadyPassed() {
        val cueStart = time(10, 3.0)
        assertEquals(time(9, 58.0), SentenceSeek.rewindTarget(cues, cueStart + 299, 300))
        assertEquals(cueStart, SentenceSeek.rewindTarget(cues, cueStart + 300, 300))
        assertEquals(cueStart, SentenceSeek.rewindTarget(cues, cueStart + 500, 300))
    }

    @Test
    fun rewindGracePeriodDoesNotApplyInGapsOrWhenDisabled() {
        assertEquals(
            time(10, 3.0),
            SentenceSeek.rewindTarget(cues, time(10, 6.5), 300)
        )
        assertEquals(
            time(10, 3.0),
            SentenceSeek.rewindTarget(cues, time(10, 3.1), 0)
        )
    }

    @Test
    fun firstSubtitleHasNoPreviousCueSoGracePeriodKeepsItAsTarget() {
        assertEquals(time(9, 58.0), SentenceSeek.rewindTarget(cues, time(9, 58.1), 300))
    }

    @Test
    fun doubleTapGoesToThePreviousCueUsingTheFirstTapAnchor() {
        val sequence = RewindTapSequence()
        val firstTapPosition = time(10, 5.5)

        // First tap: current subtitle 12, exactly 10:03.0.
        assertEquals(time(10, 3.0), sequence.target(cues, firstTapPosition, 10_000, 700))

        // Position may still be stale, or may already have moved. The second tap uses the first
        // tap's cue anchor and therefore reliably selects subtitle 11.
        assertEquals(time(9, 58.0), sequence.target(cues, firstTapPosition, 10_350, 700))
    }

    @Test
    fun repeatedRapidTapsContinueSteppingBackAndDoNotReplaceReturnPoint() {
        val list = CueList(
            listOf(
                Cue(1_000, 2_000, "one"),
                Cue(4_000, 6_000, "two"),
                Cue(7_000, 9_000, "three")
            )
        )
        val sequence = RewindTapSequence()
        assertEquals(7_000L, sequence.target(list, 8_000, 100, 700))
        assertEquals(4_000L, sequence.target(list, 7_100, 400, 700))
        assertEquals(1_000L, sequence.target(list, 4_100, 650, 700))
    }

    @Test
    fun aTapAfterTheDoubleTapWindowStartsANewSingleTapSequence() {
        val sequence = RewindTapSequence()
        assertEquals(time(10, 3.0), sequence.target(cues, time(10, 5.5), 1_000, 700))
        assertEquals(time(10, 3.0), sequence.target(cues, time(10, 5.5), 1_701, 700))
    }

    @Test
    fun resettingTheSequenceMakesTheNextTapAnewSingleTap() {
        val sequence = RewindTapSequence()
        val list = CueList(listOf(
            Cue(1_000, 2_000, "one"),
            Cue(4_000, 6_000, "two"),
            Cue(7_000, 9_000, "three")
        ))
        assertEquals(7_000L, sequence.target(list, 8_000, 100, 700))
        sequence.reset()
        // Without reset this would be interpreted as a double tap and move to 4,000 ms.
        assertEquals(7_000L, sequence.target(list, 8_000, 200, 700))
    }

    @Test
    fun overlappingVisibleCuesReplayFromTheEarliestVisibleStart() {
        val overlapping = CueList(
            listOf(
                Cue(1_000, 5_000, "long cue"),
                Cue(3_000, 4_000, "overlapping cue"),
                Cue(6_000, 7_000, "next cue")
            )
        )
        assertEquals(1_000L, SentenceSeek.currentCueTarget(overlapping, 3_500))
        assertNull(SentenceSeek.previousCueTarget(overlapping, 1_000))
        assertEquals(3_000L, SentenceSeek.previousCueTarget(overlapping, 6_000))
    }

    @Test
    fun cuesWithTheSameStartAreTreatedAsOneVisibleSentenceForPreviousNavigation() {
        val list = CueList(
            listOf(Cue(1_000, 2_000, "old"), Cue(5_000, 6_000, "a"), Cue(5_000, 6_000, "b"))
        )
        assertEquals(5_000L, SentenceSeek.currentCueTarget(list, 5_200))
        assertEquals(1_000L, SentenceSeek.previousCueTarget(list, 5_000))
    }

    @Test
    fun noCueBeforeTheCurrentPositionReturnsNullAndFirstCueHasNoPreviousCue() {
        assertNull(SentenceSeek.currentCueTarget(cues, 1_000))
        assertNull(SentenceSeek.currentCueTarget(CueList(emptyList()), 1_000))
        assertNull(SentenceSeek.previousCueTarget(cues, time(9, 58.0)))
        assertEquals(time(9, 58.0), SentenceSeek.currentCueTarget(cues, time(9, 58.5)))
    }

    @Test
    fun forwardGoesToTheNextCueStart() {
        assertEquals(time(10, 7.0), SentenceSeek.forwardTarget(cues, time(10, 5.5)))
        assertEquals(time(10, 3.0), SentenceSeek.forwardTarget(cues, time(10, 2.5)))
        assertNull(SentenceSeek.forwardTarget(cues, time(10, 7.5)))
    }

    @Test
    fun rewindTimelinePrefersMainUntilItsFinalCueEndsThenUsesContinuingHelperTimeline() {
        val main = CueList(listOf(Cue(10_000, 12_000, "main final")))
        val helper = CueList(listOf(
            Cue(1_000, 3_000, "helper first"),
            Cue(11_000, 13_000, "helper overlaps final main"),
            Cue(14_000, 16_000, "helper continues")
        ))

        // While the final S1 cue is still visible, keep using S1.
        assertEquals(main, SentenceSeek.rewindTimeline(main, helper, 11_999))
        // At the exclusive end timestamp of S1's final cue, S2 takes over.
        assertEquals(helper, SentenceSeek.rewindTimeline(main, helper, 12_000))
        assertEquals(helper, SentenceSeek.rewindTimeline(main, helper, 15_000))

        // Before S1 has any cue at the current position, the existing helper fallback remains.
        assertEquals(helper, SentenceSeek.rewindTimeline(main, helper, 5_500))
        assertEquals(helper, SentenceSeek.rewindTimeline(null, helper, 5_500))
        assertNull(SentenceSeek.rewindTimeline(main, null, 1_000))
    }

    @Test
    fun rewindDoesNotSwitchToHelperIfItsTimelineDoesNotExtendPastMainEnd() {
        val main = CueList(listOf(Cue(1_000, 12_000, "main final")))
        val helper = CueList(listOf(Cue(1_000, 11_999, "helper ends first")))

        assertEquals(main, SentenceSeek.rewindTimeline(main, helper, 12_000))
    }

    @Test
    fun afterMainEndsSingleAndDoubleRewindTapsStayOnHelperTimeline() {
        val main = CueList(listOf(Cue(1_000, 5_000, "main final")))
        val helper = CueList(listOf(
            Cue(2_000, 3_000, "helper previous"),
            Cue(7_000, 9_000, "helper current"),
            Cue(12_000, 14_000, "helper continues")
        ))
        val sequence = RewindTapSequence()

        // The main track ended at 5,000 ms. Rewind now selects the latest S2 sentence.
        assertEquals(12_000L, sequence.target(main, helper, 13_000, 10_000, 700))
        // A quick second tap steps back in that same helper track, not into S1.
        assertEquals(7_000L, sequence.target(main, helper, 13_000, 10_250, 700))
    }

    @Test
    fun pauseReplayUsesHelperAfterMainFinalTimestampHasEnded() {
        val main = CueList(listOf(Cue(1_000, 10_000, "main final")))
        val helper = CueList(listOf(
            Cue(8_000, 9_000, "helper before main ends"),
            Cue(11_000, 13_000, "helper after main ends")
        ))

        // Before S1 ends, it remains preferred.
        val beforeEnd = SentenceSeek.replayBeforePauseTimeline(main, helper, 9_500, 5_000)
        assertEquals(main, beforeEnd?.first)
        // Once S1's final cue has ended, replay-before-pause uses S2.
        val afterEnd = SentenceSeek.replayBeforePauseTimeline(main, helper, 12_000, 5_000)
        assertEquals(helper, afterEnd?.first)
        assertEquals(ReplayWindow(11_000, 13_000), afterEnd?.second)
    }

    @Test
    fun forwardTimelinePrefersMainAndFallsBackToHelperWhenMainHasNoNextCue() {
        val main = CueList(listOf(Cue(1_000, 3_000, "main"), Cue(3_500, 4_000, "main next")))
        val helper = CueList(listOf(Cue(1_000, 3_000, "helper"), Cue(5_000, 7_000, "helper next")))

        assertEquals(main, SentenceSeek.forwardTimeline(main, helper, 2_000))
        assertEquals(helper, SentenceSeek.forwardTimeline(main, helper, 4_000))
        assertEquals(helper, SentenceSeek.forwardTimeline(null, helper, 4_000))
        assertNull(SentenceSeek.forwardTimeline(main, null, 4_000))
    }

    @Test
    fun rewindSequenceUsesHelperTimelineAndKeepsItForSecondTap() {
        val helper = CueList(listOf(
            Cue(1_000, 2_000, "helper previous"),
            Cue(4_000, 6_000, "helper current")
        ))
        val sequence = RewindTapSequence()

        // S1 has no cues. A first tap must seek using the helper timestamps.
        assertEquals(4_000L, sequence.target(null, helper, 5_000, 10_000, 700, 0))
        // The player's position has not caught up yet; the next tap steps back in that same list.
        assertEquals(1_000L, sequence.target(null, helper, 5_000, 10_250, 700, 0))
    }

    @Test
    fun rewindSequenceAppliesRecentSubtitleGraceBeforeSettingItsDoubleTapAnchor() {
        val helper = CueList(listOf(
            Cue(100, 500, "before previous"),
            Cue(1_000, 2_000, "previous"),
            Cue(4_000, 6_000, "just appeared")
        ))
        val sequence = RewindTapSequence()

        assertEquals(1_000L, sequence.target(null, helper, 4_150, 10_000, 700, 300))
        // A second quick tap steps back from the first tap's selected target.
        assertEquals(100L, sequence.target(null, helper, 4_150, 10_200, 700, 300))
    }

    @Test
    fun rewindingImmediatelyAfterForwardUsesTheHelperCueThatForwardSelected() {
        val helper = CueList(listOf(
            Cue(1_000, 2_000, "helper previous"),
            Cue(5_000, 7_000, "helper next")
        ))
        val forwardTimeline = SentenceSeek.forwardTimeline(null, helper, 3_000)
        assertEquals(helper, forwardTimeline)
        val forwardedTo = SentenceSeek.forwardTarget(requireNotNull(forwardTimeline), 3_000)
        assertEquals(5_000L, forwardedTo)

        val rewindSequence = RewindTapSequence()
        assertEquals(
            1_000L,
            rewindSequence.target(
                cues = requireNotNull(forwardTimeline),
                positionMs = requireNotNull(forwardedTo),
                nowMs = 10_200,
                windowMs = 700,
                recentCueWindowMs = 300
            )
        )
    }


    @Test
    fun rewindOffsetMovesTheSeekBeforeTheCurrentSubtitleStart() {
        val sequence = RewindTapSequence()
        assertEquals(
            time(10, 1.5),
            sequence.target(
                cues = cues,
                positionMs = time(10, 5.5),
                nowMs = 10_000,
                windowMs = 700,
                rewindOffsetMs = 1_500
            )
        )
    }

    @Test
    fun rewindOffsetAlsoAppliesToThePreviousSubtitleOnDoubleTap() {
        val sequence = RewindTapSequence()
        assertEquals(
            time(10, 1.5),
            sequence.target(cues, time(10, 5.5), 10_000, 700, rewindOffsetMs = 1_500)
        )
        assertEquals(
            time(9, 56.5),
            sequence.target(cues, time(10, 5.5), 10_250, 700, rewindOffsetMs = 1_500)
        )
    }

    @Test
    fun rewindOffsetDoesNotChangeTheLogicalAnchorForFurtherRapidTaps() {
        val closeCues = CueList(
            listOf(
                Cue(500, 1_500, "first"),
                Cue(2_500, 3_500, "second"),
                Cue(4_000, 5_000, "third")
            )
        )
        val sequence = RewindTapSequence()
        assertEquals(2_000L, sequence.target(closeCues, 4_100, 100, 700, rewindOffsetMs = 2_000))
        // The real cue anchor is 4,000, so the next tap selects cue 2 (2,500 - 2,000 = 500).
        // It must not use the previous seek position (2,000) as its anchor.
        assertEquals(500L, sequence.target(closeCues, 4_100, 300, 700, rewindOffsetMs = 2_000))
    }

    @Test
    fun rewindOffsetIsClampedToTheBeginningOfTheVideo() {
        val firstCueOnly = CueList(listOf(Cue(1_000, 3_000, "first")))
        val sequence = RewindTapSequence()
        assertEquals(0L, sequence.target(firstCueOnly, 1_500, 100, 700, rewindOffsetMs = 5_000))
    }

    @Test
    fun rewindOffsetAlsoAppliesWhenHelperTimelineIsUsedAsFallback() {
        val helper = CueList(
            listOf(
                Cue(1_000, 2_000, "helper previous"),
                Cue(5_000, 7_000, "helper current")
            )
        )
        val sequence = RewindTapSequence()
        assertEquals(
            3_500L,
            sequence.target(
                mainCues = null,
                helperCues = helper,
                positionMs = 5_500,
                nowMs = 10_000,
                windowMs = 700,
                rewindOffsetMs = 1_500
            )
        )
    }

    @Test
    fun rewindOffsetIsAppliedAfterRecentSubtitleGraceSelectsThePreviousCue() {
        val sequence = RewindTapSequence()
        assertEquals(
            time(9, 56.5),
            sequence.target(
                cues = cues,
                positionMs = time(10, 3.1),
                nowMs = 10_000,
                windowMs = 700,
                recentCueWindowMs = 300,
                rewindOffsetMs = 1_500
            )
        )
    }

    @Test
    fun rewindSessionKeepsTheOriginalReturnPointUntilPlaybackCatchesUp() {
        val session = RewindSession()
        val originalPosition = time(10, 5.5)
        assertFalse(session.active)
        session.onRewindTap(originalPosition)
        assertTrue(session.active)
        session.onRewindTap(time(10, 3.4))
        assertEquals(originalPosition, session.returnPoint)
        // A refresh can run immediately after seekTo, before ExoPlayer reports the new position.
        // That must not end rewind mode and hide the helper subtitle prematurely.
        session.onPosition(originalPosition)
        assertTrue(session.active)
        session.onPosition(originalPosition - 1)
        assertTrue(session.active)
        session.onPosition(originalPosition)
        assertFalse(session.active)
    }

    @Test
    fun rewindSessionCanBeExitedAndStartedAgain() {
        val session = RewindSession()
        session.onRewindTap(5_000)
        session.exit()
        assertFalse(session.active)
        session.onRewindTap(9_000)
        assertEquals(9_000L, session.returnPoint)
    }
}
