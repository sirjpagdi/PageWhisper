package com.pagewhisper.app

import com.pagewhisper.app.data.AudioCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Saved speech: the file format and the way a section is found again. */
class PreparedTest {
    @Test fun samplesSurviveASaveAndLoad() {
        val pcm = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 0.001f)
        val back = AudioCache.decode(AudioCache.encode(pcm))
        assertEquals(pcm.size, back.size)
        for (i in pcm.indices) assertEquals(pcm[i], back[i], 0.0002f)
    }

    @Test fun loudSamplesAreClippedNotWrapped() {
        val back = AudioCache.decode(AudioCache.encode(floatArrayOf(2f, -2f)))
        assertEquals(1f, back[0], 0.001f)
        assertEquals(-1f, back[1], 0.001f)
    }

    @Test fun aSectionIsFoundByVoiceSpeedAndText() {
        val k = AudioCache.key(3, 1.35f, "Hello there.")
        assertEquals(k, AudioCache.key(3, 1.35f, "Hello there."))
        assertNotEquals(k, AudioCache.key(4, 1.35f, "Hello there."))
        assertNotEquals(k, AudioCache.key(3, 1.0f, "Hello there."))
        assertNotEquals(k, AudioCache.key(3, 1.35f, "Hello there!"))
    }
}
