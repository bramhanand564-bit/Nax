package com.jarvis.os.live

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.os.data.PrivacyShield

data class LiveUiNode(
    val index: Int,
    val label: String,
    val viewId: String,
    val centerX: Float,
    val centerY: Float,
    val clickable: Boolean,
    val editable: Boolean
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

        private var lastShieldPackage: String = ""

        fun isConnected(): Boolean = instance != null

        fun emergencyStop() {
            stopRequested = true
        }

        fun resetStopFlag() {
            stopRequested = false
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun launchRealAppOrSetting(context: Context, targetName: String): Pair<Boolean, String> {
            val lower = targetName.lowercase().trim()

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
                    NaxLiveLogger.log("⚙️", "Opened $targetName in Live Mode")
                    true to "Opened $targetName in Live Mode"
                } catch (e: Exception) {
                    false to "Settings open nahi ho paya: ${e.message}"
                }
            }

            val knownPackages = mapOf(
                "instagram" to "com.instagram.android",
                "insta" to "com.instagram.android",
                "youtube" to "com.google.android.youtube",
                "whatsapp" to "com.whatsapp",
                "chrome" to "com.android.chrome",
                "telegram" to "org.telegram.messenger",
                "gmail" to "com.google.android.gm",
                "maps" to "com.google.android.apps.maps",
                "camera" to "com.android.camera",
                "spotify" to "com.spotify.music",
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
                FloatingNaxOverlay.peekStatus(
                    context,
                    NaxMood.SHIELD,
                    "Privacy Shield Active 🛡️",
                    "Banking/UPI app protected — NAX paused."
                )
                return false to "🛡️ Privacy Shield Active: Banking/UPI/Password app ko NAX Live Mode touch nahi karega."
            }

            return try {
                val launch = pm.getLaunchIntentForPackage(finalPkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    NaxLiveLogger.log("📱", "Launched real app: $targetName ($finalPkg)")
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
        NaxLiveLogger.log("🦾", "NAX Live Accessibility Body Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            currentPackage = pkg
            if (PrivacyShield.isProtectedApp(pkg) && lastShieldPackage != pkg) {
                lastShieldPackage = pkg
                stopRequested = true
                FloatingNaxOverlay.peekStatus(
                    this,
                    NaxMood.SHIELD,
                    "Privacy Shield ON 🛡️",
                    "UPI/Banking app detected. NAX Live eyes & hands paused.",
                    4000L
                )
                NaxLiveLogger.log("🛡️", "Auto-Shield paused NAX on protected app")
            } else if (!PrivacyShield.isProtectedApp(pkg)) {
                lastShieldPackage = ""
            }
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

    fun getScreenSummaryForBrain(): String {
        val (status, nodes) = scanLiveScreenElements()
        if (nodes.isEmpty()) return status
        val preview = nodes.take(16).joinToString(", ") { "[${it.index}] ${it.label}" }
        return "$status | Visible: $preview"
    }

    private fun collectNodes(node: AccessibilityNodeInfo?, out: MutableList<LiveUiNode>) {
        if (node == null || out.size >= 50) return
        val text = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
        val viewId = node.viewIdResourceName.orEmpty().substringAfterLast("/")
        if (!PrivacyShield.isSensitiveText(text) && (node.isClickable || node.isEditable)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 16 && rect.height() > 16) {
                val label = text.ifBlank { viewId.ifBlank { node.className?.toString()?.substringAfterLast(".") ?: "Item" } }
                out.add(
                    LiveUiNode(
                        index = out.size,
                        label = label.take(45),
                        viewId = viewId,
                        centerX = rect.exactCenterX(),
                        centerY = rect.exactCenterY(),
                        clickable = node.isClickable,
                        editable = node.isEditable
                    )
                )
            }
        }
        for (i in 0 until node.childCount) {
            collectNodes(node.getChild(i), out)
        }
    }

    fun findAndClickByHints(hints: List<String>): Boolean {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) return false
        val root = rootInActiveWindow ?: return false
        val lowerHints = hints.map { it.lowercase() }

        val matchNode = findNodeRecursive(root) { n ->
            val t = n.text?.toString()?.lowercase().orEmpty()
            val d = n.contentDescription?.toString()?.lowercase().orEmpty()
            val id = n.viewIdResourceName?.lowercase().orEmpty()
            lowerHints.any { h -> t.contains(h) || d.contains(h) || id.contains(h) }
        } ?: return false

        var clickableTarget: AccessibilityNodeInfo? = matchNode
        while (clickableTarget != null && !clickableTarget.isClickable) {
            clickableTarget = clickableTarget.parent
        }
        if (clickableTarget != null && clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            NaxLiveLogger.log("👆", "Clicked UI element matching ${hints.firstOrNull()}")
            return true
        }

        val rect = Rect()
        matchNode.getBoundsInScreen(rect)
        if (rect.width() > 8 && rect.height() > 8) {
            performHumanTap(rect.exactCenterX(), rect.exactCenterY())
            NaxLiveLogger.log("👆", "Physical tap at (${rect.exactCenterX().toInt()}, ${rect.exactCenterY().toInt()})")
            return true
        }
        return false
    }

    fun inputTextIntoSearchOrEditable(query: String): Boolean {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) return false
        val root = rootInActiveWindow ?: return false

        val editable = findNodeRecursive(root) { n ->
            n.isEditable || (n.className?.toString()?.contains("EditText", ignoreCase = true) == true)
        } ?: return false

        editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query)
        }
        var ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!ok) {
            try {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("nax_input", query))
                ok = editable.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (_: Exception) {
            }
        }

        if (ok) {
            NaxLiveLogger.log("⌨️", "Typed query in Live App: \"$query\"")
            // Try IME Enter/Search button on newer Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                editable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
        }
        return ok
    }

    fun tapCenterAreaForFirstMediaResult(): Boolean {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) return false
        val dm = resources.displayMetrics
        // Tap first major content card/video below search header (~36% height, left-center)
        val x = dm.widthPixels * 0.28f
        val y = dm.heightPixels * 0.36f
        performHumanTap(x, y)
        NaxLiveLogger.log("▶️", "Physical tap on 1st media result (${x.toInt()}, ${y.toInt()})")
        return true
    }

    fun tapBottomRightKeyboardSearch(): Boolean {
        if (stopRequested || PrivacyShield.isProtectedApp(currentPackage)) return false
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.91f
        val y = dm.heightPixels * 0.93f
        performHumanTap(x, y)
        NaxLiveLogger.log("🔍", "Tapped keyboard Search/Enter key (${x.toInt()}, ${y.toInt()})")
        return true
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val found = findNodeRecursive(node.getChild(i), predicate)
            if (found != null) return found
        }
        return null
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
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
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

    fun takeScreenshotAction(): Pair<Boolean, String> {
        if (PrivacyShield.isProtectedApp(currentPackage)) {
            return false to "🛡️ Privacy Shield Active: Protected app par screenshot blocked hai."
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ok = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            if (ok) {
                NaxLiveLogger.log("📸", "Real phone screenshot captured")
                true to "📸 Done Boss! Real phone ka screenshot capture kar liya hai (Gallery mein save ho gaya)!"
            } else {
                false to "Screenshot capture nahi ho saka."
            }
        } else {
            false to "Screenshot ke liye Android 9+ chahiye."
        }
    }

    fun pressBackAction(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHomeAction(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
}
