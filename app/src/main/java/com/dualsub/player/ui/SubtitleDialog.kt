package com.dualsub.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dualsub.player.download.ReleaseInfo
import com.dualsub.player.subtitle.LanguageCodes
import com.dualsub.player.subtitle.SubtitleSlot
import com.dualsub.player.subtitle.SubtitleSource

/**
 * Subtitle picker, separately for S1 (main) and S2 (helper).
 *
 * The top shows how the video file name was read (name and season/episode, or name and year).
 * Below, subtitle files from the video's folder that match that name come first, then the
 * tracks inside the video.
 */
@Composable
fun SubtitleDialog(
    video: ReleaseInfo?,
    available: List<SubtitleSource>,
    s1: SubtitleSource?,
    s2: SubtitleSource?,
    onSelect: (SubtitleSlot, SubtitleSource?) -> Unit,
    onAddFile: (SubtitleSlot) -> Unit,
    onChooseFolder: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val files = available.filterIsInstance<SubtitleSource.External>()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Text("Subtitles")
                if (video != null) {
                    Text(
                        text = video.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = matchSummary(video, files.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(Modifier.fillMaxWidth()) {
                    SlotColumn("Main (S1)", SubtitleSlot.S1, available, s1, onSelect, onAddFile, Modifier.weight(1f))
                    SlotColumn("Helper (S2)", SubtitleSlot.S2, available, s2, onSelect, onAddFile, Modifier.weight(1f))
                }
                TextButton(onClick = onDownload) { Text("Download subtitles...") }
                TextButton(onClick = onChooseFolder) { Text("Choose subtitle folder...") }
                Text(
                    "Choose the folder with your videos once. We remember it and find the subtitles that " +
                        "match each video's name and episode.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

/** One line that says what the folder search found for this video. */
private fun matchSummary(video: ReleaseInfo?, fileCount: Int): String {
    val what = when {
        video == null || video.title.isBlank() -> "this video"
        video.isSeries -> "this episode"
        else -> "this movie"
    }
    return when (fileCount) {
        0 -> "No subtitle file in the folder matches $what."
        1 -> "1 subtitle file in the folder matches $what."
        else -> "$fileCount subtitle files in the folder match $what."
    }
}

@Composable
private fun SlotColumn(
    title: String,
    slot: SubtitleSlot,
    available: List<SubtitleSource>,
    selected: SubtitleSource?,
    onSelect: (SubtitleSlot, SubtitleSource?) -> Unit,
    onAddFile: (SubtitleSlot) -> Unit,
    modifier: Modifier
) {
    val files = available.filterIsInstance<SubtitleSource.External>()
    val embedded = available.filterIsInstance<SubtitleSource.Embedded>()

    Column(modifier.padding(end = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        OptionRow("Off", selected == null) { onSelect(slot, null) }

        if (files.isNotEmpty()) {
            Caption("In the folder")
            files.forEach { source ->
                OptionRow(optionLabel(source), selected == source) { onSelect(slot, source) }
            }
        }
        if (embedded.isNotEmpty()) {
            Caption("Inside the video")
            embedded.forEach { source ->
                OptionRow(source.label, selected == source) { onSelect(slot, source) }
            }
        }
        TextButton(onClick = { onAddFile(slot) }) { Text("Add file...") }
    }
}

/** A file shows its language on the first line and its file name on the second. */
private fun optionLabel(source: SubtitleSource): String {
    if (source !is SubtitleSource.External) return source.label
    val language = source.language?.let { LanguageCodes.displayName(it) }
    return if (language != null) "$language\n${source.name}" else source.name
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
