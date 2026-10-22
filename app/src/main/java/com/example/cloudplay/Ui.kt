package com.example.cloudplay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CopyOnWriteArrayList

class Ui(private val c: Context) {
    val violet = Color.parseColor("#8B5CF6")
    val cyan = Color.parseColor("#22D3EE")
    val muted = Color.parseColor("#9AA0B8")
    val danger = Color.parseColor("#FF6B81")
    val ok = Color.parseColor("#34D399")
    val warn = Color.parseColor("#FBBF24")

    fun dp(v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun shape(color: Int, radius: Int, stroke: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        if (stroke != 0) g.setStroke(dp(1), stroke)
        return g
    }

    fun page(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#05060F"), Color.parseColor("#0F0B2E"), Color.parseColor("#061A26"))
    )

    fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun card(): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(18), dp(16), dp(18), dp(16))
        l.background = shape(Color.parseColor("#14FFFFFF"), 22, Color.parseColor("#26FFFFFF"))
        return l
    }

    fun heroCard(): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(20), dp(20), dp(20), dp(20))
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#6D3DF0"), Color.parseColor("#0E7C99"))
        )
        g.cornerRadius = dp(26).toFloat()
        l.background = g
        return l
    }

    fun lp(top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.topMargin = dp(top)
        return p
    }

    fun weight(left: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        p.leftMargin = dp(left)
        return p
    }

    fun gradButton(label: String, onClick: () -> Unit): TextView {
        val b = text(label, 16f, Color.WHITE, true)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(16), dp(15), dp(16), dp(15))
        val g = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#8B5CF6"), Color.parseColor("#06B6D4"))
        )
        g.cornerRadius = dp(28).toFloat()
        b.background = g
        b.setOnClickListener { onClick() }
        return b
    }

    fun ghost(label: String, onClick: () -> Unit): TextView {
        val b = text(label, 14f)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(12), dp(13), dp(12), dp(13))
        b.background = shape(Color.parseColor("#14FFFFFF"), 22, Color.parseColor("#40FFFFFF"))
        b.setOnClickListener { onClick() }
        return b
    }

    fun fmt(b: Long): String {
        if (b >= 1073741824L) return String.format("%.2f GB", b / 1073741824.0)
        if (b >= 1048576L) return String.format("%.1f MB", b / 1048576.0)
        if (b >= 1024L) return String.format("%d KB", b / 1024L)
        return b.toString() + " B"
    }
}

object Transfers {
    class Job(val title: String, val kind: String, total0: Long) {
        @Volatile var total: Long = total0
        @Volatile var done: Long = 0L
        @Volatile var state: String = "चल रहा है"
        @Volatile var error: String? = null
        @Volatile var finished: Boolean = false
    }

    val jobs = CopyOnWriteArrayList<Job>()

    fun add(title: String, kind: String, total: Long): Job {
        val j = Job(title, kind, total)
        jobs.add(0, j)
        while (jobs.size > 30) jobs.removeAt(jobs.size - 1)
        return j
    }

    fun running(): Boolean = jobs.any { !it.finished }
}

object Session {
    private var tb: TeraBoxBackend? = null
    private var key: String? = null

    fun ndus(c: Context): String? =
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("ndus", null)

    fun backend(c: Context): TeraBoxBackend? {
        val n = ndus(c)
        if (n.isNullOrEmpty()) return null
        if (tb == null || key != n) {
            tb = TeraBoxBackend(c.applicationContext, n)
            key = n
        }
        return tb
    }

    fun logout(c: Context) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().remove("ndus").remove("last_status").apply()
        tb = null
        key = null
    }
}
