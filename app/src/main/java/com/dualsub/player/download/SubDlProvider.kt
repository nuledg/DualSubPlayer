package com.dualsub.player.download

import com.dualsub.player.subtitle.LanguageCodes
import org.json.JSONObject
import java.io.IOException

/** SubDL, official API: https://subdl.com/api-doc */
class SubDlProvider(private val apiKey: String) : SubtitleProvider {

    override val id: String = ID
    override val displayName: String = "SubDL"

    override fun search(query: SearchQuery): List<SubtitleCandidate> {
        val response = HttpClient.get(buildSearchUrl(query), mapOf("Accept" to "application/json"))
        return parseSearchResponse(response.text())
    }

    /** Checks a key using SubDL's official account-status endpoint. */
    fun testConnection() {
        if (apiKey.isBlank()) throw IOException("Enter your SubDL API key first.")
        val params = linkedMapOf("api_key" to apiKey)
        val response = HttpClient.get("$API/api/v1/me?" + HttpClient.encodeQuery(params), mapOf("Accept" to "application/json"))
        val json = JSONObject(response.text())
        if (json.has("status") && !json.optBoolean("status", false)) {
            throw IOException(json.optString("error").ifBlank { "SubDL rejected the API key." })
        }
        if (json.has("error") && json.optString("error").isNotBlank()) {
            throw IOException(json.optString("error"))
        }
    }

    override fun download(candidate: SubtitleCandidate): DownloadedFile {
        val url = buildDownloadUrl(candidate.ref)
        // Paid SubDL accounts can charge downloads to their account-wide quota with x-api-key.
        // Free keys may not have this entitlement, so retry anonymously on an auth rejection.
        val response = try {
            HttpClient.get(url, mapOf("x-api-key" to apiKey))
        } catch (e: HttpException) {
            if (e.code == 401 || e.code == 403) HttpClient.get(url) else throw e
        }
        val fallbackName = candidate.downloadFileName
            ?: candidate.ref.substringAfterLast('/').ifBlank { "subtitle.srt" }
        return DownloadedFile(fallbackName, response.bytes)
    }

    /** Build the documented filename-aware search URL. Exposed internally for unit testing. */
    internal fun buildSearchUrl(query: SearchQuery): String {
        val r = query.release
        val params = linkedMapOf("api_key" to apiKey)
        if (r.title.isNotBlank()) params["film_name"] = r.title
        if (r.originalFileName.isNotBlank()) params["file_name"] = r.originalFileName
        params["languages"] = query.languages.joinToString(",") { it.uppercase() }
        params["subs_per_page"] = "30"
        params["type"] = if (r.isSeries) "tv" else "movie"
        params["releases"] = "1"
        params["unpack"] = "1"
        params["client"] = "subdl_player"
        r.year?.let { params["year"] = it.toString() }
        if (r.isSeries) {
            params["season_number"] = r.season.toString()
            params["episode_number"] = r.episode.toString()
        }

        return "$API/api/v1/subtitles?" + HttpClient.encodeQuery(params)
    }

    /** Turns the JSON answer of the search into candidates, including unpacked season-pack files. */
    internal fun parseSearchResponse(body: String): List<SubtitleCandidate> {
        val json = JSONObject(body)
        if (!json.optBoolean("status", true)) {
            throw IOException(json.optString("error").ifBlank { "SubDL returned an error." })
        }

        // SubDL attaches subtitles to the first matched title in `results`. The title's year
        // lives in results[], not necessarily in each subtitle object, so keep it as a fallback
        // for movie/remake ranking.
        val resultYear = json.optJSONArray("results")
            ?.optJSONObject(0)
            ?.optInt("year", 0)
            ?.takeIf { it > 0 }
        val items = json.optJSONArray("subtitles") ?: return emptyList()
        val result = ArrayList<SubtitleCandidate>()
        for (i in 0 until items.length()) {
            val parent = items.optJSONObject(i) ?: continue
            val children = parent.optJSONArray("unpack_files")
            if (children != null) {
                for (j in 0 until children.length()) {
                    val file = children.optJSONObject(j) ?: continue
                    candidateFrom(
                        item = file,
                        parent = parent,
                        fullSeason = false,
                        fallbackName = file.optString("name"),
                        fallbackYear = resultYear
                    )?.let(result::add)
                }
            }

            // Keep the ZIP entry as a fallback for packs with no unpacked episode file.
            candidateFrom(
                item = parent,
                parent = parent,
                fullSeason = parent.optBoolean("full_season", false),
                fallbackName = parent.optString("name"),
                fallbackYear = resultYear
            )?.let(result::add)
        }
        return result.distinctBy { it.language to it.ref }
    }

    private fun candidateFrom(
        item: JSONObject,
        parent: JSONObject,
        fullSeason: Boolean,
        fallbackName: String,
        fallbackYear: Int?
    ): SubtitleCandidate? {
        val url = item.optString("url").takeUnless { it.isBlank() || it == "null" } ?: return null
        val fileName = item.optString("name").takeUnless { it.isBlank() || it == "null" }
            ?: fallbackName.takeUnless { it.isBlank() || it == "null" }
        val releaseName = item.optString("release_name").takeUnless { it.isBlank() || it == "null" }
            ?: fileName.takeUnless { it.isNullOrBlank() }
            ?: parent.optString("release_name").takeUnless { it.isBlank() || it == "null" }
            ?: ""

        val rawLanguage = sequenceOf(
            item.optString("language"), item.optString("lang"),
            parent.optString("language"), parent.optString("lang")
        ).firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
        val language = normalizeLanguage(rawLanguage)
            .ifBlank { languageFromName(rawLanguage) }
            .ifBlank { languageFromName(fileName.orEmpty()) }
            .ifBlank { languageFromName(releaseName) }
        if (language.isBlank()) return null

        val season = item.optInt("season", parent.optInt("season", 0)).takeIf { it > 0 }
        val episode = item.optInt("episode", parent.optInt("episode", 0)).takeIf { it > 0 }
        return SubtitleCandidate(
            providerId = ID,
            ref = url,
            language = language,
            releaseName = releaseName,
            season = season,
            episode = episode,
            year = item.optInt("year", parent.optInt("year", fallbackYear ?: 0)).takeIf { it > 0 },
            downloads = item.optInt("download_count", parent.optInt("download_count", 0)).takeIf { it > 0 },
            hearingImpaired = item.optBoolean("hi", parent.optBoolean("hi", false)),
            fullSeason = fullSeason,
            downloadFileName = fileName
        )
    }

    private fun normalizeLanguage(raw: String): String {
        if (raw.isBlank()) return ""
        LanguageCodes.knownOrNull(raw)?.let { return it }
        // Regional codes such as pt-BR or fa-IR use the known base language.
        LanguageCodes.knownOrNull(raw.substringBefore('-'))?.let { return it }
        // Never turn an unknown long language label into a fake ISO language code.
        if (raw.length <= 3) return LanguageCodes.normalize(raw).orEmpty()
        return ""
    }

    /** SubDL sometimes sends names such as "farsi_persian" instead of a code. */
    private fun languageFromName(name: String): String =
        name.split('_', ' ', '-', '.').firstNotNullOfOrNull { LanguageCodes.knownOrNull(it) } ?: ""

    internal fun buildDownloadUrl(ref: String): String = when {
        ref.startsWith("/") -> "$DOWNLOAD_HOST$ref"
        ref.startsWith("https://dl.subdl.com/") -> ref
        else -> throw IOException("SubDL returned an invalid download link.")
    }

    companion object {
        const val ID = "subdl"
        private const val API = "https://api.subdl.com"
        private const val DOWNLOAD_HOST = "https://dl.subdl.com"
    }
}
