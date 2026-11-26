package com.jarvis.os.routines

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jarvis.os.R
import com.jarvis.os.data.ExecutionMode
import com.jarvis.os.data.RoutineFlow
import com.jarvis.os.data.RoutineStep
import com.jarvis.os.data.VaultStorage
import java.util.Calendar
import java.util.concurrent.TimeUnit

object RoutineManager {

    private const val CHANNEL_ID = "nax_routines_channel"

    fun parseFlowFromChat(message: String, defaultMode: ExecutionMode): RoutineFlow {
        val triggerTime = extractTriggerTime(message)
        val frequency = detectFrequency(message)

        // Split multi-step Hinglish/English instructions into clean n8n-style nodes
        val rawParts = message
            .split(Regex("""\b(aur|phir|fir|then|and then)\b|,""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.length > 4 }

        val steps = if (rawParts.isEmpty()) {
            listOf(
                RoutineStep(
                    mode = defaultMode,
                    label = "Execute Command",
                    command = message.trim()
                )
            )
        } else {
            rawParts.take(4).mapIndexed { index, part ->
                val lower = part.lowercase()
                val stepMode = when {
                    lower.contains("live") || lower.contains("real phone") -> ExecutionMode.LIVE
                    lower.contains("sandbox") -> ExecutionMode.SANDBOX
                    else -> defaultMode
                }
                RoutineStep(
                    mode = stepMode,
                    label = "Step ${index + 1} (${stepMode.name})",
                    command = part
                )
            }
        }

        val cleanTitle = buildRoutineTitle(message, triggerTime)
        return RoutineFlow(
            title = cleanTitle,
            triggerTime = triggerTime,
            frequency = frequency,
            steps = steps,
            enabled = true,
            lastRunStatus = "Daily Routine • Auto-Saved"
        )
    }

    fun scheduleRoutine(context: Context, flow: RoutineFlow) {
        if (!flow.enabled) {
            cancelRoutine(context, flow.id)
            return
        }

        val delayMillis = computeInitialDelayMillis(flow.triggerTime)
        val repeatHours = when (flow.frequency.lowercase()) {
            "hourly" -> 1L
            "weekly" -> 168L
            else -> 24L
        }

        val input = workDataOf(
            "routine_id" to flow.id,
            "routine_title" to flow.title,
            "routine_command" to (flow.steps.joinToString(" → ") { it.command })
        )

        val request = PeriodicWorkRequestBuilder<NaxRoutineWorker>(repeatHours, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "nax_routine_${flow.id}",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelRoutine(context: Context, flowId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("nax_routine_$flowId")
    }

    private fun extractTriggerTime(text: String): String {
        val lower = text.lowercase()
        val amPmMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)""").find(lower)
        if (amPmMatch != null) {
            val h = amPmMatch.groupValues[1]
            val m = amPmMatch.groupValues[2].ifBlank { "00" }
            val meridiem = amPmMatch.groupValues[3].uppercase()
            return "$h:$m $meridiem"
        }

        val bajeMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*baje""").find(lower)
        if (bajeMatch != null) {
            val h = bajeMatch.groupValues[1]
            val m = bajeMatch.groupValues[2].ifBlank { "00" }
            val meridiem = when {
                lower.contains("shaam") || lower.contains("sham") || lower.contains("raat") || lower.contains("dopahar") -> "PM"
                lower.contains("subah") || lower.contains("morning") -> "AM"
                else -> if (h.toIntOrNull() in 1..7) "PM" else "AM"
            }
            return "$h:$m $meridiem"
        }

        return when {
            lower.contains("subah") || lower.contains("morning") -> "09:00 AM"
            lower.contains("shaam") || lower.contains("evening") -> "06:00 PM"
            lower.contains("raat") || lower.contains("night") -> "10:00 PM"
            else -> "09:00 AM"
        }
    }

    private fun detectFrequency(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("hour") || lower.contains("har ghante") -> "Hourly"
            lower.contains("week") || lower.contains("har hafte") -> "Weekly"
            else -> "Daily"
        }
    }

    private fun buildRoutineTitle(message: String, triggerTime: String): String {
        val trimmed = message
            .replace(Regex("""\b(roz|roj|daily|every day|har roz|subah|shaam|raat|baje|\d{1,2}(:\d{2})?\s*(am|pm)?)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val core = if (trimmed.length >= 6) trimmed.take(36) else "Automated NAX Flow"
        return "${core.replaceFirstChar { it.uppercase() }} ($triggerTime)"
    }

    private fun computeInitialDelayMillis(triggerTime: String): Long {
        val match = Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""", RegexOption.IGNORE_CASE).find(triggerTime)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        if (match != null) {
            var hour = match.groupValues[1].toIntOrNull() ?: 9
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[3].uppercase()
            if (amPm == "PM" && hour < 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            target.set(Calendar.HOUR_OF_DAY, hour)
            target.set(Calendar.MINUTE, minute)
            target.set(Calendar.SECOND, 0)
            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
        }
        return TimeUnit.MINUTES.toMillis(15)
    }

    internal fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NAX Daily Routines",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when NAX runs your scheduled n8n-style daily routines."
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    internal fun channelId(): String = CHANNEL_ID
}

class NaxRoutineWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val routineId = inputData.getString("routine_id").orEmpty()
        val title = inputData.getString("routine_title") ?: "NAX Routine"
        val command = inputData.getString("routine_command") ?: "Scheduled task ready"

        // Update routine status in VaultStorage
        val vault = VaultStorage(applicationContext)
        val updated = vault.loadRoutines().map { flow ->
            if (flow.id == routineId) {
                flow.copy(lastRunStatus = "Last triggered automatically ✓")
            } else {
                flow
            }
        }
        vault.saveRoutines(updated)

        // Post local notification if permission granted
        RoutineManager.ensureNotificationChannel(applicationContext)
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            val notif = NotificationCompat.Builder(applicationContext, RoutineManager.channelId())
                .setSmallIcon(R.drawable.ic_jarvis_logo)
                .setContentTitle("NAX Routine: $title")
                .setContentText(command.take(90))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify(routineId.hashCode(), notif)
        }

        return Result.success()
    }
}
