package com.dualsub.player.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Reads sample answers shaped like the ones the two services send. */
class ProviderParsingTest {

    @Test
    fun subDlSearchAnswer() {
        val body = """
            {"status":true,
             "results":[{"sd_id":1,"type":"movie","name":"The Movie","year":2019}],
             "subtitles":[
               {"release_name":"The.Movie.2019.1080p.BluRay.x264-SPARKS","name":"SUBDL::1","lang":"farsi_persian",
                "author":"a","url":"/subtitle/1-2.zip","season":0,"episode":0,"language":"FA","hi":false,"full_season":false},
               {"release_name":"Show.S01.COMPLETE","url":"/subtitle/3-4.zip","season":1,"episode":0,
                "language":"EN","hi":true,"full_season":true},
               {"release_name":"no url here","language":"EN"},
               {"release_name":"language only by name","url":"/subtitle/5.zip","lang":"english"}
             ]}
        """.trimIndent()

        val list = SubDlProvider("key").parseSearchResponse(body)
        assertEquals(3, list.size)

        assertEquals("fa", list[0].language)
        assertEquals("/subtitle/1-2.zip", list[0].ref)
        assertNull(list[0].season)                      // 0 means "not a series"
        assertFalse(list[0].fullSeason)

        assertEquals("en", list[1].language)
        assertEquals(1, list[1].season)
        assertNull(list[1].episode)
        assertTrue(list[1].fullSeason)
        assertTrue(list[1].hearingImpaired)

        assertEquals("en", list[2].language)
    }

    @Test
    fun subDlErrorAnswer() {
        try {
            SubDlProvider("key").parseSearchResponse("""{"status":false,"error":"Invalid API key"}""")
            fail("Expected an exception")
        } catch (e: IOException) {
            assertEquals("Invalid API key", e.message)
        }
    }

    @Test
    fun subDlEmptyAnswer() {
        assertTrue(SubDlProvider("key").parseSearchResponse("""{"status":true,"results":[],"subtitles":[]}""").isEmpty())
        assertTrue(SubDlProvider("key").parseSearchResponse("""{"status":true}""").isEmpty())
    }

    @Test
    fun openSubtitlesSearchAnswer() {
        val body = """
            {"total_pages":1,"total_count":3,"page":1,"data":[
              {"id":"123","type":"subtitle","attributes":{"subtitle_id":"123","language":"en","download_count":2500,
                "hearing_impaired":false,"release":"Show.S01E02.1080p.WEB-DL",
                "feature_details":{"feature_type":"Episode","year":2020,"title":"x","season_number":1,"episode_number":2},
                "files":[{"file_id":777,"file_name":"x.srt"}]}},
              {"id":"124","type":"subtitle","attributes":{"language":"pt-BR","files":[]}},
              {"id":"125","type":"subtitle","attributes":{"language":"fa","release":"",
                "files":[{"file_id":888,"file_name":"fallback.name.srt"}],"feature_details":{"year":0}}}
            ]}
        """.trimIndent()

        val list = OpenSubtitlesProvider("key", "user", "pass").parseSearchResponse(body)
        assertEquals(2, list.size)                      // the one without files is dropped

        assertEquals("777", list[0].ref)
        assertEquals("en", list[0].language)
        assertEquals("Show.S01E02.1080p.WEB-DL", list[0].releaseName)
        assertEquals(1, list[0].season)
        assertEquals(2, list[0].episode)
        assertEquals(2020, list[0].year)
        assertEquals(2500, list[0].downloads)

        assertEquals("fa", list[1].language)
        assertEquals("fallback.name.srt", list[1].releaseName)   // no release text: file name is used
        assertNull(list[1].year)
        assertNull(list[1].season)
    }

    @Test
    fun openSubtitlesEmptyAnswer() {
        assertTrue(OpenSubtitlesProvider("k", "u", "p").parseSearchResponse("""{"data":[]}""").isEmpty())
        assertTrue(OpenSubtitlesProvider("k", "u", "p").parseSearchResponse("""{}""").isEmpty())
    }
    @Test
    fun subDlSearchUsesWholeVideoFileNameAndAllParsedSearchFields() {
        val release = ReleaseNameParser.parse("Show.Name.S02E05.1080p.WEB-DL.x265-GROUP.mkv")
        val provider = SubDlProvider("test-key")
        val url = provider.buildSearchUrl(SearchQuery(release, listOf("en", "fa")))
        val query = url.substringAfter('?').split('&').associate { part ->
            val key = URLDecoder.decode(part.substringBefore('='), StandardCharsets.UTF_8.name())
            val value = URLDecoder.decode(part.substringAfter('=', ""), StandardCharsets.UTF_8.name())
            key to value
        }

        assertEquals("Show Name", query["film_name"])
        assertEquals("Show.Name.S02E05.1080p.WEB-DL.x265-GROUP.mkv", query["file_name"])
        assertEquals("tv", query["type"])
        assertEquals("2", query["season_number"])
        assertEquals("5", query["episode_number"])
        assertEquals("EN,FA", query["languages"])
        assertEquals("1", query["unpack"])
        assertEquals("1", query["releases"])
        assertEquals("subdl_player", query["client"])
    }

    @Test
    fun subDlUnpackFilesBecomeIndividualEpisodeCandidates() {
        val body = """
            {"status":true,"subtitles":[
              {"release_name":"Show.Name.S02.COMPLETE.1080p.WEB-DL.x265-GROUP",
               "name":"Season.Pack.zip","url":"/subtitle/100-200.zip","language":"EN",
               "season":2,"episode":1,"full_season":true,
               "unpack_files":[
                 {"name":"Show.Name.S02E05.1080p.WEB-DL.x265-GROUP.srt",
                  "release_name":"Show.Name.S02E05.1080p.WEB-DL.x265-GROUP",
                  "url":"/subtitle/100/file-en","language":"EN","season":2,"episode":5,"hi":false},
                 {"name":"Show.Name.S02E05.1080p.WEB-DL.x265-GROUP.fa.srt",
                  "release_name":"Show.Name.S02E05.1080p.WEB-DL.x265-GROUP",
                  "url":"/subtitle/100/file-fa","language":"FA","season":2,"episode":5,"hi":false}
               ]}
            ]}
        """.trimIndent()

        val candidates = SubDlProvider("test-key").parseSearchResponse(body)
        assertEquals(3, candidates.size) // two direct files plus the original ZIP fallback
        val english = candidates.first { it.language == "en" }
        val persian = candidates.first { it.language == "fa" }
        assertEquals("/subtitle/100/file-en", english.ref)
        assertEquals("Show.Name.S02E05.1080p.WEB-DL.x265-GROUP.srt", english.downloadFileName)
        assertEquals(2, english.season)
        assertEquals(5, english.episode)
        assertFalse(english.fullSeason)
        assertEquals("/subtitle/100/file-fa", persian.ref)
        assertEquals("fa", persian.language)
        assertTrue(candidates.last().fullSeason)
        assertEquals("Season.Pack.zip", candidates.last().downloadFileName)
    }

    @Test
    fun subDlCanResolveLanguageNamesAndAvoidUnknownLanguageNames() {
        val body = """
            {"status":true,"subtitles":[
              {"release_name":"Movie.2020","name":"Movie.farsi_persian.srt","url":"/subtitle/fa",
               "lang":"farsi_persian"},
              {"release_name":"Movie.2020","name":"Movie.unknown.srt","url":"/subtitle/unknown",
               "lang":"unknown-language"}
            ]}
        """.trimIndent()
        val candidates = SubDlProvider("test-key").parseSearchResponse(body)
        assertEquals(1, candidates.size)
        assertEquals("fa", candidates.single().language)
    }

    @Test
    fun subDlDownloadLinksAreResolvedAgainstOfficialDownloadHost() {
        val provider = SubDlProvider("test-key")
        assertEquals("https://dl.subdl.com/subtitle/123-456.zip", provider.buildDownloadUrl("/subtitle/123-456.zip"))
        assertEquals(
            "https://dl.subdl.com/subtitle/123/file-id",
            provider.buildDownloadUrl("https://dl.subdl.com/subtitle/123/file-id")
        )
        try {
            provider.buildDownloadUrl("https://example.com/subtitle.zip")
            fail("Expected unsafe download host to be rejected")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("invalid download link"))
        }
    }

    @Test
    fun subDlParsesDocumentedUnpackResponseAndUsesMatchedTitleYear() {
        val body = """
            {
              "status":true,
              "results":[{"sd_id":123456,"type":"tv","name":"Example Show","year":2024}],
              "subtitles":[
                {
                  "release_name":"Season Pack",
                  "name":"Season.Pack.zip",
                  "url":"/subtitle/3197651-3213944.zip",
                  "season":1,
                  "episode":1,
                  "full_season":true,
                  "unpack_files":[
                    {
                      "file_n_id":"file123",
                      "name":"Example.Show.S01E05.1080p.WEB-DL.x265.srt",
                      "release_name":"Example Show episode 5",
                      "season":1,
                      "episode":5,
                      "language":"EN",
                      "hi":false,
                      "format":"srt",
                      "size":12345,
                      "md5":"example-md5",
                      "url":"/subtitle/3197651/file123"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val candidates = SubDlProvider("fixture-key").parseSearchResponse(body)
        // The documented pack entry has no language of its own, so only its
        // individually unpacked, language-labelled episode file is selectable.
        assertEquals(1, candidates.size)
        val episode = candidates.single()
        assertEquals("en", episode.language)
        assertEquals(2024, episode.year)
        assertEquals(1, episode.season)
        assertEquals(5, episode.episode)
        assertEquals("/subtitle/3197651/file123", episode.ref)
        assertFalse(episode.fullSeason)

        val wanted = ReleaseNameParser.parse("Example.Show.2024.S01E05.1080p.WEB-DL.x265-GROUP.mkv")
        val ranked = SubtitleRanker.rank(wanted, candidates)
        assertEquals(1, ranked.size)
        assertEquals("en", ranked.single().candidate.language)
        assertTrue(ranked.single().score > 70)
    }

}
