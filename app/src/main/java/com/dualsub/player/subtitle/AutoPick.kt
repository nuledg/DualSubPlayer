package com.dualsub.player.subtitle

/**
 * Chooses which subtitles go into S1 and S2 when a video opens.
 * Rules, in this order:
 * - S1: main language, text before pictures. S2: helper language, text only.
 * - Full subtitles come before "forced" ones (forced tracks only hold a few foreign-language lines).
 * - Files next to the video come before tracks inside it (the list is given in that order).
 */
object AutoPick {

    data class Choice(val s1: SubtitleSource?, val s2: SubtitleSource?)

    fun choose(all: List<SubtitleSource>, s1Language: String, s2Language: String): Choice {
        if (all.isEmpty()) return Choice(null, null)

        // sortedBy is stable: everything else keeps its order.
        val ordered = all.sortedBy { it.isForced }
        val externals = ordered.filterIsInstance<SubtitleSource.External>()

        val s1 = ordered.firstOrNull { it.language == s1Language && !it.isImage }
            ?: ordered.firstOrNull { it.language == s1Language }
            ?: externals.firstOrNull { it.language != s2Language }
            ?: externals.firstOrNull()
        val s2 = ordered.firstOrNull { it.language == s2Language && it != s1 && !it.isImage }
            ?: externals.firstOrNull { it != s1 }
        return Choice(s1, s2)
    }
}
