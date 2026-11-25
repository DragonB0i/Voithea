
package com.dragonboi.voithea

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognitionManager(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun startListening(
        onReady: () -> Unit,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("No speech recognition service is available.")
            return
        }

        try {
            recognizer?.destroy()

            // Use the default recognition service instead
            // of forcing the on-device recognition model.
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        onReady()
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        onError(errorMessage(error))
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val text = matches?.firstOrNull().orEmpty()

                        if (text.isNotBlank()) {
                            onFinalResult(text)
                        } else {
                            onError("I couldn't understand that. Try again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        matches?.firstOrNull()?.let(onPartialResult)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )

                putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    false
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putStringArrayListExtra(
                        RecognizerIntent.EXTRA_BIASING_STRINGS,
                        arrayListOf(
                            "Voi",
                            "Voithea",
                            "Hello Voi",
                            "Hey Voi",
                            "Hi Voi"
                        )
                    )
                }
            }

            recognizer?.startListening(intent)

        } catch (exception: Exception) {
            onError(
                exception.message ?: "Unable to start speech recognition."
            )
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
            SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timed out."
            SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't understand that. Try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
            SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
            else -> "Speech recognition failed. Error: $error"
        }
    }
}
