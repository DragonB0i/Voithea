
package com.dragonboi.voithea

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VoiVoiceManager(
    context: Context,
    private val groqTtsService: GroqTtsService
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = null
    private var activeMediaCleanup: (() -> Unit)? = null

    private val ttsReady = CompletableDeferred<Boolean>()

    init {
        textToSpeech = TextToSpeech(appContext) { status ->

            val tts = textToSpeech

            if (status == TextToSpeech.SUCCESS && tts != null) {

                var languageResult = tts.setLanguage(Locale("en", "IN"))

                if (
                    languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    languageResult = tts.setLanguage(Locale.US)
                }

                tts.setSpeechRate(0.95f)
                tts.setPitch(1.05f)

                ttsReady.complete(
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                )

            } else {
                ttsReady.complete(false)
            }
        }
    }

    suspend fun speak(
        apiKey: String,
        text: String,
        onSpeakingChanged: (Boolean) -> Unit
    ): Result<Unit> {

        if (text.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Nothing to speak.")
            )
        }

        return try {

            try {
                val chunks = GroqTtsService.splitIntoChunks(text)
                val audioChunks = mutableListOf<ByteArray>()

                for (chunk in chunks) {
                    audioChunks.add(
                        groqTtsService.generateSpeech(apiKey, chunk).getOrThrow()
                    )
                }

                onSpeakingChanged(true)

                for (audio in audioChunks) {
                    playWav(audio)
                }

                Log.d("VoiVoice", "Groq TTS playback completed.")

            } catch (exception: CancellationException) {
                throw exception

            } catch (groqException: Exception) {

                Log.w(
                    "VoiVoice",
                    "Groq TTS failed. Switching to Android TTS.",
                    groqException
                )

                onSpeakingChanged(true)
                speakWithAndroidTts(text)
            }

            Result.success(Unit)

        } catch (exception: CancellationException) {
            throw exception

        } catch (exception: Exception) {
            Log.e("VoiVoice", "Speech failed.", exception)
            Result.failure(exception)

        } finally {
            onSpeakingChanged(false)
        }
    }

    private suspend fun playWav(audioBytes: ByteArray) {
        withContext(Dispatchers.Main.immediate) {

            suspendCancellableCoroutine<Unit> { continuation ->

                val audioFile = File.createTempFile(
                    "voi_audio_",
                    ".wav",
                    appContext.cacheDir
                )

                val player = MediaPlayer()
                val released = AtomicBoolean(false)

                fun cleanup() {
                    if (released.compareAndSet(false, true)) {

                        runCatching { player.stop() }
                        runCatching { player.reset() }
                        runCatching { player.release() }

                        audioFile.delete()

                        if (mediaPlayer === player) {
                            mediaPlayer = null
                        }

                        if (activeMediaCleanup != null) {
                            activeMediaCleanup = null
                        }
                    }
                }

                activeMediaCleanup = { cleanup() }
                mediaPlayer = player

                continuation.invokeOnCancellation {
                    mainHandler.post { cleanup() }
                }

                try {
                    audioFile.writeBytes(audioBytes)

                    player.setOnPreparedListener {
                        if (continuation.isActive) {
                            it.start()
                        } else {
                            cleanup()
                        }
                    }

                    player.setOnCompletionListener {
                        cleanup()

                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    player.setOnErrorListener { _, what, extra ->
                        cleanup()

                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    "Audio playback failed: $what / $extra"
                                )
                            )
                        }

                        true
                    }

                    player.setDataSource(audioFile.absolutePath)
                    player.prepareAsync()

                } catch (exception: Exception) {
                    cleanup()

                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        }
    }

    private suspend fun speakWithAndroidTts(text: String) {

        val ready = ttsReady.await()

        if (!ready) {
            throw IllegalStateException(
                "Android Text-to-Speech is unavailable."
            )
        }

        val tts = textToSpeech
            ?: throw IllegalStateException("Speech engine unavailable.")

        withContext(Dispatchers.Main.immediate) {

            suspendCancellableCoroutine<Unit> { continuation ->

                val utteranceId = UUID.randomUUID().toString()

                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(id: String?) = Unit

                        override fun onDone(id: String?) {
                            if (
                                id == utteranceId &&
                                continuation.isActive
                            ) {
                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(id: String?) {
                            if (
                                id == utteranceId &&
                                continuation.isActive
                            ) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        "Android speech playback failed."
                                    )
                                )
                            }
                        }

                        override fun onError(
                            id: String?,
                            errorCode: Int
                        ) {
                            if (
                                id == utteranceId &&
                                continuation.isActive
                            ) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        "Android speech error: $errorCode"
                                    )
                                )
                            }
                        }

                        override fun onStop(
                            id: String?,
                            interrupted: Boolean
                        ) {
                            if (
                                id == utteranceId &&
                                continuation.isActive
                            ) {
                                continuation.resume(Unit)
                            }
                        }
                    }
                )

                continuation.invokeOnCancellation {
                    mainHandler.post { tts.stop() }
                }

                val result = tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    utteranceId
                )

                if (
                    result == TextToSpeech.ERROR &&
                    continuation.isActive
                ) {
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Unable to start Android speech."
                        )
                    )
                }
            }
        }
    }

    fun stop() {
        val stopAction = {
            activeMediaCleanup?.invoke()
            runCatching { textToSpeech?.stop() }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopAction()
        } else {
            mainHandler.post { stopAction() }
        }
    }

    fun release() {
        stop()

        runCatching {
            textToSpeech?.shutdown()
        }

        textToSpeech = null
    }
}
