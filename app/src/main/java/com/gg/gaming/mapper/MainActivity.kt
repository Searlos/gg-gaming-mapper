package com.gg.gaming.mapper

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.gg.gaming.mapper.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private var cursorLockKey = "F9"

    companion object {
        const val OVERLAY_PERMISSION_REQUEST = 1001
        const val ACCESSIBILITY_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWebView()
        webView.loadUrl("file:///android_asset/index.html")
        checkPermissions()
    }

    private fun checkPermissions() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            handler.postDelayed({
                val js = "if(window.onPermissionStatus) window.onPermissionStatus({overlay:false,accessibility:false});"
                webView.evaluateJavascript(js, null)
            }, 1500)
        } else {
            checkAccessibility()
        }
    }

    private fun checkAccessibility(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.contains(packageName) == true
        val js = "if(window.onPermissionStatus) window.onPermissionStatus({overlay:true,accessibility:$enabled});"
        webView.evaluateJavascript(js, null)
        return enabled
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun setupWebView() {
        webView = binding.webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webView.addJavascriptInterface(Bridge(), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectDeviceStatus()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage?) = true
        }
    }

    private fun injectDeviceStatus() {
        val js = "if(window.onOtgStatus) window.onOtgStatus({keyboard:true,mouse:false});"
        webView.evaluateJavascript(js, null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyChar = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val js = "if(window.onOtgKey) window.onOtgKey({keyChar:'$keyChar',action:${event.action}});"
        webView.evaluateJavascript(js, null)
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
            val js = "if(window.onOtgMouse) window.onOtgMouse({x:${event.x},y:${event.y},buttonState:${event.buttonState},action:${event.action}});"
            webView.evaluateJavascript(js, null)
        }
        return super.dispatchGenericMotionEvent(event)
    }

    inner class Bridge {

        @JavascriptInterface
        fun getInstalledApps(): String {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val result = apps
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { app ->
                    mapOf(
                        "name" to (pm.getApplicationLabel(app).toString()),
                        "pkg" to app.packageName
                    )
                }
                .sortedBy { it["name"] }
            return gson.toJson(result)
        }

        @JavascriptInterface
        fun launchApp(pkg: String) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                handler.post {
                    webView.evaluateJavascript(
                        "if(window.showToast) window.showToast('App no encontrada');", null
                    )
                }
            }
        }

        @JavascriptInterface
        fun startOverlay(profileJson: String) {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                requestOverlayPermission()
                return
            }
            val intent = Intent(this@MainActivity, OverlayService::class.java)
            intent.putExtra("profile", profileJson)
            startService(intent)
        }

        @JavascriptInterface
        fun stopOverlay() {
            stopService(Intent(this@MainActivity, OverlayService::class.java))
        }

        @JavascriptInterface
        fun requestOverlay() {
            handler.post { requestOverlayPermission() }
        }

        @JavascriptInterface
        fun requestAccessibility() {
            handler.post { requestAccessibilityPermission() }
        }

        @JavascriptInterface
        fun isAccessibilityEnabled(): Boolean {
            val enabled = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabled?.contains(packageName) == true
        }

        @JavascriptInterface
        fun isOverlayEnabled(): Boolean {
            return Settings.canDrawOverlays(this@MainActivity)
        }

        @JavascriptInterface
        fun saveProfile(json: String) {
            getSharedPreferences("gg_data", Context.MODE_PRIVATE)
                .edit().putString("profile", json).apply()
        }

        @JavascriptInterface
        fun loadProfile(): String {
            return getSharedPreferences("gg_data", Context.MODE_PRIVATE)
                .getString("profile", "{}") ?: "{}"
        }

        @JavascriptInterface
        fun setCursorLockKey(key: String) {
            cursorLockKey = key
            GGAccessibilityService.instance?.setCursorLockKey(key)
        }

        @JavascriptInterface
        fun vibrate(ms: Long) {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(ms)
        }

        @JavascriptInterface
        fun runMacro(stepsJson: String) {
            GGAccessibilityService.instance?.runMacro(stepsJson)
        }

        @JavascriptInterface
        fun performTouch(x: Float, y: Float) {
            GGAccessibilityService.instance?.performTouch(x, y)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            checkPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibility()
    }
}