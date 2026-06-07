package com.gg.gaming.mapper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())
    private val allViews = mutableListOf<View>()
    private var menuVisible = false
    private var keysVisible = true
    private var screenW = 0
    private var screenH = 0

    data class MapKey(
        val id: Int,
        val label: String,
        val key: String,
        var xPct: Float,
        var yPct: Float,
        val type: String,
        var view: View? = null
    )

    private val mapKeys = mutableListOf<MapKey>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val profile = intent?.getStringExtra("profile") ?: loadSavedProfile()
        parseProfile(profile)
        showAll()
        return START_STICKY
    }

    private fun loadSavedProfile(): String {
        return getSharedPreferences("gg_data", Context.MODE_PRIVATE)
            .getString("profile", "{}") ?: "{}"
    }

    private fun parseProfile(json: String) {
        mapKeys.clear()
        try {
            val p = gson.fromJson(json, Map::class.java)
            val keysJson = gson.toJson(p["keys"])
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val keys: List<Map<String, Any>> = gson.fromJson(keysJson, type)
            keys.forEachIndexed { i, k ->
                mapKeys.add(MapKey(
                    id = i,
                    label = k["lbl"] as? String ?: k["label"] as? String ?: "?",
                    key = k["key"] as? String ?: "?",
                    xPct = (k["x"] as? Double)?.toFloat() ?: 50f,
                    yPct = (k["y"] as? Double)?.toFloat() ?: 50f,
                    type = k["type"] as? String ?: "tap"
                ))
            }
        } catch (e: Exception) {
            loadDefaultKeys()
        }
        if (mapKeys.isEmpty()) loadDefaultKeys()
    }

    private fun loadDefaultKeys() {
        mapKeys.clear()
        listOf(
            MapKey(0,"G","G",4f,40f,"tap"),
            MapKey(1,"C","C",33f,13f,"tap"),
            MapKey(2,"Espacio","Space",58f,88f,"tap"),
            MapKey(3,"F","F",76f,74f,"tap"),
            MapKey(4,"R","R",70f,70f,"tap"),
            MapKey(5,"TAB","Tab",79f,63f,"tap"),
            MapKey(6,"B","B",93f,55f,"tap"),
            MapKey(7,"V","V",84f,79f,"tap"),
            MapKey(8,"1","1",90f,50f,"tap"),
            MapKey(9,"3","3",95f,50f,"tap"),
            MapKey(10,"Macro","M1",15f,60f,"macro"),
            MapKey(11,"Macro","M2",28f,60f,"macro"),
        ).forEach { mapKeys.add(it) }
    }

    // ── SHOW EVERYTHING ──
    private fun showAll() {
        removeAll()
        showFloatingLogo()
        showMapKeys()
    }

    // ── FLOATING LOGO (GG button) ──
    private fun showFloatingLogo() {
        val size = 52.dp()
        val params = overlayParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 16.dp()
            flags = flags or FLAG_NOT_TOUCH_MODAL
            flags = flags and FLAG_NOT_FOCUSABLE.inv()
        }

        val logo = TextView(this).apply {
            text = "GG"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circleDrawable(Color.parseColor("#CC5C6BC0"), 26f)
            setPadding(0,0,0,0)
        }

        logo.setOnClickListener {
            if (menuVisible) hideMenu() else showMenu()
        }

        wm.addView(logo, params)
        allViews.add(logo)
    }

    // ── MAP KEYS ──
    private fun showMapKeys() {
        mapKeys.forEach { mk ->
            val size = 44.dp()
            val x = ((mk.xPct / 100f) * screenW - size / 2).toInt()
            val y = ((mk.yPct / 100f) * screenH - size / 2).toInt()

            val params = overlayParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }

            val color = when (mk.type) {
                "macro" -> Color.parseColor("#CCAB47BC")
                "fps" -> Color.parseColor("#CCEF5350")
                else -> Color.parseColor("#CC5C6BC0")
            }

            val tv = TextView(this).apply {
                text = mk.label
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = circleDrawable(color, 22f)
            }

            tv.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.alpha = 0.5f
                        GGAccessibilityService.instance?.performTouchPercent(mk.xPct, mk.yPct)
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
            mk.view = tv
            allViews.add(tv)
        }
    }

    // ── EDITOR MENU (when GG logo tapped) ──
    private var menuView: View? = null

    private fun showMenu() {
        menuVisible = true
        hideMenuView()

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(Color.parseColor("#EE5C6BC0"), 30f)
            setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
        }

        // Menu items: icon + label
        listOf(
            Triple("↩", "Salir") { stopSelf() },
            Triple("👁", if (keysVisible) "Ocultar" else "Mostrar") { toggleKeys() },
            Triple("⌨", "Editor") { openEditor() },
            Triple("🔒", "Bloquear") { toggleCursorLock() },
            Triple("⚙", "Config") { openConfig() },
        ).forEach { (ico, lbl, action) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp())
            }
            val icoTv = TextView(this).apply {
                text = ico; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            val lblTv = TextView(this).apply {
                text = lbl; textSize = 8f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#CCFFFFFF"))
            }
            item.addView(icoTv)
            item.addView(lblTv)
            item.setOnClickListener { action() }
            ll.addView(item)
        }

        val params = overlayParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 76.dp()
            flags = flags and FLAG_NOT_FOCUSABLE.inv()
        }

        wm.addView(ll, params)
        menuView = ll
        allViews.add(ll)
    }

    private fun hideMenu() {
        menuVisible = false
        hideMenuView()
    }

    private fun hideMenuView() {
        menuView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            allViews.remove(it)
        }
        menuView = null
    }

    // ── TOGGLE KEYS ──
    private fun toggleKeys() {
        keysVisible = !keysVisible
        mapKeys.forEach { mk ->
            mk.view?.visibility = if (keysVisible) View.VISIBLE else View.INVISIBLE
        }
        hideMenu()
        showToast(if (keysVisible) "Teclas visibles" else "Teclas ocultas")
    }

    // ── TOGGLE CURSOR LOCK ──
    private var cursorLocked = false
    private fun toggleCursorLock() {
        cursorLocked = !cursorLocked
        GGAccessibilityService.instance?.let {
            // notify accessibility service
        }
        hideMenu()
        showToast(if (cursorLocked) "🔒 Mouse bloqueado" else "🔓 Mouse libre")
    }

    // ── OPEN EDITOR (brings app to front) ──
    private fun openEditor() {
        hideMenu()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("openEditor", true)
        }
        startActivity(intent)
    }

    // ── OPEN CONFIG ──
    private fun openConfig() {
        hideMenu()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    // ── TOAST ──
    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ── HANDLE KEY FROM OTG ──
    fun handleKey(keyChar: String, action: Int) {
        val mk = mapKeys.find {
            it.key.equals(keyChar, ignoreCase = true)
        } ?: return

        handler.post {
            if (action == 0) { // key down
                mk.view?.alpha = 0.5f
                GGAccessibilityService.instance?.performTouchPercent(mk.xPct, mk.yPct)
            } else { // key up
                mk.view?.alpha = 1f
            }
        }
    }

    // ── HELPERS ──
    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            TYPE_APPLICATION_OVERLAY else TYPE_PHONE,
        FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    )

    private fun circleDrawable(color: Int, radius: Float) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            cornerRadius = radius
        }

    private fun roundRect(color: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun removeAll() {
        allViews.forEach {
            try { wm.removeView(it) } catch (e: Exception) {}
        }
        allViews.clear()
        mapKeys.forEach { it.view = null }
        menuView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "GG Gaming Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
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
            .setContentText("🎮 Mapeador activo — toca GG para el menú")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAll()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}