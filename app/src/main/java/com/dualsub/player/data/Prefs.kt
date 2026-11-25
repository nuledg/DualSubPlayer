package com.dualsub.player.data

import android.content.Context
import com.dualsub.player.player.ZoneLayout
import com.dualsub.player.subtitle.SubtitleSlot
import com.dualsub.player.subtitle.SubtitleVisibility
import com.dualsub.player.subtitle.SubtitleStyle

/**
 * Small wrapper around SharedPreferences.
 * M6 will add a settings screen on top of this.
 * Note: API keys and the OpenSubtitles password are stored in plain app-private storage.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("dualsub_prefs", Context.MODE_PRIVATE)

    /** Resume entries not opened for this many days are deleted. */
    var retentionDays: Int
        get() = sp.getInt(KEY_RETENTION_DAYS, 30)
        set(value) = sp.edit().putInt(KEY_RETENTION_DAYS, value).apply()

    // ---- Tap zones ----

    /** Which action sits where, and where the two dividing lines are. */
    var zoneLayout: ZoneLayout
        get() = ZoneLayout(
            order = ZoneLayout.parseOrder(sp.getString(KEY_ZONE_ORDER, null)),
            firstEdge = sp.getInt(KEY_ZONE_EDGE1, 35),
            secondEdge = sp.getInt(KEY_ZONE_EDGE2, 65)
        ).let { it.withEdges(it.firstEdge, it.secondEdge) }
        set(value) = sp.edit()
            .putString(KEY_ZONE_ORDER, value.serializeOrder())
            .putInt(KEY_ZONE_EDGE1, value.firstEdge)
            .putInt(KEY_ZONE_EDGE2, value.secondEdge)
            .apply()

    /** After a pause with the middle tap, show the zones on the screen. */
    var zonesOnPause: Boolean
        get() = sp.getBoolean(KEY_ZONES_ON_PAUSE, true)
        set(value) = sp.edit().putBoolean(KEY_ZONES_ON_PAUSE, value).apply()

    var zoneAxes: Boolean
        get() = sp.getBoolean(KEY_ZONE_AXES, true)
        set(value) = sp.edit().putBoolean(KEY_ZONE_AXES, value).apply()

    var zoneIcons: Boolean
        get() = sp.getBoolean(KEY_ZONE_ICONS, true)
        set(value) = sp.edit().putBoolean(KEY_ZONE_ICONS, value).apply()

    /** Show elapsed/remaining time and seek slider in the paused controls overlay. */
    var pausedTimelineVisible: Boolean
        get() = sp.getBoolean(KEY_PAUSED_TIMELINE_VISIBLE, true)
        set(value) = sp.edit().putBoolean(KEY_PAUSED_TIMELINE_VISIBLE, value).apply()

    /** Show title/action icons (swap, subtitles, settings) in the paused controls overlay. */
    var pausedActionsVisible: Boolean
        get() = sp.getBoolean(KEY_PAUSED_ACTIONS_VISIBLE, true)
        set(value) = sp.edit().putBoolean(KEY_PAUSED_ACTIONS_VISIBLE, value).apply()

    /** Allow top/bottom edge taps while paused to briefly show the right-side time without the seek bar. */
    var pausedEdgeTimeTapEnabled: Boolean
        get() = sp.getBoolean(KEY_PAUSED_EDGE_TIME_TAP_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_PAUSED_EDGE_TIME_TAP_ENABLED, value).apply()

    /** Replay the selected subtitle from its offset-adjusted start, then pause at its end. */
    var rewindSubtitleBeforePause: Boolean
        get() = sp.getBoolean(KEY_REWIND_SUBTITLE_BEFORE_PAUSE, false)
        set(value) = sp.edit().putBoolean(KEY_REWIND_SUBTITLE_BEFORE_PAUSE, value).apply()

    // ---- When each subtitle is shown ----

    fun visibility(slot: SubtitleSlot): Int =
        sp.getInt(visibilityKey(slot), SubtitleVisibility.defaultFor(slot))

    fun setVisibility(slot: SubtitleSlot, mask: Int) {
        sp.edit().putInt(visibilityKey(slot), mask).apply()
    }

    private fun visibilityKey(slot: SubtitleSlot) = "visibility_${slot.name.lowercase()}"

    /**
     * If rewind is tapped shortly after a new subtitle appears, treat that new cue as already
     * passed and go to the previous cue. Default: 300 ms. Set to 0 to disable this behavior.
     */
    var recentSubtitleRewindWindowMs: Long
        get() = sp.getLong(KEY_RECENT_SUBTITLE_REWIND_WINDOW, 300L)
        set(value) = sp.edit()
            .putLong(KEY_RECENT_SUBTITLE_REWIND_WINDOW, value.coerceIn(0L, 2_000L))
            .apply()

    /**
     * Extra time to seek before the selected subtitle cue's start when rewinding.
     * This is applied to the selected cue for single taps, recent-cue grace taps, and double taps.
     * Forward navigation is not affected. Default: 0 ms.
     */
    var rewindOffsetMs: Long
        get() = sp.getLong(KEY_REWIND_OFFSET, 0L)
        set(value) = sp.edit()
            .putLong(KEY_REWIND_OFFSET, value.coerceIn(0L, 5_000L))
            .apply()

    /** Fixed jump used when there are no subtitle cues (for example image subtitles). */
    var fallbackJumpMs: Long
        get() = sp.getLong(KEY_FALLBACK_JUMP, 5_000L)
        set(value) = sp.edit().putLong(KEY_FALLBACK_JUMP, value).apply()

    /** A horizontal swipe across the full screen width seeks this many milliseconds. */
    var swipeSeekMsPerScreen: Long
        get() = sp.getLong(KEY_SWIPE_SEEK, 120_000L)
        set(value) = sp.edit().putLong(KEY_SWIPE_SEEK, value).apply()

    /** Package name of the preferred word-translation app; null means ask every time. */
    var subtitleTranslatorPackage: String?
        get() = sp.getString(KEY_TRANSLATOR_PACKAGE, null)
        set(value) = putNullable(KEY_TRANSLATOR_PACKAGE, value)

    /** Resume playback automatically when returning from a word-translation app. Default: on. */
    var resumeAfterTranslation: Boolean
        get() = sp.getBoolean(KEY_RESUME_AFTER_TRANSLATION, true)
        set(value) = sp.edit().putBoolean(KEY_RESUME_AFTER_TRANSLATION, value).apply()

    /** Allow tapping words in the main (S1) subtitle to open the translator. Default: on. */
    var mainSubtitleWordTranslationEnabled: Boolean
        get() = sp.getBoolean(KEY_MAIN_WORD_TRANSLATION_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_MAIN_WORD_TRANSLATION_ENABLED, value).apply()

    /** Allow tapping words in the helper (S2) subtitle to open the translator. Default: on. */
    var helperSubtitleWordTranslationEnabled: Boolean
        get() = sp.getBoolean(KEY_HELPER_WORD_TRANSLATION_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_HELPER_WORD_TRANSLATION_ENABLED, value).apply()

    /** Language that auto-loads as S1 (main) and S2 (helper). */
    var s1Language: String
        get() = sp.getString(KEY_S1_LANGUAGE, "en") ?: "en"
        set(value) = sp.edit().putString(KEY_S1_LANGUAGE, value).apply()

    var s2Language: String
        get() = sp.getString(KEY_S2_LANGUAGE, "fa") ?: "fa"
        set(value) = sp.edit().putString(KEY_S2_LANGUAGE, value).apply()

    /** Folder (SAF tree URI) with the user's videos. Lets us find and write subtitles next to a video. */
    var subtitleFolderUri: String?
        get() = sp.getString(KEY_SUBTITLE_FOLDER, null)
        set(value) = putNullable(KEY_SUBTITLE_FOLDER, value)

    // ---- Subtitle download ----

    var subdlApiKey: String
        get() = sp.getString(KEY_SUBDL_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_SUBDL_KEY, value.trim()).apply()

    var openSubtitlesApiKey: String
        get() = sp.getString(KEY_OS_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_OS_KEY, value.trim()).apply()

    var openSubtitlesUser: String
        get() = sp.getString(KEY_OS_USER, "") ?: ""
        set(value) = sp.edit().putString(KEY_OS_USER, value.trim()).apply()

    var openSubtitlesPassword: String
        get() = sp.getString(KEY_OS_PASSWORD, "") ?: ""
        set(value) = sp.edit().putString(KEY_OS_PASSWORD, value).apply()

    /** "subdl" or "opensubtitles". */
    var lastProvider: String
        get() = sp.getString(KEY_PROVIDER, "subdl") ?: "subdl"
        set(value) = sp.edit().putString(KEY_PROVIDER, value).apply()

    /** "next" = next to the video, "folder" = the folder in [downloadFolderUri]. */
    var downloadMode: String
        get() = sp.getString(KEY_DOWNLOAD_MODE, MODE_NEXT) ?: MODE_NEXT
        set(value) = sp.edit().putString(KEY_DOWNLOAD_MODE, value).apply()

    /** Folder (SAF tree URI) for downloaded subtitles. */
    var downloadFolderUri: String?
        get() = sp.getString(KEY_DOWNLOAD_FOLDER, null)
        set(value) = putNullable(KEY_DOWNLOAD_FOLDER, value)

    // ---- Subtitle look and position (one style for S1, one for S2) ----

    fun subtitleStyle(slot: SubtitleSlot): SubtitleStyle =
        SubtitleStyle.parse(sp.getString(styleKey(slot), null), SubtitleStyle.defaultFor(slot))

    fun setSubtitleStyle(slot: SubtitleSlot, style: SubtitleStyle) {
        sp.edit().putString(styleKey(slot), style.coerced().serialize()).apply()
    }

    private fun styleKey(slot: SubtitleSlot) = "subtitle_style_${slot.name.lowercase()}"

    private fun putNullable(key: String, value: String?) {
        sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    companion object {
        const val MODE_NEXT = "next"
        const val MODE_FOLDER = "folder"

        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_ZONE_ORDER = "zone_order"
        private const val KEY_ZONE_EDGE1 = "zone_edge_1"
        private const val KEY_ZONE_EDGE2 = "zone_edge_2"
        private const val KEY_ZONES_ON_PAUSE = "zones_on_pause"
        private const val KEY_ZONE_AXES = "zone_axes"
        private const val KEY_ZONE_ICONS = "zone_icons"
        private const val KEY_PAUSED_TIMELINE_VISIBLE = "paused_timeline_visible"
        private const val KEY_PAUSED_ACTIONS_VISIBLE = "paused_actions_visible"
        private const val KEY_PAUSED_EDGE_TIME_TAP_ENABLED = "paused_edge_time_tap_enabled"
        private const val KEY_REWIND_SUBTITLE_BEFORE_PAUSE = "rewind_subtitle_before_pause"
        private const val KEY_RECENT_SUBTITLE_REWIND_WINDOW = "recent_subtitle_rewind_window_ms"
        private const val KEY_REWIND_OFFSET = "rewind_offset_ms"
        private const val KEY_FALLBACK_JUMP = "fallback_jump_ms"
        private const val KEY_SWIPE_SEEK = "swipe_seek_ms_per_screen"
        private const val KEY_TRANSLATOR_PACKAGE = "subtitle_translator_package"
        private const val KEY_RESUME_AFTER_TRANSLATION = "resume_after_translation"
        private const val KEY_MAIN_WORD_TRANSLATION_ENABLED = "main_subtitle_word_translation_enabled"
        private const val KEY_HELPER_WORD_TRANSLATION_ENABLED = "helper_subtitle_word_translation_enabled"
        private const val KEY_S1_LANGUAGE = "s1_language"
        private const val KEY_S2_LANGUAGE = "s2_language"
        private const val KEY_SUBTITLE_FOLDER = "subtitle_folder_uri"
        private const val KEY_SUBDL_KEY = "subdl_api_key"
        private const val KEY_OS_KEY = "opensubtitles_api_key"
        private const val KEY_OS_USER = "opensubtitles_user"
        private const val KEY_OS_PASSWORD = "opensubtitles_password"
        private const val KEY_PROVIDER = "last_provider"
        private const val KEY_DOWNLOAD_MODE = "download_mode"
        private const val KEY_DOWNLOAD_FOLDER = "download_folder_uri"
    }
}
