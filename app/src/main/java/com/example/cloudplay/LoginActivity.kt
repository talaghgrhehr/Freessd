package com.example.cloudplay

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val info = TextView(this).apply { textSize = 12f; text = "लोड हो रहा है..."; setPadding(24, 12, 24, 12) }
        val web = WebView(this)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-A156B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                info.text = "खुला: " + url
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) info.text = "एरर: " + error.description + " (" + error.errorCode + ")"
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) info.text = "HTTP " + errorResponse.statusCode + ": " + request.url
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        val reload = Button(this)
        reload.text = "दूसरा पेज खोलो (main)"
        reload.setOnClickListener { web.loadUrl("https://www.terabox.com/main") }

        val btn = Button(this)
        btn.text = "लॉगिन हो गया — सेव करो"
        btn.setOnClickListener {
            val cm = CookieManager.getInstance()
            val urls = listOfNotNull(web.url, "https://www.terabox.com", "https://terabox.com")
            var found: String? = null
            for (u in urls) {
                val ck = cm.getCookie(u) ?: continue
                for (part in ck.split(";")) {
                    val t = part.trim()
                    if (t.startsWith("ndus=")) found = t.removePrefix("ndus=")
                }
                if (!found.isNullOrEmpty()) break
            }
            if (found.isNullOrEmpty()) {
                Toast.makeText(this, "ndus कुकी नहीं मिली — पहले पूरा लॉगिन करो", Toast.LENGTH_LONG).show()
            } else {
                getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("ndus", found).apply()
                contentResolver.notifyChange(DocumentsContract.buildRootsUri("com.example.cloudplay.documents"), null)
                Toast.makeText(this, "TeraBox जुड़ गया", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(info, LinearLayout.LayoutParams(match, wrap))
        root.addView(web, LinearLayout.LayoutParams(match, 0, 1f))
        root.addView(reload, LinearLayout.LayoutParams(match, wrap))
        root.addView(btn, LinearLayout.LayoutParams(match, wrap))
        setContentView(root)
        web.loadUrl("https://www.terabox.com/wap/outlogin/login")
    }
}
