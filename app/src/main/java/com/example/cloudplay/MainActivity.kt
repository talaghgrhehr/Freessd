package com.example.cloudplay

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private val pick = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("tree_uri", uri.toString()).apply()
            contentResolver.notifyChange(DocumentsContract.buildRootsUri("com.example.cloudplay.documents"), null)
            refresh()
        }
    }

    private fun refresh() {
        val on = getSharedPreferences("cfg", MODE_PRIVATE).getString("tree_uri", null) != null
        status.text = if (on) "स्थिति: TeraBox जुड़ा है" else "स्थिति: डेमो मोड (TeraBox नहीं जुड़ा)"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "TeraBox जोड़ो"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "लिस्ट में TeraBox चुनो, फिर \"इस फ़ोल्डर का उपयोग करें\" दबाओ", Toast.LENGTH_LONG).show()
                pick.launch(null)
            }
        })
        root.addView(Button(this).apply {
            text = "TeraBox हटाओ (डेमो मोड)"
            setOnClickListener {
                prefs.edit().remove("tree_uri").apply()
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
                Toast.makeText(this@MainActivity, "सेव: $v GB", Toast.LENGTH_SHORT).show()
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
