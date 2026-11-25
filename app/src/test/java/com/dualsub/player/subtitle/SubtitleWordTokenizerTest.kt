package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleWordTokenizerTest {
    @Test fun separatesPunctuationAndKeepsApostrophesInsideWords() {
        assertEquals(
            listOf("Don't", "stop", "now"),
            SubtitleWordTokenizer.words("Don't, stop! now.").map { it.text }
        )
    }

    @Test fun keepsPersianCombiningMarksWithTheirWord() {
        assertEquals(
            listOf("می‌روم", "خانه"),
            SubtitleWordTokenizer.words("می‌روم خانه").map { it.text }
        )
    }

    @Test fun ignoresPunctuationAndNumbersRemainTappable() {
        assertEquals(
            listOf("Hello", "2026", "world"),
            SubtitleWordTokenizer.words("(Hello) 2026-world!").map { it.text }
        )
    }
}
