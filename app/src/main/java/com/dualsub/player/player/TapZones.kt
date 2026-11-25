package com.dualsub.player.player

/** What a tap does. */
enum class TapZone { REWIND, PLAY_PAUSE, FORWARD }

/**
 * The screen is split in three vertical zones, from left to right. You choose which action sits
 * in which place ([order]) and where the two dividing lines are ([firstEdge], [secondEdge], in percent).
 *
 * Example: order = [FORWARD, PLAY_PAUSE, REWIND], firstEdge = 30, secondEdge = 60
 * means forward on the left 30%, play/pause in the middle 30%, rewind on the right 40%.
 */
data class ZoneLayout(
    val order: List<TapZone> = DEFAULT_ORDER,
    val firstEdge: Int = 35,
    val secondEdge: Int = 65
) {
    init {
        require(order.size == 3 && order.toSet().size == 3) { "Each action must be used once" }
    }

    /** Widths in percent, in screen order. They always add up to 100. */
    val widths: List<Int> get() = listOf(firstEdge, secondEdge - firstEdge, 100 - secondEdge)

    fun widthOf(zone: TapZone): Int = widths[order.indexOf(zone)]

    fun resolve(x: Float, width: Float): TapZone {
        val percent = if (width <= 0f) 50f else x / width * 100f
        return when {
            percent < firstEdge -> order[0]
            percent < secondEdge -> order[1]
            else -> order[2]
        }
    }

    /** Keeps both edges valid: each zone is at least [MIN_WIDTH] percent wide. */
    fun withEdges(first: Int, second: Int): ZoneLayout {
        val a = first.coerceIn(MIN_WIDTH, 100 - 2 * MIN_WIDTH)
        val b = second.coerceIn(a + MIN_WIDTH, 100 - MIN_WIDTH)
        return copy(firstEdge = a, secondEdge = b)
    }

    /** Puts [zone] at screen position [position] (0 = left). The action that was there swaps places. */
    fun withZoneAt(position: Int, zone: TapZone): ZoneLayout {
        val list = order.toMutableList()
        val other = list.indexOf(zone)
        list[other] = list[position]
        list[position] = zone
        return copy(order = list)
    }

    fun serializeOrder(): String = order.joinToString("") { it.code.toString() }

    companion object {
        const val MIN_WIDTH = 10
        val DEFAULT_ORDER = listOf(TapZone.REWIND, TapZone.PLAY_PAUSE, TapZone.FORWARD)

        fun parseOrder(text: String?): List<TapZone> {
            val parsed = text?.mapNotNull { c -> TapZone.entries.firstOrNull { it.code == c } }.orEmpty()
            return if (parsed.size == 3 && parsed.toSet().size == 3) parsed else DEFAULT_ORDER
        }
    }
}

val TapZone.code: Char
    get() = when (this) {
        TapZone.REWIND -> 'R'
        TapZone.PLAY_PAUSE -> 'P'
        TapZone.FORWARD -> 'F'
    }
