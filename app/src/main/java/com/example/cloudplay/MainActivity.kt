package com.example.cloudplay

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private val pick = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val n = ImportStore.add(this, uris)
            notifyDrive()
            Toast.makeText(this, n.toString() + " फ़ाइलें जुड़ीं", Toast.LENGTH_SHORT).show()
            render()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun notifyDrive() {
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(CloudDocumentsProvider.AUTH), null)
        contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(CloudDocumentsProvider.AUTH, "/"), null)
    }

    private fun fmt(b: Long): String {
        if (b >= 1073741824L) return String.format("%.2f GB", b / 1073741824.0)
        if (b >= 1048576L) return String.format("%.1f MB", b / 1048576.0)
        if (b >= 1024L) return String.format("%d KB", b / 1024L)
        return b.toString() + " B"
    }

    private fun rounded(color: Int, radius: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(18), dp(16), dp(18), dp(16))
        l.background = rounded(Color.parseColor("#1AFFFFFF"), 22, Color.parseColor("#26FFFFFF"))
        return l
    }

    private fun lp(top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView {
        val b = tv(label, 16f, Color.WHITE, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(16), dp(16), dp(16), dp(16))
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#00B8D4")))
        g.cornerRadius = dp(28).toFloat()
        b.background = g
        b.setOnClickListener { onClick() }
        return b
    }

    private fun ghostButton(label: String, onClick: () -> Unit): TextView {
        val b = tv(label, 14f)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(14), dp(12), dp(14), dp(12))
        b.background = rounded(Color.parseColor("#14FFFFFF"), 24, Color.parseColor("#40FFFFFF"))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun cacheDirSize(): Long {
        val files = File(cacheDir, "cloud").listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0B0C1E")
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val ndus = prefs.getString("ndus", null)
        val items = ImportStore.all(this)
        val muted = Color.parseColor("#B0B3C7")

        val scroll = ScrollView(this)
        scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0B0C1E"), Color.parseColor("#1A1040"), Color.parseColor("#0B0C1E")))
        scroll.isFillViewport = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(36), dp(20), dp(32))
        scroll.addView(col)

        col.addView(tv("CloudPlay", 34f, Color.WHITE, true))
        col.addView(tv("तुम्हारा TeraBox, फ़ोन की ड्राइव की तरह", 14f, muted), lp(2))

        // स्थिति कार्ड
        val st = card()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val dot = View(this)
        val dg = GradientDrawable()
        dg.shape = GradientDrawable.OVAL
        val on = !ndus.isNullOrEmpty() || items.isNotEmpty()
        dg.setColor(Color.parseColor(if (on) "#00E676" else "#FFB300"))
        dot.background = dg
        row.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        val msg = if (!ndus.isNullOrEmpty()) "TeraBox लॉगिन चालू (प्रयोगात्मक)"
            else if (items.isNotEmpty()) items.size.toString() + " फ़ाइलें जुड़ी हैं"
            else "अभी कुछ जुड़ा नहीं है"
        val msgView = tv(msg, 16f, Color.WHITE, true)
        msgView.setPadding(dp(12), 0, 0, 0)
        row.addView(msgView)
        st.addView(row)
        col.addView(st, lp(22))

        col.addView(primaryButton("＋  TeraBox से फ़ाइलें जोड़ो") { pick.launch(arrayOf("*/*")) }, lp(16))
        col.addView(tv("खुलने वाली लिस्ट में ☰ दबाकर TeraBox चुनो, फ़ोल्डर खोलो और फ़ाइलें चुनो।", 12f, muted), lp(8))

        // जुड़ी फ़ाइलें
        if (items.isNotEmpty()) {
            val fc = card()
            fc.addView(tv("जुड़ी फ़ाइलें", 15f, Color.WHITE, true))
            for ((idx, item) in items.withIndex()) {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                val name = tv(item.name, 14f)
                name.maxLines = 1
                name.ellipsize = TextUtils.TruncateAt.END
                r.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val sz = tv(fmt(item.size), 12f, muted)
                sz.setPadding(dp(8), 0, dp(8), 0)
                r.addView(sz)
                val x = tv("✕", 16f, Color.parseColor("#FF8A80"), true)
                x.setPadding(dp(10), dp(8), dp(4), dp(8))
                x.setOnClickListener {
                    ImportStore.remove(this, idx)
                    notifyDrive()
                    render()
                }
                r.addView(x)
                fc.addView(r, lp(8))
            }
            col.addView(fc, lp(16))
        }

        // कैश कार्ड
        val limitGb = prefs.getInt("cache_gb", 5)
        val used = cacheDirSize()
        val cc = card()
        cc.addView(tv("कैश", 15f, Color.WHITE, true))
        cc.addView(tv(fmt(used) + " इस्तेमाल  /  " + limitGb + " GB सीमा", 13f, muted), lp(4))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        val limitBytes = limitGb * 1073741824L
        bar.progress = if (limitBytes > 0) ((used * 1000L) / limitBytes).toInt().coerceIn(0, 1000) else 0
        bar.progressTintList = ColorStateList.valueOf(Color.parseColor("#7C4DFF"))
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        cc.addView(bar, lp(10))

        val gbRow = LinearLayout(this)
        gbRow.orientation = LinearLayout.HORIZONTAL
        gbRow.gravity = Gravity.CENTER_VERTICAL
        val gb = EditText(this)
        gb.inputType = InputType.TYPE_CLASS_NUMBER
        gb.setText(limitGb.toString())
        gb.setTextColor(Color.WHITE)
        gb.background = rounded(Color.parseColor("#22FFFFFF"), 14)
        gb.setPadding(dp(14), dp(10), dp(14), dp(10))
        gbRow.addView(gb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val save = ghostButton("सेव GB") {
            val v = gb.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 5
            prefs.edit().putInt("cache_gb", v).apply()
            render()
        }
        val sp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sp.leftMargin = dp(10)
        gbRow.addView(save, sp)
        cc.addView(gbRow, lp(12))
        cc.addView(ghostButton("कैश साफ़ करो") {
            File(cacheDir, "cloud").listFiles()?.forEach { it.delete() }
            render()
        }, lp(10))
        col.addView(cc, lp(16))

        col.addView(ghostButton("Files में CloudPlay ड्राइव खोलो") {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            })
        }, lp(16))

        // एडवांस्ड
        val adv = tv(if (ndus.isNullOrEmpty()) "एडवांस्ड: वेब लॉगिन (प्रयोगात्मक)" else "लॉगआउट (प्रयोगात्मक वेब लॉगिन)", 12f, muted)
        adv.gravity = Gravity.CENTER
        adv.setPadding(dp(8), dp(16), dp(8), dp(8))
        adv.setOnClickListener {
            if (ndus.isNullOrEmpty()) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                prefs.edit().remove("ndus").remove("last_status").apply()
                CookieManager.getInstance().removeAllCookies(null)
                notifyDrive()
                render()
            }
        }
        col.addView(adv, lp(8))
        val last = prefs.getString("last_status", null)
        if (!last.isNullOrEmpty()) col.addView(tv(last, 11f, muted), lp(4))

        setContentView(scroll)
    }
}
