package com.example.cloudplay

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(TextView(this).apply {
            textSize = 16f
            text = "CloudPlay Mobile\n\nFiles ऐप खोलो, फिर तीन-लाइन मेनू से \"CloudPlay (TeraBox)\" चुनो।\n\nकैश साइज़ (GB):"
        })
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
                Toast.makeText(this@MainActivity, "सेव: $v GB", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Files ऐप खोलो"
            setOnClickListener {
                try { startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }) }
                catch (e: Exception) { Toast.makeText(this@MainActivity, "Files ऐप नहीं मिला", Toast.LENGTH_SHORT).show() }
            }
        })
        setContentView(root)
    }
}
