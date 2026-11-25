package com.dualsub.player.subtitle

import android.net.Uri
import org.json.JSONObject

/** Serializes subtitle choices and resolves them against the tracks/files discovered for a video. */
object SubtitleSelectionCodec {

    fun encode(source: SubtitleSource?): String? = when (source) {
        null -> null
        is SubtitleSource.External -> JSONObject()
            .put("type", "external")
            .put("name", source.name)
            .put("uri", source.uri.toString())
            .putNullable("language", source.language)
            .toString()
        is SubtitleSource.Embedded -> {
            val track = source.track
            JSONObject()
                .put("type", "embedded")
                .put("container", track.container.name)
                .put("id", track.id)
                .put("index", track.index)
                .put("codec", track.codec)
                .putNullable("language", track.language)
                .putNullable("title", track.title)
                .put("isText", track.isText)
                .toString()
        }
    }

    /**
     * Resolve an old selection only against the current video's discovered sources.
     * External files use the persisted document URI first, then a unique exact filename match.
     * Embedded tracks use container + track ID first, with a stable metadata fallback.
     */
    fun resolve(descriptor: String?, available: List<SubtitleSource>): SubtitleSource? {
        if (descriptor.isNullOrBlank()) return null
        val saved = runCatching { JSONObject(descriptor) }.getOrNull() ?: return null
        return when (saved.optString("type")) {
            "external" -> {
                val savedUri = saved.optString("uri")
                    .takeIf { it.isNotBlank() }
                    ?.let { Uri.parse(it) }
                val savedName = saved.optString("name").ifBlank {
                    savedUri?.lastPathSegment ?: "Saved subtitle"
                }
                val savedLanguage = saved.nullableString("language")
                val files = available.filterIsInstance<SubtitleSource.External>()
                files.firstOrNull { savedUri != null && it.uri == savedUri }
                    ?: files.filter { it.name.equals(savedName, ignoreCase = true) }.singleOrNull()
                    // A manually picked file may be outside the video's folder and therefore
                    // absent from the discovery list. Its persisted SAF/file URI is still usable.
                    ?: savedUri?.let { SubtitleSource.External(savedName, it, savedLanguage) }
            }
            "embedded" -> {
                val tracks = available.filterIsInstance<SubtitleSource.Embedded>()
                val container = saved.optString("container")
                val id = saved.optLong("id", Long.MIN_VALUE)
                val codec = saved.optString("codec")
                tracks.firstOrNull {
                    it.track.container.name == container && it.track.id == id &&
                        it.track.codec.equals(codec, ignoreCase = true)
                } ?: run {
                    val language = saved.nullableString("language")
                    val title = saved.nullableString("title")
                    val index = saved.optInt("index", -1)
                    val metadataMatches = tracks.filter {
                        it.track.codec.equals(codec, ignoreCase = true) &&
                            it.track.language.equals(language, ignoreCase = true) &&
                            it.track.title.equals(title, ignoreCase = true)
                    }
                    metadataMatches.singleOrNull()
                        ?: metadataMatches.firstOrNull { it.track.index == index }
                }
            }
            else -> null
        }
    }

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
