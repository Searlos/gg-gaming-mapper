package com.gg.gaming.mapper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "gg_overlay_channel"
        const val NOTIF_ID = 2001
        var instance: OverlayService? = null
    }

    private lateinit var wm: WindowManager
    private val gson = Gson()
    private val overlayViews = mutableListOf<View>()

    // Map key buttons: id -> (view, xPct, yPct)
    data class MapButton(
        val id: Int,
        val label: String,
        val key: String,
        val xPct: Float,
        val yPct: Float,
        val type: String,
        var view: View? = null
    )

    private val mapButtons = mutableListOf<MapButton>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profileJson = intent?.getStringExtra("profile") ?: "{}"
        loadProfile(profileJson)
        showOverlay()
        return START_STICKY
    }

    private fun loadProfile(json: String) {
        try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val profile: Map<String, Any> = gson.fromJson(json, type)
            val keysJson = gson.toJson(profile["keys"])
            val keysType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val keys: List<Map<String, Any>> = gson.fromJson(keysJson, keysType)
            mapButtons.clear()
            keys.forEachIndexed { i, k ->
                mapButtons.add(
                    MapButton(
                        id = i,
                        label = k["label"] as? String ?: "?",
                        key = k["key"] as? String ?: "?",
                        xPct = (k["x"] as? Double)?.toFloat() ?: 50f,
                        yPct = (k["y"] as? Double)?.toFloat() ?: 50f,
                        type = k["type"] as? String ?: "tap"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadDefaultButtons()
        }
    }

    private fun loadDefaultButtons() {
        mapButtons.clear()
        listOf(
            MapButton(0,"G","G",4f,40f,"tap"),
            MapButton(1,"C","C",33f,13f,"tap"),
            MapButton(2,"Space","Space",58f,88f,"tap"),
            MapButton(3,"F","F",76f,74f,"tap"),
            MapButton(4,"R","R",70f,70f,"tap"),
            MapButton(5,"TAB","TAB",79f,63f,"tap"),
        ).forEach { mapButtons.add(it) }
    }

    private fun showOverlay() {
        removeAllViews()
        val screen = resources.displayMetrics
        val w = screen.widthPixels
        val h = screen.heightPixels

        mapButtons.forEach { btn ->
            val view = createKeyButton(btn, w, h)
            btn.view = view
        }

        // Show top toolbar
        showToolbar(w)
    }

    private fun createKeyButton(btn: MapButton, screenW: Int, screenH: Int): View {
        val size = 44.dpToPx()
        val x = ((btn.xPct / 100f) * screenW - size / 2).toInt()
        val y = ((btn.yPct / 100f) * screenH - size / 2).toInt()

        val params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                TYPE_APPLICATION_OVERLAY else TYPE_PHONE,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        val tv = TextView(this).apply {
            text = btn.label
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = createCircleBackground(btn.type)
        }

        tv.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.6f
                    // Perform real touch via accessibility service
                    GGAccessibilityService.instance?.performTouchPercent(
                        btn.xPct, btn.yPct
                    )
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1f
                    true
                }
                else -> false
            }
        }

        wm.addView(tv, params)
        overlayViews.add(tv)
        return tv
    }

    private fun createCircleBackground(type: String): android.graphics.drawable.GradientDrawable {
        val color = when (type) {
            "macro" -> Color.parseColor("#AB47BC")
            "fps" -> Color.parseColor("#EF5350")
            else -> Color.parseColor("#5C6BC0")
        }
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            alpha = 210
        }
    }

    private fun showToolbar(screenW: Int) {
        // Simple floating toolbar at top
        val params = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                TYPE_APPLICATION_OVERLAY else TYPE_PHONE,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 32.dpToPx()
        }

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#CC5C6BC0"))
                cornerRadius = 24.dpToPx().toFloat()
            }
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
        }

        listOf("Salir" to "exit", "Guardar" to "save").forEach { (label, action) ->
            val btn = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                setOnClickListener {
                    when (action) {
                        "exit" -> stopSelf()
                        "save" -> {}
                    }
                }
            }
            ll.addView(btn)
        }

        wm.addView(ll, params)
        overlayViews.add(ll)
    }

    private fun removeAllViews() {
        overlayViews.forEach {
            try { wm.removeView(it) } catch (e: Exception) {}
        }
        overlayViews.clear()
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "GG Gaming Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Overlay del mapeador activo"
                setShowBadge(false)
            }
            (getSystemService(NotificationManager::class.java))
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Gaming")
            .setContentText("🎮 Mapeador activo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAllViews()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}