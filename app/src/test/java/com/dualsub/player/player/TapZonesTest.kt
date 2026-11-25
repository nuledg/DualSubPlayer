package com.dualsub.player.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapZonesTest {

    @Test
    fun defaultLayoutIsRewindPlayForward() {
        val l = ZoneLayout()
        assertEquals(TapZone.REWIND, l.resolve(100f, 1000f))
        assertEquals(TapZone.REWIND, l.resolve(349f, 1000f))
        assertEquals(TapZone.PLAY_PAUSE, l.resolve(350f, 1000f))
        assertEquals(TapZone.PLAY_PAUSE, l.resolve(649f, 1000f))
        assertEquals(TapZone.FORWARD, l.resolve(650f, 1000f))
        assertEquals(listOf(35, 30, 35), l.widths)
    }

    @Test
    fun anyOrderWorks() {
        val l = ZoneLayout(listOf(TapZone.FORWARD, TapZone.PLAY_PAUSE, TapZone.REWIND), 20, 70)
        assertEquals(TapZone.FORWARD, l.resolve(100f, 1000f))
        assertEquals(TapZone.PLAY_PAUSE, l.resolve(500f, 1000f))
        assertEquals(TapZone.REWIND, l.resolve(900f, 1000f))
        assertEquals(30, l.widthOf(TapZone.REWIND))
        assertEquals(50, l.widthOf(TapZone.PLAY_PAUSE))
    }

    @Test
    fun placingAnActionSwapsWithTheOneThatWasThere() {
        val l = ZoneLayout().withZoneAt(0, TapZone.FORWARD)
        assertEquals(listOf(TapZone.FORWARD, TapZone.PLAY_PAUSE, TapZone.REWIND), l.order)
        val m = l.withZoneAt(1, TapZone.REWIND)
        assertEquals(listOf(TapZone.FORWARD, TapZone.REWIND, TapZone.PLAY_PAUSE), m.order)
    }

    @Test
    fun edgesAlwaysLeaveEveryZoneUsable() {
        val l = ZoneLayout().withEdges(95, 99)
        assertTrue(l.widths.all { it >= ZoneLayout.MIN_WIDTH })
        assertEquals(100, l.widths.sum())
        val m = ZoneLayout().withEdges(0, 0)
        assertTrue(m.widths.all { it >= ZoneLayout.MIN_WIDTH })
    }

    @Test
    fun orderIsSavedAndReadBack() {
        val l = ZoneLayout().withZoneAt(0, TapZone.PLAY_PAUSE)
        assertEquals(l.order, ZoneLayout.parseOrder(l.serializeOrder()))
        assertEquals(ZoneLayout.DEFAULT_ORDER, ZoneLayout.parseOrder("RRF"))
        assertEquals(ZoneLayout.DEFAULT_ORDER, ZoneLayout.parseOrder(null))
    }
}
