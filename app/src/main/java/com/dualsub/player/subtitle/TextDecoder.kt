package com.dualsub.player.subtitle

import org.mozilla.universalchardet.UniversalDetector
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Turns the bytes of a subtitle file into a normal string (UTF-8 inside the app). */
object TextDecoder {

    private val WINDOWS_1256: Charset = Charset.forName("windows-1256")
    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")
    private val RTL_LANGUAGES = setOf("fa", "ar", "ur", "ps", "ku")

    fun decode(bytes: ByteArray, languageHint: String?): String {
        val text = decodeCharset(bytes, languageHint)
        return if (languageHint == "fa") persianLetters(text) else text
    }

    /**
     * Windows-1256 has no Persian yeh and kaf, so old files use the Arabic ones.
     * They look wrong in Persian text (for example the dots of a final yeh), so we replace them.
     */
    private fun persianLetters(text: String): String =
        text.replace('\u064A', '\u06CC').replace('\u0643', '\u06A9')

    private fun decodeCharset(bytes: ByteArray, languageHint: String?): String {
        // 1) Byte order marks
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        // 2) Strict UTF-8. Old 8-bit files almost never pass this test by accident.
        strictUtf8(bytes)?.let { return it }

        // 3) Persian and Arabic files that are not UTF-8 are almost always Windows-1256.
        //    The detector can guess wrong on short text, so we do not ask it here.
        if (languageHint in RTL_LANGUAGES) return String(bytes, WINDOWS_1256)

        // 3b) Other languages with a well-known old 8-bit code page.
        legacyCharset(languageHint)?.let { return String(bytes, it) }

        // 4) No language known (for example "movie.srt"). The detector often takes Windows-1256 Persian
        //    for Thai, so we check for Arabic-script text ourselves first.
        if (languageHint == null) looksLikeWindows1256(bytes)?.let { return it }

        // 5) Other languages: ask the detector, then fall back to Windows-1252.
        return String(bytes, detect(bytes) ?: WINDOWS_1252)
    }

    private val LEGACY_NAMES = mapOf(
        "he" to "windows-1255", "el" to "windows-1253", "tr" to "windows-1254",
        "ru" to "windows-1251", "uk" to "windows-1251", "bg" to "windows-1251", "be" to "windows-1251",
        "sr" to "windows-1251", "mk" to "windows-1251",
        "pl" to "windows-1250", "cs" to "windows-1250", "sk" to "windows-1250", "hu" to "windows-1250",
        "ro" to "windows-1250", "hr" to "windows-1250", "sl" to "windows-1250", "sq" to "windows-1250",
        "bs" to "windows-1250", "lt" to "windows-1257", "lv" to "windows-1257", "et" to "windows-1257",
        "vi" to "windows-1258", "th" to "windows-874", "ja" to "Shift_JIS", "ko" to "EUC-KR", "zh" to "GBK"
    )

    private fun legacyCharset(language: String?): Charset? =
        LEGACY_NAMES[language]?.let { runCatching { Charset.forName(it) }.getOrNull() }

    /**
     * Decodes as Windows-1256 and keeps the result only when almost every non-ASCII byte became an
     * Arabic-script letter. Western text with a few accents fails this test.
     */
    private fun looksLikeWindows1256(bytes: ByteArray): String? {
        val sample = if (bytes.size > 200_000) bytes.copyOf(200_000) else bytes
        val high = sample.count { it < 0 }
        if (high < 20) return null
        val decoded = String(sample, WINDOWS_1256)
        val arabic = decoded.count { it in '\u0600'..'\u06FF' }
        return if (arabic >= high * 0.8) String(bytes, WINDOWS_1256) else null
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun detect(bytes: ByteArray): Charset? {
        val detector = UniversalDetector(null)
        return try {
            detector.handleData(bytes, 0, minOf(bytes.size, 200_000))
            detector.dataEnd()
            detector.detectedCharset?.let { runCatching { Charset.forName(it) }.getOrNull() }
        } finally {
            detector.reset()
        }
    }
}
