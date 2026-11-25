package com.dualsub.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dualsub.player.data.Prefs
import com.dualsub.player.download.OpenSubtitlesProvider
import com.dualsub.player.download.RankedCandidate
import com.dualsub.player.download.ReleaseInfo
import com.dualsub.player.download.ReleaseNameParser
import com.dualsub.player.download.SubDlProvider
import com.dualsub.player.download.SubtitleDownloadController
import com.dualsub.player.subtitle.SubtitleSlot

private val PROVIDERS = listOf(SubDlProvider.ID to "SubDL", OpenSubtitlesProvider.ID to "OpenSubtitles")

/** Search, manually choose, or automatically download matching subtitles. */
@Composable
fun SubtitleDownloadDialog(
    controller: SubtitleDownloadController,
    prefs: Prefs,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    val configVersion = controller.configVersion
    val selectedProvider = controller.providerId
    val configured = remember(configVersion, selectedProvider) { controller.isConfigured() }
    var editingKeys by remember { mutableStateOf(false) }
    var manualSelection by remember(controller) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val grouped = remember(controller.results) { controller.results.groupBy { it.candidate.language } }
    val languageOrder = remember(grouped, prefs.s1Language, prefs.s2Language) {
        (listOf(prefs.s1Language, prefs.s2Language) + grouped.keys)
            .distinct()
            .filter { it in grouped }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .widthIn(max = 640.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Download subtitles",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                // One scrollable content area keeps the result list and the setup fields usable
                // on short screens. The two action buttons remain fixed and visibly labelled.
                LaunchedEffect(manualSelection, controller.status) {
                    if (manualSelection) {
                        val indexBeforeResults =
                            (if (controller.release != null) 1 else 0) + 3 +
                                (if (controller.status != null) 1 else 0)
                        listState.animateScrollToItem(indexBeforeResults)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    controller.release?.let { release ->
                        item(key = "detected_filename") {
                            DetectedFilenameSection(release)
                        }
                    }

                    item(key = "provider_picker") {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Subtitle provider", style = MaterialTheme.typography.labelMedium)
                            ProviderPicker(controller)
                        }
                    }

                    item(key = "provider_credentials") {
                        if (!configured || editingKeys) {
                            KeyFields(controller, prefs) { editingKeys = false }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                SavedConfiguration(
                                    providerId = controller.providerId,
                                    onChange = { editingKeys = true },
                                    onTest = { controller.testSubDlConnection() },
                                    busy = controller.busy
                                )
                                controller.connectionStatus?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item(key = "save_location") {
                        SaveLocation(controller, prefs, onChooseFolder)
                    }

                    controller.status?.let { status ->
                        item(key = "download_status") {
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (manualSelection) {
                        item(key = "manual_results_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Choose a subtitle",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${controller.results.size} option(s)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(
                                    "All matching results are shown below. Choose S1 or S2 for the subtitle you want.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (controller.results.isEmpty()) {
                            item(key = "manual_results_empty") {
                                val message = when {
                                    controller.busy -> "Searching ${controller.providerId}... available subtitles will appear here."
                                    controller.status?.contains("No matching", ignoreCase = true) == true ->
                                        "No matching subtitles were returned. Check the video title, selected languages and provider credentials."
                                    controller.release == null -> "Open a video first so its filename can be used for searching."
                                    else -> "Press Choose manually to search and display every matching subtitle option."
                                }
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                )
                            }
                        }

                        languageOrder.forEach { language ->
                            item(key = "language_header_$language") {
                                Text(languageName(language), style = MaterialTheme.typography.titleSmall)
                            }
                            items(
                                items = grouped.getValue(language),
                                key = { "${it.candidate.providerId}_${it.candidate.ref}" }
                            ) { ranked ->
                                ResultRow(ranked, enabled = !controller.busy) { slot ->
                                    controller.download(ranked, slot)
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            manualSelection = true
                            controller.search()
                        },
                        enabled = configured && !controller.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose subtitle manually")
                    }
                    Button(
                        onClick = {
                            manualSelection = false
                            controller.autoPick()
                        },
                        enabled = configured && !controller.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Find best subtitle automatically")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedFilenameSection(release: ReleaseInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Detected from video filename", style = MaterialTheme.typography.labelMedium)
            val title = buildList {
                add(release.title.ifBlank { "(unknown title)" })
                release.year?.let { add(it.toString()) }
                if (release.season != null && release.episode != null) {
                    add("S%02dE%02d".format(release.season, release.episode))
                }
            }.joinToString("  •  ")
            Text(title, style = MaterialTheme.typography.bodyMedium)

            val tags = buildList {
                release.resolution?.let { add("${it}p") }
                release.bitDepth?.let { add("${it}bit") }
                release.codec?.let { add(codecLabel(it)) }
                release.source?.let { add(it.uppercase()) }
                release.group?.let { add("Group: $it") }
            }
            if (tags.isNotEmpty()) {
                // No maxLines/ellipsis here: technical tags such as 10-bit and x265 must remain visible.
                Text(
                    tags.joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SavedConfiguration(
    providerId: String,
    onChange: () -> Unit,
    onTest: () -> Unit,
    busy: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    if (providerId == SubDlProvider.ID) "SubDL API key saved" else "OpenSubtitles credentials saved",
                    style = MaterialTheme.typography.labelLarge
                )
                Text("Stored on this device", style = MaterialTheme.typography.bodySmall)
            }
            if (providerId == SubDlProvider.ID) {
                TextButton(onClick = onTest, enabled = !busy) { Text("Test API") }
            }
            TextButton(onClick = onChange, enabled = !busy) { Text("Change") }
        }
    }
}

@Composable
internal fun ProviderPicker(controller: SubtitleDownloadController) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PROVIDERS.forEach { (id, label) ->
            if (controller.providerId == id) {
                Button(onClick = {}, enabled = false) { Text(label) }
            } else {
                OutlinedButton(onClick = { controller.selectProvider(id) }) { Text(label) }
            }
        }
    }
}

@Composable
internal fun KeyFields(controller: SubtitleDownloadController, prefs: Prefs, onDone: () -> Unit) {
    if (controller.providerId == SubDlProvider.ID) {
        var key by remember { mutableStateOf(prefs.subdlApiKey) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("SubDL API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { controller.saveSubDlKey(key); onDone() }, enabled = key.isNotBlank()) {
                Text("Save")
            }
        }
    } else {
        var key by remember { mutableStateOf(prefs.openSubtitlesApiKey) }
        var user by remember { mutableStateOf(prefs.openSubtitlesUser) }
        var password by remember { mutableStateOf(prefs.openSubtitlesPassword) }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Username") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { controller.saveOpenSubtitles(key, user, password); onDone() },
                    enabled = key.isNotBlank() && user.isNotBlank() && password.isNotBlank()
                ) { Text("Save") }
            }
        }
    }
}

@Composable
internal fun SaveLocation(controller: SubtitleDownloadController, prefs: Prefs, onChooseFolder: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("Save:", style = MaterialTheme.typography.bodySmall)
        RadioButton(
            selected = controller.saveMode == Prefs.MODE_NEXT,
            onClick = { controller.chooseSaveMode(Prefs.MODE_NEXT) },
            modifier = Modifier.size(34.dp)
        )
        Text("Video", style = MaterialTheme.typography.bodySmall)
        RadioButton(
            selected = controller.saveMode == Prefs.MODE_FOLDER,
            onClick = { controller.chooseSaveMode(Prefs.MODE_FOLDER) },
            modifier = Modifier.size(34.dp)
        )
        Text("Folder", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onChooseFolder, modifier = Modifier.weight(1f)) {
            Text(if (prefs.downloadFolderUri != null) "Choose folder" else "Set folder")
        }
    }
}

@Composable
private fun ResultRow(ranked: RankedCandidate, enabled: Boolean, onUse: (SubtitleSlot) -> Unit) {
    val candidate = ranked.candidate
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                candidate.releaseName.ifBlank { "(no name)" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val releaseInfo = ReleaseNameParser.parse(candidate.releaseName)
            val fileInfo = candidate.downloadFileName?.let(ReleaseNameParser::parse)
            val tags = buildList {
                add("score ${ranked.score}")
                (releaseInfo.resolution ?: fileInfo?.resolution)?.let { add("${it}p") }
                (releaseInfo.bitDepth ?: fileInfo?.bitDepth)?.let { add("${it}bit") }
                (releaseInfo.codec ?: fileInfo?.codec)?.let { add(codecLabel(it)) }
                (releaseInfo.source ?: fileInfo?.source)?.let { add(it.uppercase()) }
                (releaseInfo.group ?: fileInfo?.group)?.let { add(it) }
                if (ranked.seasonPack) add("season pack")
                if (candidate.hearingImpaired) add("hearing impaired")
            }
            Text(tags.joinToString("  •  "), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { onUse(SubtitleSlot.S1) }, enabled = enabled) { Text("S1") }
        OutlinedButton(
            onClick = { onUse(SubtitleSlot.S2) }, enabled = enabled,
            modifier = Modifier.padding(start = 6.dp)
        ) { Text("S2") }
    }
}

private fun languageName(code: String) = com.dualsub.player.subtitle.LanguageCodes.displayName(code)

private fun codecLabel(codec: String): String = when (codec.lowercase()) {
    "hevc" -> "x265 (HEVC)"
    "h264" -> "x264 (H.264)"
    "xvid" -> "XviD"
    "av1" -> "AV1"
    else -> codec.uppercase()
}
