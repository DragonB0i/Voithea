
package com.dragonboi.voithea

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class VoiScheduler(
    private val scope: CoroutineScope,
    private val announcements: VoiAnnouncementManager
) {

    private data class ActiveTimer(
        val id: String,
        val label: String,
        val dueElapsedRealtime: Long
    )

    private data class PersonalReminder(
        val id: String,
        val text: String,
        val dueEpochMillis: Long
    )

    private val lock = Any()

    private val timers = mutableMapOf<String, ActiveTimer>()
    private val reminders = mutableMapOf<String, PersonalReminder>()

    private var hourlyEnabled = false
    private var nextHourlyEpochMillis: Long? = null
    private var started = false
    private var closed = false

    private var ticker: Job? = null

    private val zone: ZoneId
        get() = ZoneId.systemDefault()

    // =====================================
    // SESSION
    // =====================================

    fun start() {

        synchronized(lock) {
            if (closed || started) return
            started = true
        }

        ticker = scope.launch {
            runTicker()
        }
    }

    fun close() {

        synchronized(lock) {
            if (closed) return

            closed = true
            started = false
            hourlyEnabled = false
            nextHourlyEpochMillis = null

            timers.clear()
            reminders.clear()
        }

        ticker?.cancel()
        ticker = null
    }

    // =====================================
    // TIMERS
    // =====================================

    fun createTimer(
        durationMinutes: Long,
        label: String = "timer"
    ): String {

        require(durationMinutes in 1L..1440L) {
            "Timer duration must be between 1 and 1440 minutes."
        }

        val id = UUID.randomUUID().toString()

        val durationMillis = durationMinutes * 60_000L

        val timer = ActiveTimer(
            id = id,
            label = label.ifBlank { "timer" },
            dueElapsedRealtime =
                SystemClock.elapsedRealtime() + durationMillis
        )

        synchronized(lock) {
            check(!closed) { "Voi scheduler is closed." }
            timers[id] = timer
        }

        return id
    }

    fun cancelTimer(id: String): Boolean {
        return synchronized(lock) {
            timers.remove(id) != null
        }
    }

    // =====================================
    // PERSONAL REMINDERS
    // =====================================

    fun createReminderIn(
        minutes: Long,
        text: String
    ): String {

        require(minutes in 1L..525600L) {
            "Reminder must be between 1 minute and 365 days."
        }

        require(text.isNotBlank()) {
            "Reminder text cannot be empty."
        }

        val id = UUID.randomUUID().toString()

        val reminder = PersonalReminder(
            id = id,
            text = text.trim(),
            dueEpochMillis =
                System.currentTimeMillis() + minutes * 60_000L
        )

        synchronized(lock) {
            check(!closed) { "Voi scheduler is closed." }
            reminders[id] = reminder
        }

        return id
    }

    /**
     * Expected format: yyyy-MM-dd'T'HH:mm
     * Example: 2026-11-25T16:00
     */
    fun createReminderAt(
        localDateTime: String,
        text: String
    ): String {

        require(text.isNotBlank()) {
            "Reminder text cannot be empty."
        }

        val parsed = LocalDateTime.parse(
            localDateTime,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )

        val dueMillis = parsed
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        require(dueMillis > System.currentTimeMillis()) {
            "That reminder time has already passed. Choose a future time."
        }

        val id = UUID.randomUUID().toString()

        val reminder = PersonalReminder(
            id = id,
            text = text.trim(),
            dueEpochMillis = dueMillis
        )

        synchronized(lock) {
            check(!closed) { "Voi scheduler is closed." }
            reminders[id] = reminder
        }

        return id
    }

    fun cancelReminder(id: String): Boolean {
        return synchronized(lock) {
            reminders.remove(id) != null
        }
    }

    // =====================================
    // HOURLY ANNOUNCEMENTS
    // =====================================

    fun setHourlyReminders(enabled: Boolean) {

        synchronized(lock) {

            check(!closed) {
                "Voi scheduler is closed."
            }

            hourlyEnabled = enabled

            nextHourlyEpochMillis = if (enabled) {
                nextHourBoundary()
            } else {
                null
            }
        }
    }

    fun isHourlyEnabled(): Boolean {
        return synchronized(lock) {
            hourlyEnabled
        }
    }

    private fun nextHourBoundary(): Long {

        val now = LocalDateTime.now(zone)

        val nextHour = now
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(1)

        return nextHour
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    // =====================================
    // SCHEDULER LOOP
    // =====================================

    private suspend fun runTicker() {

        while (scope.isActive) {

            try {

                delay(250L)

                val nowEpoch = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtime()

                val dueTimers = mutableListOf<ActiveTimer>()
                val dueReminders = mutableListOf<PersonalReminder>()
                var hourlyDue = false

                synchronized(lock) {

                    if (closed) return

                    val timerIterator =
                        timers.values.iterator()

                    while (timerIterator.hasNext()) {

                        val timer = timerIterator.next()

                        if (nowElapsed >= timer.dueElapsedRealtime) {
                            dueTimers.add(timer)
                            timerIterator.remove()
                        }
                    }

                    val reminderIterator =
                        reminders.values.iterator()

                    while (reminderIterator.hasNext()) {

                        val reminder = reminderIterator.next()

                        if (nowEpoch >= reminder.dueEpochMillis) {
                            dueReminders.add(reminder)
                            reminderIterator.remove()
                        }
                    }

                    if (
                        hourlyEnabled &&
                        nextHourlyEpochMillis != null &&
                        nowEpoch >= nextHourlyEpochMillis!!
                    ) {

                        hourlyDue = true

                        // Move to the next future hour. This avoids
                        // repeated announcements after a clock change.
                        nextHourlyEpochMillis = nextHourBoundary()
                    }
                }

                // All due personal reminders enter the queue first.
                dueReminders.forEach { reminder ->

                    announcements.enqueue(
                        text = "It's time to ${reminder.text}.",
                        priority =
                            VoiAnnouncementPriority.PERSONAL_REMINDER
                    )
                }

                // Timers are also personal alerts.
                dueTimers.forEach { timer ->

                    val message =
                        if (timer.label.equals("timer", true)) {
                            "Your timer is up!"
                        } else {
                            "Your ${timer.label} timer is up!"
                        }

                    announcements.enqueue(
                        text = message,
                        priority =
                            VoiAnnouncementPriority.PERSONAL_REMINDER
                    )
                }

                if (hourlyDue) {

                    val currentTime = LocalDateTime.now(zone)

                    val spokenTime = currentTime.format(
                        DateTimeFormatter.ofPattern("h:mm a")
                    )

                    announcements.enqueue(
                        text = "The time is $spokenTime.",
                        priority = VoiAnnouncementPriority.HOURLY
                    )
                }

            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // An individual scheduler error must not kill the loop.
                delay(500L)
            }
        }
    }

    // =====================================
    // STATUS
    // =====================================

    fun status(): JSONObject {

        synchronized(lock) {

            val timerArray = org.json.JSONArray()

            timers.values.forEach {
                timerArray.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("label", it.label)
                )
            }

            val reminderArray = org.json.JSONArray()

            reminders.values.forEach {
                reminderArray.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("text", it.text)
                )
            }

            return JSONObject()
                .put("hourlyEnabled", hourlyEnabled)
                .put("timers", timerArray)
                .put("reminders", reminderArray)
        }
    }
}
