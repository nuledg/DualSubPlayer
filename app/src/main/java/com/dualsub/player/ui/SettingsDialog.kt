package com.dualsub.player.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dualsub.player.R
import com.dualsub.player.player.PlayerViewModel
import com.dualsub.player.player.TapZone
import com.dualsub.player.player.ZoneLayout
import com.dualsub.player.subtitle.LanguageCodes
import com.dualsub.player.subtitle.SubtitleSlot
import com.dualsub.player.subtitle.SubtitleStyle
import com.dualsub.player.subtitle.SubtitleVisibility
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Full screen settings with tabs. The left side holds the options of the tab, the right side
 * shows a live preview that changes while you move a slider or tick a box.
 */
@Composable
fun SettingsDialog(
    vm: PlayerViewModel,
    onPickFont: (SubtitleSlot) -> Unit,
    onChooseDownloadFolder: () -> Unit,
    mainWordTranslationEnabled: Boolean,
    helperWordTranslationEnabled: Boolean,
    onMainWordTranslationChanged: (Boolean) -> Unit,
    onHelperWordTranslationChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val prefs = vm.prefs
    val tabs = listOf("Gestures", "Playback", "Subtitles", "Style", "Translation", "Download", "About")
    var tab by remember { mutableIntStateOf(0) }

    // Values that both the options and the preview need.
    var layout by remember { mutableStateOf(prefs.zoneLayout) }
    var overlayOn by remember { mutableStateOf(prefs.zonesOnPause) }
    var axes by remember { mutableStateOf(prefs.zoneAxes) }
    var icons by remember { mutableStateOf(prefs.zoneIcons) }
    var pausedTimeline by remember { mutableStateOf(prefs.pausedTimelineVisible) }
    var pausedActions by remember { mutableStateOf(prefs.pausedActionsVisible) }
    var pausedEdgeTimeTap by remember { mutableStateOf(prefs.pausedEdgeTimeTapEnabled) }
    var rewindBeforePause by remember { mutableStateOf(prefs.rewindSubtitleBeforePause) }
    var recentSubtitleWindow by remember {
        mutableIntStateOf(prefs.recentSubtitleRewindWindowMs.toInt())
    }
    var rewindOffsetMs by remember { mutableIntStateOf(prefs.rewindOffsetMs.toInt()) }
    var previewSituation by remember { mutableIntStateOf(SubtitleVisibility.WHILE_PLAYING) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 12.dp,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, name ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) })
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (tab) {
                            0 -> GesturesTab(
                                vm, layout, overlayOn, axes, icons,
                                onLayout = { layout = it; prefs.zoneLayout = it },
                                onOverlay = { overlayOn = it; prefs.zonesOnPause = it },
                                onAxes = { axes = it; prefs.zoneAxes = it },
                                onIcons = { icons = it; prefs.zoneIcons = it },
                                pausedTimeline = pausedTimeline,
                                pausedActions = pausedActions,
                                pausedEdgeTimeTap = pausedEdgeTimeTap,
                                rewindBeforePause = rewindBeforePause,
                                onPausedTimeline = { pausedTimeline = it; prefs.pausedTimelineVisible = it },
                                onPausedActions = { pausedActions = it; prefs.pausedActionsVisible = it },
                                onPausedEdgeTimeTap = { pausedEdgeTimeTap = it; prefs.pausedEdgeTimeTapEnabled = it },
                                onRewindBeforePause = { rewindBeforePause = it; prefs.rewindSubtitleBeforePause = it }
                            )
                            1 -> PlaybackTab(
                                vm,
                                recentSubtitleWindow,
                                rewindOffsetMs,
                                onRecentWindow = {
                                    recentSubtitleWindow = it
                                    prefs.recentSubtitleRewindWindowMs = it.toLong()
                                },
                                onRewindOffset = {
                                    rewindOffsetMs = it
                                    prefs.rewindOffsetMs = it.toLong()
                                }
                            )
                            2 -> SubtitlesTab(vm)
                            3 -> SettingsCard(null) { StyleSection(vm, onPickFont) }
                            4 -> SettingsCard(null) {
                                TranslationSection(
                                    vm = vm,
                                    mainWordTranslationEnabled = mainWordTranslationEnabled,
                                    helperWordTranslationEnabled = helperWordTranslationEnabled,
                                    onMainWordTranslationChanged = onMainWordTranslationChanged,
                                    onHelperWordTranslationChanged = onHelperWordTranslationChanged
                                )
                            }
                            5 -> {
                                SettingsCard(null) { AccessSection() }
                                SettingsCard(null) { DownloadSection(vm, onChooseDownloadFolder) }
                            }
                            6 -> SettingsCard("About") { GitHubLinkRow() }
                        }
                    }
                    if (tab in 0..3) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Preview", style = MaterialTheme.typography.titleMedium)
                            when (tab) {
                                0 -> ZonesPreview(layout, overlayOn, axes, icons)
                                1 -> PlaybackPreview(recentSubtitleWindow, rewindOffsetMs)
                                2 -> SubtitlesPreview(vm, previewSituation) { previewSituation = it }
                                else -> {
                                    Text(
                                        "Drag the main or helper subtitle to place it. " +
                                            "The position is saved as a percentage of the screen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SubtitlePreview(vm, showS1 = true, showS2 = true, draggable = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Cards ----

@Composable
private fun SettingsCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun GitHubLinkRow() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/nuledg/DualSubPlayer"))
                context.startActivity(intent)
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_github),
                contentDescription = "GitHub logo",
                modifier = Modifier.size(36.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text("GitHub", style = MaterialTheme.typography.titleSmall)
                Text(
                    "github.com/nuledg/DualSubPlayer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "↗",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- Tab: Gestures ----

@Composable
private fun GesturesTab(
    vm: PlayerViewModel,
    layout: ZoneLayout,
    overlayOn: Boolean,
    axes: Boolean,
    icons: Boolean,
    onLayout: (ZoneLayout) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onAxes: (Boolean) -> Unit,
    onIcons: (Boolean) -> Unit,
    pausedTimeline: Boolean,
    pausedActions: Boolean,
    pausedEdgeTimeTap: Boolean,
    rewindBeforePause: Boolean,
    onPausedTimeline: (Boolean) -> Unit,
    onPausedActions: (Boolean) -> Unit,
    onPausedEdgeTimeTap: (Boolean) -> Unit,
    onRewindBeforePause: (Boolean) -> Unit
) {
    val prefs = vm.prefs
    SettingsCard("Tap zones") {
        Hint("Choose what each part of the screen does. Put rewind on the right and forward on the left if you like.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Left", "Middle", "Right").forEachIndexed { position, name ->
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    ZoneDropdown(layout.order[position]) { onLayout(layout.withZoneAt(position, it)) }
                }
            }
        }
        val w = layout.widths
        LabeledSlider("Left zone width", "${w[0]}%", w[0].toFloat(), 10f..80f) {
            val first = it.roundToInt()
            onLayout(layout.withEdges(first, first + w[1]))
        }
        LabeledSlider("Middle zone width", "${w[1]}%", w[1].toFloat(), 10f..80f) {
            onLayout(layout.withEdges(w[0], w[0] + it.roundToInt()))
        }
        Text("Right zone width: ${w[2]}%", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onLayout(ZoneLayout()) }) { Text("Reset zones") }
    }

    SettingsCard("Playback controls and tap zones") {
        Hint("Pausing with a middle-zone tap also opens the playback controls, including Settings, subtitle actions, and the elapsed/remaining timeline. These options control the optional tap-zone guide and playback display.")
        SwitchRow("Show the zones", overlayOn, onOverlay)
        SwitchRow("Dividing lines (axes)", axes, onAxes)
        SwitchRow("Tap-zone icons", icons, onIcons)
        HorizontalDivider()
        Text("Playback controls", style = MaterialTheme.typography.titleSmall)
        SwitchRow("Show time and seek slider", pausedTimeline, onPausedTimeline)
        SwitchRow("Show action icons (settings, subtitles, swap)", pausedActions, onPausedActions)
        SwitchRow("Show time on edge tap", pausedEdgeTimeTap, onPausedEdgeTimeTap)
        Hint("During playback or while paused, tap the top or bottom quarter of the screen (outside active controls) to show only the right-side time, without the seek slider. It fades away after 5 seconds. The middle play/pause area is unchanged, and vertical swipes still control brightness or volume.")
        HorizontalDivider()
        SwitchRow("Rewind current subtitle before pausing", rewindBeforePause, onRewindBeforePause)
        Hint("When enabled, pausing rewinds to the start of the current subtitle (or one that ended within the last 5 seconds), applies the rewind offset, replays it, then pauses 200 ms before the subtitle ends so it remains visible. Pressing play resumes from that paused position.")
    }

    SettingsCard("Swipes") {
        var swipeSeconds by remember { mutableIntStateOf((prefs.swipeSeekMsPerScreen / 1000).toInt()) }
        LabeledSlider(
            label = "Horizontal swipe range",
            valueText = "$swipeSeconds s per screen width",
            value = swipeSeconds.toFloat(),
            range = 30f..300f
        ) {
            swipeSeconds = it.roundToInt()
            prefs.swipeSeekMsPerScreen = swipeSeconds * 1000L
        }
        Hint("The video follows your finger while you slide. Up or down on the left half changes brightness, on the right half volume.")
    }
}

@Composable
private fun ZoneDropdown(current: TapZone, onPick: (TapZone) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(zoneIcon(current), contentDescription = null, tint = zoneColor(current), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(zoneLabel(current), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TapZone.entries.forEach { zone ->
                DropdownMenuItem(
                    text = { Text(zoneLabel(zone)) },
                    leadingIcon = { Icon(zoneIcon(zone), contentDescription = null, tint = zoneColor(zone)) },
                    onClick = {
                        open = false
                        onPick(zone)
                    }
                )
            }
        }
    }
}

@Composable
private fun ZonesPreview(layout: ZoneLayout, overlayOn: Boolean, axes: Boolean, icons: Boolean) {
    val config = LocalConfiguration.current
    val aspect = config.screenWidthDp.toFloat() / config.screenHeightDp.toFloat().coerceAtLeast(1f)

    Hint("Your tap zones, left to right:")
    ZoneVisual(
        layout = layout,
        showAxes = true,
        showIcons = true,
        showLabels = true,
        modifier = Modifier.fillMaxWidth().height(110.dp).clip(MaterialTheme.shapes.medium),
        tint = 0.35f
    )
    Hint("What you see on the video after pausing with the middle tap:")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF000000)))),
        contentAlignment = Alignment.Center
    ) {
        if (overlayOn && (axes || icons)) {
            ZoneVisual(layout, axes, icons, showLabels = false, modifier = Modifier.fillMaxSize(), tint = 0.12f)
        } else {
            Text("Nothing is drawn", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

// ---- Tab: Playback ----

@Composable
private fun PlaybackTab(
    vm: PlayerViewModel,
    recentSubtitleWindow: Int,
    rewindOffsetMs: Int,
    onRecentWindow: (Int) -> Unit,
    onRewindOffset: (Int) -> Unit
) {
    val prefs = vm.prefs
    SettingsCard("Subtitle rewind") {
        Hint(
            "Rewind uses the main subtitle timestamps first. When the main subtitle has no usable " +
                "cue at the current position, it uses the helper subtitle timestamps."
        )
        LabeledSlider(
            label = "New subtitle grace period",
            valueText = if (recentSubtitleWindow == 0) "Off" else "${"%.2f".format(recentSubtitleWindow / 1000f)} s",
            value = recentSubtitleWindow.toFloat(),
            range = 0f..1000f
        ) { onRecentWindow((it / 50f).roundToInt() * 50) }
        Hint(
            "When a new subtitle has appeared within this time, tapping rewind goes to the subtitle " +
                "before it instead of replaying the one that just appeared. Set to Off to disable."
        )
        LabeledSlider(
            label = "Rewind offset before subtitle start",
            valueText = "${rewindOffsetMs} ms (${"%.2f".format(rewindOffsetMs / 1000f)} s)",
            value = rewindOffsetMs.toFloat(),
            range = 0f..5_000f
        ) {
            onRewindOffset(((it / 100f).roundToInt() * 100).coerceIn(0, 5_000))
        }
        Hint(
            "After choosing the current or previous subtitle, rewind starts this many milliseconds " +
                "before its timestamp. For example, 1,500 ms starts 1.5 seconds before the sentence. " +
                "The position is clamped to the beginning of the video. Forward navigation is unchanged."
        )
        var jump by remember { mutableIntStateOf((prefs.fallbackJumpMs / 1000).toInt()) }
        LabeledSlider(
            label = "Fixed jump (when subtitle timing is unavailable)",
            valueText = "$jump s",
            value = jump.toFloat(),
            range = 1f..30f
        ) {
            jump = it.roundToInt()
            prefs.fallbackJumpMs = jump * 1000L
        }
    }
    SettingsCard(null) { ResumeSection(vm) }
}

@Composable
private fun PlaybackPreview(recentSubtitleWindow: Int, rewindOffsetMs: Int) {
    val window = "%.2f".format(recentSubtitleWindow / 1000f)
    Hint("Rewind and forward use subtitle timestamps, not subtitle line numbers.")
    Row(Modifier.fillMaxWidth().height(54.dp).clip(MaterialTheme.shapes.medium)) {
        Box(
            Modifier.weight(1f).fillMaxHeight().background(zoneColor(TapZone.REWIND).copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Text("Normal tap\nCurrent subtitle", fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
        }
        Box(
            Modifier.weight(1f).fillMaxHeight().background(zoneColor(TapZone.REWIND).copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Text("New cue\nPrevious subtitle", fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
        }
    }
    Text("Normal tap: replay from the start timestamp of the current subtitle.", style = MaterialTheme.typography.bodyMedium)
    Text(
        if (recentSubtitleWindow == 0) "The new-subtitle grace period is off."
        else "For $window s after a new subtitle appears, rewind goes to the previous subtitle.",
        style = MaterialTheme.typography.bodyMedium
    )
    Text("A second quick tap still steps back one more subtitle.", style = MaterialTheme.typography.bodyMedium)
    Text(
        if (rewindOffsetMs == 0) "Rewind offset is off: seeking starts exactly at the selected subtitle timestamp."
        else "Rewind offset: $rewindOffsetMs ms (${"%.2f".format(rewindOffsetMs / 1000f)} s) before each selected subtitle timestamp.",
        style = MaterialTheme.typography.bodyMedium
    )
    Text("Forward navigation is unchanged. When subtitle timing is unavailable, the fixed jump is used instead.", style = MaterialTheme.typography.bodyMedium)
}

// ---- Tab: Subtitles ----

@Composable
private fun SubtitlesTab(vm: PlayerViewModel) {
    val prefs = vm.prefs
    var s1 by remember { mutableStateOf(prefs.s1Language) }
    var s2 by remember { mutableStateOf(prefs.s2Language) }
    var picking by remember { mutableStateOf<SubtitleSlot?>(null) }

    SettingsCard("Languages") {
        Hint("Used when a video opens and when you search for subtitles. Any language works.")
        LanguageRow("Main subtitle", s1) { picking = SubtitleSlot.S1 }
        LanguageRow("Helper subtitle", s2) { picking = SubtitleSlot.S2 }
    }

    SettingsCard("When to show them") {
        Hint("Tick the situations in which each subtitle is shown. Any mix is fine.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("Main", Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            Text("Helper", Modifier.width(72.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        }
        val main = vm.visibilityOf(SubtitleSlot.S1)
        val helper = vm.visibilityOf(SubtitleSlot.S2)
        SITUATIONS.forEach { (bit, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = main and bit != 0, onCheckedChange = {
                        vm.setVisibility(SubtitleSlot.S1, if (it) main or bit else main and bit.inv())
                    })
                }
                Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = helper and bit != 0, onCheckedChange = {
                        vm.setVisibility(SubtitleSlot.S2, if (it) helper or bit else helper and bit.inv())
                    })
                }
            }
        }
        Hint("After a rewind tap: until playback reaches the place where you tapped. After a forward tap: until that sentence ends.")
    }

    picking?.let { slot ->
        LanguagePickerDialog(
            title = if (slot == SubtitleSlot.S1) "Main subtitle language" else "Helper subtitle language",
            current = if (slot == SubtitleSlot.S1) s1 else s2,
            onPick = { code ->
                if (slot == SubtitleSlot.S1) {
                    s1 = code
                    prefs.s1Language = code
                } else {
                    s2 = code
                    prefs.s2Language = code
                }
                picking = null
            },
            onDismiss = { picking = null }
        )
    }
}

private val SITUATIONS = listOf(
    SubtitleVisibility.WHILE_PLAYING to "While playing",
    SubtitleVisibility.WHILE_PAUSED to "While paused",
    SubtitleVisibility.AFTER_REWIND to "After a rewind tap",
    SubtitleVisibility.AFTER_FORWARD to "After a forward tap"
)

@Composable
private fun LanguageRow(label: String, code: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = onClick) {
            Text(LanguageCodes.displayName(code), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LanguagePickerDialog(title: String, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) {
        val q = query.trim().lowercase()
        LanguageCodes.all().filter {
            q.isEmpty() || it.english.lowercase().contains(q) || it.native.lowercase().contains(q) || it.code.contains(q)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.55f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp).heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(matches, key = { it.code }) { language ->
                        val selected = language.code == current
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(language.code) }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                language.display,
                                modifier = Modifier.weight(1f),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(language.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun SubtitlesPreview(vm: PlayerViewModel, situation: Int, onSituation: (Int) -> Unit) {
    Hint("Pick a situation to see which subtitles are shown:")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SITUATIONS.forEach { (bit, label) ->
            FilterChip(selected = situation == bit, onClick = { onSituation(bit) }, label = { Text(label, fontSize = 12.sp) })
        }
    }
    val paused = situation == SubtitleVisibility.WHILE_PAUSED
    val rewind = situation == SubtitleVisibility.AFTER_REWIND
    val forward = situation == SubtitleVisibility.AFTER_FORWARD
    val showMain = SubtitleVisibility.isVisible(vm.visibilityOf(SubtitleSlot.S1), paused, rewind, forward)
    val showHelper = SubtitleVisibility.isVisible(vm.visibilityOf(SubtitleSlot.S2), paused, rewind, forward)
    SubtitlePreview(vm, showS1 = showMain, showS2 = showHelper, draggable = false)
    Hint(
        when {
            showMain && showHelper -> "Both subtitles are shown."
            showMain -> "Only the main subtitle is shown."
            showHelper -> "Only the helper subtitle is shown."
            else -> "No subtitle is shown."
        }
    )
}

// ---- Preview of subtitles, optionally draggable ----

@Composable
private fun SubtitlePreview(vm: PlayerViewModel, showS1: Boolean, showS2: Boolean, draggable: Boolean) {
    val config = LocalConfiguration.current
    val aspect = config.screenWidthDp.toFloat() / config.screenHeightDp.toFloat().coerceAtLeast(1f)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    // How much smaller the preview is than the real screen. Text is scaled by the same amount.
    val scale = with(density) { size.width.toDp().value } / config.screenWidthDp.toFloat().coerceAtLeast(1f)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF000000))))
            .onSizeChanged { size = it }
    ) {
        if (size.width > 0) {
            if (showS2) PreviewSubtitle(vm, SubtitleSlot.S2, "Helper subtitle", size, scale, draggable)
            if (showS1) PreviewSubtitle(vm, SubtitleSlot.S1, "Main subtitle", size, scale, draggable)
        }
    }
}

@Composable
private fun PreviewSubtitle(
    vm: PlayerViewModel,
    slot: SubtitleSlot,
    text: String,
    boxSize: IntSize,
    scale: Float,
    draggable: Boolean
) {
    val style = vm.styleOf(slot)
    // The drag code starts once. These always hold the newest values.
    val latestStyle by rememberUpdatedState(style)
    val latestSize by rememberUpdatedState(boxSize)

    val dragModifier = if (draggable) {
        Modifier.pointerInput(slot) {
            detectDragGestures { change, drag ->
                change.consume()
                val s = latestStyle
                val box = latestSize
                vm.updateStyle(
                    slot,
                    s.copy(
                        xPercent = s.xPercent + drag.x / box.width * 100f,
                        yPercent = s.yPercent + drag.y / box.height * 100f
                    )
                )
            }
        }
    } else {
        Modifier
    }

    SubtitleText(
        text = text,
        style = style,
        scale = scale,
        modifier = Modifier.placeAtPercent(style.xPercent, style.yPercent).then(dragModifier)
    )
}

// ---- Options kept from the earlier version ----

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun StyleSection(vm: PlayerViewModel, onPickFont: (SubtitleSlot) -> Unit) {
    SectionTitle("Subtitle style")
    var slot by remember { mutableStateOf(SubtitleSlot.S1) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(SubtitleSlot.S1 to "Main (S1)", SubtitleSlot.S2 to "Helper (S2)").forEach { (s, label) ->
            if (slot == s) Button(onClick = {}) { Text(label) }
            else OutlinedButton(onClick = { slot = s }) { Text(label) }
        }
    }

    val style = vm.styleOf(slot)
    fun change(new: SubtitleStyle) = vm.updateStyle(slot, new)

    // Font
    Text("Font", style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SubtitleStyle.FONT_DEFAULT to "Default",
            SubtitleStyle.FONT_SERIF to "Serif",
            SubtitleStyle.FONT_MONO to "Mono",
            SubtitleStyle.FONT_CURSIVE to "Cursive"
        ).forEach { (id, label) ->
            ChoiceButton(label, style.font == id) { change(style.copy(font = id)) }
        }
        ChoiceButton(
            label = if (style.font == SubtitleStyle.FONT_CUSTOM) "My font (change...)" else "My font file...",
            selected = style.font == SubtitleStyle.FONT_CUSTOM
        ) { onPickFont(slot) }
    }

    LabeledSlider("Size", "${style.sizeSp.roundToInt()} sp", style.sizeSp,
        SubtitleStyle.MIN_SIZE..SubtitleStyle.MAX_SIZE) { change(style.copy(sizeSp = it)) }

    Text("Text color", style = MaterialTheme.typography.bodyMedium)
    ColorRow(SubtitleStyle.TEXT_COLORS, style.colorArgb) { change(style.copy(colorArgb = it)) }

    SwitchRow("Bold", style.bold) { change(style.copy(bold = it)) }

    LabeledSlider("Outline", if (style.outlineWidth == 0f) "off" else "%.1f".format(style.outlineWidth),
        style.outlineWidth, 0f..SubtitleStyle.MAX_OUTLINE) { change(style.copy(outlineWidth = it)) }

    SwitchRow("Shadow", style.shadow) { change(style.copy(shadow = it)) }

    Text("Background color", style = MaterialTheme.typography.bodyMedium)
    ColorRow(SubtitleStyle.BACKGROUND_COLORS.map { it or 0xFF000000.toInt() }, style.backgroundRgb or 0xFF000000.toInt()) {
        change(style.copy(backgroundRgb = it and 0x00FFFFFF))
    }
    LabeledSlider("Background opacity", "${(style.backgroundOpacity * 100).roundToInt()}%",
        style.backgroundOpacity, 0f..1f) { change(style.copy(backgroundOpacity = it)) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val d = SubtitleStyle.defaultFor(slot)
            change(style.copy(xPercent = d.xPercent, yPercent = d.yPercent))
        }) { Text("Reset position") }
        OutlinedButton(onClick = { vm.resetStyle(slot) }) { Text("Reset style") }
    }
}

@Composable
private fun ResumeSection(vm: PlayerViewModel) {
    val prefs = vm.prefs
    SectionTitle("Resume")
    var days by remember { mutableIntStateOf(prefs.retentionDays) }
    LabeledSlider(
        label = "Forget videos not opened for",
        valueText = "$days days",
        value = days.toFloat(),
        range = 1f..365f
    ) {
        days = it.roundToInt()
        prefs.retentionDays = days
    }
}

@Composable
private fun AccessSection() {
    // Only Android 11 and newer needs this switch. Older versions use the normal storage permission.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

    val context = LocalContext.current
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    // Look again every second, so the text changes when you come back from the system screen.
    LaunchedEffect(Unit) {
        while (true) {
            granted = Environment.isExternalStorageManager()
            delay(1_000)
        }
    }

    SectionTitle("Find subtitles next to the video")
    Text(
        if (granted) {
            "On. Subtitle files next to a video are found by themselves, and downloads can be saved there."
        } else {
            "Off. Android only shows a video's folder to apps that have \"All files access\". " +
                "Turn it on here, or choose your video folder once in the Subtitles menu."
        },
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = {
        val appIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        try {
            context.startActivity(appIntent)
        } catch (_: Exception) {
            // Some phones do not have the page for one app. The general page is the fallback.
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }) { Text(if (granted) "Open the system setting" else "Turn on All files access") }
}

@Composable
private fun WordTranslationSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun TranslationSection(
    vm: PlayerViewModel,
    mainWordTranslationEnabled: Boolean,
    helperWordTranslationEnabled: Boolean,
    onMainWordTranslationChanged: (Boolean) -> Unit,
    onHelperWordTranslationChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val processIntent = remember {
        Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, "example")
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
    }
    @Suppress("DEPRECATION")
    val apps = remember(context) {
        context.packageManager.queryIntentActivities(processIntent, 0)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
    }
    var expanded by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf(vm.prefs.subtitleTranslatorPackage) }
    var resumeAfterTranslation by remember { mutableStateOf(vm.prefs.resumeAfterTranslation) }
    val selectedLabel = apps.firstOrNull { it.activityInfo.packageName == selectedPackage }
        ?.loadLabel(context.packageManager)?.toString()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Word translation", style = MaterialTheme.typography.titleMedium)
        Text(
            "Choose independently which subtitle can open the translator when you tap a word. The player pauses at that exact position and resumes when you return, by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WordTranslationSwitch(
            title = "Main subtitle (S1)",
            description = "Allow tapping words in the main subtitle.",
            checked = mainWordTranslationEnabled,
            onCheckedChange = onMainWordTranslationChanged
        )
        WordTranslationSwitch(
            title = "Helper subtitle (S2)",
            description = "Allow tapping words in the helper subtitle.",
            checked = helperWordTranslationEnabled,
            onCheckedChange = onHelperWordTranslationChanged
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedLabel ?: if (selectedPackage == null) "Ask every time" else "Selected app unavailable")
                Icon(Icons.Filled.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Ask every time") },
                    onClick = {
                        selectedPackage = null
                        vm.prefs.subtitleTranslatorPackage = null
                        expanded = false
                    }
                )
                apps.forEach { app ->
                    val packageName = app.activityInfo.packageName
                    DropdownMenuItem(
                        text = { Text(app.loadLabel(context.packageManager).toString()) },
                        onClick = {
                            selectedPackage = packageName
                            vm.prefs.subtitleTranslatorPackage = packageName
                            expanded = false
                        }
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Resume when returning", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Continue the movie automatically from the tapped word's exact position.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = resumeAfterTranslation,
                onCheckedChange = {
                    resumeAfterTranslation = it
                    vm.prefs.resumeAfterTranslation = it
                }
            )
        }
        if (apps.isEmpty()) {
            Text(
                "No compatible text-processing apps were found. Android's share chooser is used as a fallback when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "You can change the preferred app or choose Ask every time whenever you like.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadSection(vm: PlayerViewModel, onChooseDownloadFolder: () -> Unit) {
    SectionTitle("Subtitle download")
    ProviderPicker(vm.download)
    KeyFields(vm.download, vm.prefs) {}
    SaveLocation(vm.download, vm.prefs, onChooseDownloadFolder)
}

// ---- Preview with dragging ----

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(34.dp)
                    .background(Color(argb), CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onSelect(argb) }
            )
        }
    }
}
