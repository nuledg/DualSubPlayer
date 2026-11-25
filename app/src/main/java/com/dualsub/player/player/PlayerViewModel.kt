package com.dualsub.player.player

import android.app.Application
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.dualsub.player.data.AppDatabase
import com.dualsub.player.data.Prefs
import com.dualsub.player.data.ResumeEntry
import com.dualsub.player.data.SubtitleSelectionEntry
import com.dualsub.player.download.ReleaseInfo
import com.dualsub.player.download.ReleaseNameParser
import com.dualsub.player.download.SubtitleDownloadController
import com.dualsub.player.subtitle.AutoPick
import com.dualsub.player.subtitle.Cue
import com.dualsub.player.subtitle.CueList
import com.dualsub.player.subtitle.ForwardSession
import com.dualsub.player.subtitle.LanguageCodes
import com.dualsub.player.subtitle.RewindSession
import com.dualsub.player.subtitle.RewindTapSequence
import com.dualsub.player.subtitle.SentenceSeek
import com.dualsub.player.subtitle.SubtitleLocator
import com.dualsub.player.subtitle.SubtitleParser
import com.dualsub.player.subtitle.SubtitleSlot
import com.dualsub.player.subtitle.SubtitleSource
import com.dualsub.player.subtitle.SubtitleSelectionCodec
import com.dualsub.player.subtitle.SubtitleStyle
import com.dualsub.player.subtitle.SubtitleVisibility
import com.dualsub.player.subtitle.TextDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** Holds the ExoPlayer, resume logic, subtitles, and sentence rewind. Survives rotation. */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    // After a seek, ExoPlayer's default lets the video renderer say "ready" for up to 5 seconds
    // BEFORE it has drawn a frame (allowed joining time). Audio then starts at the new position
    // while the screen still shows the old frame, until the decoder catches up from the previous
    // key frame. Setting it to 0 makes the whole player wait for the first frame of the new
    // position, so audio and picture always start together.
    private val renderersFactory = DefaultRenderersFactory(app)
        .setAllowedVideoJoiningTimeMs(0L)
        .setEnableDecoderFallback(true)

    val player: ExoPlayer = ExoPlayer.Builder(app, renderersFactory).build().apply {
        // Exact seeking: video, audio and subtitles land on the same frame.
        setSeekParameters(SeekParameters.EXACT)
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        setHandleAudioBecomingNoisy(true)
        // We draw text subtitles ourselves (dual subtitles), so ExoPlayer's text track is off.
        // It is only switched on for picture subtitles (PGS, DVB) in S1.
        trackSelectionParameters = trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    val prefs = Prefs(app)

    /** Search and download of subtitles (the download dialog reads its state here). */
    val download = SubtitleDownloadController(app, prefs, viewModelScope) { slot, source ->
        registerDownloaded(slot, source)
    }

    var currentUri: Uri? = null
        private set
    var title by mutableStateOf("")
        private set

    /** The video file name split into name, season/episode or year. Shown in the subtitle window. */
    var videoRelease by mutableStateOf<ReleaseInfo?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    // ---- Subtitle state shown in the UI ----

    /** Text of S1 at the current position. Always visible. */
    var s1Text by mutableStateOf("")
        private set

    /** Text of S2. Only filled while in rewind mode. */
    var s2Text by mutableStateOf("")
        private set

    /** Look and position of S1 and S2. Changing them updates the screen at once. */
    var s1Style by mutableStateOf(prefs.subtitleStyle(SubtitleSlot.S1))
        private set
    var s2Style by mutableStateOf(prefs.subtitleStyle(SubtitleSlot.S2))
        private set

    /** Files next to the video first, then tracks inside the video. */
    var availableSubs by mutableStateOf<List<SubtitleSource>>(emptyList())
        private set
    var s1Source by mutableStateOf<SubtitleSource?>(null)
        private set
    var s2Source by mutableStateOf<SubtitleSource?>(null)
        private set

    /** True when S1 is a picture subtitle that ExoPlayer draws. The screen then shows its subtitle view. */
    var imageSubtitleActive by mutableStateOf(false)
        private set

    /** Short message for the user (for example "No subtitle lines found"). */
    var notice by mutableStateOf<String?>(null)
        private set

    private var s1Cues: CueList? = null
    private var s2Cues: CueList? = null
    private val rewind = RewindSession()
    private val forward = ForwardSession()
    private val rewindTapSequence = RewindTapSequence()
    private data class RecentForwardCue(val cues: CueList, val startMs: Long, val tappedAtMs: Long)
    private var recentForwardCue: RecentForwardCue? = null

    /** When each subtitle is shown. Changed in Settings and used at once. */
    var s1Visibility by mutableStateOf(prefs.visibility(SubtitleSlot.S1))
        private set
    var s2Visibility by mutableStateOf(prefs.visibility(SubtitleSlot.S2))
        private set

    /** True after the middle tap paused the video: the screen then shows the tap zones. */
    var zoneOverlay by mutableStateOf(false)
        private set

    private var kickJob: Job? = null
    private var discoverJob: Job? = null
    private var s1Job: Job? = null
    private var s2Job: Job? = null

    private val embeddedStore = EmbeddedSubtitleStore(app)
    private var videoKey: String? = null

    // ExoPlayer text track handling (picture subtitles)
    private var appliedTextIndex: Int? = null
    private var wantedTextIndex: Int? = null

    // ---- Resume state ----

    private val database = AppDatabase.get(app)
    private val dao = database.resumeDao()
    private val subtitleSelectionDao = database.subtitleSelectionDao()

    // Final saves must finish even when the ViewModel is closing, so they use their own scope.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subtitleSelectionWriteMutex = Mutex()
    private val subtitleSelectionRevision = AtomicLong(0L)

    private var meta: VideoMeta? = null
    private var key: ResumeKey? = null
    private var resumeReady = false
    private var resumeStarted = false
    private var saveJob: Job? = null
    private var translationReturnPending = false
    private var subtitleSelectionConfigured = false
    // Retain unresolved saved descriptors until the user explicitly changes that slot.
    private var unresolvedS1Descriptor: String? = null
    private var unresolvedS2Descriptor: String? = null

    private data class ResumeKey(val name: String, val size: Long, val duration: Long)

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            // Duration is only known once the player is READY, so the resume lookup happens here.
            if (state == Player.STATE_READY && !resumeStarted) startResume()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) zoneOverlay = false
            if (!isPlaying) saveNow() // pause, end, or stop
        }

        override fun onTracksChanged(tracks: Tracks) {
            // The picture subtitle we wanted may not have been known yet when it was chosen.
            if (wantedTextIndex != null) updateExoText()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            refreshSubtitles()
        }

        override fun onPlayerError(e: PlaybackException) {
            error = "Cannot play this video (${e.errorCodeName})"
        }
    }

    init {
        player.addListener(listener)

        ioScope.launch {
            // Delete resume entries and cached subtitles that were not used for a long time.
            val days = prefs.retentionDays
            val cutoff = System.currentTimeMillis() - days * DAY_MS
            dao.deleteOlderThan(cutoff)
            subtitleSelectionDao.deleteOlderThan(cutoff)
            embeddedStore.cleanup(days)
        }

        // Subtitles follow the playback position, 10 times per second.
        viewModelScope.launch {
            while (isActive) {
                refreshSubtitles()
                delay(SUBTITLE_TICK_MS)
            }
        }
    }

    fun open(uri: Uri) {
        saveNow() // save the previous video first
        cancelPauseAfterReplay()
        kickJob?.cancel()
        saveJob?.cancel()
        currentUri = uri
        translationReturnPending = false
        subtitleSelectionConfigured = false
        unresolvedS1Descriptor = null
        unresolvedS2Descriptor = null
        error = null
        key = null
        resumeReady = false
        resumeStarted = false

        // New video: forget everything about the old subtitles.
        rewind.exit()
        forward.exit()
        resetRewindGestureSequence()
        zoneOverlay = false
        s1Job?.cancel()
        s2Job?.cancel()
        s1Cues = null
        s2Cues = null
        s1Source = null
        s2Source = null
        availableSubs = emptyList()
        s1Text = ""
        s2Text = ""
        wantedTextIndex = null
        updateExoText()

        val info = MediaInfo.query(getApplication(), uri)
        meta = info
        title = info.name
        videoRelease = ReleaseNameParser.parse(info.name)
        videoKey = EmbeddedSubtitleStore.keyFor(info.name, info.size)

        player.setMediaItem(MediaItem.fromUri(uri))
        player.playWhenReady = false // wait until we know the resume position
        player.prepare()

        download.bind(uri, info.name)
        discoverSubtitles(uri, info.name)
    }

    // ---- Seek actions used by gestures and the seek bar ----

    private var pauseAfterReplayJob: Job? = null

    fun togglePlayPause() {
        resetRewindGestureSequence()
        togglePlaybackFromUserAction()
    }

    /**
     * The replay-before-pause feature is only for a real playing -> pause transition.
     * A paused -> play transition must never seek: it resumes at the exact current position.
     * playWhenReady is still used to handle a pause request while ExoPlayer is buffering.
     */
    private fun togglePlaybackFromUserAction() {
        val playbackWasRequested = player.playWhenReady

        // isPlaying is deliberately used here instead of playWhenReady. The latter can be true
        // while buffering, when there is no active playback to rewind and replay.
        if (player.isPlaying && pauseAfterReplayJob?.isActive != true &&
            startSubtitleReplayThenPause()
        ) {
            // The video keeps playing through the selected subtitle. The coroutine pauses only
            // when playback reaches that cue's end timestamp.
            zoneOverlay = false
            return
        }

        // Cancel an older replay job before taking direct control. In particular, this prevents
        // a stale auto-pause from stopping playback after the user presses Play again.
        cancelPauseAfterReplay()
        if (playbackWasRequested) {
            player.pause()
            zoneOverlay = prefs.zonesOnPause
        } else {
            // No seek here: Play always resumes from ExoPlayer's current position.
            player.play()
            zoneOverlay = false
        }
    }

    /** False after an error, at the end of the video, or when nothing is loaded. */
    private fun canProgress(): Boolean =
        player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING

    private fun cancelPauseAfterReplay() {
        pauseAfterReplayJob?.cancel()
        pauseAfterReplayJob = null
    }

    /** Replays the current cue from its configured lead-in, then pauses at the pre-pause position. */
    private fun startSubtitleReplayThenPause(): Boolean {
        if (!prefs.rewindSubtitleBeforePause) return false
        val originalPosition = player.currentPosition
        // Replay only when a cue is active now or ended within the previous five seconds.
        // S1 is preferred; S2 supplies timing when S1 has no recent cue.
        val selected = SentenceSeek.replayBeforePauseTimeline(
            s1Cues,
            s2Cues,
            originalPosition,
            PAUSE_REPLAY_LOOKBACK_MS
        ) ?: return false
        val replayWindow = selected.second
        val replayStart = (replayWindow.startMs - prefs.rewindOffsetMs).coerceAtLeast(0L)
        val mediaDuration = player.duration
        val cueEnd = if (mediaDuration != C.TIME_UNSET && mediaDuration >= 0L) {
            minOf(replayWindow.endMs, mediaDuration)
        } else {
            replayWindow.endMs
        }
        // Stop slightly before the subtitle's end so it is still visible on the paused frame.
        // For very short cues, reduce the margin so the pause point stays inside the cue.
        val endMargin = minOf(PAUSE_REPLAY_END_MARGIN_MS, (cueEnd - replayWindow.startMs).coerceAtLeast(0L) / 2L)
        val replayEnd = cueEnd - endMargin
        // If the cue starts exactly at the pause position, there is no earlier segment to replay.
        if (replayStart >= originalPosition || replayEnd <= replayStart) return false

        cancelPauseAfterReplay()
        // Keep helper subtitle mode active through the real cue end. The player pauses a little
        // earlier, so S2 remains visible on the paused frame and until playback reaches cueEnd.
        rewind.onRewindTap(cueEnd)
        forward.exit()
        resetRewindGestureSequence()
        player.setSeekParameters(SeekParameters.EXACT)
        seekWithinVideo(replayStart)
        refreshSubtitles()
        player.play()
        pauseAfterReplayJob = viewModelScope.launch {
            // First wait for ExoPlayer to apply the backward seek. This matters when the selected
            // cue ended before the original pause position: the stale pre-seek position may already
            // be beyond replayEnd and must not trigger an immediate pause.
            while (isActive && player.playWhenReady && canProgress() &&
                player.currentPosition >= originalPosition
            ) {
                delay(10L)
            }
            // Replay until just before the cue ends, leaving its subtitle visible on the paused
            // frame. The original pause position is intentionally irrelevant after cue selection.
            while (isActive && player.playWhenReady && canProgress() &&
                player.currentPosition < replayEnd
            ) {
                delay(15L)
            }
            if (isActive) {
                val reachedCueEnd = player.playWhenReady && player.currentPosition >= replayEnd
                player.pause()
                // Polling can observe a frame just beyond the target. Clamp to the pre-end pause
                // position so Play resumes with the final part of the subtitle still visible.
                if (reachedCueEnd) seekWithinVideo(replayEnd)
                zoneOverlay = prefs.zonesOnPause
                refreshSubtitles()
            }
        }
        return true
    }

    /** A non-rewind gesture breaks the consecutive-tap sequence. */
    fun cancelPendingRewindDoubleTap() {
        resetRewindGestureSequence()
    }

    private fun resetRewindGestureSequence() {
        rewindTapSequence.reset()
        recentForwardCue = null
    }

    /** Tap in the play/pause zone. Pausing opens the playback controls and optional zone guide. */
    fun tapPlayPause() {
        togglePlaybackFromUserAction()
        resetRewindGestureSequence()
    }

    /**
     * Tap in the rewind zone.
     * One tap always replays the subtitle currently displayed, from its exact start timestamp.
     * A second tap within the fixed double-tap interval goes to the previous subtitle. If the
     * current subtitle appeared very recently, the first tap instead treats it as already passed
     * and goes to the previous subtitle. S1 is preferred; S2 supplies timing when S1 cannot.
     * Without text cue timing in either slot, use the configured fixed jump.
     * The first tap in a sequence keeps the original return point until playback catches up.
     */
    fun tapRewind() {
        cancelPauseAfterReplay()
        val t = player.currentPosition
        val now = SystemClock.uptimeMillis()
        val recentWindow = prefs.recentSubtitleRewindWindowMs
        val justForwarded = recentForwardCue?.takeIf {
            recentWindow > 0L && now >= it.tappedAtMs && now - it.tappedAtMs < recentWindow
        }
        recentForwardCue = null

        // A forward seek may not yet be reflected by currentPosition on the first frame. Use the
        // exact cue and timestamp selected by the forward action so an immediate rewind reliably
        // returns to the cue before that one, including when S2 supplied the timeline.
        val target = (if (justForwarded != null) {
            rewindTapSequence.target(
                cues = justForwarded.cues,
                positionMs = justForwarded.startMs,
                nowMs = now,
                windowMs = DOUBLE_TAP_WINDOW_MS,
                recentCueWindowMs = recentWindow,
                rewindOffsetMs = prefs.rewindOffsetMs
            )
        } else {
            rewindTapSequence.target(
                mainCues = s1Cues,
                helperCues = s2Cues,
                positionMs = t,
                nowMs = now,
                windowMs = DOUBLE_TAP_WINDOW_MS,
                recentCueWindowMs = recentWindow,
                rewindOffsetMs = prefs.rewindOffsetMs
            )
        }) ?: run {
            rewindTapSequence.reset()
            t - prefs.fallbackJumpMs
        }

        // Only the first tap of a series stores the position. A tap that does not move back
        // (target is not before the current position) would leave rewind mode stuck on, because
        // playback never goes "behind" the return point.
        if (target < t) rewind.onRewindTap(t)
        forward.exit()
        jumpAndPlay(target)
    }

    /** Tap in the forward zone: next S1 cue, or the S2 cue when S1 has no upcoming cue. */
    fun tapForward() {
        cancelPauseAfterReplay()
        resetRewindGestureSequence()
        val t = player.currentPosition
        val cues = SentenceSeek.forwardTimeline(s1Cues, s2Cues, t)
        val target: Long
        val sentenceEnd: Long
        if (cues != null && !cues.isEmpty()) {
            target = SentenceSeek.forwardTarget(cues, t) ?: return
            val i = cues.lastStartingAtOrBefore(target)
            sentenceEnd = if (i >= 0) cues[i].endMs else target + prefs.fallbackJumpMs
        } else {
            target = t + prefs.fallbackJumpMs
            sentenceEnd = target + FORWARD_MODE_FALLBACK_MS
        }
        if (cues != null) {
            recentForwardCue = RecentForwardCue(cues, target, SystemClock.uptimeMillis())
        }
        forward.start(sentenceEnd)
        jumpAndPlay(target)
    }

    /** Seek bar. A manual seek leaves rewind and forward mode and hides the helper subtitle. */
    fun seekManual(positionMs: Long) {
        cancelPauseAfterReplay()
        rewind.exit()
        forward.exit()
        resetRewindGestureSequence()
        seekWithinVideo(positionMs)
        refreshSubtitles()
    }

    // ---- Sliding a finger across the screen: the video follows the finger ----

    private var scrubWasPlaying = false
    private var scrubStartPosition = 0L
    private var lastScrubSeek = 0L

    fun scrubStart() {
        cancelPauseAfterReplay()
        resetRewindGestureSequence()
        scrubStartPosition = player.currentPosition
        scrubWasPlaying = player.playWhenReady
        player.pause()
        // While the finger moves, jumping to the nearest key frame is fast and smooth.
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        lastScrubSeek = 0L
    }

    fun scrubTo(positionMs: Long) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScrubSeek < SCRUB_INTERVAL_MS) return
        lastScrubSeek = now
        seekWithinVideo(positionMs)
        refreshSubtitles()
    }

    /** Finger lifted. [commit] false (touch cancelled) goes back to where the swipe started. */
    fun scrubEnd(finalMs: Long, commit: Boolean) {
        player.setSeekParameters(SeekParameters.EXACT) // the final spot is frame exact
        if (commit) {
            rewind.exit()
            forward.exit()
            resetRewindGestureSequence()
        }
        seekWithinVideo(if (commit) finalMs else scrubStartPosition)
        if (scrubWasPlaying) player.play()
        refreshSubtitles()
        watchForFreeze()
    }

    /** Jump to [target] and keep playing from there, so the sentence replays at once. */
    private fun jumpAndPlay(target: Long) {
        player.setSeekParameters(SeekParameters.EXACT)
        seekWithinVideo(target)
        if (!player.playWhenReady) player.play()
        zoneOverlay = false
        refreshSubtitles()
        watchForFreeze()
    }

    private fun seekWithinVideo(target: Long) {
        val duration = player.duration
        val max = if (duration == C.TIME_UNSET) Long.MAX_VALUE else duration
        player.seekTo(target.coerceIn(0L, max))
    }

    /**
     * Some phones keep showing the old picture after a seek until you pause and play again.
     * If the position does not move shortly after a seek, we seek once more to the same spot,
     * which wakes the video decoder up.
     */
    private fun watchForFreeze() {
        kickJob?.cancel()
        kickJob = viewModelScope.launch {
            delay(FREEZE_CHECK_DELAY_MS)
            if (!player.playWhenReady || player.playbackState != Player.STATE_READY) return@launch
            val before = player.currentPosition
            delay(FREEZE_CHECK_WINDOW_MS)
            val stuck = player.playWhenReady && player.playbackState == Player.STATE_READY &&
                player.currentPosition - before < FREEZE_MIN_PROGRESS_MS
            if (stuck) {
                player.seekTo(player.currentPosition)
                if (!player.isPlaying) player.play()
            }
        }
    }

    // ---- Subtitles ----

    private fun refreshSubtitles() {
        val t = player.currentPosition
        rewind.onPosition(t)   // S2 hides when playback reaches the return point
        forward.onPosition(t)  // forward mode ends when the sentence is over
        val paused = !player.playWhenReady
        s1Text = if (SubtitleVisibility.isVisible(s1Visibility, paused, rewind.active, forward.active)) {
            s1Cues?.textAt(t) ?: ""
        } else ""
        s2Text = if (SubtitleVisibility.isVisible(s2Visibility, paused, rewind.active, forward.active)) {
            s2Cues?.s2TextAt(t) ?: ""
        } else ""
    }

    /** Settings: when should this subtitle be shown? */
    fun setVisibility(slot: SubtitleSlot, mask: Int) {
        if (slot == SubtitleSlot.S1) s1Visibility = mask else s2Visibility = mask
        prefs.setVisibility(slot, mask)
        refreshSubtitles()
    }

    fun visibilityOf(slot: SubtitleSlot): Int = if (slot == SubtitleSlot.S1) s1Visibility else s2Visibility

    private fun discoverSubtitles(uri: Uri, videoName: String) {
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            val tree = prefs.subtitleFolderUri?.let { Uri.parse(it) }
            val downloadTree = prefs.downloadFolderUri?.let { Uri.parse(it) }
            val externals = withContext(Dispatchers.IO) {
                SubtitleLocator.find(getApplication(), uri, videoName, tree, downloadTree)
            }
            val embedded = withContext(Dispatchers.IO) {
                embeddedStore.listTracks(uri)
                    .map { SubtitleSource.Embedded(it) }
                    .sortedBy { it.isImage } // text tracks first
            }
            val all: List<SubtitleSource> = externals + embedded
            val currentMeta = meta ?: return@launch
            // Folder access can be granted again while this video is already open. In that case,
            // keep the live selections (including manually added files and Off slots) rather than
            // replacing them with a stale database read or running auto-pick over the user's choice.
            if (subtitleSelectionConfigured) {
                availableSubs = all + listOfNotNull(s1Source, s2Source).filterNot { it in all }
                persistSubtitleSelection()
                return@launch
            }
            val selection = withContext(Dispatchers.IO) {
                subtitleSelectionDao.find(currentMeta.name, currentMeta.size)
            }
            // A selection row means the user has a saved configuration for this exact video.
            // Restore both slots as saved, including an intentionally empty slot. Never replace
            // a missing saved subtitle silently with a different language or embedded track.
            if (selection != null) {
                subtitleSelectionConfigured = true
                val savedS1 = SubtitleSelectionCodec.resolve(selection.s1Descriptor, all)
                val savedS2 = SubtitleSelectionCodec.resolve(selection.s2Descriptor, all)
                val missingS1 = selection.s1Descriptor != null && savedS1 == null
                val missingS2 = selection.s2Descriptor != null && savedS2 == null
                unresolvedS1Descriptor = if (missingS1) selection.s1Descriptor else null
                unresolvedS2Descriptor = if (missingS2) selection.s2Descriptor else null
                // A manually chosen external file may live outside every scanned folder. Keep it
                // in the picker as well as loading it from its remembered URI.
                availableSubs = all + listOfNotNull(savedS1, savedS2).filterNot { it in all }
                assignSubtitle(SubtitleSlot.S1, savedS1, persistSelection = false)
                assignSubtitle(SubtitleSlot.S2, savedS2, persistSelection = false)
                withContext(Dispatchers.IO) {
                    subtitleSelectionDao.touch(currentMeta.name, currentMeta.size, System.currentTimeMillis())
                }
                if (missingS1 || missingS2) {
                    val missingSlots = listOfNotNull(
                        if (missingS1) "main (S1)" else null,
                        if (missingS2) "helper (S2)" else null
                    ).joinToString(" and ")
                    notice = "Previously selected $missingSlots subtitle could not be found. Choose it again in Subtitles."
                }
                return@launch
            }

            availableSubs = all
            if (all.isEmpty()) {
                if (tree == null) {
                    notice = "No subtitles found. Long press, open Subtitles, and choose your subtitle folder."
                }
                return@launch
            }

            // First open: auto pick by language. Text beats pictures, full subtitles beat forced
            // ones, files beat embedded tracks. Save the actual choices so later opens are exact.
            val choice = AutoPick.choose(all, prefs.s1Language, prefs.s2Language)
            assignSubtitle(SubtitleSlot.S1, choice.s1, persistSelection = false)
            assignSubtitle(SubtitleSlot.S2, choice.s2, persistSelection = false)
            if (choice.s1 != null || choice.s2 != null) {
                subtitleSelectionConfigured = true
                persistSubtitleSelection()
            }
        }
    }

    /** Puts [source] into S1 or S2 (null turns the slot off) and loads its cues. */
    fun assignSubtitle(
        slot: SubtitleSlot,
        source: SubtitleSource?,
        persistSelection: Boolean = true
    ) {
        resetRewindGestureSequence()
        if (persistSelection) {
            subtitleSelectionConfigured = true
            when (slot) {
                SubtitleSlot.S1 -> unresolvedS1Descriptor = null
                SubtitleSlot.S2 -> unresolvedS2Descriptor = null
            }
        }
        when (slot) {
            SubtitleSlot.S1 -> {
                s1Source = source
                s1Job?.cancel()
                s1Job = loadInto(slot, source)
            }
            SubtitleSlot.S2 -> {
                s2Source = source
                s2Job?.cancel()
                s2Job = loadInto(slot, source)
            }
        }
        updateExoText()
        if (persistSelection) persistSubtitleSelection()
    }

    /** One tap swaps what is in S1 and S2. */
    fun swapSubtitles() {
        val a = s1Source
        val b = s2Source
        if (a == null && b == null) return

        val loading = s1Job?.isActive == true || s2Job?.isActive == true
        val oldUnresolvedS1 = unresolvedS1Descriptor
        val oldUnresolvedS2 = unresolvedS2Descriptor
        unresolvedS1Descriptor = oldUnresolvedS2
        unresolvedS2Descriptor = oldUnresolvedS1
        if (loading) {
            // Something is still being read. Load both again in the new places.
            assignSubtitle(SubtitleSlot.S1, b, persistSelection = false)
            assignSubtitle(SubtitleSlot.S2, a, persistSelection = false)
        } else {
            // Everything is loaded, so we just move the cue lists.
            resetRewindGestureSequence()
            val cues = s1Cues
            s1Cues = s2Cues
            s2Cues = cues
            s1Source = b
            s2Source = a
            updateExoText()
            imageNoticeFor(SubtitleSlot.S1, b)?.let { notice = it }
            imageNoticeFor(SubtitleSlot.S2, a)?.let { notice = it }
        }
        persistSubtitleSelection()
        refreshSubtitles()
    }

    /** Persist exact external-file URIs or embedded track identities for this video. */
    private fun persistSubtitleSelection() {
        val info = meta ?: return
        if (!subtitleSelectionConfigured) return
        val revision = subtitleSelectionRevision.incrementAndGet()
        val entry = SubtitleSelectionEntry(
            videoName = info.name,
            videoSize = info.size,
            s1Descriptor = SubtitleSelectionCodec.encode(s1Source) ?: unresolvedS1Descriptor,
            s2Descriptor = SubtitleSelectionCodec.encode(s2Source) ?: unresolvedS2Descriptor,
            updatedAt = System.currentTimeMillis()
        )
        ioScope.launch {
            subtitleSelectionWriteMutex.withLock {
                // Several quick slot changes can enqueue writes together. Only the newest
                // snapshot is allowed to win, regardless of IO dispatcher scheduling order.
                if (revision == subtitleSelectionRevision.get()) {
                    subtitleSelectionDao.upsert(entry)
                }
            }
        }
    }

    private fun loadInto(slot: SubtitleSlot, source: SubtitleSource?): Job = viewModelScope.launch {
        var cues: CueList? = null
        when (source) {
            null -> Unit
            is SubtitleSource.External -> cues = loadExternal(source)
            is SubtitleSource.Embedded -> cues = loadEmbedded(source)
        }
        ensureActive() // do not apply the result when the user already chose something else
        if (slot == SubtitleSlot.S1) s1Cues = cues else s2Cues = cues
        imageNoticeFor(slot, source)?.let { notice = it }
        refreshSubtitles()
    }

    /** Text and picture tracks both have rules: pictures have no timing and can only be S1. */
    private fun imageNoticeFor(slot: SubtitleSlot, source: SubtitleSource?): String? {
        if (source !is SubtitleSource.Embedded || source.track.isText) return null
        val displayable = source.track.codec in EXO_IMAGE_CODECS
        return when {
            slot == SubtitleSlot.S2 -> "Picture subtitles can only be shown as S1."
            !displayable -> "${source.track.codec} subtitles cannot be shown by this player. Pick another track."
            else -> "Picture subtitles have no timing. Rewind jumps back ${prefs.fallbackJumpMs / 1000} seconds."
        }
    }

    private suspend fun loadExternal(source: SubtitleSource.External): CueList? {
        val result = withContext(Dispatchers.IO) { runCatching { readExternalCues(source) } }
        coroutineContext.ensureActive()
        return result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    notice = "No subtitle lines found in ${source.name}"
                    null
                } else {
                    CueList(list)
                }
            },
            onFailure = {
                notice = "Cannot read ${source.name}"
                null
            }
        )
    }

    private fun readExternalCues(source: SubtitleSource.External): List<Cue> {
        val bytes = getApplication<Application>().contentResolver.openInputStream(source.uri)
            ?.use { it.readBytes() } ?: error("Cannot open ${source.name}")
        require(bytes.size <= MAX_SUBTITLE_BYTES) { "Subtitle file is too large" }
        val text = TextDecoder.decode(bytes, source.language) // converts to UTF-8 text
        return SubtitleParser.parse(text, source.name)
    }

    /** Reads all cues of a text track inside the video. Pictures return null (no timing). */
    private suspend fun loadEmbedded(source: SubtitleSource.Embedded): CueList? {
        if (!source.track.isText) return null
        val uri = currentUri ?: return null
        val key = videoKey ?: return null

        notice = "Reading subtitles from the video..."
        val result = withContext(Dispatchers.IO) {
            val context = coroutineContext
            runCatching {
                embeddedStore.readCues(uri, key, source.track) { context.ensureActive() }
            }
        }
        coroutineContext.ensureActive()
        return result.fold(
            onSuccess = { list ->
                if (list.isEmpty()) {
                    notice = "No subtitle lines found in this track."
                    null
                } else {
                    notice = null
                    CueList(list)
                }
            },
            onFailure = {
                notice = "Cannot read this subtitle track."
                null
            }
        )
    }

    // ---- Picture subtitles: ExoPlayer draws them ----

    /**
     * Switches ExoPlayer's own subtitle rendering on for a picture track in S1, and off otherwise.
     *
     * Which ExoPlayer track is ours? We look only at ExoPlayer text tracks of the same picture format
     * (PGS, DVB) and take the one in the same position among tracks of that format. Counting only
     * picture tracks means text tracks, which ExoPlayer lists too, cannot shift the order.
     */
    private fun updateExoText() {
        val track = (s1Source as? SubtitleSource.Embedded)?.track
            ?.takeIf { !it.isText && it.codec in EXO_IMAGE_CODECS }
        val desired = track?.index
        imageSubtitleActive = desired != null

        val group = track?.let { findExoGroup(it) }
        if (track != null && group == null) {
            wantedTextIndex = track.index // tracks are not known yet, onTracksChanged will call us again
            return
        }
        wantedTextIndex = null
        if (desired == appliedTextIndex) return

        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (group == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = builder.build()
        appliedTextIndex = desired
    }

    private fun findExoGroup(track: com.dualsub.player.subtitle.EmbeddedTrack): Tracks.Group? {
        val mime = EXO_IMAGE_MIME[track.codec] ?: return null
        val sameFormat = player.currentTracks.groups.filter { group ->
            group.type == C.TRACK_TYPE_TEXT && group.length > 0 && group.getTrackFormat(0).sampleMimeType == mime
        }
        // Position of our track among the tracks of the same picture format in the file.
        val ordinal = availableSubs
            .filterIsInstance<SubtitleSource.Embedded>()
            .count { it.track.codec == track.codec && it.track.index < track.index }
        return sameFormat.getOrNull(ordinal)
    }

    // ---- Adding subtitles by hand ----

    /** The user picked a subtitle file by hand. It goes into [slot]. */
    fun addSubtitleFile(slot: SubtitleSlot, uri: Uri) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val name = MediaInfo.query(getApplication(), uri).name
        // The video's own name can contain words like "English" or "Per", so read only what follows it.
        val videoBase = meta?.name?.substringBeforeLast('.')
        val language = if (videoBase != null && name.startsWith("$videoBase.", ignoreCase = true)) {
            LanguageCodes.fromFileName(name.substring(videoBase.length + 1))
        } else {
            LanguageCodes.fromFileName(name, maxTokensFromEnd = 3) // the name still holds a title
        }
        val source = SubtitleSource.External(name, uri, language)
        if (availableSubs.none { it is SubtitleSource.External && it.uri == uri }) {
            availableSubs = availableSubs + source
        }
        assignSubtitle(slot, source)
    }

    /** A downloaded subtitle was saved. It goes into the slot the user chose. */
    private fun registerDownloaded(slot: SubtitleSlot, source: SubtitleSource.External) {
        availableSubs = availableSubs.filterNot { it is SubtitleSource.External && it.name == source.name } + source
        assignSubtitle(slot, source)
    }

    /** The user chose the folder that holds videos and subtitles. We remember it and search again. */
    fun onSubtitleFolderChosen(uri: Uri) {
        try {
            // Read and write: write access lets downloads be saved next to the video.
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        prefs.subtitleFolderUri = uri.toString()
        val video = currentUri
        val name = meta?.name
        if (video != null && name != null) discoverSubtitles(video, name)
    }

    fun clearNotice() {
        notice = null
    }

    // ---- Subtitle style ----

    fun styleOf(slot: SubtitleSlot): SubtitleStyle = if (slot == SubtitleSlot.S1) s1Style else s2Style

    fun updateStyle(slot: SubtitleSlot, style: SubtitleStyle) {
        val fixed = style.coerced()
        if (slot == SubtitleSlot.S1) s1Style = fixed else s2Style = fixed
        prefs.setSubtitleStyle(slot, fixed)
    }

    /** Back to the default look. Position goes back too. */
    fun resetStyle(slot: SubtitleSlot) {
        styleOf(slot).customFontPath?.let { File(it).delete() }
        updateStyle(slot, SubtitleStyle.defaultFor(slot))
    }

    /** Copies a font file (.ttf or .otf) into the app and uses it for [slot]. */
    fun importFont(slot: SubtitleSlot, uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(app.filesDir, "fonts").apply { mkdirs() }
                    // A new name every time, so the screen never shows an old cached font.
                    val target = File(dir, "font_${slot.name.lowercase()}_${System.currentTimeMillis()}.ttf")
                    val input = app.contentResolver.openInputStream(uri) ?: error("Cannot open the file")
                    input.use { source -> target.outputStream().use { source.copyTo(it) } }
                    if (Typeface.Builder(target).build() == null) {
                        target.delete()
                        error("Not a font")
                    }
                    target.path
                }
            }
            result
                .onSuccess { path ->
                    val old = styleOf(slot).customFontPath
                    updateStyle(slot, styleOf(slot).copy(font = SubtitleStyle.FONT_CUSTOM, customFontPath = path))
                    old?.let { File(it).delete() }
                }
                .onFailure { notice = "That file is not a usable font." }
        }
    }

    // ---- Resume ----

    private fun startResume() {
        val info = meta ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET) {
            // Live streams and some .ts/.avi files have no known duration. We cannot match a
            // resume entry, but the video must still start (open() leaves it paused).
            if (!resumeStarted) {
                resumeStarted = true
                player.play()
            }
            return
        }
        val k = ResumeKey(info.name, info.size, duration)
        key = k
        resumeStarted = true

        viewModelScope.launch {
            val saved = dao.find(k.name, k.size, k.duration)
            if (key !== k) return@launch // another video was opened while we were reading
            if (saved != null && saved.positionMs > 0 && duration - saved.positionMs > NEAR_END_MS) {
                player.seekTo(saved.positionMs)
            }
            resumeReady = true
            player.play()
            startSaveLoop()
        }
    }

    private fun startSaveLoop() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (player.isPlaying) saveNow()
            }
        }
    }

    /** Saves the position, or deletes the entry when the video is almost finished. */
    fun saveNow() {
        val k = key ?: return
        if (!resumeReady) return // never overwrite a saved position with 0
        val position = player.currentPosition
        val now = System.currentTimeMillis()
        ioScope.launch {
            if (k.duration - position <= NEAR_END_MS) {
                dao.delete(k.name, k.size, k.duration)
            } else {
                dao.upsert(ResumeEntry(k.name, k.size, k.duration, position, now))
            }
        }
    }

    /** Pause without seeking before a subtitle word is handed to another app. */
    fun pauseForTranslation() {
        cancelPauseAfterReplay()
        resetRewindGestureSequence()
        player.pause()
        zoneOverlay = false
        saveNow()
    }

    /** Called immediately before launching the translator/Android text chooser. */
    fun expectTranslationReturn() {
        translationReturnPending = prefs.resumeAfterTranslation
    }

    /** Cancels the pending return behavior if Android could not launch any handler. */
    fun cancelExpectedTranslationReturn() {
        translationReturnPending = false
    }

    /**
     * A translation app has returned control to this Activity. Resume by default, without seeking,
     * so playback continues from the exact frame at which the user tapped the word.
     */
    fun onActivityResume() {
        if (!translationReturnPending) return
        translationReturnPending = false
        if (currentUri != null && player.mediaItemCount > 0) {
            cancelPauseAfterReplay()
            player.play()
            zoneOverlay = false
            refreshSubtitles()
        }
    }

    /** Called from the Activity when it leaves the screen. */
    fun onActivityStop() {
        cancelPauseAfterReplay()
        player.pause()
        saveNow()
        persistSubtitleSelection()
    }

    override fun onCleared() {
        persistSubtitleSelection()
        player.removeListener(listener)
        player.release()
    }

    private companion object {
        /** Fixed internal interval for the existing double-tap-to-previous gesture. */
        const val DOUBLE_TAP_WINDOW_MS = 700L
        const val PAUSE_REPLAY_LOOKBACK_MS = 5_000L
        // Keep the final subtitle visible after the automatic pause (200 ms before cue end).
        const val PAUSE_REPLAY_END_MARGIN_MS = 200L
        const val SAVE_INTERVAL_MS = 5_000L
        const val NEAR_END_MS = 20_000L
        const val SCRUB_INTERVAL_MS = 70L
        const val FORWARD_MODE_FALLBACK_MS = 3_000L
        const val FREEZE_CHECK_DELAY_MS = 1_200L
        const val FREEZE_CHECK_WINDOW_MS = 700L
        const val FREEZE_MIN_PROGRESS_MS = 200L
        const val DAY_MS = 86_400_000L
        const val SUBTITLE_TICK_MS = 100L
        const val MAX_SUBTITLE_BYTES = 10 * 1024 * 1024

        /** Picture formats that ExoPlayer can draw. VobSub is not one of them. */
        val EXO_IMAGE_CODECS = setOf("pgs", "dvbsub")

        /** The MIME type ExoPlayer gives to each picture format. */
        val EXO_IMAGE_MIME = mapOf("pgs" to "application/pgs", "dvbsub" to "application/dvbsubs")
    }
}
