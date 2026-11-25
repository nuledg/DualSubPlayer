package com.dualsub.player.subtitle

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

/** A [ByteSource] over a byte array, for tests. */
class BytesSource(private val bytes: ByteArray) : ByteSource {
    override val size: Long = bytes.size.toLong()

    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val n = minOf(length, bytes.size - position.toInt())
        System.arraycopy(bytes, position.toInt(), dst, offset, n)
        return n
    }
}

fun fixtureBytes(name: String): ByteArray {
    val stream = object {}.javaClass.classLoader!!.getResourceAsStream("fixtures/$name")
        ?: error("Missing test file: $name")
    return stream.use { it.readBytes() }
}

fun fixtureSource(name: String): ByteSource = BytesSource(fixtureBytes(name))

/** Builds small Matroska files by hand, so tests can cover cases no real encoder makes. */
object MkvBuilder {

    private fun id(v: Long): ByteArray {
        var n = 1
        while (n < 4 && (v shr (8 * n)) != 0L) n++
        return ByteArray(n) { i -> (v shr (8 * (n - 1 - i))).toByte() }
    }

    /** Sizes are always written with 8 bytes. That is allowed and keeps the builder simple. */
    private fun size(n: Long): ByteArray =
        byteArrayOf(0x01) + ByteArray(7) { i -> (n shr (8 * (6 - i))).toByte() }

    private val UNKNOWN_SIZE = byteArrayOf(0x01, -1, -1, -1, -1, -1, -1, -1)

    private fun element(id: Long, payload: ByteArray) = id(id) + size(payload.size.toLong()) + payload

    private fun uint(id: Long, value: Long): ByteArray {
        var n = 1
        while (n < 8 && (value shr (8 * n)) != 0L) n++
        return element(id, ByteArray(n) { i -> (value shr (8 * (n - 1 - i))).toByte() })
    }

    private fun text(id: Long, s: String) = element(id, s.toByteArray(Charsets.UTF_8))

    private fun join(parts: List<ByteArray>): ByteArray = parts.fold(ByteArray(0)) { a, b -> a + b }

    fun master(id: Long, vararg children: ByteArray) = element(id, join(children.toList()))

    /** A container whose size is "unknown", as in live recordings. */
    fun masterUnknownSize(id: Long, vararg children: ByteArray) =
        id(id) + UNKNOWN_SIZE + join(children.toList())

    fun header(): ByteArray = master(0x1A45DFA3, text(0x4282, "matroska"))

    fun info(timecodeScale: Long = 1_000_000L): ByteArray = master(0x1549A966, uint(0x2AD7B1, timecodeScale))

    fun tracks(vararg entries: ByteArray): ByteArray = master(0x1654AE6B, *entries)

    fun subtitleTrack(
        number: Long,
        codecId: String,
        language: String,
        name: String? = null,
        zlib: Boolean = false
    ): ByteArray {
        val parts = mutableListOf(
            uint(0xD7, number), uint(0x83, 17), text(0x86, codecId), text(0x22B59C, language)
        )
        if (name != null) parts.add(text(0x536E, name))
        if (zlib) parts.add(master(0x6D80, master(0x6240, master(0x5034, uint(0x4254, 0)))))
        return master(0xAE, *parts.toTypedArray())
    }

    fun videoTrack(number: Long): ByteArray =
        master(0xAE, uint(0xD7, number), uint(0x83, 1), text(0x86, "V_MPEG4/ISO/AVC"))

    fun clusterTimecode(units: Long) = uint(0xE7, units)

    private fun blockPayload(track: Long, relative: Int, data: ByteArray): ByteArray =
        byteArrayOf((0x80 or track.toInt()).toByte(), (relative shr 8).toByte(), relative.toByte(), 0) + data

    /** A block with a known duration (the normal way for subtitles). */
    fun blockGroup(track: Long, relative: Int, data: ByteArray, duration: Long): ByteArray =
        master(0xA0, element(0xA1, blockPayload(track, relative, data)), uint(0x9B, duration))

    /** A block without a duration. The reader has to guess the end. */
    fun simpleBlock(track: Long, relative: Int, data: ByteArray): ByteArray =
        element(0xA3, blockPayload(track, relative, data))

    fun zlib(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    fun file(segmentChildren: List<ByteArray>): ByteArray =
        header() + masterUnknownSize(0x18538067, *segmentChildren.toTypedArray())
}
