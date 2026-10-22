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
    private val host = "https://www.terabox.com"
    private val ua = "terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox"
    private val cookie = "lang=en; ndus=" + ndus
    private val fsIds = ConcurrentHashMap<String, Long>()
    private val known = ConcurrentHashMap<String, CloudEntry>()

    private fun note(msg: String) {
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("last_status", msg).apply()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun json(path: String, post: String?): JSONObject {
        val c = URL(host + path).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Cookie", cookie)
        try {
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(post.toByteArray()) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP " + code)
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    override fun list(parentId: String): List<CloudEntry> {
        val out = ArrayList<CloudEntry>()
        try {
            val body = "order=name&desc=0&dir=" + enc(parentId) + "&num=20000&page=1&showempty=0"
            val j = json("/api/list", body)
            val errno = j.optInt("errno", 0)
            if (errno != 0) {
                note("list errno=" + errno + " (दोबारा लॉगिन करके देखो)")
                return out
            }
            val arr = j.optJSONArray("list") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("server_filename", "")
                if (name.isEmpty()) continue
                var path = o.optString("path", "")
                if (path.isEmpty()) path = parentId.trimEnd('/') + "/" + name
                val e = CloudEntry(path, name, o.optInt("isdir", 0) == 1, o.optLong("size", 0L), o.optLong("server_mtime", 0L) * 1000)
                known[path] = e
                fsIds[path] = o.optLong("fs_id", 0L)
                out.add(e)
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

    private fun signDownload(s1: String, s2: String): String {
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

    override fun download(entry: CloudEntry, dest: File) {
        try {
            val fsid = fsIds[entry.id] ?: throw IOException("fs_id नहीं मिला")
            val home = json("/api/home/info", null)
            if (home.optInt("errno", -1) != 0) throw IOException("home/info errno=" + home.optInt("errno", -1))
            val d = home.getJSONObject("data")
            val sign = signDownload(d.getString("sign3"), d.getString("sign1"))
            val ts = d.get("timestamp").toString()
            val body = "fidlist=" + enc("[" + fsid + "]") + "&type=dlink&vip=2&sign=" + enc(sign) +
                "&timestamp=" + enc(ts) + "&need_speed=1"
            val r = json("/api/download", body)
            if (r.optInt("errno", -1) != 0) throw IOException("download errno=" + r.optInt("errno", -1))
            var link = ""
            val arr = r.optJSONArray("dlink")
            if (arr != null && arr.length() > 0) link = arr.getJSONObject(0).optString("dlink", "")
            if (link.isEmpty()) link = r.optString("dlink", "")
            if (link.isEmpty()) throw IOException("dlink नहीं मिला")

            val c = URL(link).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Cookie", cookie)
            try {
                if (c.responseCode !in 200..299) throw IOException("file HTTP " + c.responseCode)
                c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            } finally {
                c.disconnect()
            }
            note("download OK: " + entry.name)
        } catch (e: Exception) {
            note("download error: " + e.message)
            throw e
        }
    }
}
