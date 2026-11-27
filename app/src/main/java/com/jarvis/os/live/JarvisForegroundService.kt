package com.jarvis.os.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.os.MainActivity
import com.jarvis.os.R
import com.jarvis.os.data.VaultStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class JarvisForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "nax_engine_channel"
        private const val NOTIF_ID = 7001
        private const val ACTION_STOP = "com.jarvis.os.ACTION_STOP_NAX"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lowBatteryAlerted = false
    private var fullBatteryAlerted = false
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ctx = context ?: return
            val prefs = NaxCompanionPrefs.load(ctx)
            if (!prefs.batteryGuardianEnabled) return

            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else return
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            if (!charging && pct in 1..20 && !lowBatteryAlerted) {
                lowBatteryAlerted = true
                val msg = "Boss! Battery sirf $pct% bachi hai. Charger laga lo ya Dim 25% tap karo!"
                NaxLiveLogger.log("🔋", "Low Battery Alert ($pct%)")
                FloatingNaxOverlay.peekStatus(ctx, NaxMood.WARNING, "Low Battery ($pct%)", msg, 7000L)
                NaxVoiceEngine.speak(ctx, msg)
            } else if (pct > 25 || charging) {
                lowBatteryAlerted = false
            }

            if (charging && pct >= 99 && !fullBatteryAlerted) {
                fullBatteryAlerted = true
                val msg = "Boss! Battery $pct% full charge ho gayi hai. Charger nikal sakte ho!"
                NaxLiveLogger.log("⚡", "Full Battery Alert ($pct%)")
                FloatingNaxOverlay.peekStatus(ctx, NaxMood.HAPPY, "Battery Full ($pct%)", msg, 6000L)
                NaxVoiceEngine.speak(ctx, msg)
            } else if (pct < 95) {
                fullBatteryAlerted = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LiveTouchService.emergencyStop()
            FloatingNaxOverlay.hide()
            NaxVoiceEngine.stop()
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        isRunning = true
        NaxVoiceEngine.init(this)
        FloatingNaxOverlay.show(this)
        registerBatteryWatcher()
        startCompanionClockLoop()
        NaxLiveLogger.log("🚀", "NAX 24/7 Companion Engine started")
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        if (receiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        FloatingNaxOverlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerBatteryWatcher() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun startCompanionClockLoop() {
        serviceScope.launch {
            val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            while (isActive) {
                try {
                    val prefs = NaxCompanionPrefs.load(this@JarvisForegroundService)
                    if (prefs.morningNightEnabled) {
                        val cal = Calendar.getInstance()
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val today = dayFmt.format(Date())

                        if (hour in 7..9 && NaxCompanionPrefs.getLastBriefingDate(this@JarvisForegroundService, "last_morning") != today) {
                            NaxCompanionPrefs.setLastBriefingDate(this@JarvisForegroundService, "last_morning", today)
                            val bat = DeviceSensorBridge.getBatteryPercent(this@JarvisForegroundService)
                            val activeRoutines = VaultStorage(this@JarvisForegroundService).loadRoutines().count { it.enabled }
                            val msg = "Good Morning Boss! Battery $bat% hai aur $activeRoutines daily routines active hain. Aaj kya plan hai?"
                            NaxLiveLogger.log("☀️", "Good Morning Briefing delivered")
                            FloatingNaxOverlay.peekStatus(this@JarvisForegroundService, NaxMood.MORNING, "Good Morning Boss ☀️", msg, 8000L)
                            NaxVoiceEngine.speak(this@JarvisForegroundService, msg)
                        }

                        if (hour in 22..23 && NaxCompanionPrefs.getLastBriefingDate(this@JarvisForegroundService, "last_night") != today) {
                            NaxCompanionPrefs.setLastBriefingDate(this@JarvisForegroundService, "last_night", today)
                            val tel = VaultStorage(this@JarvisForegroundService).loadTelemetry()
                            val bat = DeviceSensorBridge.getBatteryPercent(this@JarvisForegroundService)
                            val msg = "Good Night Boss! Aaj NAX ne ${tel.totalCalls} AI calls kiye. Battery $bat% hai — araam karo!"
                            NaxLiveLogger.log("🌙", "Good Night Recap delivered")
                            FloatingNaxOverlay.peekStatus(this@JarvisForegroundService, NaxMood.NIGHT, "Good Night Boss 🌙", msg, 8000L)
                            NaxVoiceEngine.speak(this@JarvisForegroundService, msg)
                        }
                    }
                } catch (_: Exception) {
                }
                delay(60_000L)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "NAX 24/7 Companion Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps NAX Floating Orb, Good Morning/Night briefings & Live Control ready."
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, JarvisForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis_logo)
            .setContentTitle("NAX 24/7 Companion Active")
            .setContentText("Floating Orb • Battery Guardian • Privacy Shield ON")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopIntent)
            .build()
    }
}
