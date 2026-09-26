package com.internship.scritto.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Guards against the things that make a synthesised chime sound distorted. */
class AssistantSoundsTest {

    private fun samples(cue: AssistantSounds.Cue) = AssistantSounds.render(cue)

    @Test
    fun neverClipsAndStaysComfortablyQuiet() {
        AssistantSounds.Cue.values().forEach { cue ->
            val peak = samples(cue).maxOf { abs(it.toInt()) }
            assertTrue("$cue peak $peak should be below 45% of full scale", peak < 0.45 * Short.MAX_VALUE)
            assertTrue("$cue should actually be audible", peak > 0.05 * Short.MAX_VALUE)
        }
    }

    @Test
    fun startsAndEndsAtSilenceSoThereAreNoClicks() {
        AssistantSounds.Cue.values().forEach { cue ->
            val s = samples(cue)
            assertTrue("$cue start", abs(s.first().toInt()) < 40)
            assertTrue("$cue end", abs(s.last().toInt()) < 40)
        }
    }

    @Test
    fun noSampleToSampleJumpsThatWouldSoundLikeClicks() {
        AssistantSounds.Cue.values().forEach { cue ->
            val s = samples(cue)
            var worst = 0
            for (i in 1 until s.size) worst = maxOf(worst, abs(s[i] - s[i - 1]))

            // A 990 Hz sine at this level moves at most ~2*pi*990/44100*peak per sample.
            val peak = s.maxOf { abs(it.toInt()) }
            val smoothLimit = (2 * Math.PI * 1000 / AssistantSounds.SAMPLE_RATE * peak * 1.6).toInt()
            assertTrue("$cue worst step $worst exceeds smooth limit $smoothLimit", worst <= smoothLimit)
        }
    }

    @Test
    fun fadesOutBeforeTheEnd() {
        AssistantSounds.Cue.values().forEach { cue ->
            val s = samples(cue)
            val tail = s.takeLast(AssistantSounds.SAMPLE_RATE / 100).maxOf { abs(it.toInt()) } // last 10 ms
            val peak = s.maxOf { abs(it.toInt()) }
            assertTrue("$cue tail $tail should be tiny vs peak $peak", tail < peak * 0.05)
        }
    }

    @Test
    fun cuesAreShortEnoughNotToOverlapTheSpokenReply() {
        assertTrue(AssistantSounds.durationMs(AssistantSounds.Cue.DONE) <= 850)
        assertTrue(AssistantSounds.durationMs(AssistantSounds.Cue.LISTEN) <= 650)
        assertEquals(
            AssistantSounds.durationMs(AssistantSounds.Cue.DONE) * AssistantSounds.SAMPLE_RATE / 1000,
            samples(AssistantSounds.Cue.DONE).size
        )
    }
}
