package com.dualsub.player.download

import com.dualsub.player.subtitle.LanguageCodes
import org.json.JSONObject
import java.io.IOException
import java.util.TreeMap

/**
 * OpenSubtitles.com, official REST API: https://opensubtitles.stoplight.io
 * Search needs an API key. Downloading also needs your OpenSubtitles username and password
 * (the API gives out a login token for it).
 */
class OpenSubtitlesProvider(
    private val apiKey: String,
    private val username: String,
    private val password: String
) : SubtitleProvider {

    override val id: String = ID
    override val displayName: String = "OpenSubtitles"

    private fun baseHeaders(): Map<String, String> = mapOf(
        "Api-Key" to apiKey,
        "User-Agent" to USER_AGENT,
        "Accept" to "application/json"
    )

    override fun search(query: SearchQuery): List<SubtitleCandidate> {
        val r = query.release
        // The API likes lowercase parameters in alphabetical order.
        val params = TreeMap<String, String>()
        params["languages"] = query.languages.joinToString(",") { it.lowercase() }
        params["query"] = r.title.lowercase()
        params["type"] = if (r.isSeries) "episode" else "movie"
        r.year?.let { params["year"] = it.toString() }
        if (r.isSeries) {
            params["season_number"] = r.season.toString()
            params["episode_number"] = r.episode.toString()
        }

        val response = HttpClient.get("https://$DEFAULT_HOST/api/v1/subtitles?" + HttpClient.encodeQuery(params), baseHeaders())
        return parseSearchResponse(response.text())
    }

    /** Turns the JSON answer of the search into candidates. */
    internal fun parseSearchResponse(body: String): List<SubtitleCandidate> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()

        val result = ArrayList<SubtitleCandidate>()
        for (i in 0 until data.length()) {
            val attributes = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
            val file = attributes.optJSONArray("files")?.optJSONObject(0) ?: continue
            val fileId = file.optLong("file_id", 0L)
            if (fileId == 0L) continue

            val details = attributes.optJSONObject("feature_details")
            result.add(
                SubtitleCandidate(
                    providerId = ID,
                    ref = fileId.toString(),
                    language = LanguageCodes.normalize(attributes.optString("language").substringBefore('-')) ?: "",
                    releaseName = attributes.optString("release").ifBlank { file.optString("file_name") },
                    season = details?.optInt("season_number", 0)?.takeIf { it > 0 },
                    episode = details?.optInt("episode_number", 0)?.takeIf { it > 0 },
                    year = details?.optInt("year", 0)?.takeIf { it > 0 },
                    downloads = attributes.optInt("download_count", 0),
                    hearingImpaired = attributes.optBoolean("hearing_impaired", false),
                    fullSeason = false
                )
            )
        }
        return result
    }

    override fun download(candidate: SubtitleCandidate): DownloadedFile {
        val session = session()
        val body = JSONObject().put("file_id", candidate.ref.toLong()).put("sub_format", "srt").toString()
        val headers = baseHeaders() + ("Authorization" to "Bearer ${session.token}")

        val response = HttpClient.postJson("https://${session.host}/api/v1/download", body, headers)
        val link = JSONObject(response.text()).optString("link")
        if (link.isBlank()) throw IOException("OpenSubtitles did not give a download link.")

        val file = HttpClient.get(link) // the link points to a plain file, no key needed
        return DownloadedFile(candidate.releaseName.ifBlank { "subtitle" } + ".srt", file.bytes)
    }

    // ---- Login (the token is reused for the next downloads) ----

    private class Session(val key: String, val token: String, val host: String)

    private fun session(): Session {
        val key = "$apiKey|$username"
        cached?.takeIf { it.key == key }?.let { return it }

        val body = JSONObject().put("username", username).put("password", password).toString()
        val response = HttpClient.postJson("https://$DEFAULT_HOST/api/v1/login", body, baseHeaders())
        val json = JSONObject(response.text())
        val token = json.optString("token")
        if (token.isBlank()) throw IOException("OpenSubtitles login failed.")
        val host = json.optString("base_url").ifBlank { DEFAULT_HOST }
            .removePrefix("https://").trimEnd('/')
        return Session(key, token, host).also { cached = it }
    }

    companion object {
        const val ID = "opensubtitles"
        private const val DEFAULT_HOST = "api.opensubtitles.com"
        private const val USER_AGENT = "DualSubPlayer v0.5"

        @Volatile
        private var cached: Session? = null
    }
}
