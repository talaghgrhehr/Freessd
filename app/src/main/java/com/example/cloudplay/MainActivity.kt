package com.example.cloudplay

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var ui: Ui
    private lateinit var body: FrameLayout
    private lateinit var tabBar: LinearLayout
    private val tabViews = ArrayList<TextView>()
    private val h = Handler(Looper.getMainLooper())

    private var tab = 0
    private var curDir = "/"
    private var listing: List<CloudEntry>? = null
    private var listError: String? = null
    private var loading = false
    private var quota: LongArray? = null
    private var quotaError: String? = null

    private val tick = object : Runnable {
        override fun run() {
            if (tab == 2 || tab == 0) render()
        }
    }

    private val pickUpload = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) startUploads(uris)
    }

    private val pickImport = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val n = ImportStore.add(this, uris)
            notifyDrive()
            Toast.makeText(this, n.toString() + " फ़ाइलें जुड़ीं", Toast.LENGTH_SHORT).show()
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.statusBarColor = Color.parseColor("#05060F")
        window.navigationBarColor = Color.parseColor("#12142A")
        buildShell()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (Session.backend(this) != null) {
            if (listing == null && !loading) loadDir(curDir)
            if (quota == null) loadQuota()
        }
        render()
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        super.onDestroy()
    }

    // ---------------- ढाँचा ----------------
    private fun buildShell() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = ui.page()

        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(8))
        tabBar.background = ui.shape(Color.parseColor("#12142A"), 0)
        val names = arrayOf("🏠\nहोम", "📁\nफ़ाइलें", "⇅\nट्रांसफ़र", "💾\nकैश", "👤\nअकाउंट")
        for (i in names.indices) {
            val t = ui.text(names[i], 11f, ui.muted)
            t.gravity = Gravity.CENTER
            t.setPadding(0, ui.dp(8), 0, ui.dp(8))
            t.setOnClickListener {
                tab = i
                render()
            }
            tabViews.add(t)
            tabBar.addView(t, ui.weight())
        }
        root.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun render() {
        for (i in tabViews.indices) {
            val on = i == tab
            tabViews[i].setTextColor(if (on) Color.WHITE else ui.muted)
            tabViews[i].background = if (on) ui.shape(Color.parseColor("#33FFFFFF"), 16) else null
        }
        body.removeAllViews()
        val v = when (tab) {
            0 -> homeView()
            1 -> filesView()
            2 -> transfersView()
            3 -> cacheView()
            else -> accountView()
        }
        body.addView(v)
        if (Transfers.running()) {
            h.removeCallbacks(tick)
            h.postDelayed(tick, 800)
        }
    }

    private fun scroller(): Pair<ScrollView, LinearLayout> {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(20), ui.dp(32), ui.dp(20), ui.dp(24))
        sv.addView(col)
        return Pair(sv, col)
    }

    private fun header(col: LinearLayout, title: String, sub: String) {
        col.addView(ui.text(title, 30f, Color.WHITE, true))
        col.addView(ui.text(sub, 13f, ui.muted), ui.lp(2))
    }

    private fun toast(s: String) {
        runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    }

    private fun notifyDrive() {
        contentResolver.notifyChange(DocumentsContract.buildRootsUri(CloudDocumentsProvider.AUTH), null)
        contentResolver.notifyChange(DocumentsContract.buildChildDocumentsUri(CloudDocumentsProvider.AUTH, "/"), null)
    }

    private fun cacheUsed(): Long {
        val files = File(cacheDir, "cloud").listFiles() ?: return 0L
        return files.sumOf { it.length() }
    }

    // ---------------- डेटा लोड ----------------
    private fun loadDir(dir: String) {
        val b = Session.backend(this) ?: return
        loading = true
        listError = null
        curDir = dir
        render()
        Thread {
            try {
                val r = b.listOrThrow(dir)
                runOnUiThread {
                    listing = r
                    loading = false
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    listing = ArrayList()
                    listError = e.message
                    loading = false
                    render()
                }
            }
        }.start()
    }

    private fun loadQuota() {
        val b = Session.backend(this) ?: return
        Thread {
            try {
                val q = b.quota()
                runOnUiThread {
                    quota = q
                    quotaError = null
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    quotaError = e.message
                    render()
                }
            }
        }.start()
    }

    // ---------------- होम ----------------
    private fun homeView(): View {
        val (sv, col) = scroller()
        val logged = Session.backend(this) != null
        header(col, "CloudPlay", "तुम्हारा TeraBox, फ़ोन की ड्राइव की तरह")

        val hero = ui.heroCard()
        if (logged) {
            hero.addView(ui.text("● कनेक्टेड", 12f, Color.parseColor("#B9F6CA"), true))
            val q = quota
            if (q != null && q[0] > 0L) {
                hero.addView(ui.text(ui.fmt(q[1]) + " इस्तेमाल", 26f, Color.WHITE, true), ui.lp(8))
                hero.addView(ui.text(ui.fmt(q[0]) + " में से", 13f, Color.parseColor("#DDE3FF")), ui.lp(2))
                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                bar.max = 1000
                bar.progress = ((q[1] * 1000L) / q[0]).toInt().coerceIn(0, 1000)
                bar.progressTintList = ColorStateList.valueOf(Color.WHITE)
                bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
                hero.addView(bar, ui.lp(12))
            } else {
                hero.addView(ui.text("स्टोरेज की जानकारी लोड हो रही है…", 15f, Color.WHITE, true), ui.lp(8))
                if (quotaError != null) hero.addView(ui.text(quotaError ?: "", 11f, Color.parseColor("#DDE3FF")), ui.lp(4))
            }
        } else {
            hero.addView(ui.text("शुरू करो", 12f, Color.parseColor("#DDE3FF"), true))
            hero.addView(ui.text("TeraBox से जुड़ो", 24f, Color.WHITE, true), ui.lp(6))
            hero.addView(ui.text("एक बार लॉगिन करो, फिर फ़ाइलें अपने आप दिखेंगी।", 13f, Color.parseColor("#DDE3FF")), ui.lp(4))
            hero.addView(ui.ghost("लॉगिन करो") { startActivity(Intent(this, LoginActivity::class.java)) }, ui.lp(14))
        }
        col.addView(hero, ui.lp(20))

        // तेज़ काम
        val r1 = LinearLayout(this)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(quick("⬆", "अपलोड") { if (needLogin()) { tab = 1; pickUpload.launch(arrayOf("*/*")) } }, ui.weight())
        r1.addView(quick("📁", "नया फ़ोल्डर") { if (needLogin()) { tab = 1; newFolderDialog() } }, ui.weight(10))
        col.addView(r1, ui.lp(16))
        val r2 = LinearLayout(this)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(quick("⟳", "रीफ़्रेश") { if (needLogin()) { loadDir(curDir); loadQuota() } }, ui.weight())
        r2.addView(quick("🧹", "कैश साफ़") { clearCache(); render() }, ui.weight(10))
        col.addView(r2, ui.lp(10))

        // स्मार्ट सुझाव (नियमों पर आधारित)
        val tips = ArrayList<String>()
        if (!logged) tips.add("लॉगिन करो ताकि फ़ाइलें अपने आप लोड हों।")
        val limit = getSharedPreferences("cfg", MODE_PRIVATE).getInt("cache_gb", 5) * 1073741824L
        val used = cacheUsed()
        if (limit > 0 && used * 100 / limit >= 80) tips.add("कैश " + (used * 100 / limit) + "% भर चुका है। साफ़ करो या सीमा बढ़ाओ।")
        val q = quota
        if (q != null && q[0] > 0L && q[1] * 100 / q[0] >= 90) tips.add("TeraBox स्टोरेज 90% से ज़्यादा भरा है।")
        if (tips.isEmpty()) tips.add("सब ठीक चल रहा है।")
        val sc = ui.card()
        sc.addView(ui.text("✦ स्मार्ट सुझाव", 15f, Color.WHITE, true))
        for (t in tips) sc.addView(ui.text("• " + t, 13f, ui.muted), ui.lp(8))
        col.addView(sc, ui.lp(16))

        // हाल के ट्रांसफ़र
        if (Transfers.jobs.isNotEmpty()) {
            val tc = ui.card()
            tc.addView(ui.text("हाल की गतिविधि", 15f, Color.WHITE, true))
            for (j in Transfers.jobs.take(3)) {
                tc.addView(ui.text(j.kind + " " + j.title + " — " + j.state, 13f, ui.muted), ui.lp(8))
            }
            col.addView(tc, ui.lp(16))
        }
        return sv
    }

    private fun quick(icon: String, label: String, onClick: () -> Unit): TextView {
        val t = ui.text(icon + "\n" + label, 14f, Color.WHITE, true)
        t.gravity = Gravity.CENTER
        t.setPadding(ui.dp(8), ui.dp(18), ui.dp(8), ui.dp(18))
        t.background = ui.shape(Color.parseColor("#14FFFFFF"), 22, Color.parseColor("#26FFFFFF"))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun needLogin(): Boolean {
        if (Session.backend(this) == null) {
            Toast.makeText(this, "पहले लॉगिन करो", Toast.LENGTH_SHORT).show()
            tab = 4
            render()
            return false
        }
        return true
    }

    // ---------------- फ़ाइलें ----------------
    private fun filesView(): View {
        val (sv, col) = scroller()
        header(col, "फ़ाइलें", "तुम्हारा TeraBox, अपने आप लोड")
        val b = Session.backend(this)
        if (b == null) {
            val c = ui.card()
            c.addView(ui.text("अभी लॉगिन नहीं है", 16f, Color.WHITE, true))
            c.addView(ui.text("लॉगिन के बाद फ़ाइलें यहाँ अपने आप आ जाएँगी, चुननी नहीं पड़ेंगी।", 13f, ui.muted), ui.lp(6))
            c.addView(ui.gradButton("लॉगिन करो") { startActivity(Intent(this, LoginActivity::class.java)) }, ui.lp(14))
            col.addView(c, ui.lp(20))
            return sv
        }

        col.addView(ui.text("📍 " + curDir, 13f, ui.cyan), ui.lp(14))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.addView(ui.gradButton("＋ अपलोड") { pickUpload.launch(arrayOf("*/*")) }, ui.weight())
        bar.addView(ui.ghost("📁 नया") { newFolderDialog() }, ui.weight(8))
        bar.addView(ui.ghost("⟳") { loadDir(curDir) }, ui.weight(8))
        col.addView(bar, ui.lp(12))

        if (curDir != "/") {
            val up = ui.ghost("⬆ ऊपर वाला फ़ोल्डर") {
                val p = curDir.trimEnd('/').substringBeforeLast('/', "")
                loadDir(if (p.isEmpty()) "/" else p)
            }
            col.addView(up, ui.lp(10))
        }

        if (loading) {
            col.addView(ui.text("लोड हो रहा है…", 14f, ui.muted), ui.lp(20))
            return sv
        }
        val err = listError
        if (err != null) {
            val c = ui.card()
            c.addView(ui.text("लिस्ट नहीं आई", 15f, ui.danger, true))
            c.addView(ui.text(err, 12f, ui.muted), ui.lp(6))
            c.addView(ui.text("अगर errno आए तो अकाउंट टैब में लॉगआउट करके दोबारा लॉगिन करो।", 12f, ui.muted), ui.lp(6))
            col.addView(c, ui.lp(14))
            return sv
        }
        val items = listing ?: ArrayList()
        if (items.isEmpty()) {
            col.addView(ui.text("यह फ़ोल्डर खाली है।", 14f, ui.muted), ui.lp(20))
        }
        val card = ui.card()
        var first = true
        for (e in items) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, ui.dp(10), 0, ui.dp(10))
            row.addView(ui.text(if (e.isDir) "📁" else "📄", 20f))
            val name = ui.text(e.name, 14f)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            name.setPadding(ui.dp(12), 0, ui.dp(8), 0)
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!e.isDir) row.addView(ui.text(ui.fmt(e.size), 12f, ui.muted))
            val more = ui.text("⋮", 20f, ui.muted, true)
            more.setPadding(ui.dp(12), 0, 0, 0)
            more.setOnClickListener { entryMenu(e) }
            row.addView(more)
            row.setOnClickListener { if (e.isDir) loadDir(e.id) else entryMenu(e) }
            if (!first) {
                val line = View(this)
                line.setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                card.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }
            first = false
            card.addView(row)
        }
        if (items.isNotEmpty()) col.addView(card, ui.lp(14))
        return sv
    }

    private fun entryMenu(e: CloudEntry) {
        val opts = if (e.isDir) arrayOf("खोलो", "डिलीट करो") else arrayOf("खोलो", "कैश में उतारो", "डिलीट करो")
        AlertDialog.Builder(this)
            .setTitle(e.name)
            .setItems(opts) { _, w ->
                val pick = opts[w]
                if (pick == "खोलो") {
                    if (e.isDir) loadDir(e.id) else openFile(e)
                } else if (pick == "कैश में उतारो") {
                    downloadToCache(e)
                } else {
                    confirmDelete(e)
                }
            }
            .show()
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    private fun openFile(e: CloudEntry) {
        val uri = DocumentsContract.buildDocumentUri(CloudDocumentsProvider.AUTH, e.id)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, mimeOf(e.name))
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(i, e.name))
        } catch (x: ActivityNotFoundException) {
            Toast.makeText(this, "इस फ़ाइल को खोलने वाला ऐप नहीं मिला", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadToCache(e: CloudEntry) {
        val b = Session.backend(this) ?: return
        val job = Transfers.add(e.name, "⬇", e.size)
        Toast.makeText(this, "डाउनलोड शुरू", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                CacheManager(this).get(b, e)
                job.done = job.total
                job.state = "कैश में तैयार"
            } catch (x: Exception) {
                job.error = x.message
                job.state = "फ़ेल"
            } finally {
                job.finished = true
                runOnUiThread { render() }
            }
        }.start()
        render()
    }

    private fun confirmDelete(e: CloudEntry) {
        AlertDialog.Builder(this)
            .setTitle("डिलीट करें?")
            .setMessage(e.name + "\n\nयह TeraBox से हटेगा।")
            .setNegativeButton("रद्द", null)
            .setPositiveButton("डिलीट") { _, _ ->
                val b = Session.backend(this)
                if (b != null) {
                    Thread {
                        try {
                            b.delete(listOf(e.id))
                            toast("डिलीट हो गया")
                        } catch (x: Exception) {
                            toast("डिलीट फ़ेल: " + x.message)
                        }
                        runOnUiThread {
                            loadDir(curDir)
                            loadQuota()
                        }
                    }.start()
                }
            }
            .show()
    }

    private fun newFolderDialog() {
        val b = Session.backend(this) ?: return
        val input = EditText(this)
        input.hint = "फ़ोल्डर का नाम"
        AlertDialog.Builder(this)
            .setTitle("नया फ़ोल्डर")
            .setView(input)
            .setNegativeButton("रद्द", null)
            .setPositiveButton("बनाओ") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    Thread {
                        try {
                            b.mkdir(curDir.trimEnd('/') + "/" + n)
                        } catch (x: Exception) {
                            toast("फ़ोल्डर फ़ेल: " + x.message)
                        }
                        runOnUiThread { loadDir(curDir) }
                    }.start()
                }
            }
            .show()
    }

    // ---------------- अपलोड ----------------
    private fun nameSize(u: Uri): Pair<String, Long> {
        var n = "file"
        var s = 0L
        try {
            contentResolver.query(u, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) n = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) s = c.getLong(si)
                }
            }
        } catch (e: Exception) {
        }
        return Pair(n, s)
    }

    private fun startUploads(uris: List<Uri>) {
        val b = Session.backend(this) ?: return
        val dir = curDir
        val jobs = ArrayList<Triple<Uri, String, Transfers.Job>>()
        for (u in uris) {
            val (n, s) = nameSize(u)
            jobs.add(Triple(u, n, Transfers.add(n, "⬆", s)))
        }
        Toast.makeText(this, jobs.size.toString() + " फ़ाइल अपलोड शुरू", Toast.LENGTH_SHORT).show()
        Thread {
            for ((u, n, job) in jobs) {
                try {
                    b.upload(dir, n, { contentResolver.openInputStream(u) ?: throw java.io.IOException("फ़ाइल नहीं खुली") }) { done ->
                        job.done = done
                    }
                    job.done = job.total
                    job.state = "पूरा ✓"
                } catch (x: Exception) {
                    job.error = x.message
                    job.state = "फ़ेल"
                } finally {
                    job.finished = true
                }
                runOnUiThread { render() }
            }
            runOnUiThread {
                loadDir(dir)
                loadQuota()
            }
        }.start()
        tab = 2
        render()
    }

    // ---------------- ट्रांसफ़र ----------------
    private fun transfersView(): View {
        val (sv, col) = scroller()
        header(col, "ट्रांसफ़र", "अपलोड और डाउनलोड की हालत")
        if (Transfers.jobs.isEmpty()) {
            col.addView(ui.text("अभी कुछ नहीं चल रहा।", 14f, ui.muted), ui.lp(20))
            return sv
        }
        for (j in Transfers.jobs) {
            val c = ui.card()
            c.addView(ui.text(j.kind + " " + j.title, 14f, Color.WHITE, true))
            val st = if (j.error != null) j.state + ": " + j.error else j.state
            c.addView(ui.text(st, 12f, if (j.error != null) ui.danger else ui.muted), ui.lp(4))
            if (!j.finished && j.total > 0L) {
                val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
                pb.max = 1000
                pb.progress = ((j.done * 1000L) / j.total).toInt().coerceIn(0, 1000)
                pb.progressTintList = ColorStateList.valueOf(ui.violet)
                pb.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
                c.addView(pb, ui.lp(8))
                c.addView(ui.text(ui.fmt(j.done) + " / " + ui.fmt(j.total), 11f, ui.muted), ui.lp(2))
            } else if (!j.finished) {
                c.addView(ui.text("चल रहा है…", 11f, ui.muted), ui.lp(6))
            }
            col.addView(c, ui.lp(12))
        }
        return sv
    }

    // ---------------- कैश ----------------
    private fun clearCache() {
        File(cacheDir, "cloud").listFiles()?.forEach { it.delete() }
    }

    private fun cacheView(): View {
        val (sv, col) = scroller()
        header(col, "कैश", "फ़ोन में रुकी हुई फ़ाइलें")
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        val limitGb = prefs.getInt("cache_gb", 5)
        val used = cacheUsed()

        val c = ui.card()
        c.addView(ui.text(ui.fmt(used) + " इस्तेमाल", 22f, Color.WHITE, true))
        c.addView(ui.text(limitGb.toString() + " GB सीमा", 13f, ui.muted), ui.lp(2))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        val limitBytes = limitGb * 1073741824L
        bar.progress = if (limitBytes > 0) ((used * 1000L) / limitBytes).toInt().coerceIn(0, 1000) else 0
        bar.progressTintList = ColorStateList.valueOf(ui.violet)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        c.addView(bar, ui.lp(12))

        val gb = EditText(this)
        gb.inputType = InputType.TYPE_CLASS_NUMBER
        gb.setText(limitGb.toString())
        gb.setTextColor(Color.WHITE)
        gb.background = ui.shape(Color.parseColor("#22FFFFFF"), 14)
        gb.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(gb, ui.weight())
        row.addView(ui.ghost("सेव GB") {
            val v = gb.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 5
            prefs.edit().putInt("cache_gb", v).apply()
            render()
        }, ui.weight(10))
        c.addView(row, ui.lp(14))
        c.addView(ui.ghost("कैश साफ़ करो") { clearCache(); render() }, ui.lp(12))
        col.addView(c, ui.lp(18))

        col.addView(ui.text("कैश भरने पर सबसे पुरानी फ़ाइलें अपने आप हटती हैं।", 12f, ui.muted), ui.lp(12))
        return sv
    }

    // ---------------- अकाउंट ----------------
    private fun accountView(): View {
        val (sv, col) = scroller()
        header(col, "अकाउंट", "कनेक्शन और सुरक्षा")
        val logged = Session.backend(this) != null

        val c = ui.card()
        c.addView(ui.text(if (logged) "● TeraBox से जुड़ा है" else "○ जुड़ा नहीं है", 16f, if (logged) ui.ok else ui.warn, true))
        if (logged) {
            c.addView(ui.ghost("लॉगआउट") {
                Session.logout(this)
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                listing = null
                quota = null
                notifyDrive()
                render()
            }, ui.lp(14))
        } else {
            c.addView(ui.gradButton("TeraBox लॉगिन") { startActivity(Intent(this, LoginActivity::class.java)) }, ui.lp(14))
        }
        col.addView(c, ui.lp(18))

        val last = getSharedPreferences("cfg", MODE_PRIVATE).getString("last_status", null)
        if (!last.isNullOrEmpty()) {
            val lc = ui.card()
            lc.addView(ui.text("आख़िरी संदेश", 13f, Color.WHITE, true))
            val t = ui.text(last, 12f, ui.muted)
            t.setTextIsSelectable(true)
            lc.addView(t, ui.lp(4))
            col.addView(lc, ui.lp(12))
        }

        val ic = ui.card()
        ic.addView(ui.text("बिना लॉगिन वाला तरीका", 14f, Color.WHITE, true))
        ic.addView(ui.text("TeraBox ऐप से फ़ाइलें चुनकर जोड़ो। यह आधिकारिक रास्ता है।", 12f, ui.muted), ui.lp(4))
        ic.addView(ui.ghost("फ़ाइलें चुनो") { pickImport.launch(arrayOf("*/*")) }, ui.lp(10))
        col.addView(ic, ui.lp(12))

        val n = ui.card()
        n.addView(ui.text("⚠ ध्यान रखो", 14f, ui.warn, true))
        n.addView(ui.text("यह लॉगिन TeraBox का अनऑफ़िशियल रास्ता है। TeraBox इसे रोक सकता है या अकाउंट सीमित कर सकता है। पहले टेस्ट अकाउंट से चलाओ, और डिलीट करने से पहले हमेशा पुष्टि होती है।", 12f, ui.muted), ui.lp(4))
        col.addView(n, ui.lp(12))
        return sv
    }
}
