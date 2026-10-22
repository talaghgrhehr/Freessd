package com.example.cloudplay

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

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

        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        web.loadUrl("https://www.terabox.com/wap/outlogin/login")
    }
}
