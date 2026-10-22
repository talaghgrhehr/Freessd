package com.example.cloudplay

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class TeraBoxBackend(private val ctx: Context, ndus: String) : CloudBackend {
    private val prefs = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private var base = prefs.getString("tb_base", null) ?: "https://www.terabox.com"
    private val cookie = prefs.getString("tb_cookie", null) ?: ("lang=en; ndus=" + ndus)
    private val ua = prefs.getString("tb_ua", null) ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private var js = prefs.getString("tb_js", "") ?: ""
    private val fsIds = ConcurrentHashMap<String, Long>()
    private val known = ConcurrentHashMap<String, CloudEntry>()

    private fun note(msg: String) { prefs.edit().putString("last_status", msg).apply() }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun conn(url: String, post: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Cookie", cookie)
        c.setRequestProperty("Referer", base.trimEnd('/') + "/")
        c.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        c.setRequestProperty("Accept", "application/json, text/plain, */*")
        if (post != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            c.outputStream.use { it.write(post.toByteArray()) }
        }
        return c
    }

    private fun refreshJs() {
        val c = conn(base.trimEnd('/') + "/main", null)
        try {
            val html = c.inputStream.bufferedReader().readText()
            val m = Regex("function%20fn%28a%29%7Bwindow.jsToken%20%3D%20a%7D%3Bfn%28%22(.*?)%22%29").find(html)
            val t = m?.groupValues?.get(1)
            if (t.isNullOrEmpty()) throw IOException("jsToken नहीं मिला")
            js = t
            prefs.edit().putString("tb_js", t).apply()
        } finally {
            c.disconnect()
        }
    }

    private fun api(path: String, q: String, post: String? = null): JSONObject {
        if (js.isEmpty()) { try { refreshJs() } catch (e: Exception) { } }
        var jsTried = false
        var hostTried = false
        while (true) {
            val url = base.trimEnd('/') + path + "?" + q + (if (q.isEmpty()) "" else "&") +
                "app_id=250528&channel=dubox&clienttype=0" + (if (js.isNotEmpty()) "&jsToken=" + enc(js) else "")
            val c = conn(url, post)
            try {
                val code = c.responseCode
                if (code in 300..399) throw IOException("सेशन खत्म (redirect), दोबारा लॉगिन करो")
                val prefix = c.getHeaderField("Url-Domain-Prefix")
                if (code !in 200..299) throw IOException("HTTP " + code)
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                val e = j.optInt("errno", 0)
                if ((e == 4000023 || e == 450016) && !jsTried) {
                    jsTried = true
                    refreshJs()
                    continue
                }
                if (e == -6 && !prefix.isNullOrEmpty() && !hostTried && Regex("^[a-z0-9-]{1,40}$").matches(prefix)) {
                    hostTried = true
                    val parts = URL(base).host.split('.')
                    if (parts.size >= 2) {
                        base = "https://" + prefix + "." + parts[parts.size - 2] + "." + parts[parts.size - 1]
                        prefs.edit().putString("tb_base", base).apply()
                        continue
                    }
                }
                return j
            } finally {
                c.disconnect()
            }
        }
    }

    override fun list(parentId: String): List<CloudEntry> {
        val out = ArrayList<CloudEntry>()
        try {
            var page = 1
            while (true) {
                val j = api("/api/list", "dir=" + enc(parentId) + "&page=" + page + "&num=100")
                val errno = j.optInt("errno", 0)
                if (errno != 0) {
                    note("list errno=" + errno)
                    return out
                }
                val arr = j.optJSONArray("list") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("server_filename", "")
                    if (name.isEmpty()) continue
                    var path = o.optString("path", "")
                    if (path.isEmpty()) path = parentId.trimEnd('/') + "/" + name
                    val e = CloudEntry(path, name, o.optInt("isdir", 0) == 1, o.optLong("size", 0L), o.optLong("server_mtime", 0L) * 1000L)
                    known[path] = e
                    fsIds[path] = o.optLong("fs_id", 0L)
                    out.add(e)
                }
                if (arr.length() < 100) break
                page++
            }
            note("list OK: " + out.size + " items (" + parentId + ")")
        } catch (e: Exception) {
            note("list error: " + e.message)
        }
        return out
    }

    override fun stat(id: String): CloudEntry? {
        if (id == "/") return CloudEntry("/", "TeraBox", true, 0L, 0L)
        known[id]?.let { return it }
        var parent = id.substringBeforeLast('/', "")
        if (parent.isEmpty()) parent = "/"
        list(parent)
        return known[id]
    }

    private fun sign(s1: String, s2: String): String {
        val p = IntArray(256) { it }
        val a = IntArray(256) { s1[it % s1.length].code }
        var j = 0
        for (n in 0 until 256) {
            j = (j + p[n] + a[n]) % 256
            val t = p[n]; p[n] = p[j]; p[j] = t
        }
        var i = 0
        j = 0
        val out = ByteArray(s2.length)
        for (q in s2.indices) {
            i = (i + 1) % 256
            j = (j + p[i]) % 256
            val t = p[i]; p[i] = p[j]; p[j] = t
            val k = p[(p[i] + p[j]) % 256]
            out[q] = (s2[q].code xor k).toByte()
        }
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun dlink(fsid: Long, path: String): String {
        try {
            val j = api("/api/filemetas", "target=" + enc(JSONArray().put(path).toString()) + "&dlink=1")
            val l = j.optJSONArray("info")?.optJSONObject(0)?.optString("dlink", "") ?: ""
            if (l.isNotEmpty()) return l
        } catch (e: Exception) { }
        val h = api("/api/home/info", "")
        val d = h.getJSONObject("data")
        val s = sign(d.getString("sign3"), d.getString("sign1"))
        val ts = (System.currentTimeMillis() / 1000).toString()
        val r = api("/api/download", "type=dlink&vip=2&sign=" + enc(s) + "&timestamp=" + ts + "&need_speed=1&fidlist=" + enc("[" + fsid + "]"))
        if (r.optInt("errno", -1) != 0) throw IOException("download errno=" + r.optInt("errno", -1))
        val l = r.optJSONArray("dlink")?.optJSONObject(0)?.optString("dlink", "") ?: ""
        if (l.isEmpty()) throw IOException("dlink नहीं मिला")
        return l
    }

    override fun download(entry: CloudEntry, dest: File) {
        try {
            val fsid = fsIds[entry.id] ?: throw IOException("fs_id नहीं मिला")
            var url = dlink(fsid, entry.id)
            val site = URL(base).host.split('.').takeLast(2).joinToString(".")
            for (hop in 0..5) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.setRequestProperty("User-Agent", ua)
                if (URL(url).host.endsWith(site)) c.setRequestProperty("Cookie", cookie)
                try {
                    val code = c.responseCode
                    if (code in 300..399) {
                        url = URL(URL(url), c.getHeaderField("Location")).toString()
                        continue
                    }
                    if (code !in 200..299) throw IOException("file HTTP " + code)
                    c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
                    note("download OK: " + entry.name)
                    return
                } finally {
                    c.disconnect()
                }
            }
            throw IOException("बहुत ज़्यादा redirect")
        } catch (e: Exception) {
            note("download error: " + e.message)
            throw e
        }
    }
}
