package com.dualsub.player.ui

import android.view.View
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dualsub.player.player.DeviceControls
import com.dualsub.player.player.PlayerViewModel
import com.dualsub.player.player.TapZone
import com.dualsub.player.subtitle.SubtitleSlot
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Small indicator shown while swiping. */
private enum class HudSide { Start, Center, End }

private data class Hud(val label: String, val progress: Float?, val side: HudSide)

private const val CONTROLS_HIDE_MS = 4_000L
private const val HUD_LINGER_MS = 600L
private const val NOTICE_MS = 3_500L

/** A full-height vertical swipe is not needed: 80% of the height covers 0 to 100. */
private const val VERTICAL_RANGE_FRACTION = 0.8f

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel, device: DeviceControls) {
    val exo = vm.player // do not name this "player": inside PlayerView.apply it would clash
    val context = LocalContext.current

    var controlsVisible by remember { mutableStateOf(false) }
    var controlsTick by remember { mutableIntStateOf(0) }
    var controlsForced by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var showTotalDuration by remember { mutableStateOf(false) }
    var edgeTimeVisible by remember { mutableStateOf(false) }
    var edgeTimeTick by remember { mutableIntStateOf(0) }
    var subtitleDialogOpen by remember { mutableStateOf(false) }
    var downloadDialogOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var mainWordTranslationEnabled by remember {
        mutableStateOf(vm.prefs.mainSubtitleWordTranslationEnabled)
    }
    var helperWordTranslationEnabled by remember {
        mutableStateOf(vm.prefs.helperSubtitleWordTranslationEnabled)
    }

    var isPlaying by remember { mutableStateOf(exo.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudActive by remember { mutableStateOf(false) }

    // Which slot the "Add file..." button was pressed for.
    var pickSlot by remember { mutableStateOf<SubtitleSlot?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pickSlot
        pickSlot = null
        if (uri != null && slot != null) vm.addSubtitleFile(slot, uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onSubtitleFolderChosen(uri)
    }
    // Font file for a subtitle (the slot is kept until the file is chosen)
    var fontSlot by remember { mutableStateOf<SubtitleSlot?>(null) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = fontSlot
        fontSlot = null
        if (uri != null && slot != null) vm.importFont(slot, uri)
    }
    // Folder for downloaded subtitles
    val downloadFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.download.onFolderChosen(uri) else vm.download.onFolderCancelled()
    }

    DisposableEffect(exo) {
        // "Playing" for the screen also covers the short wait for a frame after a seek, so the
        // pause icon does not flicker and the controls timer is not restarted by every rewind.
        fun sync() {
            isPlaying = exo.isPlaying ||
                (exo.playWhenReady && exo.playbackState == Player.STATE_BUFFERING)
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) = sync()
            override fun onPlaybackStateChanged(playbackState: Int) = sync()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sync()
        }
        sync()
        exo.addListener(listener)
        onDispose { exo.removeListener(listener) }
    }

    // Poll the position 4 times per second for the seek bar.
    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    // While playback is running, controls auto-hide after 4 seconds.
    // While paused, keep them on screen so the user can use the visible controls
    // without having to tap again. Individual action/timeline visibility remains
    // governed by the paused-controls settings in ControlsOverlay.
    LaunchedEffect(isPlaying, controlsVisible, controlsTick, scrubbing, subtitleDialogOpen, downloadDialogOpen, settingsOpen) {
        if (isPlaying && controlsVisible && !scrubbing && !subtitleDialogOpen && !downloadDialogOpen && !settingsOpen) {
            delay(CONTROLS_HIDE_MS)
            if (isPlaying && !scrubbing && !subtitleDialogOpen && !downloadDialogOpen && !settingsOpen) {
                controlsVisible = false
            }
        }
    }

    // An edge tap shows only the right-side time during playback or while paused.
    // A new edge tap restarts the 5-second visibility window.
    LaunchedEffect(edgeTimeVisible, edgeTimeTick) {
        if (edgeTimeVisible) {
            delay(5_000L)
            edgeTimeVisible = false
        }
    }

    // After the finger lifts, the volume/brightness indicator stays a moment, then disappears.
    LaunchedEffect(hudActive, hud) {
        if (!hudActive && hud != null) {
            delay(HUD_LINGER_MS)
            hud = null
        }
    }

    // A download needs a folder: open the folder picker.
    LaunchedEffect(vm.download.needFolder) {
        if (vm.download.needFolder) downloadFolderPicker.launch(null)
    }

    // Short messages disappear by themselves.
    LaunchedEffect(vm.notice) {
        if (vm.notice != null) {
            delay(NOTICE_MS)
            vm.clearNotice()
        }
    }

    val gestures = remember {
        object : PlayerGestureListener {
            private var startValue = 0f
            private var seekStart = 0L
            private var seekTarget = 0L

            override fun onTap(x: Float, width: Float, y: Float, height: Float) {
                // When enabled, the outer quarter at either vertical edge is reserved for a
                // temporary time-only readout during playback and while paused. The middle
                // play/pause zone is unaffected, and vertical swipes remain handled separately.
                if (vm.prefs.pausedEdgeTimeTapEnabled &&
                    (y <= height * 0.25f || y >= height * 0.75f)
                ) {
                    vm.cancelPendingRewindDoubleTap()
                    edgeTimeVisible = true
                    edgeTimeTick++
                    return
                }

                // Normal screen taps continue to use the configured left/middle/right zones.
                when (vm.prefs.zoneLayout.resolve(x, width)) {
                    TapZone.REWIND -> vm.tapRewind()
                    TapZone.PLAY_PAUSE -> {
                        val wasPlayRequested = exo.playWhenReady
                        vm.tapPlayPause()
                        if (wasPlayRequested) {
                            // Pausing opens the full controls: Settings, subtitle actions and timeline.
                            controlsVisible = true
                            controlsForced = false
                            controlsTick++
                        } else {
                            controlsVisible = false
                            controlsForced = false
                            edgeTimeVisible = false
                        }
                    }
                    TapZone.FORWARD -> vm.tapForward()
                }
            }

            override fun onLongPress() {
                vm.togglePlayPause()
                controlsVisible = true
                controlsForced = true
                controlsTick++
            }

            override fun onVerticalStart(leftHalf: Boolean) {
                vm.cancelPendingRewindDoubleTap()
                // Left half: brightness. Right half: volume.
                startValue = if (leftHalf) device.brightnessPercent() else device.volumePercent()
                hudActive = true
            }

            override fun onVerticalDrag(leftHalf: Boolean, totalDy: Float, height: Float) {
                // Finger up (negative dy) raises the value.
                val change = -totalDy / (height * VERTICAL_RANGE_FRACTION) * 100f
                val value = (startValue + change).coerceIn(0f, 100f)
                if (leftHalf) {
                    device.setBrightnessPercent(value)
                    hud = Hud("Brightness ${value.roundToInt()}%", value / 100f, HudSide.Start)
                } else {
                    device.setVolumePercent(value)
                    hud = Hud("Volume ${value.roundToInt()}%", value / 100f, HudSide.End)
                }
            }

            override fun onVerticalEnd() {
                hudActive = false
            }

            override fun onHorizontalStart() {
                seekStart = exo.currentPosition
                seekTarget = seekStart
                hudActive = true
                vm.scrubStart() // the video follows the finger from now on
            }

            override fun onHorizontalDrag(totalDx: Float, width: Float) {
                // Distance dragged decides the seek amount. It is not subtitle based.
                val delta = (totalDx / width * vm.prefs.swipeSeekMsPerScreen).toLong()
                val total = exo.duration
                val max = if (total == C.TIME_UNSET) Long.MAX_VALUE else total
                seekTarget = (seekStart + delta).coerceIn(0L, max)
                vm.scrubTo(seekTarget)
                val shownDelta = seekTarget - seekStart
                val sign = if (shownDelta >= 0) "+" else "-"
                hud = Hud(
                    label = "${formatTime(seekTarget)} / ${formatTime(duration)}  " +
                        "($sign${formatTime(abs(shownDelta))})",
                    progress = null,
                    side = HudSide.Center
                )
            }

            override fun onHorizontalEnd(cancelled: Boolean) {
                vm.scrubEnd(seekTarget, commit = !cancelled) // exact seek on release, leaves rewind mode
                hudActive = false
                hud = null
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Layer 1: the video. Subtitles are not drawn by this view.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.visibility = View.GONE
                    this.player = exo
                }
            },
            // Picture subtitles (PGS) in S1 are drawn by ExoPlayer's own subtitle view.
            update = { view ->
                view.subtitleView?.visibility = if (vm.imageSubtitleActive) View.VISIBLE else View.GONE
            }
        )

        // Layer 2: the gesture surface sits behind subtitles. This lets a tapped word consume
        // the touch instead of accidentally triggering the player's rewind/play/forward zones.
        Box(Modifier.fillMaxSize().playerGestures(gestures))

        // Layer 3: our own subtitles. S1 always, S2 only in rewind mode.
        val translateWord: (String) -> Unit = { word ->
            // Pause synchronously before launching another activity. ExoPlayer keeps its
            // current position, so returning from the translator never loses the word.
            vm.pauseForTranslation()
            vm.expectTranslationReturn()
            val launched = openSubtitleWordTranslator(
                context,
                word,
                vm.prefs.subtitleTranslatorPackage
            )
            if (!launched) vm.cancelExpectedTranslationReturn()
        }
        SubtitleLayer(
            s1 = vm.s1Text,
            s2 = vm.s2Text,
            s1Style = vm.s1Style,
            s2Style = vm.s2Style,
            onS1WordClick = if (mainWordTranslationEnabled) translateWord else null,
            onS2WordClick = if (helperWordTranslationEnabled) translateWord else null
        )

        // Layer 3b: after a pause with the middle tap, show the tap zones (set in Settings).
        if (vm.zoneOverlay && (vm.prefs.zoneAxes || vm.prefs.zoneIcons)) {
            ZoneVisual(
                layout = vm.prefs.zoneLayout,
                showAxes = vm.prefs.zoneAxes,
                showIcons = vm.prefs.zoneIcons,
                showLabels = false,
                modifier = Modifier.fillMaxSize(),
                tint = 0.12f
            )
        }

        // Layer 4: controls overlay (long press or middle-zone pause).
        if (controlsVisible) {
            ControlsOverlay(
                title = vm.title,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onPlayPause = { vm.togglePlayPause() },
                onSeek = { vm.seekManual(it) },
                onScrubbingChange = { scrubbing = it },
                onInteraction = { controlsTick++ },
                onSubtitlesClick = { subtitleDialogOpen = true },
                onSwapClick = { vm.swapSubtitles() },
                onSettingsClick = { settingsOpen = true },
                showTotalDuration = showTotalDuration,
                onToggleTimeDisplay = {
                    showTotalDuration = !showTotalDuration
                    controlsTick++
                },
                showTimeline = vm.prefs.pausedTimelineVisible,
                showActions = vm.prefs.pausedActionsVisible,
                allowEdgeTimeTaps = vm.prefs.pausedEdgeTimeTapEnabled,
                forceAllControls = controlsForced
            )
        }

        AnimatedVisibility(
            visible = edgeTimeVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(end = 20.dp, bottom = 18.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val edgeTime = if (showTotalDuration) {
                formatTime(duration)
            } else {
                "-${formatTime((duration - position).coerceAtLeast(0L))}"
            }
            Text(
                text = edgeTime,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xB814161B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            )
        }

        // Layer 5: swipe indicator and short messages.
        hud?.let { HudBox(it) }

        vm.notice?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 56.dp)
                    .background(Color(0xCC14161B), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        vm.error?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }

    if (subtitleDialogOpen) {
        SubtitleDialog(
            video = vm.videoRelease,
            available = vm.availableSubs,
            s1 = vm.s1Source,
            s2 = vm.s2Source,
            onSelect = { slot, source -> vm.assignSubtitle(slot, source) },
            onAddFile = { slot ->
                pickSlot = slot
                filePicker.launch(arrayOf("*/*")) // .srt has no reliable MIME type
            },
            onChooseFolder = { folderPicker.launch(null) },
            onDownload = {
                subtitleDialogOpen = false
                downloadDialogOpen = true
            },
            onDismiss = {
                subtitleDialogOpen = false
                controlsTick++
            }
        )
    }

    if (settingsOpen) {
        SettingsDialog(
            vm = vm,
            onPickFont = { slot ->
                fontSlot = slot
                fontPicker.launch(arrayOf("*/*")) // font files have no reliable MIME type
            },
            onChooseDownloadFolder = { downloadFolderPicker.launch(null) },
            mainWordTranslationEnabled = mainWordTranslationEnabled,
            helperWordTranslationEnabled = helperWordTranslationEnabled,
            onMainWordTranslationChanged = {
                mainWordTranslationEnabled = it
                vm.prefs.mainSubtitleWordTranslationEnabled = it
            },
            onHelperWordTranslationChanged = {
                helperWordTranslationEnabled = it
                vm.prefs.helperSubtitleWordTranslationEnabled = it
            },
            onDismiss = {
                settingsOpen = false
                controlsTick++
            }
        )
    }

    if (downloadDialogOpen) {
        SubtitleDownloadDialog(
            controller = vm.download,
            prefs = vm.prefs,
            onChooseFolder = { downloadFolderPicker.launch(null) },
            onDismiss = {
                downloadDialogOpen = false
                controlsTick++
            }
        )
    }
}

@Composable
private fun BoxScope.HudBox(hud: Hud) {
    val align = when (hud.side) {
        HudSide.Start -> Alignment.CenterStart
        HudSide.Center -> Alignment.Center
        HudSide.End -> Alignment.CenterEnd
    }
    Column(
        modifier = Modifier
            .align(align)
            .padding(24.dp)
            .background(Color(0xCC14161B), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(hud.label, color = Color.White)
        hud.progress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier.padding(top = 8.dp).width(140.dp)
            )
        }
    }
}

/**
 * Let Android resolve the user's preferred text-processing/translation app. The first tap
 * displays the system chooser unless the user selected a preferred app in Settings.
 */
private fun openSubtitleWordTranslator(
    context: android.content.Context,
    word: String,
    preferredPackage: String?
): Boolean {
    val processText = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, word)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        if (!preferredPackage.isNullOrBlank()) setPackage(preferredPackage)
    }

    // Use the preferred app directly. If it was removed or no longer handles PROCESS_TEXT,
    // continue with the available apps instead of leaving the user with a dead selection.
    if (!preferredPackage.isNullOrBlank()) {
        try {
            context.startActivity(processText)
            return true
        } catch (_: android.content.ActivityNotFoundException) {
            // Fall through to the full app chooser.
        }
    }

    val chooserIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, word)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    @Suppress("DEPRECATION")
    val processHandlers = context.packageManager.queryIntentActivities(chooserIntent, 0)
    if (processHandlers.isNotEmpty()) {
        try {
            context.startActivity(Intent.createChooser(chooserIntent, "Translate word with"))
            return true
        } catch (_: android.content.ActivityNotFoundException) {
            // Try the generic share route below.
        }
    }

    val shareText = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, word)
    }
    return try {
        context.startActivity(Intent.createChooser(shareText, "Translate or share word"))
        true
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app is available to translate this word.", Toast.LENGTH_SHORT).show()
        false
    }
}
