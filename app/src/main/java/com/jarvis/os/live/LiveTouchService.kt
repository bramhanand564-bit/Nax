package com.jarvis.os.live

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.os.data.PrivacyShield

data class LiveUiNode(
    val index: Int,
    val label: String,
    val centerX: Float,
    val centerY: Float,
    val clickable: Boolean
)

class LiveTouchService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LiveTouchService? = null
            private set

        @Volatile
        var currentPackage: String = ""
            private set

        @Volatile
        var stopRequested: Boolean = false

        fun isConnected(): Boolean = instance != null

        fun emergencyStop() {
            stopRequested = true
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun launchRealAppOrSetting(context: Context, targetName: String): Pair<Boolean, String> {
            val lower = targetName.lowercase().trim()

            // Direct Android System Settings shortcuts
            val settingsIntent: Intent? = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> Intent(Settings.ACTION_WIFI_SETTINGS)
                lower.contains("bluetooth") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                lower.contains("battery") -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                lower.contains("display") || lower.contains("brightness") -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                lower.contains("sound") || lower.contains("volume") -> Intent(Settings.ACTION_SOUND_SETTINGS)
                lower.contains("accessibility") -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                lower == "settings" || lower.contains("setting") -> Intent(Settings.ACTION_SETTINGS)
                else -> null
            }

            if (settingsIntent != null) {
                return try {
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                    true to "Opened $targetName in Live Mode"
                } catch (e: Exception) {
                    false to "Settings open nahi ho paya: ${e.message}"
                }
            }

            // Well-known app packages
            val knownPackages = mapOf(
                "youtube" to "com.google.android.youtube",
                "instagram" to "com.instagram.android",
                "whatsapp" to "com.whatsapp",
                "chrome" to "com.android.chrome",
                "telegram" to "org.telegram.messenger",
                "gmail" to "com.google.android.gm",
                "maps" to "com.google.android.apps.maps",
                "camera" to "com.android.camera",
                "play store" to "com.android.vending"
            )

            val pm = context.packageManager
            val matchedPkg = knownPackages.entries.firstOrNull { lower.contains(it.key) }?.value

            val finalPkg = matchedPkg ?: run {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = pm.queryIntentActivities(mainIntent, 0)
                apps.firstOrNull {
                    it.loadLabel(pm).toString().lowercase().contains(lower)
                }?.activityInfo?.packageName
            }

            if (finalPkg == null) {
                return false to "Real phone par '$targetName' app nahi mila."
            }

            if (PrivacyShield.isProtectedApp(finalPkg)) {
                return false to "🛡️ Privacy Shield Active: Banking/UPI/Password app ko NAX Live Mode touch nahi karega."
            }

            return try {
                val launch = pm.getLaunchIntentForPackage(finalPkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    true to "Live Mode mein $targetName launch kar diya!"
                } else {
                    false to "$targetName launch nahi ho saka."
                }
            } catch (e: Exception) {
                false to "Launch error: ${e.message}"
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        stopRequested = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            currentPackage = pkg
        }
    }

    override fun onInterrupt() {
        stopRequested = true
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun scanLiveScreenElements(): Pair<String, List<LiveUiNode>> {
        if (PrivacyShield.isProtectedApp(currentPackage)) {
            return "🛡️ Privacy Shield Active ($currentPackage protected)" to emptyList()
        }
        val root = rootInActiveWindow ?: return "Screen root unavailable" to emptyList()
        val nodes = mutableListOf<LiveUiNode>()
        collectNodes(root, nodes)
        return "Scanned ${nodes.size} elements on $currentPackage" to nodes
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, out: MutableList<LiveUiNode>) {
        if (node == null || out.size >= 40) return
        val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
        if (text.isNotBlank() && !PrivacyShield.isSensitiveText(text) && (node.isClickable || node.isEditable)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 16 && rect.height() > 16) {
                out.add(
                    LiveUiNode(
                        index = out.size,
                        label = text.take(45),
                        centerX = rect.exactCenterX(),
                        centerY = rect.exactCenterY(),
                        clickable = node.isClickable
                    )
                )
            }
        }
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), out)
        }
    }

    fun clickElementByText(query: String): Boolean {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query) ?: return false
        for (node in matches) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                target = target.parent
            }
            if (target != null && target.isClickable) {
                return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        return false
    }

    fun performHumanTap(x: Float, y: Float, onDone: (Boolean) -> Unit = {}) {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) {
            onDone(false)
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 95L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onDone(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                onDone(false)
            }
        }, null)
    }

    fun performSwipeUp(onDone: (Boolean) -> Unit = {}) {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) {
            onDone(false)
            return
        }
        val dm = resources.displayMetrics
        val startX = dm.widthPixels * 0.5f
        val startY = dm.heightPixels * 0.72f
        val endY = dm.heightPixels * 0.28f
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
        }, null)
    }

    fun pressBackAction(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHomeAction(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
}
