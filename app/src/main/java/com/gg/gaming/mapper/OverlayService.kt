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
    private var editMode = false
    private var screenW = 0
    private var screenH = 0
    private var cursorLocked = false
    private var menuView: View? = null

    data class MapKey(
        val id: Int,
        var label: String,
        var key: String,
        var xPct: Float,
        var yPct: Float,
        val type: String,
        var view: View? = null,
        var params: WindowManager.LayoutParams? = null
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
        handler.postDelayed({ showAll() }, 500)
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
            MapKey(7,"1","1",90f,50f,"tap"),
            MapKey(8,"Macro","M1",15f,60f,"macro"),
            MapKey(9,"Macro","M2",28f,60f,"macro"),
        ).forEach { mapKeys.add(it) }
    }

    private fun showAll() {
        removeAll()
        showFloatingLogo()
        showMapKeys()
    }

    // ── FLOATING GG LOGO ──
    private fun showFloatingLogo() {
        val size = 48.dp()
        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            // FLAG_NOT_FOCUSABLE so game still gets input
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 20.dp()
        }

        val logo = TextView(this).apply {
            text = "GG"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circleDrawable(Color.parseColor("#DD5C6BC0"))
        }

        logo.setOnClickListener {
            if (menuVisible) hideMenu() else showMenu()
        }

        wm.addView(logo, params)
        allViews.add(logo)
    }

    // ── MAP KEYS ──
    private fun showMapKeys() {
        mapKeys.forEach { mk -> addKeyView(mk) }
    }

    private fun addKeyView(mk: MapKey) {
        val size = 44.dp()
        val x = ((mk.xPct / 100f) * screenW - size / 2).toInt()
        val y = ((mk.yPct / 100f) * screenH - size / 2).toInt()

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        val color = when (mk.type) {
            "macro" -> Color.parseColor("#CCAB47BC")
            else -> Color.parseColor("#CC5C6BC0")
        }

        val tv = TextView(this).apply {
            text = mk.label
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = circleDrawable(color)
        }

        setupKeyTouch(tv, mk, params)

        wm.addView(tv, params)
        mk.view = tv
        mk.params = params
        allViews.add(tv)
    }

    private fun setupKeyTouch(tv: TextView, mk: MapKey, params: WindowManager.LayoutParams) {
        var startX = 0f
        var startY = 0f
        var startRawX = 0f
        var startRawY = 0f
        var isDragging = false

        tv.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startRawX = params.x.toFloat()
                    startRawY = params.y.toFloat()
                    isDragging = false
                    if (!editMode) {
                        v.alpha = 0.5f
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (editMode && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        isDragging = true
                        params.x = (startRawX + dx).toInt()
                        params.y = (startRawY + dy).toInt()
                        try { wm.updateViewLayout(v, params) } catch (e: Exception) {}
                        // Update percentage
                        mk.xPct = ((params.x + 22.dp()) / screenW.toFloat()) * 100f
                        mk.yPct = ((params.y + 22.dp()) / screenH.toFloat()) * 100f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (editMode && isDragging) {
                        // Dragged - just update position
                        v.alpha = 1f
                    } else if (editMode && !isDragging) {
                        // Tapped in edit mode - show edit dialog
                        showEditKeyDialog(mk)
                    } else if (!editMode) {
                        // Normal mode - perform touch
                        v.alpha = 1f
                        GGAccessibilityService.instance?.performTouchPercent(mk.xPct, mk.yPct)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ── EDIT KEY DIALOG ──
    private fun showEditKeyDialog(mk: MapKey) {
        hideEditDialog()

        val dialogParams = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            overlayType(),
            FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.parseColor("#F05C6BC0"), 16f)
            setPadding(20.dp(), 16.dp(), 20.dp(), 16.dp())
        }

        // Title
        val title = TextView(this).apply {
            text = "Editar botón: ${mk.label}"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12.dp())
        }
        ll.addView(title)

        // Current key display
        val keyDisp = TextView(this).apply {
            text = "Tecla: ${mk.key}"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFD700"))
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 12.dp())
        }
        ll.addView(keyDisp)

        // Info
        val info = TextView(this).apply {
            text = "Presiona una tecla del teclado OTG\npara asignarla a este botón"
            textSize = 11f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dp())
        }
        ll.addView(info)

        // Buttons row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancelar"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = roundRect(Color.parseColor("#AA444444"), 20f)
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
            gravity = Gravity.CENTER
        }
        cancelBtn.setOnClickListener { hideEditDialog() }

        val deleteBtn = TextView(this).apply {
            text = "Eliminar"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = roundRect(Color.parseColor("#AAEF5350"), 20f)
            setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
            gravity = Gravity.CENTER
            (layoutParams as? LinearLayout.LayoutParams)?.setMargins(8.dp(), 0, 0, 0)
        }
        deleteBtn.setOnClickListener {
            hideEditDialog()
            removeKeyView(mk)
        }

        val lp1 = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        val lp2 = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            setMargins(8.dp(), 0, 0, 0)
        }
        btnRow.addView(cancelBtn, lp1)
        btnRow.addView(deleteBtn, lp2)
        ll.addView(btnRow)

        // Store reference and listen for key
        currentEditKey = mk
        currentKeyDisp = keyDisp

        wm.addView(ll, dialogParams)
        editDialogView = ll
        allViews.add(ll)
    }

    private var editDialogView: View? = null
    private var currentEditKey: MapKey? = null
    private var currentKeyDisp: TextView? = null

    fun onKeyFromOtg(keyChar: String, action: Int) {
        if (action != 0) return // only key down
        handler.post {
            if (editDialogView != null && currentEditKey != null) {
                // Assign key to button
                currentEditKey!!.key = keyChar
                currentEditKey!!.label = keyChar
                currentEditKey!!.view?.let { v ->
                    (v as? TextView)?.text = keyChar
                }
                currentKeyDisp?.text = "Tecla: $keyChar ✓"
                handler.postDelayed({ hideEditDialog() }, 800)
            } else {
                // Normal mode - trigger mapped key
                val mk = mapKeys.find {
                    it.key.equals(keyChar, ignoreCase = true)
                }
                if (mk != null && !editMode) {
                    mk.view?.alpha = 0.5f
                    GGAccessibilityService.instance?.performTouchPercent(mk.xPct, mk.yPct)
                    handler.postDelayed({ mk.view?.alpha = 1f }, 150)
                }
            }
        }
    }

    private fun hideEditDialog() {
        editDialogView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            allViews.remove(it)
        }
        editDialogView = null
        currentEditKey = null
        currentKeyDisp = null
    }

    private fun removeKeyView(mk: MapKey) {
        mk.view?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            allViews.remove(it)
        }
        mapKeys.remove(mk)
    }

    // ── MENU ──
    private fun showMenu() {
        menuVisible = true
        hideMenuView()

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(Color.parseColor("#EE5C6BC0"), 30f)
            setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
        }

        data class MenuItem(val ico: String, val lbl: String, val action: () -> Unit)

        val items = listOf(
            MenuItem("↩", "Salir") { stopSelf() },
            MenuItem("✏️", if (editMode) "Listo" else "Editar") { toggleEditMode() },
            MenuItem("👁", "Ocultar") { toggleKeys() },
            MenuItem("🔒", if (cursorLocked) "Libre" else "Bloquear") { toggleCursorLock() },
            MenuItem("💾", "Guardar") { saveLayout() },
        )

        items.forEach { item ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
            }
            val icoTv = TextView(this).apply {
                text = item.ico; textSize = 18f
                gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            }
            val lblTv = TextView(this).apply {
                text = item.lbl; textSize = 8f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#CCFFFFFF"))
            }
            col.addView(icoTv)
            col.addView(lblTv)
            col.setOnClickListener { item.action(); hideMenu() }
            ll.addView(col)
        }

        val params = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            overlayType(),
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 76.dp()
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

    // ── EDIT MODE ──
    private fun toggleEditMode() {
        editMode = !editMode
        showToast(if (editMode) "✏️ Modo edición — arrastra los botones y tócalos para cambiar tecla" else "✅ Edición finalizada")
        if (!editMode) saveLayout()
    }

    // ── TOGGLE KEYS ──
    private var keysVisible = true
    private fun toggleKeys() {
        keysVisible = !keysVisible
        mapKeys.forEach { mk ->
            mk.view?.visibility = if (keysVisible) View.VISIBLE else View.INVISIBLE
        }
        showToast(if (keysVisible) "Teclas visibles" else "Teclas ocultas")
    }

    // ── CURSOR LOCK ──
    private fun toggleCursorLock() {
        cursorLocked = !cursorLocked
        showToast(if (cursorLocked) "🔒 Mouse bloquado — mueve la cámara" else "🔓 Mouse libre")
    }

    // ── SAVE LAYOUT ──
    private fun saveLayout() {
        try {
            val keysList = mapKeys.map { mk ->
                mapOf("lbl" to mk.label, "key" to mk.key,
                    "x" to mk.xPct, "y" to mk.yPct, "type" to mk.type)
            }
            val profile = mapOf("keys" to keysList)
            val json = gson.toJson(profile)
            getSharedPreferences("gg_data", Context.MODE_PRIVATE)
                .edit().putString("profile", json).apply()
            showToast("💾 Layout guardado")
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── HELPERS ──
    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        TYPE_APPLICATION_OVERLAY else TYPE_PHONE

    private fun circleDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundRect(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private fun showToast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun removeAll() {
        allViews.forEach { try { wm.removeView(it) } catch (e: Exception) {} }
        allViews.clear()
        mapKeys.forEach { it.view = null }
        menuView = null
        editDialogView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "GG Gaming Overlay",
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GG Gaming")
            .setContentText("🎮 Activo — toca GG para editar")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAll()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
