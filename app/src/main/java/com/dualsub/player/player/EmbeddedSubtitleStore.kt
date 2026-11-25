package com.dualsub.player.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dualsub.player.subtitle.ByteSource
import com.dualsub.player.subtitle.Cue
import com.dualsub.player.subtitle.EmbeddedSubtitles
import com.dualsub.player.subtitle.EmbeddedTrack
import com.dualsub.player.subtitle.SrtWriter
import com.dualsub.player.subtitle.SubtitleParser
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Reads a video through a file descriptor so the readers can jump around in it. */
class ChannelByteSource(private val pfd: ParcelFileDescriptor) : ByteSource, Closeable {
    private val stream = FileInputStream(pfd.fileDescriptor)
    private val channel = stream.channel
    override val size: Long = channel.size()

    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int =
        channel.read(ByteBuffer.wrap(dst, offset, length), position)

    override fun close() {
        try {
            stream.close()
        } finally {
            pfd.close()
        }
    }
}

/**
 * Lists embedded subtitle tracks and reads all their cues.
 * Cues are cached on disk as SRT, one file per video and track, so the long read happens once.
 */
class EmbeddedSubtitleStore(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "embedded_subs").apply { mkdirs() }

    /** Only local files can be opened this way. Streams (http, https) are not supported. */
    private fun open(uri: Uri): ChannelByteSource? {
        if (uri.scheme != "content" && uri.scheme != "file") return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.let { ChannelByteSource(it) }
        } catch (_: Exception) {
            null // no permission, provider without seekable files, and so on
        }
    }

    fun listTracks(uri: Uri): List<EmbeddedTrack> =
        open(uri)?.use { EmbeddedSubtitles.listTracks(it) } ?: emptyList()

    /** [videoKey] identifies the video (see [keyFor]). [checkCancelled] may throw to stop a long read. */
    fun readCues(uri: Uri, videoKey: String, track: EmbeddedTrack, checkCancelled: () -> Unit): List<Cue> {
        val cacheFile = File(cacheDir, "${videoKey}_${track.container}_${track.id}.srt")

        if (cacheFile.length() > 0) {
            val cached = SubtitleParser.parse(cacheFile.readText(Charsets.UTF_8), cacheFile.name)
            if (cached.isNotEmpty()) {
                cacheFile.setLastModified(System.currentTimeMillis()) // keeps it alive for the cleanup
                return cached
            }
        }

        val cues = open(uri)?.use { EmbeddedSubtitles.readCues(it, track, checkCancelled) } ?: emptyList()
        if (cues.isNotEmpty()) {
            // Write to a temporary file first, so a half-written file is never used.
            val temp = File(cacheDir, cacheFile.name + ".tmp")
            temp.writeText(SrtWriter.write(cues), Charsets.UTF_8)
            temp.renameTo(cacheFile)
        }
        return cues
    }

    /** Deletes cache files not used for [days] days. */
    fun cleanup(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        cacheDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    companion object {
        /** Same video (name and size) gives the same key, even when the URI changes. */
        fun keyFor(name: String, size: Long): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("$name|$size".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(20)
        }
    }
}
