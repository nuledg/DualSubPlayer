package com.dualsub.player.subtitle

/** Random access to the bytes of a video file. No Android classes, so it can be tested on a normal JVM. */
interface ByteSource {
    val size: Long

    /** Reads up to [length] bytes at [position]. Returns the number of bytes read, or -1 at the end. */
    fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int
}

/** Small read buffer on top of a [ByteSource], with the helpers needed for MKV and MP4. */
class ByteReader(
    private val source: ByteSource,
    var position: Long = 0L,
    /** Small for header-skipping scans (MKV), large for reading tables in order (MP4). */
    bufferSize: Int = 8192
) {

    private val buf = ByteArray(bufferSize.coerceAtLeast(16))
    private var bufStart = 0L
    private var bufLen = 0

    val size: Long get() = source.size

    /** Next byte, or -1 at the end of the file. */
    fun u8(): Int {
        if (position < bufStart || position >= bufStart + bufLen) {
            val n = source.readAt(position, buf, 0, buf.size)
            if (n <= 0) return -1
            bufStart = position
            bufLen = n
        }
        val value = buf[(position - bufStart).toInt()].toInt() and 0xFF
        position++
        return value
    }

    /** Big-endian unsigned number of [length] bytes (0..8). Returns -1 at the end of the file. */
    fun uint(length: Int): Long {
        var value = 0L
        repeat(length) {
            val b = u8()
            if (b < 0) return -1L
            value = (value shl 8) or b.toLong()
        }
        return value
    }

    fun int16(): Int = uint(2).toInt().toShort().toInt()

    /** Exactly [length] bytes, or null when the file is shorter. */
    fun readBytes(length: Int): ByteArray? {
        if (length < 0) return null
        val out = ByteArray(length)
        var done = 0
        while (done < length) {
            val n = source.readAt(position + done, out, done, length - done)
            if (n <= 0) return null
            done += n
        }
        position += length
        return out
    }

    // ---- EBML (Matroska) numbers ----

    /** Element id with its length marker kept, as written in the spec (for example 0x1A45DFA3). -1 on error. */
    fun ebmlId(): Long {
        val first = u8()
        if (first <= 0) return -1L
        var length = 1
        var mask = 0x80
        while (first and mask == 0) {
            length++
            mask = mask shr 1
        }
        if (length > 4) return -1L
        var value = first.toLong()
        repeat(length - 1) {
            val b = u8()
            if (b < 0) return -1L
            value = (value shl 8) or b.toLong()
        }
        return value
    }

    /** Variable size number with the marker removed. Returns [UNKNOWN_SIZE] for "unknown", or -2 on error. */
    fun ebmlSize(): Long {
        val first = u8()
        if (first < 0) return -2L
        var length = 1
        var mask = 0x80
        while (first and mask == 0) {
            length++
            mask = mask shr 1
            if (length > 8) return -2L
        }
        var value = (first and (mask - 1)).toLong()
        repeat(length - 1) {
            val b = u8()
            if (b < 0) return -2L
            value = (value shl 8) or b.toLong()
        }
        val allOnes = (1L shl (7 * length)) - 1
        return if (value == allOnes) UNKNOWN_SIZE else value
    }

    companion object {
        const val UNKNOWN_SIZE = -1L
    }
}
