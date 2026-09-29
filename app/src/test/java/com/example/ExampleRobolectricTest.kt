package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.StudioAudioSynthesizer
import com.example.data.BuiltInEqPresets
import com.example.data.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and studio audio synthesizer and lrc parser`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Obsidian Pulse", appName)

        // Verify 10-band EQ frequencies and presets
        assertEquals(10, BuiltInEqPresets.FREQUENCY_LABELS.size)
        assertEquals(7, BuiltInEqPresets.ALL_PRESETS.size)

        // Verify LRC parser
        val parsed = LrcParser.parse("[00:04.50] Neon horizon line\n[01:10.00] Second verse")
        assertEquals(2, parsed.size)
        assertEquals(4500L, parsed[0].timeMs)
        assertEquals(70000L, parsed[1].timeMs)

        // Verify studio catalog specs
        assertEquals(8, StudioAudioSynthesizer.STUDIO_CATALOG.size)
        assertTrue(StudioAudioSynthesizer.STUDIO_CATALOG.any { it.bpm >= 140 })
    }
}
