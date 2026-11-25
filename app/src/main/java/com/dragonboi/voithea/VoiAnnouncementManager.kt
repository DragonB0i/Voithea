
package com.dragonboi.voithea

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.PriorityQueue
import java.util.UUID

enum class VoiAnnouncementPriority(val order: Int) {
    PERSONAL_REMINDER(0),
    EMAIL(1),
    HOURLY(2),
    CONVERSATION(3)
}

data class VoiAnnouncement(
    val id: String,
    val text: String,
    val priority: VoiAnnouncementPriority,
    val sequence: Long
)

class VoiAnnouncementManager(
    private val scope: CoroutineScope,
    private val voiceManager: VoiVoiceManager,
    private val apiKeyProvider: () -> String?,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
    private val onAnnouncementChanged: (String) -> Unit = {}
) {

    private val lock = Any()

    private var sequence = 0L
    private var closed = false

    private val queue = PriorityQueue<VoiAnnouncement>(
        compareBy<VoiAnnouncement> { it.priority.order }
            .thenBy { it.sequence }
    )

    private val wakeups = Channel<Unit>(Channel.UNLIMITED)

    private val worker: Job = scope.launch {
        runWorker()
    }

    fun enqueue(
        text: String,
        priority: VoiAnnouncementPriority
    ): String {

        require(text.isNotBlank()) {
            "Announcement text cannot be empty."
        }

        val announcement = synchronized(lock) {

            check(!closed) {
                "Announcement manager is closed."
            }

            sequence++

            VoiAnnouncement(
                id = UUID.randomUUID().toString(),
                text = text.trim(),
                priority = priority,
                sequence = sequence
            ).also {
                queue.add(it)
            }
        }

        wakeups.trySend(Unit)

        return announcement.id
    }

    fun stopCurrentSpeech() {
        voiceManager.stop()
    }

    fun pendingCount(): Int {
        return synchronized(lock) {
            queue.size
        }
    }

    private suspend fun runWorker() {

        while (scope.isActive) {

            try {
                wakeups.receive()

                // Collect events that arrive almost simultaneously.
                delay(750L)

                while (scope.isActive) {

                    val next = synchronized(lock) {
                        if (closed) null else queue.poll()
                    } ?: break

                    val apiKey = apiKeyProvider()

                    if (apiKey.isNullOrBlank()) {
                        // Do not discard the announcement if setup is
                        // temporarily unavailable. Put it back.
                        synchronized(lock) {
                            if (!closed) {
                                queue.add(next)
                            }
                        }

                        delay(2_000L)
                        wakeups.trySend(Unit)
                        break
                    }

                    onAnnouncementChanged(next.text)

                    try {

                        val result = voiceManager.speak(
                            apiKey = apiKey,
                            text = next.text,
                            onSpeakingChanged = onSpeakingChanged
                        )

                        if (result.isFailure) {
                            onAnnouncementChanged(
                                "Announcement failed: ${
                                    result.exceptionOrNull()?.message
                                        ?: "Voice playback error"
                                }"
                            )
                        }

                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        onAnnouncementChanged(
                            "Announcement failed: ${
                                exception.message ?: "Unknown error"
                            }"
                        )
                    } finally {
                        onSpeakingChanged(false)
                    }

                    // A wakeup received during playback remains in the
                    // channel. New announcements are never discarded.
                }

            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // Keep the queue worker alive after an individual error.
                delay(500L)
            }
        }
    }

    fun close() {

        synchronized(lock) {
            if (closed) return
            closed = true
            queue.clear()
        }

        wakeups.close()
        worker.cancel()
        voiceManager.stop()
        onSpeakingChanged(false)
    }
}
