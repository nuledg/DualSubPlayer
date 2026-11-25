package com.dualsub.player.subtitle

import android.net.Uri

/** Something the user can put into S1 or S2: a subtitle file next to the video, or a track inside it. */
sealed class SubtitleSource {

    abstract val label: String
    abstract val language: String?

    /** True for picture subtitles (PGS, VobSub, DVB). They have no text and no timing for sentence rewind. */
    val isImage: Boolean get() = this is Embedded && !track.isText

    /** "Forced" subtitles only hold a few lines (for example foreign speech). They are a poor main subtitle. */
    val isForced: Boolean
        get() = when (this) {
            is Embedded -> track.isForced
            is External -> name.contains("forced", ignoreCase = true)
        }

    /** A file such as movie.en.srt. [language] is "en", "fa" or null. */
    data class External(
        val name: String,
        val uri: Uri,
        override val language: String?
    ) : SubtitleSource() {
        override val label: String get() = name
    }

    /** A track inside the video file. */
    data class Embedded(val track: EmbeddedTrack) : SubtitleSource() {
        override val language: String? get() = track.language
        override val label: String
            get() = buildString {
                append("Embedded ").append(track.index + 1)
                track.language?.let { append(": ").append(it) }
                track.title?.let { append(" \"").append(it).append('"') }
                append(" (").append(track.codec)
                if (!track.isText) append(", image")
                append(')')
            }
    }
}
