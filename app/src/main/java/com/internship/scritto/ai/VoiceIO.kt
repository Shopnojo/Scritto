package com.internship.scritto.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** In-app speech recognition (no system dialog). Create, use and [release] on the main thread. */
class VoiceInput(
    context: Context,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onLevel: (Float) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        release()

        if (!isAvailable) {
            onError("Speech recognition isn't available on this device. You can still type to me.")
            return
        }

        val created = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = created

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onEndOfSpeech() = onLevel(0f)

            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2..10 dB → 0..1
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                bestMatch(partialResults)?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = bestMatch(results)

                if (text.isNullOrBlank()) {
                    this@VoiceInput.onError("I didn't catch that. Tap the orb and try again.")
                } else {
                    onResult(text)
                }
            }

            override fun onError(error: Int) {
                onLevel(0f)
                this@VoiceInput.onError(messageFor(error))
            }
        })

        created.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
        )
    }

    /** Finish the utterance now and deliver what was heard. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.apply {
            setRecognitionListener(null)
            cancel()
            destroy()
        }
        recognizer = null
    }

    private fun bestMatch(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the orb and try again."

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "I need microphone access to listen. Allow it in Settings, or just type to me."

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition needs an internet connection right now."

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again in a moment."

        else -> "Something went wrong with the microphone. Tap the orb to try again."
    }
}

/** Speaks replies aloud. Create and [shutdown] on the main thread. */
class VoiceSpeaker(
    context: Context,
    private val onSpeaking: (Boolean) -> Unit
) {
    private var ready = false
    private var queued: String? = null
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) return@TextToSpeech

        ready = true
        val locale = Locale.getDefault()
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            tts.language = Locale.US
        } else {
            tts.language = locale
        }

        // Slightly slower for a calm delivery. Pitch stays natural: shifting it is what
        // makes many speech engines sound robotic or distorted.
        tts.setSpeechRate(0.95f)
        tts.setPitch(1.0f)

        queued?.let { speak(it) }
        queued = null
    }.also { engine ->
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onSpeaking(true)
            override fun onDone(utteranceId: String?) = onSpeaking(false)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onSpeaking(false)
        })
    }

    fun speak(text: String) {
        if (text.isBlank()) {
            onSpeaking(false)
            return
        }

        if (!ready) {
            queued = text
            return
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scritto-${counter++}")
    }

    fun stop() {
        queued = null
        tts.stop()
        onSpeaking(false)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
