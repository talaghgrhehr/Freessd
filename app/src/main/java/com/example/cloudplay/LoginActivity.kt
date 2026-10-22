package com.example.cloudplay

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private val h = Handler(Looper.getMainLooper())
    private var done = false
    private lateinit var web: WebView
    private lateinit var info: TextView

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            if (check()) return
            h.postDelayed(this, 1500)
        }
    }

    private fun check(): Boolean {
        val cm = CookieManager.getInstance()
        val urls = listOfNotNull(
            web.url, "https://www.terabox.com", "https://terabox.com",
            "https://www.1024terabox.com", "https://www.4funbox.com"
        )
        for (u in urls) {
            val ck = cm.getCookie(u) ?: continue
            for (part in ck.split(";")) {
                val t = part.trim()
                if (t.startsWith("ndus=") && t.length > 8) {
                    save(t.removePrefix("ndus="))
                    return true
                }
            }
        }
        return false
    }

    private fun save(v: String) {
        done = true
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("ndus", v).remove("last_status").apply()
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(CloudDocumentsProvider.AUTH), null)
        Toast.makeText(this, "लॉगिन हो गया ✓", Toast.LENGTH_SHORT).show()
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#05060F")
        val ui = Ui(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = ui.page()

        info = ui.text("लोड हो रहा है…", 12f, ui.muted)
        info.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(8))

        web = WebView(this)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-A156B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                info.text = "खुला: " + url + "\nलॉगिन पूरा होते ही ऐप अपने आप वापस आ जाएगा।"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) info.text = "एरर: " + error.description
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) info.text = "HTTP " + errorResponse.statusCode
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(12))
        row.addView(ui.ghost("पेज 1") { web.loadUrl("https://www.terabox.com/wap/outlogin/login") }, ui.weight())
        row.addView(ui.ghost("पेज 2") { web.loadUrl("https://www.terabox.com/login") }, ui.weight(8))
        row.addView(ui.ghost("पेज 3") { web.loadUrl("https://www.terabox.com/main") }, ui.weight(8))

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        root.addView(info, LinearLayout.LayoutParams(match, wrap))
        root.addView(web, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(row, LinearLayout.LayoutParams(match, wrap))
        setContentView(root)
        web.loadUrl("https://www.terabox.com/wap/outlogin/login")
        h.postDelayed(poll, 1500)
    }

    override fun onDestroy() {
        h.removeCallbacks(poll)
        web.destroy()
        super.onDestroy()
    }
}
