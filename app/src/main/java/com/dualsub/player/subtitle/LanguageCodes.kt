package com.dualsub.player.subtitle

/** One language the user can pick. [code] is the two-letter code used everywhere in the app. */
data class Language(val code: String, val english: String, val native: String) {
    /** For lists: "Persian (فارسی)", or just "English" when both names are the same. */
    val display: String get() = if (native == english) english else "$english ($native)"
}

/**
 * Turns the many spellings of a language into one two-letter code, and back into a name.
 * Any language in the table below can be used as main or helper language.
 */
object LanguageCodes {

    // code | other codes | English name | native name | other names used in file names
    private const val TABLE = """
en|eng|English|English|
fa|fas,per|Persian|فارسی|farsi
ar|ara|Arabic|العربية|
es|spa|Spanish|Español|castilian
fr|fra,fre|French|Français|
de|deu,ger|German|Deutsch|
it|ita|Italian|Italiano|
pt|por|Portuguese|Português|
ru|rus|Russian|Русский|
tr|tur|Turkish|Türkçe|
zh|zho,chi|Chinese|中文|mandarin,cantonese
ja|jpn|Japanese|日本語|
ko|kor|Korean|한국어|
hi|hin|Hindi|हिन्दी|
ur|urd|Urdu|اردو|
bn|ben|Bengali|বাংলা|bangla
id|ind|Indonesian|Bahasa Indonesia|
ms|msa,may|Malay|Bahasa Melayu|
th|tha|Thai|ไทย|
vi|vie|Vietnamese|Tiếng Việt|
nl|nld,dut|Dutch|Nederlands|
sv|swe|Swedish|Svenska|
no|nor,nob,nno,nb,nn|Norwegian|Norsk|
da|dan|Danish|Dansk|
fi|fin|Finnish|Suomi|
pl|pol|Polish|Polski|
cs|ces,cze|Czech|Čeština|
sk|slk,slo|Slovak|Slovenčina|
hu|hun|Hungarian|Magyar|
ro|ron,rum|Romanian|Română|
bg|bul|Bulgarian|Български|
el|ell,gre|Greek|Ελληνικά|
he|heb,iw|Hebrew|עברית|
uk|ukr|Ukrainian|Українська|
hr|hrv|Croatian|Hrvatski|
sr|srp|Serbian|Српски|
sl|slv|Slovenian|Slovenščina|
bs|bos|Bosnian|Bosanski|
mk|mkd,mac|Macedonian|Македонски|
sq|sqi,alb|Albanian|Shqip|
lt|lit|Lithuanian|Lietuvių|
lv|lav|Latvian|Latviešu|
et|est|Estonian|Eesti|
is|isl,ice|Icelandic|Íslenska|
ga|gle|Irish|Gaeilge|
ca|cat|Catalan|Català|
eu|eus,baq|Basque|Euskara|
gl|glg|Galician|Galego|
af|afr|Afrikaans|Afrikaans|
sw|swa|Swahili|Kiswahili|
ta|tam|Tamil|தமிழ்|
te|tel|Telugu|తెలుగు|
ml|mal|Malayalam|മലയാളം|
kn|kan|Kannada|ಕನ್ನಡ|
mr|mar|Marathi|मराठी|
gu|guj|Gujarati|ગુજરાતી|
pa|pan|Punjabi|ਪੰਜਾਬੀ|
ne|nep|Nepali|नेपाली|
si|sin|Sinhala|සිංහල|sinhalese
my|mya,bur|Burmese|မြန်မာ|
km|khm|Khmer|ខ្មែរ|
lo|lao|Lao|ລາວ|
ka|kat,geo|Georgian|ქართული|
hy|hye,arm|Armenian|Հայերեն|
az|aze|Azerbaijani|Azərbaycan|azeri
kk|kaz|Kazakh|Қазақша|
uz|uzb|Uzbek|Oʻzbekcha|
mn|mon|Mongolian|Монгол|
ps|pus|Pashto|پښتو|
ku|kur|Kurdish|Kurdî|
tl|tgl,fil|Filipino|Filipino|tagalog
cy|cym,wel|Welsh|Cymraeg|
mt|mlt|Maltese|Malti|
be|bel|Belarusian|Беларуская|
"""

    private val languages: List<Language>
    private val lookup = HashMap<String, String>()

    init {
        val list = ArrayList<Language>()
        for (line in TABLE.lines()) {
            val parts = line.split('|')
            if (parts.size < 4) continue
            val code = parts[0].trim()
            list.add(Language(code, parts[2].trim(), parts[3].trim()))
            lookup[code] = code
            parts[1].split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { lookup[it] = code }
            lookup[parts[2].trim().lowercase()] = code
            parts.getOrNull(4)?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }
                ?.forEach { lookup[it] = code }
        }
        languages = list.sortedBy { it.english }
    }

    /** All languages, sorted by English name. */
    fun all(): List<Language> = languages

    fun find(code: String): Language? = languages.firstOrNull { it.code == code }

    /** "fa" -> "Persian (فارسی)". Unknown codes are shown in capitals. */
    fun displayName(code: String): String = find(code)?.display ?: code.uppercase()

    /** A code, a three-letter code or a language name -> the two-letter code. Null when unknown. */
    fun knownOrNull(token: String): String? = lookup[token.lowercase()]

    /** Tokens that look like a language but are tags ("hi" = hearing impaired). */
    private const val HEARING_IMPAIRED_TAG = "hi"

    /**
     * "en.srt" or "Movie.2020.fa.srt" -> "en" / "fa" / null.
     * The language tag sits near the end of the name, so we read from the end. This way a title
     * such as "The.English.Patient.fa.srt" gives "fa", not "en". [maxTokensFromEnd] limits how far
     * back we look (use a small number for names that still contain the movie title).
     */
    fun fromFileName(name: String, maxTokensFromEnd: Int = Int.MAX_VALUE): String? {
        val withoutExt = name.substringBeforeLast('.', name)
        val matches = withoutExt.split('.', '_', '-', ' ')
            .asReversed()
            .take(maxTokensFromEnd)
            .mapNotNull { token -> knownOrNull(token)?.let { token.lowercase() to it } }
        // "Movie.en.hi.srt": the "hi" is the hearing-impaired tag, not Hindi, when another language is there.
        return matches.firstOrNull { !(it.first == HEARING_IMPAIRED_TAG && matches.size > 1) }?.second
    }

    /** For embedded tracks: "eng" -> "en", "per" -> "fa", "und" -> null. Unknown codes stay as they are. */
    fun normalize(code: String?): String? {
        val c = code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
        if (c.isNullOrEmpty() || c == "und" || c == "zxx" || c == "mis") return null
        return lookup[c] ?: c
    }

    private val RTL = setOf("fa", "ar", "he", "ur", "ps", "ku")
    fun isRtl(code: String?): Boolean = code in RTL
}
