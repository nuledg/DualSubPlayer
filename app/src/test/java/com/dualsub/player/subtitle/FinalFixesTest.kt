package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the fixes made during the final audit (M7). */
class FinalFixesTest {

    // ---- Language from file name: read from the end ----

    @Test
    fun titleWordsDoNotFoolTheLanguageDetector() {
        assertEquals("fa", LanguageCodes.fromFileName("The.English.Patient.1996.fa.srt"))
        assertEquals("en", LanguageCodes.fromFileName("Per.Aspera.2020.en.srt"))
        assertEquals("fa", LanguageCodes.fromFileName("Movie.fa.forced.srt"))
        assertEquals("en", LanguageCodes.fromFileName("Movie.en.hi.srt"))
    }

    // ---- Document id -> real path ----

    @Test
    fun documentIdsBecomePaths() {
        assertEquals("/storage/emulated/0/Movies/Show/ep1.mkv",
            StoragePaths.fromExternalStorageDocId("primary:Movies/Show/ep1.mkv"))
        assertEquals("/storage/1A2B-3C4D/Videos/film.mkv",
            StoragePaths.fromExternalStorageDocId("1A2B-3C4D:Videos/film.mkv"))
        assertEquals("/storage/emulated/0", StoragePaths.fromExternalStorageDocId("primary:"))
        assertNull(StoragePaths.fromExternalStorageDocId("no-colon"))
        assertEquals("primary:Movies/Show",
            StoragePaths.parentDocumentIdWithinTree("primary:Movies", "/storage/emulated/0/Movies/Show/ep1.mkv"))
        assertEquals("primary:Movies",
            StoragePaths.parentDocumentIdWithinTree("primary:Movies", "/storage/emulated/0/Movies/ep1.mkv"))
        assertNull(StoragePaths.parentDocumentIdWithinTree("primary:Movies/Show", "/storage/emulated/0/Movies/Other/ep1.mkv"))
        assertEquals(1234L, StoragePaths.mediaVideoId("video:1234"))
        assertNull(StoragePaths.mediaVideoId("audio:1234"))
        assertNull(StoragePaths.mediaVideoId("video:abc"))
    }

    // ---- Auto pick ----

    private fun track(index: Int, lang: String?, codec: String = "subrip", text: Boolean = true, forced: Boolean = false) =
        SubtitleSource.Embedded(
            EmbeddedTrack(ContainerType.MKV, index + 1L, index, codec, lang, null, text, false, forced)
        )

    @Test
    fun forcedTracksAreNotChosenAsMainSubtitle() {
        val forced = track(0, "en", forced = true)
        val full = track(1, "en")
        val persian = track(2, "fa")
        val choice = AutoPick.choose(listOf(forced, full, persian), "en", "fa")
        assertSame(full, choice.s1)
        assertSame(persian, choice.s2)
    }

    @Test
    fun textBeatsPicturesAndPicturesCanOnlyBeS1() {
        val pgsEn = track(0, "en", codec = "pgs", text = false)
        val persian = track(1, "fa")
        val choice = AutoPick.choose(listOf(pgsEn, persian), "en", "fa")
        assertSame(pgsEn, choice.s1)          // only English is a picture: still used for S1
        assertSame(persian, choice.s2)

        val pgsFa = track(0, "fa", codec = "pgs", text = false)
        assertNull(AutoPick.choose(listOf(pgsFa), "en", "fa").s2)   // a picture never goes to S2
    }

    @Test
    fun nothingAvailableGivesNothing() {
        val choice = AutoPick.choose(emptyList(), "en", "fa")
        assertNull(choice.s1)
        assertNull(choice.s2)
    }

    // ---- Subtitle placement ----

    @Test
    fun tallSubtitlesMoveHelperUpInsteadOfOverlapping() {
        val height = 360
        val s1 = SubtitlePlacement.Box(100, SubtitlePlacement.start(90f, 60, height), 400, 60)   // top 294
        val s2y = SubtitlePlacement.start(79f, 60, height)                                       // top 254
        val s2 = SubtitlePlacement.Box(100, s2y, 400, 60)
        assertTrue(s2.bottom > s1.top)                                                           // they touch by default
        val moved = SubtitlePlacement.s2Top(s1, s2, gap = 8, s2Percent = 79f, s1Percent = 90f)
        assertEquals(s1.top - 8 - 60, moved)
    }

    @Test
    fun helperThatDoesNotTouchOrWasPlacedBelowStaysPut() {
        val s1 = SubtitlePlacement.Box(100, 300, 400, 30)
        val s2 = SubtitlePlacement.Box(100, 200, 400, 30)
        assertEquals(200, SubtitlePlacement.s2Top(s1, s2, 8, 55f, 90f))                         // far above
        assertEquals(310, SubtitlePlacement.s2Top(s1, s2.copy(top = 310), 8, 95f, 90f))         // below on purpose
        val side = SubtitlePlacement.Box(600, 290, 100, 30)                                      // no shared width
        assertEquals(290, SubtitlePlacement.s2Top(s1, side, 8, 79f, 90f))
    }

    @Test
    fun placementStaysInsideTheScreen() {
        assertEquals(0, SubtitlePlacement.start(0f, 50, 400))
        assertEquals(350, SubtitlePlacement.start(100f, 50, 400))
        assertEquals(0, SubtitlePlacement.start(50f, 500, 400))     // bigger than the parent
    }

    // ---- Text decoding (the general detector had no test) ----

    @Test
    fun decodesUtf8WithAndWithoutBom() {
        val text = "سلام دنیا"
        assertEquals(text, TextDecoder.decode(text.toByteArray(Charsets.UTF_8), "fa"))
        assertEquals(text, TextDecoder.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(), null))
    }

    @Test
    fun decodesWindows1256PersianWithTheLanguageHint() {
        val text = "سلام، حال شما چطور است؟"
        val bytes = text.toByteArray(charset("windows-1256"))
        assertEquals(text, TextDecoder.decode(bytes, "fa"))
        // Arabic yeh and kaf in an old file become the Persian letters when the language is Persian.
        val old = "كتاب خوب است".toByteArray(charset("windows-1256")) + "ي".toByteArray(charset("windows-1256"))
        assertEquals("کتاب خوب استی", TextDecoder.decode(old, "fa"))
    }

    @Test
    fun decodesUtf16WithBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hi there".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hi there", TextDecoder.decode(bytes, "en"))
    }

    @Test
    fun detectorHandlesWesternAndPersianWithoutAHint() {
        val western = "Café déjà vu, naïve señor. Où est la bibliothèque? Très bien, merci beaucoup.".repeat(3)
        assertEquals(western, TextDecoder.decode(western.toByteArray(charset("windows-1252")), null))

        // Windows-1256 has no Persian yeh and kaf, so real old files use the Arabic ones (ي and ك).
        val persian = "ايـن يك جمله فارسي براي آزمايش تشخيص رمزگذاري است. ".repeat(20)
        val decoded = TextDecoder.decode(persian.toByteArray(charset("windows-1256")), null)
        assertEquals(persian, decoded)
    }
}
