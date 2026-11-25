package com.dualsub.player.download

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dualsub.player.data.Prefs
import com.dualsub.player.subtitle.SrtWriter
import com.dualsub.player.subtitle.SubtitleParser
import com.dualsub.player.subtitle.SubtitleSlot
import com.dualsub.player.subtitle.SubtitleSource
import com.dualsub.player.subtitle.TextDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Search, rank, download and save subtitles. The download dialog reads its state from here.
 * [onSaved] is called for every saved file so the player can load it into S1 or S2.
 */
class SubtitleDownloadController(
    private val app: Application,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    private val onSaved: (SubtitleSlot, SubtitleSource.External) -> Unit
) {
    var providerId by mutableStateOf(prefs.lastProvider)
        private set
    var saveMode by mutableStateOf(prefs.downloadMode)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<RankedCandidate>>(emptyList())
        private set
    var release by mutableStateOf<ReleaseInfo?>(null)
        private set

    /** True when the user has to choose a folder. The screen opens the folder picker. */
    var needFolder by mutableStateOf(false)
        private set

    /** Changes when keys are saved, so the dialog updates. */
    var configVersion by mutableIntStateOf(0)
        private set

    /** Result of the optional SubDL account-status check shown beside the saved-key indicator. */
    var connectionStatus by mutableStateOf<String?>(null)
        private set

    private val saver = SubtitleSaver(app)
    private var videoUri: Uri? = null
    private var videoName: String? = null
    private var job: Job? = null
    private var pending: PendingSave? = null

    private class PendingSave(val bytes: ByteArray, val language: String, val slot: SubtitleSlot)

    /** Called when a video is opened. Everything about the previous video is forgotten. */
    fun bind(uri: Uri, name: String) {
        job?.cancel()
        videoUri = uri
        videoName = name
        release = ReleaseNameParser.parse(name)
        results = emptyList()
        status = null
        busy = false
        pending = null
        needFolder = false
    }

    // ---- Setup ----

    fun selectProvider(id: String) {
        providerId = id
        prefs.lastProvider = id
        results = emptyList()
        status = null
        connectionStatus = null
    }

    fun chooseSaveMode(mode: String) {
        saveMode = mode
        prefs.downloadMode = mode
    }

    fun isConfigured(id: String = providerId): Boolean = when (id) {
        SubDlProvider.ID -> prefs.subdlApiKey.isNotBlank()
        OpenSubtitlesProvider.ID ->
            prefs.openSubtitlesApiKey.isNotBlank() && prefs.openSubtitlesUser.isNotBlank() &&
                prefs.openSubtitlesPassword.isNotBlank()
        else -> false
    }

    fun saveSubDlKey(key: String) {
        prefs.subdlApiKey = key.trim()
        results = emptyList()
        connectionStatus = null
        status = "SubDL API key saved on this device."
        configVersion++
    }

    fun saveOpenSubtitles(key: String, user: String, password: String) {
        prefs.openSubtitlesApiKey = key.trim()
        prefs.openSubtitlesUser = user.trim()
        prefs.openSubtitlesPassword = password
        results = emptyList()
        connectionStatus = null
        status = "OpenSubtitles credentials saved on this device."
        configVersion++
    }

    /** Tests the saved key, account endpoint, and filename-aware subtitle search without downloading. */
    fun testSubDlConnection() {
        val key = prefs.subdlApiKey
        val currentRelease = release
        if (key.isBlank()) {
            connectionStatus = "Save your SubDL API key first."
            return
        }
        if (currentRelease == null) {
            connectionStatus = "Open a video before testing subtitle search."
            return
        }
        job?.cancel()
        job = scope.launch {
            busy = true
            results = emptyList()
            connectionStatus = "Checking the key and testing subtitle search..."
            try {
                val ranked = withContext(Dispatchers.IO) {
                    val provider = SubDlProvider(key)
                    provider.testConnection()
                    SubtitleRanker.rank(currentRelease, provider.search(SearchQuery(currentRelease, languages())))
                }
                results = ranked
                connectionStatus = if (ranked.isEmpty()) {
                    "API key accepted and search worked, but no matching subtitles were returned for this video."
                } else {
                    "API key accepted. Subtitle search worked and found ${ranked.size} matching result(s)."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                connectionStatus = describe(e)
            } finally {
                busy = false
            }
        }
    }

    /** To add a provider, add a branch here (and to the list in the dialog). */
    private fun providerFor(id: String): SubtitleProvider? {
        if (!isConfigured(id)) return null
        return when (id) {
            SubDlProvider.ID -> SubDlProvider(prefs.subdlApiKey)
            OpenSubtitlesProvider.ID -> OpenSubtitlesProvider(
                prefs.openSubtitlesApiKey, prefs.openSubtitlesUser, prefs.openSubtitlesPassword
            )
            else -> null
        }
    }

    private fun languages(): List<String> = listOf(prefs.s1Language, prefs.s2Language).distinct()

    // ---- Actions ----

    fun search() {
        val rel = release ?: return
        val provider = providerFor(providerId)
        if (provider == null) {
            status = "Enter the key for this provider first."
            return
        }
        job?.cancel()
        job = scope.launch {
            busy = true
            results = emptyList()
            status = "Searching ${provider.displayName}..."
            try {
                results = searchAndRank(provider, rel)
                status = if (results.isEmpty()) "No matching subtitles found." else "${results.size} subtitles found."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = describe(e)
            } finally {
                busy = false
            }
        }
    }

    /** Downloads one chosen result and puts it into [slot]. */
    fun download(candidate: RankedCandidate, slot: SubtitleSlot) {
        job?.cancel()
        job = scope.launch {
            busy = true
            try {
                status = "Downloading..."
                downloadAndSave(candidate.candidate, slot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = describe(e)
            } finally {
                busy = false
            }
        }
    }

    /** Best English result goes to S1, best Persian result to S2 (by the language settings). */
    fun autoPick() {
        val rel = release ?: return
        val provider = providerFor(providerId)
        if (provider == null) {
            status = "Enter the key for this provider first."
            return
        }
        job?.cancel()
        job = scope.launch {
            busy = true
            try {
                if (results.isEmpty()) {
                    status = "Searching ${provider.displayName}..."
                    results = searchAndRank(provider, rel)
                }
                val plan = listOf(prefs.s1Language to SubtitleSlot.S1, prefs.s2Language to SubtitleSlot.S2)
                    .distinctBy { it.first }
                var saved = 0
                for ((language, slot) in plan) {
                    val best = results.firstOrNull { it.candidate.language == language } ?: continue
                    status = "Downloading ${language.uppercase()} subtitle..."
                    if (downloadAndSave(best.candidate, slot)) saved++
                    if (needFolder) return@launch // waiting for the user to choose a folder
                }
                status = if (saved > 0) "Done. $saved subtitle(s) saved and loaded."
                else "No matching subtitle could be downloaded."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = describe(e)
            } finally {
                busy = false
            }
        }
    }

    /** The user chose a folder for subtitles. We keep write access and finish the pending save. */
    fun onFolderChosen(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        prefs.downloadFolderUri = uri.toString()
        needFolder = false
        configVersion++

        val waiting = pending ?: return
        pending = null
        scope.launch {
            busy = true
            try {
                saveBytes(waiting.bytes, waiting.language, waiting.slot)
            } finally {
                busy = false
            }
        }
    }

    fun onFolderCancelled() {
        needFolder = false
        pending = null
        status = "Not saved. Choose a folder to keep the subtitle."
    }

    // ---- Work ----

    private suspend fun searchAndRank(provider: SubtitleProvider, rel: ReleaseInfo): List<RankedCandidate> {
        val found = withContext(Dispatchers.IO) { provider.search(SearchQuery(rel, languages())) }
        return SubtitleRanker.rank(rel, found)
    }

    private suspend fun downloadAndSave(candidate: SubtitleCandidate, slot: SubtitleSlot): Boolean {
        val provider = providerFor(candidate.providerId)
        if (provider == null) {
            status = "This provider is not set up."
            return false
        }
        val rel = release ?: return false

        // Network, unzip and conversion run in the background.
        val srt = withContext(Dispatchers.IO) {
            val file = provider.download(candidate)
            val subtitle = if (ZipPicker.isZip(file.bytes)) {
                val picked = ZipPicker.pick(file.bytes, rel, candidate.language)
                    ?: throw IOException("There is no subtitle file inside the zip.")
                DownloadedFile(picked.fileName, picked.bytes)
            } else {
                file
            }
            toSrtUtf8(subtitle, candidate.language)
        }
        return saveBytes(srt, candidate.language, slot)
    }

    private suspend fun saveBytes(bytes: ByteArray, language: String, slot: SubtitleSlot): Boolean {
        val uri = videoUri ?: return false
        val name = videoName ?: return false

        val result = withContext(Dispatchers.IO) {
            saver.save(
                videoUri = uri,
                videoName = name,
                language = language,
                content = bytes,
                mode = prefs.downloadMode,
                videoFolderTree = prefs.subtitleFolderUri?.let { Uri.parse(it) },
                downloadFolderTree = prefs.downloadFolderUri?.let { Uri.parse(it) }
            )
        }
        return when (result) {
            is SubtitleSaver.Result.Saved -> {
                status = "Saved ${result.fileName}${result.note.orEmpty()}"
                onSaved(slot, SubtitleSource.External(result.fileName, result.uri, language))
                true
            }
            is SubtitleSaver.Result.NeedFolder -> {
                pending = PendingSave(bytes, language, slot)
                status = result.reason
                needFolder = true
                false
            }
            is SubtitleSaver.Result.Failed -> {
                status = result.message
                false
            }
        }
    }

    /** Always saves UTF-8 SRT, whatever the download was (old encodings, ASS, WebVTT). */
    private fun toSrtUtf8(file: DownloadedFile, language: String): ByteArray {
        val text = TextDecoder.decode(file.bytes, language).removePrefix("\uFEFF")
        val cues = SubtitleParser.parse(text, file.fileName)
        if (cues.isEmpty()) throw IOException("The downloaded file has no subtitle lines.")
        val ext = file.fileName.substringAfterLast('.', "").lowercase()
        val output = if (ext == "srt") text else SrtWriter.write(cues)
        return output.toByteArray(Charsets.UTF_8)
    }

    private fun describe(e: Exception): String = when {
        e is HttpException && (e.code == 401 || e.code == 403) -> "The provider rejected the key or the login."
        e is HttpException && e.code == 406 -> "The daily download limit is reached."
        e is HttpException && e.code == 429 -> "Too many requests. Please wait a little and try again."
        e is HttpException -> "The provider answered with an error (${e.code})."
        e is UnknownHostException || e is SocketTimeoutException || e is ConnectException ->
            "No connection to the provider."
        else -> e.message ?: "Something went wrong."
    }
}
