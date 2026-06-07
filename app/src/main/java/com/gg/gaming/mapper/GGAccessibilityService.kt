package com.gg.gaming.mapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
    private var screenW = 1080f
    private var screenH = 2400f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        screenW = resources.displayMetrics.widthPixels.toFloat()
        screenH = resources.displayMetrics.heightPixels.toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // ── Capture OTG keyboard keys ──
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            val keyChar = KeyEvent.keyCodeToString(it.keyCode)
                .removePrefix("KEYCODE_")
            // Send to overlay service to handle
            OverlayService.instance?.onKeyFromOtg(keyChar, it.action)
        }
        // Return false so the key still works in the game
        return false
    }

    // ── Perform real tap at percentage position ──
    fun performTouchPercent(xPct: Float, yPct: Float) {
        val x = screenW * (xPct / 100f)
        val y = screenH * (yPct / 100f)
        performTouch(x, y)
    }

    // ── Perform real tap at absolute position ──
    fun performTouch(x: Float, y: Float) {
        try {
            val path = Path()
            path.moveTo(x, y)
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(path, 0, 50)
                )
                .build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Perform swipe ──
    fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 150
    ) {
        try {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(path, 0, duration)
                )
                .build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ── Handle mouse movement for camera ──
    fun handleMouseMove(dx: Float, dy: Float, sensitivity: Float = 1.0f) {
        val cx = screenW * 0.5f
        val cy = screenH * 0.4f
        val scale = sensitivity * 2f
        performSwipe(
            cx, cy,
            (cx - dx * scale).coerceIn(0f, screenW),
            (cy - dy * scale).coerceIn(0f, screenH),
            80
        )
    }

    // ── Run macro steps ──
    fun runMacro(stepsJson: String) {
        try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val steps: List<Map<String, Any>> = gson.fromJson(stepsJson, type)
            var totalDelay = 0L
            steps.forEach { step ->
                val delay = (step["delay"] as? Double)?.toLong() ?: 0L
                val x = (step["x"] as? Double)?.toFloat() ?: 50f
                val y = (step["y"] as? Double)?.toFloat() ?: 50f
                totalDelay += delay
                handler.postDelayed({
                    performTouchPercent(x, y)
                }, totalDelay)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setCursorLockKey(key: String) {
        cursorLockKey = key
    }
}