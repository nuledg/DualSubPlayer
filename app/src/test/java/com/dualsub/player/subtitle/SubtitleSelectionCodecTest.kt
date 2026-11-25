package com.dualsub.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubtitleSelectionCodecTest {

    @Test
    fun embeddedTrackSelectionRoundTripsByStableTrackId() {
        val selected = SubtitleSource.Embedded(
            EmbeddedTrack(
                container = ContainerType.MKV,
                id = 7L,
                index = 1,
                codec = "subrip",
                language = "fa",
                title = "Persian",
                isText = true,
                isDefault = false,
                isForced = false
            )
        )
        val other = SubtitleSource.Embedded(
            EmbeddedTrack(
                container = ContainerType.MKV,
                id = 3L,
                index = 0,
                codec = "subrip",
                language = "en",
                title = "English",
                isText = true,
                isDefault = true,
                isForced = false
            )
        )

        val descriptor = SubtitleSelectionCodec.encode(selected)
        assertNotNull(descriptor)
        assertEquals(selected, SubtitleSelectionCodec.resolve(descriptor, listOf(other, selected)))
    }

    @Test
    fun embeddedTrackCanBeFoundByMetadataIfContainerTrackIdChanges() {
        val saved = SubtitleSource.Embedded(
            EmbeddedTrack(ContainerType.MKV, 7L, 1, "subrip", "fa", "Persian", true, false, false)
        )
        val current = SubtitleSource.Embedded(
            EmbeddedTrack(ContainerType.MKV, 12L, 2, "subrip", "fa", "Persian", true, false, false)
        )

        assertEquals(
            current,
            SubtitleSelectionCodec.resolve(SubtitleSelectionCodec.encode(saved), listOf(current))
        )
    }

    @Test
    fun invalidOrDifferentTrackDescriptorDoesNotSilentlySelectAnotherSubtitle() {
        val current = SubtitleSource.Embedded(
            EmbeddedTrack(ContainerType.MKV, 12L, 2, "subrip", "en", "English", true, true, false)
        )

        assertNull(SubtitleSelectionCodec.resolve("not-json", listOf(current)))
        assertNull(SubtitleSelectionCodec.resolve(null, listOf(current)))
    }
}
