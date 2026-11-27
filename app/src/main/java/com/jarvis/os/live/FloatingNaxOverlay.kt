package com.jarvis.os.live

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.jarvis.os.MainActivity
import kotlin.math.abs

enum class NaxMood(val emoji: String, val accentHex: String) {
    IDLE("✨", "#7C5CFF"),
    THINKING("🧠", "#38BDF8"),
    WORKING("⚡", "#F59E0B"),
    HAPPY("✅", "#22C55E"),
    WARNING("⚠️", "#FF4D6D"),
    SHIELD("🛡️", "#22C55E"),
    MORNING("☀️", "#F59E0B"),
    NIGHT("🌙", "#818CF8")
}

object FloatingNaxOverlay {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var orbView: TextView? = null
    private var peekPanel: LinearLayout? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null

    private var currentMood: NaxMood = NaxMood.IDLE
    private var lastTitle: String = "NAX Ready"
    private var lastDetail: String = "Auto Sandbox & Live Engine standing by"
    private var isExpanded: Boolean = false
    private var stealthMode: Boolean = false
    private var edgeAlpha: Float = 0.78f

    private var collapseRunnable: Runnable? = null

    fun isShowing(): Boolean = rootView != null

    fun show(context: Context) {
        val appCtx = context.applicationContext
        if (!Settings.canDrawOverlays(appCtx)) return
        val prefs = NaxCompanionPrefs.load(appCtx)
        if (!prefs.floatingOrbEnabled) return
        stealthMode = prefs.stealthMode
        edgeAlpha = prefs.edgeAlphaPercent.coerceIn(10, 100) / 100f

        mainHandler.post {
            if (rootView != null) {
                applyVisualStyle()
                return@post
            }
            try {
                val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm

                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 20
                    y = 260
                }
                params = lp

                val container = LinearLayout(appCtx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val orb = TextView(appCtx).apply {
                    text = currentMood.emoji
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    val sizePx = dp(appCtx, 46)
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                }

                val panel = LinearLayout(appCtx).apply {
                    orientation = LinearLayout.VERTICAL
                    visibility = View.GONE
                    setPadding(dp(appCtx, 12), dp(appCtx, 8), dp(appCtx, 12), dp(appCtx, 8))
                    layoutParams = LinearLayout.LayoutParams(
                        dp(appCtx, 238),
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        leftMargin = dp(appCtx, 6)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(appCtx, 16).toFloat()
                        setColor(Color.parseColor("#EE121826"))
                        setStroke(dp(appCtx, 1), Color.parseColor("#38BDF8"))
                    }
                }

                val tvTitle = TextView(appCtx).apply {
                    text = lastTitle
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F8FAFC"))
                    maxLines = 1
                }

                val tvDetail = TextView(appCtx).apply {
                    text = lastDetail
                    textSize = 11f
                    setTextColor(Color.parseColor("#CBD5E1"))
                    maxLines = 3
                    setPadding(0, dp(appCtx, 2), 0, dp(appCtx, 6))
                }

                val btnRow = LinearLayout(appCtx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val btnOpen = makeMiniButton(appCtx, "Open NAX", "#7C5CFF") {
                    collapseNow()
                    val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    appCtx.startActivity(openIntent)
                }

                val btnDim = makeMiniButton(appCtx, "Dim 25%", "#1E2942") {
                    val (_, msg) = DeviceSensorBridge.setRealBrightness(appCtx, 25)
                    peekStatus(appCtx, NaxMood.HAPPY, "Brightness Updated", msg.replace("**", ""), 3500L)
                }

                val btnStop = makeMiniButton(appCtx, "STOP", "#FF4D6D") {
                    LiveTouchService.emergencyStop()
                    NaxVoiceEngine.stop()
                    NaxLiveLogger.log("🛑", "Emergency STOP pressed from Floating Orb")
                    peekStatus(appCtx, NaxMood.WARNING, "NAX Stopped", "All live actions stopped immediately.", 3000L)
                }

                btnRow.addView(btnOpen)
                btnRow.addView(btnDim)
                btnRow.addView(btnStop)

                panel.addView(tvTitle)
                panel.addView(tvDetail)
                panel.addView(btnRow)

                container.addView(orb)
                container.addView(panel)

                attachDragAndTap(orb, wm, lp, container)

                orbView = orb
                peekPanel = panel
                titleView = tvTitle
                detailView = tvDetail
                rootView = container

                wm.addView(container, lp)
                applyVisualStyle()
                NaxLiveLogger.log("✨", "Floating NAX Orb active outside app")
            } catch (e: Exception) {
                NaxLiveLogger.log("⚠️", "Floating Orb error: ${e.message}")
            }
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                collapseRunnable?.let { mainHandler.removeCallbacks(it) }
                rootView?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            } finally {
                rootView = null
                orbView = null
                peekPanel = null
                titleView = null
                detailView = null
                isExpanded = false
            }
        }
    }

    fun applyConfig(context: Context, settings: CompanionSettings) {
        stealthMode = settings.stealthMode
        edgeAlpha = settings.edgeAlphaPercent.coerceIn(10, 100) / 100f
        if (!settings.floatingOrbEnabled) {
            hide()
        } else if (Settings.canDrawOverlays(context)) {
            if (rootView == null) show(context) else mainHandler.post { applyVisualStyle() }
        }
    }

    fun peekStatus(
        context: Context,
        mood: NaxMood,
        title: String,
        detail: String,
        autoHideMs: Long = 5000L
    ) {
        val appCtx = context.applicationContext
        currentMood = mood
        lastTitle = title
        lastDetail = detail

        if (!Settings.canDrawOverlays(appCtx)) return
        val prefs = NaxCompanionPrefs.load(appCtx)
        if (!prefs.floatingOrbEnabled) return

        mainHandler.post {
            if (rootView == null) show(appCtx)
            orbView?.text = mood.emoji
            titleView?.text = title
            detailView?.text = detail
            isExpanded = true
            peekPanel?.visibility = View.VISIBLE
            rootView?.alpha = 1.0f
            updateOrbDrawable()

            collapseRunnable?.let { mainHandler.removeCallbacks(it) }
            val r = Runnable { collapseNow() }
            collapseRunnable = r
            mainHandler.postDelayed(r, autoHideMs)
        }
    }

    private fun collapseNow() {
        isExpanded = false
        peekPanel?.visibility = View.GONE
        applyVisualStyle()
    }

    private fun applyVisualStyle() {
        updateOrbDrawable()
        rootView?.alpha = when {
            isExpanded -> 1.0f
            stealthMode -> 0.14f
            else -> edgeAlpha
        }
    }

    private fun updateOrbDrawable() {
        val orb = orbView ?: return
        val ctx = orb.context
        orb.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#E6121826"))
            setStroke(dp(ctx, 2), Color.parseColor(currentMood.accentHex))
        }
    }

    private fun attachDragAndTap(
        orb: View,
        wm: WindowManager,
        lp: WindowManager.LayoutParams,
        root: View
    ) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        moved = true
                        lp.x = initialX + dx
                        lp.y = initialY + dy
                        try {
                            wm.updateViewLayout(root, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        collapseRunnable?.let { mainHandler.removeCallbacks(it) }
                        isExpanded = !isExpanded
                        peekPanel?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        applyVisualStyle()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun makeMiniButton(context: Context, label: String, bgHex: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(context, 8), dp(context, 5), dp(context, 8), dp(context, 5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(context, 6)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 8).toFloat()
                setColor(Color.parseColor(bgHex))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
