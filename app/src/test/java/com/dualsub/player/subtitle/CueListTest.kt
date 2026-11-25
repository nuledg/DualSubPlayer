package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueListTest {

    private val cues = CueList(
        listOf(
            Cue(1000, 3000, "a"),
            Cue(5000, 8000, "b"),
            Cue(9000, 10_000, "c")
        )
    )

    @Test
    fun textIsFoundByTimeOnly() {
        assertEquals("", cues.textAt(500))
        assertEquals("a", cues.textAt(1000))
        assertEquals("a", cues.textAt(2999))
        assertEquals("", cues.textAt(3000))   // the end is exclusive
        assertEquals("b", cues.textAt(6000))
        assertEquals("", cues.textAt(20_000))
    }

    @Test
    fun overlappingCuesAreShownTogetherIncludingLongOnes() {
        val list = CueList(listOf(Cue(0, 100_000, "sign"), Cue(1000, 2000, "line")))
        assertEquals("sign\nline", list.textAt(1500))
        assertEquals("sign", list.textAt(50_000))   // the long cue is found far from its start
    }

    @Test
    fun unsortedInputIsSorted() {
        val list = CueList(listOf(Cue(9000, 10_000, "c"), Cue(1000, 3000, "a")))
        assertEquals("a", list[0].text)
        assertEquals("c", list[1].text)
    }

    @Test
    fun indexLookups() {
        assertEquals(-1, cues.lastStartingAtOrBefore(999))
        assertEquals(0, cues.lastStartingAtOrBefore(1000))
        assertEquals(0, cues.lastStartingAtOrBefore(4000))   // in a gap: the last cue before
        assertEquals(1, cues.lastStartingAtOrBefore(5000))
        assertEquals(-1, cues.lastStartingBefore(1000))
        assertEquals(0, cues.lastStartingBefore(1001))
        assertEquals(1, cues.firstStartingAfter(1000))
        assertEquals(-1, cues.firstStartingAfter(9000))
    }

    @Test
    fun helperTextUsesTimeOverlapWithASmallLead() {
        assertEquals("b", cues.s2TextAt(6000))         // inside a cue
        assertEquals("b", cues.s2TextAt(4600))         // starts within 500 ms
        assertEquals("", cues.s2TextAt(4400))          // starts later than that
        assertEquals("c", cues.s2TextAt(8500))         // exactly 500 ms before the next cue: still shown
        assertEquals("", cues.s2TextAt(8400))          // more than 500 ms before it: not yet
        assertTrue(CueList(emptyList()).s2TextAt(0).isEmpty())
    }
}
