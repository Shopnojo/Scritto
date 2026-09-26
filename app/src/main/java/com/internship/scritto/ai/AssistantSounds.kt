package com.internship.scritto.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Scritto's own assistant sounds: soft glass-bell chimes on a calm pentatonic scale,
 * synthesised on the fly (no audio files).
 *
 * Built to stay clean on small phone speakers:
 *  - only pure, harmonic partials (no metallic inharmonic overtones);
 *  - every note fades in and the whole cue fades out smoothly, so there are no clicks;
 *  - a soft limiter and a low master gain keep the peak well below clipping;
 *  - notes stay above ~390 Hz, where phone speakers are not distorting;
 *  - cues are short, so a chime never overlaps the spoken reply or the microphone.
 */
object AssistantSounds {

    enum class Cue {
        /** Gentle rising pair: "I'm listening". */
        LISTEN,

        /** A single soft tick: "got it". */
        HEARD,

        /** Warm falling pair: "done". */
        DONE,

        /** Quiet, lower two-note: "that didn't work". */
        PROBLEM
    }

    const val SAMPLE_RATE = 44_100

    private const val MASTER_GAIN = 0.30
    private const val ATTACK_SECONDS = 0.014
    private const val RELEASE_SECONDS = 0.16

    private class Note(val hz: Double, val startMs: Int, val decaySeconds: Double, val gain: Double)

    private class Recipe(val totalMs: Int, val notes: List<Note>)

    private val recipes: Map<Cue, Recipe> = mapOf(
        Cue.LISTEN to Recipe(
            600,
            listOf(
                Note(659.25, 0, 0.16, 0.9),    // E5
                Note(880.00, 110, 0.20, 1.0)   // A5
            )
        ),
        Cue.HEARD to Recipe(
            340,
            listOf(Note(783.99, 0, 0.11, 0.85)) // G5
        ),
        Cue.DONE to Recipe(
            820,
            listOf(
                Note(987.77, 0, 0.17, 0.9),     // B5
                Note(659.25, 150, 0.24, 1.0)    // E5
            )
        ),
        Cue.PROBLEM to Recipe(
            640,
            listOf(
                Note(493.88, 0, 0.16, 0.9),     // B4
                Note(392.00, 150, 0.22, 0.9)    // G4
            )
        )
    )

    private val rendered = HashMap<Cue, ShortArray>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Length of a cue in milliseconds, so callers can wait for it to finish. */
    fun durationMs(cue: Cue): Int = recipes.getValue(cue).totalMs

    fun play(cue: Cue) {
        val samples = synchronized(rendered) { rendered.getOrPut(cue) { render(cue) } }

        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()

            val lengthMs = samples.size * 1000L / SAMPLE_RATE
            main.postDelayed({ runCatching { track.release() } }, lengthMs + 300)
        }
    }

    /** The raw samples of a cue (used by tests to check for clipping and clicks). */
    internal fun render(cue: Cue): ShortArray {
        val recipe = recipes.getValue(cue)
        val frames = SAMPLE_RATE * recipe.totalMs / 1000
        val mix = DoubleArray(frames)

        recipe.notes.forEach { note ->
            val first = SAMPLE_RATE * note.startMs / 1000

            for (i in first until frames) {
                val t = (i - first).toDouble() / SAMPLE_RATE

                // raised-cosine fade-in: starts and ends its slope at exactly zero
                val attack = if (t < ATTACK_SECONDS) 0.5 - 0.5 * cos(PI * t / ATTACK_SECONDS) else 1.0
                val body = exp(-t / note.decaySeconds)

                // fundamental + a quiet octave: warm and round, never harsh
                val tone = sin(2 * PI * note.hz * t) +
                    0.16 * sin(2 * PI * note.hz * 2.0 * t) * exp(-t / (note.decaySeconds * 0.6))

                mix[i] += tone * attack * body * note.gain
            }
        }

        val releaseFrames = (SAMPLE_RATE * RELEASE_SECONDS).toInt().coerceAtMost(frames)

        return ShortArray(frames) { i ->
            // raised-cosine release over the last RELEASE_SECONDS
            val remaining = frames - 1 - i
            val release = if (remaining >= releaseFrames) {
                1.0
            } else {
                0.5 - 0.5 * cos(PI * remaining / releaseFrames)
            }

            // soft limiter: gently rounds any peak instead of hard-clipping it
            val limited = tanh(mix[i] * release * MASTER_GAIN * 1.4) / 1.4

            (limited * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
