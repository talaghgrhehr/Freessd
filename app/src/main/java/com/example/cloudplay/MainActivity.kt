package com.example.cloudplay

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.webkit.CookieManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private fun refresh() {
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        val on = !p.getString("ndus", null).isNullOrEmpty()
        val last = p.getString("last_status", "—")
        status.text = (if (on) "स्थिति: TeraBox लॉगिन है" else "स्थिति: डेमो मोड (लॉगिन नहीं)") + "\nआख़िरी संदेश: " + last
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        status = TextView(this).apply { textSize = 15f; setTextIsSelectable(true) }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "TeraBox लॉगिन"
            setOnClickListener { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) }
        })
        root.addView(Button(this).apply {
            text = "लॉगआउट (डेमो मोड)"
            setOnClickListener {
                prefs.edit().remove("ndus").remove("last_status").apply()
                CookieManager.getInstance().removeAllCookies(null)
                contentResolver.notifyChange(DocumentsContract.buildRootsUri("com.example.cloudplay.documents"), null)
                refresh()
            }
        })

        root.addView(TextView(this).apply { text = "\nकैश साइज़ (GB):" })
        val gb = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("cache_gb", 5).toString())
        }
        root.addView(gb)
        root.addView(Button(this).apply {
            text = "कैश साइज़ सेव करो"
            setOnClickListener {
                val v = gb.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 5
                prefs.edit().putInt("cache_gb", v).apply()
                Toast.makeText(this@MainActivity, "सेव: " + v + " GB", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "फ़ाइल चुनकर टेस्ट करो"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" })
            }
        })
        setContentView(root)
        refresh()
    }
}
