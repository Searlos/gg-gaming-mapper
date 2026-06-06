package com.gg.gaming.mapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GGAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GGAccessibilityService? = null
    }

    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private var cursorLockKey = "F9"
    private var cursorLocked = false

    // Track current mouse position
    private var mouseX = 500f
    private var mouseY = 500f

    // Screen dimensions
    private var screenW = 1080f
    private var screenH = 2400f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = serviceInfo
        screenW = resources.displayMetrics.widthPixels.toFloat()
        screenH = resources.displayMetrics.heightPixels.toFloat()
        mouseX = screenW / 2f
        mouseY = screenH / 2f
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // ── Set cursor lock key ──
    fun setCursorLockKey(key: String) {
        cursorLockKey = key
    }

    // ── Toggle cursor lock ──
    fun toggleCursorLock() {
        cursorLocked = !cursorLocked
    }

    // ── Perform a real tap at x,y on screen ──
    fun performTouch(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ── Perform tap at percentage position ──
    fun performTouchPercent(xPct: Float, yPct: Float) {
        val x = screenW * (xPct / 100f)
        val y = screenH * (yPct / 100f)
        performTouch(x, y)
    }

    // ── Perform swipe ──
    fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 200
    ) {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ── Handle mouse movement to move camera ──
    fun handleMouseMove(dx: Float, dy: Float, sensitivity: Float = 1.0f) {
        if (!cursorLocked) return

        // Mouse movement translates to swipe gesture on the aim zone
        val aimCenterX = screenW * 0.5f
        val aimCenterY = screenH * 0.4f
        val moveScale = sensitivity * 2f

        val startX = aimCenterX
        val startY = aimCenterY
        val endX = aimCenterX - (dx * moveScale)
        val endY = aimCenterY - (dy * moveScale)

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(
            endX.coerceIn(0f, screenW),
            endY.coerceIn(0f, screenH)
        )
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ── Handle mouse click ──
    fun handleMouseClick(button: Int, xPct: Float, yPct: Float) {
        // button 1 = left click = shoot
        // button 2 = right click = ADS
        val x = screenW * (xPct / 100f)
        val y = screenH * (yPct / 100f)
        performTouch(x, y)
    }

    // ── Run macro ──
    fun runMacro(stepsJson: String) {
        try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val steps: List<Map<String, Any>> = gson.fromJson(stepsJson, type)
            var totalDelay = 0L
            steps.forEach { step ->
                val delay = (step["delay"] as? Double)?.toLong() ?: 0L
                val key = step["key"] as? String ?: return@forEach
                val xPct = (step["x"] as? Double)?.toFloat() ?: 50f
                val yPct = (step["y"] as? Double)?.toFloat() ?: 50f
                totalDelay += delay
                handler.postDelayed({
                    performTouchPercent(xPct, yPct)
                }, totalDelay)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Handle keyboard key mapped to screen position ──
    fun handleMappedKey(key: String, xPct: Float, yPct: Float, isDown: Boolean) {
        if (isDown) {
            performTouchPercent(xPct, yPct)
        }
    }
}