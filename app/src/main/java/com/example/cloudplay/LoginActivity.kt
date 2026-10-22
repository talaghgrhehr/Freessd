package com.example.cloudplay

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var done = false
    private var desktop = false
    private var lastConsole = ""
    private lateinit var web: WebView
    private lateinit var info: TextView
    private lateinit var addr: EditText
    private lateinit var prog: ProgressBar

    private val mobileUa = "Mozilla/5.0 (Linux; Android 14; SM-A156B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            if (check()) return
            h.postDelayed(this, 1500)
        }
    }

    private fun check(): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val urls = listOfNotNull(
            web.url, "https://www.terabox.com", "https://terabox.com",
            "https://www.1024terabox.com", "https://www.terabox.app", "https://www.4funbox.com"
        )
        for (u in urls) {
            val ck = cm.getCookie(u) ?: continue
            for (part in ck.split(";")) {
                val t = part.trim()
                if (t.startsWith("ndus=") && t.length > 8) {
                    val dom = if (u.contains("1024terabox")) "1024terabox.com" else if (u.contains("terabox.app")) "terabox.app" else if (u.contains("4funbox")) "4funbox.com" else "terabox.com"
                    getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("domain", dom).apply()
                    verifyAndSave(t.removePrefix("ndus="), dom)
                    return false
                }
            }
        }
        return false
    }

    private var verifying = false
    private var badToken = ""

    private fun verifyAndSave(v: String, dom: String) {
        if (verifying || v == badToken) return
        verifying = true
        Thread {
            var ok = false
            try {
                val c = URL("https://www." + dom + "/api/quota?checkfree=1&checkexpire=1").openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty("User-Agent", "terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox")
                c.setRequestProperty("Cookie", "lang=en; ndus=" + v)
                try {
                    ok = JSONObject(c.inputStream.bufferedReader().readText()).optInt("errno", -1) == 0
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
            }
            runOnUiThread {
                verifying = false
                if (ok) {
                    save(v)
                } else {
                    badToken = v
                    info.text = "लॉगिन अभी पूरा नहीं हुआ। ईमेल और पासवर्ड से लॉगिन पूरा करो, ऐप अपने आप पहचान लेगा।"
                }
            }
        }.start()
    }

    private fun forceSave() {
        val cm = CookieManager.getInstance()
        cm.flush()
        val urls = listOfNotNull(
            web.url, "https://www.1024terabox.com", "https://dm.1024terabox.com",
            "https://1024terabox.com", "https://www.terabox.com"
        )
        for (u in urls) {
            val ck = cm.getCookie(u) ?: continue
            for (part in ck.split(";")) {
                val t = part.trim()
                if (t.startsWith("ndus=") && t.length > 8) {
                    val dom = if (u.contains("1024terabox")) "1024terabox.com" else "terabox.com"
                    getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("domain", dom).apply()
                    save(t.removePrefix("ndus="))
                    return
                }
            }
        }
        Toast.makeText(this, "ndus कुकी नहीं मिली", Toast.LENGTH_LONG).show()
    }

    private fun save(v: String) {
        done = true
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("ndus", v).remove("last_status").apply()
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(CloudDocumentsProvider.AUTH), null)
        Toast.makeText(this, "लॉगिन हो गया ✓", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun go(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = if (u.contains(".") && !u.contains(" ")) "https://" + u
            else "https://www.google.com/search?q=" + Uri.encode(u)
        }
        web.loadUrl(u)
    }

    private fun applyUa() {
        web.settings.userAgentString = if (desktop) desktopUa else mobileUa
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#05060F")
        window.navigationBarColor = Color.parseColor("#12142A")
        val ui = Ui(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = ui.page()

        // पता-पट्टी
        addr = EditText(this)
        addr.setSingleLine(true)
        addr.hint = "वेबसाइट या खोज"
        addr.setTextColor(Color.WHITE)
        addr.setHintTextColor(ui.muted)
        addr.textSize = 14f
        addr.imeOptions = EditorInfo.IME_ACTION_GO
        addr.background = ui.shape(Color.parseColor("#22FFFFFF"), 20)
        addr.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))
        addr.setOnEditorActionListener { v, _, _ ->
            go(v.text.toString())
            true
        }
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(6))
        top.addView(addr, ui.weight())
        top.addView(ui.ghost("जाओ") { go(addr.text.toString()) }, ui.weight(8))

        prog = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        prog.max = 100
        prog.progressTintList = android.content.res.ColorStateList.valueOf(ui.cyan)

        info = ui.text("शुरू हो रहा है…", 11f, ui.muted)
        info.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(6))

        web = WebView(this)
        web.setBackgroundColor(Color.WHITE)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.mediaPlaybackRequiresUserGesture = true
        applyUa()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val sch = request.url.scheme ?: ""
                return sch != "http" && sch != "https"
            }

            override fun onPageFinished(view: WebView, url: String?) {
                addr.setText(url ?: "")
                info.text = "खुला: " + (url ?: "").substringBefore('?') + (if (lastConsole.isNotEmpty()) "\n" + lastConsole else "")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) info.text = "एरर: " + error.description + "\n" + request.url.toString().substringBefore('?')
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) info.text = "HTTP " + errorResponse.statusCode + "\n" + request.url.toString().substringBefore('?')
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                prog.progress = newProgress
                if (newProgress < 100) info.text = "लोड: " + newProgress + "%  " + (view.url ?: "").substringBefore('?')
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    lastConsole = "JS: " + m.message().take(120)
                }
                return true
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        // पेज बटन
        val r1 = LinearLayout(this)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), 0)
        r1.addView(ui.ghost("terabox") { go("https://www.terabox.com/main") }, ui.weight())
        r1.addView(ui.ghost("1024tera") { go("https://www.1024terabox.com/main") }, ui.weight(8))
        r1.addView(ui.ghost("wap लॉगिन") { go("https://www.terabox.com/wap/outlogin/login") }, ui.weight(8))

        val r2 = LinearLayout(this)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))
        r2.addView(ui.ghost("⟳") { web.reload() }, ui.weight())
        r2.addView(ui.ghost("डेस्कटॉप") {
            desktop = !desktop
            applyUa()
            Toast.makeText(this, if (desktop) "डेस्कटॉप मोड" else "मोबाइल मोड", Toast.LENGTH_SHORT).show()
            web.reload()
        }, ui.weight(8))
        r2.addView(ui.ghost("Chrome") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web.url ?: "https://www.terabox.com")))
            } catch (e: Exception) {
            }
        }, ui.weight(8))

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        root.addView(top, LinearLayout.LayoutParams(match, wrap))
        root.addView(prog, LinearLayout.LayoutParams(match, ui.dp(3)))
        root.addView(info, LinearLayout.LayoutParams(match, wrap))
        root.addView(web, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(r1, LinearLayout.LayoutParams(match, wrap))
        root.addView(r2, LinearLayout.LayoutParams(match, wrap))
        val sb = ui.gradButton("✓ लॉगिन हो गया, सेव करो") { forceSave() }
        val sbp = LinearLayout.LayoutParams(match, wrap)
        sbp.setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(14))
        root.addView(sb, sbp)
        setContentView(root)
        go("https://www.terabox.com/main")
        h.postDelayed(poll, 1500)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        h.removeCallbacks(poll)
        web.destroy()
        super.onDestroy()
    }
}
