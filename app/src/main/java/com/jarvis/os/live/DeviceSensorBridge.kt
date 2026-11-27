package com.jarvis.os.live

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import com.jarvis.os.data.DeviceVideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveApiMetrics(
    val callsLastMinute: Int = 0,
    val sessionOk: Int = 0,
    val session429: Int = 0,
    val avgLatencyMs: Long = 0L
)

data class CompanionSettings(
    val floatingOrbEnabled: Boolean = true,
    val stealthMode: Boolean = false,
    val edgeAlphaPercent: Int = 75,
    val morningNightEnabled: Boolean = true,
    val batteryGuardianEnabled: Boolean = true
)

object NaxCompanionPrefs {
    private const val PREF_NAME = "nax_companion_v2"

    fun load(context: Context): CompanionSettings {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return CompanionSettings(
            floatingOrbEnabled = p.getBoolean("floating_orb", true),
            stealthMode = p.getBoolean("stealth_mode", false),
            edgeAlphaPercent = p.getInt("edge_alpha", 75),
            morningNightEnabled = p.getBoolean("morning_night", true),
            batteryGuardianEnabled = p.getBoolean("battery_guard", true)
        )
    }

    fun save(context: Context, s: CompanionSettings) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("floating_orb", s.floatingOrbEnabled)
            .putBoolean("stealth_mode", s.stealthMode)
            .putInt("edge_alpha", s.edgeAlphaPercent.coerceIn(10, 100))
            .putBoolean("morning_night", s.morningNightEnabled)
            .putBoolean("battery_guard", s.batteryGuardianEnabled)
            .apply()
    }

    fun getLastBriefingDate(context: Context, key: String): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(key, "") ?: ""

    fun setLastBriefingDate(context: Context, key: String, dateStr: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(key, dateStr)
            .apply()
    }
}

object NaxLiveLogger {

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _metrics = MutableStateFlow(LiveApiMetrics())
    val metrics: StateFlow<LiveApiMetrics> = _metrics.asStateFlow()

    private val recentTimestamps = mutableListOf<Long>()
    private var totalLatencySum = 0L
    private var totalCallsCount = 0

    fun log(emoji: String, message: String) {
        val ts = timeFmt.format(Date())
        val line = "[$ts] $emoji $message"
        _logs.value = (listOf(line) + _logs.value).take(200)
    }

    fun recordApiCall(provider: String, latencyMs: Long, ok: Boolean, is429: Boolean, detail: String = "") {
        val now = System.currentTimeMillis()
        synchronized(recentTimestamps) {
            recentTimestamps.add(now)
            recentTimestamps.removeAll { now - it > 60_000L }
            totalCallsCount++
            totalLatencySum += latencyMs
            val prev = _metrics.value
            _metrics.value = LiveApiMetrics(
                callsLastMinute = recentTimestamps.size,
                sessionOk = prev.sessionOk + if (ok) 1 else 0,
                session429 = prev.session429 + if (is429) 1 else 0,
                avgLatencyMs = if (totalCallsCount > 0) totalLatencySum / totalCallsCount else 0L
            )
        }
        val icon = if (ok) "✅" else if (is429) "⚠️" else "❌"
        val extra = if (detail.isNotBlank()) " • $detail" else ""
        log(icon, "API $provider (${latencyMs}ms)$extra")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun resetMetrics() {
        synchronized(recentTimestamps) {
            recentTimestamps.clear()
            totalLatencySum = 0L
            totalCallsCount = 0
            _metrics.value = LiveApiMetrics()
        }
    }

    fun exportText(): String = _logs.value.joinToString("\n")
}

object DeviceSensorBridge {

    fun getBatteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun getRealBatteryReport(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Discharging"
        }

        val tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Weak"
            else -> "Normal"
        }
        return "$pct% (${if (charging) "Charging via $source" else "Not Charging"}, Temp: ${tempC}°C, Health: $health)"
    }

    fun getBrightnessPercent(context: Context): Int {
        return try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            (raw.coerceIn(0, 255) * 100) / 255
        } catch (_: Exception) {
            -1
        }
    }

    fun getMediaVolumePercent(context: Context): Int {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            (cur * 100) / max
        } catch (_: Exception) {
            -1
        }
    }

    fun getNetworkType(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Offline"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data (4G/5G)"
                else -> "Connected"
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun getRamReport(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            "${info.availMem / (1024 * 1024)}MB free / ${info.totalMem / (1024 * 1024)}MB total"
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    fun scanDeviceVideos(context: Context, limit: Int = 8): List<DeviceVideoItem> {
        val result = mutableListOf<DeviceVideoItem>()
        try {
            val projection = arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext() && result.size < limit) {
                    val name = cursor.getString(nameCol) ?: "Video.mp4"
                    val path = cursor.getString(pathCol) ?: ""
                    val bytes = cursor.getLong(sizeCol)
                    val mb = String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
                    result.add(DeviceVideoItem(name = name, path = path, sizeMb = mb))
                }
            }
        } catch (e: Exception) {
            NaxLiveLogger.log("⚠️", "Video folder scan needs Media permission: ${e.message}")
        }
        return result
    }

    fun buildLiveDeviceSnapshot(context: Context): String {
        val videoCount = scanDeviceVideos(context, 5).size
        return """
REAL-TIME ANDROID DEVICE SENSORS (100% EXACT LIVE HARDWARE DATA — NEVER GUESS OR INVENT NUMBERS):
- Real Battery: ${getRealBatteryReport(context)}
- Real Screen Brightness: ${getBrightnessPercent(context)}% (Modify Settings Permission: ${if (canModifySystemSettings(context)) "GRANTED" else "NEEDS_PERMISSION"})
- Real Media Volume: ${getMediaVolumePercent(context)}%
- Network: ${getNetworkType(context)}
- RAM: ${getRamReport(context)}
- Recent Videos Found in Phone Folder: $videoCount
- Floating NAX Orb Permission: ${if (canDrawOverlays(context)) "GRANTED" else "NOT_GRANTED"}
- Accessibility Live Control: ${if (LiveTouchService.isConnected()) "CONNECTED" else "NOT_ENABLED"}
- Active Foreground App: ${LiveTouchService.currentPackage.ifBlank { "NAX App" }}
        """.trimIndent()
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun canModifySystemSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openWriteSettingsPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestUnstoppableBatteryMode(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun setRealBrightness(context: Context, targetPercent: Int): Pair<Boolean, String> {
        if (!Settings.System.canWrite(context)) {
            openWriteSettingsPermission(context)
            NaxLiveLogger.log("⚙️", "WRITE_SETTINGS permission needed for brightness")
            return false to "Direct brightness ke liye 'Modify system settings' ON kar do (abhi settings khol diya hai) — phir 1 second mein brightness adjust ho jayegi!"
        }
        return try {
            val clamped = targetPercent.coerceIn(5, 100)
            val val255 = ((clamped * 255) / 100).coerceIn(10, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, val255)
            NaxLiveLogger.log("☀️", "Screen brightness set to $clamped% ($val255/255)")
            true to "Done Boss! Real phone ki brightness seedha **$clamped%** kar di hai! ☀️"
        } catch (e: Exception) {
            NaxLiveLogger.log("❌", "Brightness error: ${e.message}")
            false to "Brightness change error: ${e.message}"
        }
    }

    fun setRealMediaVolume(context: Context, targetPercent: Int): Pair<Boolean, String> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val clamped = targetPercent.coerceIn(0, 100)
            val idx = ((clamped * max) / 100).coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, idx, AudioManager.FLAG_SHOW_UI)
            NaxLiveLogger.log("🔊", "Media volume set to $clamped% ($idx/$max)")
            true to "Done Boss! Media volume **$clamped%** kar diya hai! 🔊"
        } catch (e: Exception) {
            NaxLiveLogger.log("❌", "Volume error: ${e.message}")
            false to "Volume error: ${e.message}"
        }
    }
}
