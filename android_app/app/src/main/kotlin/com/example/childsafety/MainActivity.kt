package com.example.childsafety

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_CLOSE_APP = "com.example.childsafety.CLOSE_APP"
    }

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private val DEFAULT_URL = "https://olive-unaligned-crisping.ngrok-free.dev/child.html"

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            closeAndExit()
        }
    }

    private fun closeAndExit() {
        try {
            webView.loadUrl("about:blank")
        } catch (e: Exception) {}
        finishAffinity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("close_app", false)) {
            closeAndExit()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_CLOSE_APP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (audioGranted && locationGranted) {
            startChildService()
            loadChildWeb()
        } else {
            Toast.makeText(this, "يرجى منح أذونات الصوت والموقع والرسائل ليعمل التطبيق", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إبقاء الشاشة مضاءة وفتح التطبيق فوق شاشة القفل
        unlockAndTurnScreenOn()

        prefs = getSharedPreferences("ChildSafetyPrefs", Context.MODE_PRIVATE)

        // التأكد من استخدام الرابط النشط وإلغاء أي روابط قديمة غير صالحة
        val savedUrl = prefs.getString("server_url", "")
        if (savedUrl.isNullOrEmpty() || savedUrl.contains("trycloudflare") || !savedUrl.contains("olive-unaligned-crisping.ngrok-free.dev")) {
            prefs.edit().putString("server_url", DEFAULT_URL).apply()
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // شريط إعدادات وتحديث
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF064E3B.toInt())
        }

        val urlInput = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setText(prefs.getString("server_url", DEFAULT_URL))
            setTextColor(0xFFFFFFFF.toInt())
            hint = "رابط الخادم (HTTPS)"
            setHintTextColor(0xFFA7F3D0.toInt())
            textSize = 13f
        }

        val btnConnect = Button(this).apply {
            text = "حفظ وتحديث"
            setOnClickListener {
                val newUrl = urlInput.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString("server_url", newUrl).apply()
                    val headers = mapOf("ngrok-skip-browser-warning" to "1")
                    webView.loadUrl(newUrl, headers)
                    Toast.makeText(this@MainActivity, "تم تحديث الرابط والاتصال", Toast.LENGTH_SHORT).show()
                }
            }
        }

        topBar.addView(urlInput)
        topBar.addView(btnConnect)
        rootLayout.addView(topBar)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setupWebView()
        rootLayout.addView(webView)
        setContentView(rootLayout)

        checkAndRequestPermissions()
    }

    private fun unlockAndTurnScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        // تشغيل الصوت بدون الحاجة للمس الشاشة
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setGeolocationEnabled(true)
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // User-Agent مخصص لمنع صفحة تحذير ngrok نهائياً لجميع الطلبات
        settings.userAgentString = "ChildSafetyApp/1.0"

        // مكبر الصوت الخارجي (Speaker)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = true

        // تهيئة الكوكيز لتجاوز صفحة ngrok الترحيبية تلقائياً
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        cookieManager.setCookie("https://olive-unaligned-crisping.ngrok-free.dev", "ngrok-skip-browser-warning=1; Path=/; SameSite=None; Secure")
        cookieManager.setCookie("https://olive-unaligned-crisping.ngrok-free.dev/", "ngrok-skip-browser-warning=1; Path=/; SameSite=None; Secure")
        cookieManager.flush()

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // قبول شهادة التشفير تلقائياً لضمان استمرار الاتصال
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // إذا ظهرت أي أزرار استمرار يتم الضغط عليها برمجياً
                view?.evaluateJavascript(
                    """
                    (function() {
                        var btns = document.querySelectorAll('button, a');
                        for (var i = 0; i < btns.length; i++) {
                            var t = (btns[i].innerText || '').toLowerCase();
                            if (t.includes('visit') || t.includes('continue')) {
                                btns[i].click();
                                break;
                            }
                        }
                    })();
                    """.trimIndent(), null
                )
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@MainActivity, "تنبيه شبكة: ${error?.description}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                // موافقة برمجية تلقائية لاستخدام الصوت والكاميرا
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                android.util.Log.d("ChildSafetyWebView", consoleMessage?.message() ?: "")
                return true
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isEmpty()) {
            startChildService()
            loadChildWeb()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startChildService() {
        ChildSafetyService.startService(this)
    }

    private fun loadChildWeb() {
        val url = prefs.getString("server_url", DEFAULT_URL) ?: DEFAULT_URL
        val headers = mapOf("ngrok-skip-browser-warning" to "1")
        webView.loadUrl(url, headers)
    }
}
