package com.example.cloudplay

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TeraBoxBackend(private val ctx: Context, ndus: String) : CloudBackend {
    private val host = "https://www.terabox.com"
    private var uhost = "https://c-all.terabox.com"
    private val ua = "terabox;1.40.0.132;PC;PC-Windows;10.0.26100;WindowsTeraBox"
    private val cookie = "lang=en; ndus=" + ndus
    private val appQ = "app_id=250528&web=1&channel=dubox&clienttype=0"
    private val fsIds = ConcurrentHashMap<String, Long>()
    private val known = ConcurrentHashMap<String, CloudEntry>()

    @Volatile private var jsToken = ""
    @Volatile private var hostChecked = false

    private fun note(msg: String) {
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit().putString("last_status", msg).apply()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(String.format("%02x", x.toInt() and 0xff))
        return sb.toString()
    }

    private fun call(url: String, post: String?): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
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

    private fun ensureToken(force: Boolean = false) {
        if (!force && jsToken.isNotEmpty()) return
        val c = URL(host + "/main").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Cookie", cookie)
        try {
            val html = c.inputStream.bufferedReader().readText()
            val m = Regex("%28%22([0-9A-Za-z]+)%22%29").find(html)
            val t = m?.groupValues?.get(1) ?: ""
            if (t.isEmpty()) throw IOException("jsToken नहीं मिला (दोबारा लॉगिन करो)")
            jsToken = t
        } finally {
            c.disconnect()
        }
    }

    private fun checkErrno(j: JSONObject, what: String) {
        val e = j.optInt("errno", 0)
        if (e != 0) throw IOException(what + " errno=" + e)
    }

    // ---------- लिस्ट ----------
    fun listOrThrow(parentId: String): List<CloudEntry> {
        val body = "order=name&desc=0&dir=" + enc(parentId) + "&num=20000&page=1&showempty=0"
        val j = call(host + "/api/list", body)
        checkErrno(j, "list")
        val out = ArrayList<CloudEntry>()
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
        out.sortWith(compareBy<CloudEntry>({ !it.isDir }, { it.name.lowercase() }))
        note("list OK: " + out.size + " items (" + parentId + ")")
        return out
    }

    override fun list(parentId: String): List<CloudEntry> {
        return try {
            listOrThrow(parentId)
        } catch (e: Exception) {
            note("list error: " + e.message)
            ArrayList()
        }
    }

    override fun stat(id: String): CloudEntry? {
        if (id == "/") return CloudEntry("/", "TeraBox", true, 0L, 0L)
        known[id]?.let { return it }
        var parent = id.substringBeforeLast('/', "")
        if (parent.isEmpty()) parent = "/"
        list(parent)
        return known[id]
    }

    // ---------- स्टोरेज ----------
    fun quota(): LongArray {
        val j = call(host + "/api/quota?checkfree=1&checkexpire=1", null)
        checkErrno(j, "quota")
        return longArrayOf(j.optLong("total", 0L), j.optLong("used", 0L))
    }

    // ---------- डिलीट / नया फ़ोल्डर ----------
    fun delete(paths: List<String>) {
        for (attempt in 0..1) {
            ensureToken(attempt > 0)
            val arr = JSONArray()
            for (p in paths) arr.put(p)
            val j = call(
                host + "/api/filemanager?" + appQ + "&jsToken=" + enc(jsToken) + "&onnest=fail&opera=delete",
                "filelist=" + enc(arr.toString())
            )
            val e = j.optInt("errno", 0)
            if (e == 450016 && attempt == 0) continue
            checkErrno(j, "delete")
            break
        }
        for (p in paths) known.remove(p)
        note("delete OK: " + paths.size)
    }

    fun mkdir(path: String) {
        val j = call(
            host + "/api/create?a=commit",
            "path=" + enc(path) + "&isdir=1&block_list=" + enc("[]")
        )
        checkErrno(j, "mkdir")
        note("mkdir OK: " + path)
    }

    // ---------- अपलोड ----------
    private fun locateUploadHost() {
        if (hostChecked) return
        hostChecked = true
        try {
            val j = call(host + "/rest/2.0/pcs/file?method=locateupload", null)
            if (!j.has("errno")) {
                val h = j.optString("host", "")
                if (h.isNotEmpty()) uhost = "https://" + h
            }
        } catch (e: Exception) {
        }
    }

    private fun readFull(ins: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = ins.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    private fun postChunk(path: String, uploadId: String, seq: Int, data: ByteArray, len: Int) {
        val url = uhost + "/rest/2.0/pcs/superfile2?method=upload&" + appQ +
            "&path=" + enc(path) + "&uploadid=" + enc(uploadId) + "&partseq=" + seq
        val boundary = "----cp" + System.nanoTime()
        val head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"blob\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
        val tail = ("\r\n--" + boundary + "--\r\n").toByteArray()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 120000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Cookie", cookie)
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        c.setFixedLengthStreamingMode(head.size + len + tail.size)
        try {
            c.outputStream.use {
                it.write(head)
                it.write(data, 0, len)
                it.write(tail)
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("chunk HTTP " + code)
            val txt = c.inputStream.bufferedReader().readText()
            val j = JSONObject(txt)
            if (j.has("error_code") && j.optInt("error_code", 0) != 0) throw IOException("chunk error_code=" + j.optInt("error_code"))
        } finally {
            c.disconnect()
        }
    }

    fun upload(dir: String, name: String, open: () -> InputStream, progress: (Long) -> Unit) {
        val CH = 4 * 1024 * 1024
        val path = dir.trimEnd('/') + "/" + name

        // पास 1: MD5 निकालो
        val full = MessageDigest.getInstance("MD5")
        val slice = MessageDigest.getInstance("MD5")
        val blocks = ArrayList<String>()
        var total = 0L
        open().use { ins ->
            val buf = ByteArray(CH)
            while (true) {
                val n = readFull(ins, buf)
                if (n <= 0) break
                full.update(buf, 0, n)
                if (blocks.isEmpty()) slice.update(buf, 0, minOf(n, 262144))
                val m = MessageDigest.getInstance("MD5")
                m.update(buf, 0, n)
                blocks.add(hex(m.digest()))
                total += n
                if (n < CH) break
            }
        }
        if (total <= 0L) throw IOException("फ़ाइल खाली है")
        val fullHex = hex(full.digest())
        val sliceHex = hex(slice.digest())
        val blockJson = JSONArray(blocks).toString()

        // precreate
        var pre: JSONObject? = null
        for (attempt in 0..1) {
            ensureToken(attempt > 0)
            val body = "path=" + enc(path) + "&autoinit=1&size=" + total +
                "&file_limit_switch_v34=true&block_list=" + enc(blockJson) + "&rtype=2" +
                "&content-md5=" + fullHex + "&slice-md5=" + sliceHex
            val j = call(host + "/api/precreate?" + appQ + "&jsToken=" + enc(jsToken), body)
            if (j.optInt("errno", 0) == 4000023 && attempt == 0) continue
            checkErrno(j, "precreate")
            pre = j
            break
        }
        val uploadId = pre?.optString("uploadid", "") ?: ""
        if (uploadId.isEmpty()) throw IOException("uploadid नहीं मिला: " + (pre?.toString() ?: ""))

        // पास 2: टुकड़े भेजो
        locateUploadHost()
        var sent = 0L
        open().use { ins ->
            val buf = ByteArray(CH)
            var seq = 0
            while (true) {
                val n = readFull(ins, buf)
                if (n <= 0) break
                var ok = false
                var last: Exception? = null
                for (t in 0..2) {
                    try {
                        postChunk(path, uploadId, seq, buf, n)
                        ok = true
                        break
                    } catch (e: Exception) {
                        last = e
                    }
                }
                if (!ok) throw IOException("टुकड़ा " + seq + " फ़ेल: " + (last?.message ?: ""))
                sent += n
                progress(sent)
                seq++
                if (n < CH) break
            }
        }

        // create
        val body = "path=" + enc(path) + "&size=" + total + "&isdir=0&content-md5=" + fullHex +
            "&slice-md5=" + sliceHex + "&block_list=" + enc(blockJson) + "&uploadid=" + enc(uploadId) + "&rtype=2"
        val r = call(host + "/api/create", body)
        checkErrno(r, "create")
        note("upload OK: " + name)
    }

    // ---------- डाउनलोड ----------
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
            if (!fsIds.containsKey(entry.id)) stat(entry.id)
            val fsid = fsIds[entry.id] ?: throw IOException("fs_id नहीं मिला")
            val home = call(host + "/api/home/info", null)
            if (home.optInt("errno", -1) != 0) throw IOException("home/info errno=" + home.optInt("errno", -1))
            val d = home.getJSONObject("data")
            val sign = signDownload(d.getString("sign3"), d.getString("sign1"))
            val ts = d.get("timestamp").toString()
            val body = "fidlist=" + enc("[" + fsid + "]") + "&type=dlink&vip=2&sign=" + enc(sign) +
                "&timestamp=" + enc(ts) + "&need_speed=1"
            val r = call(host + "/api/download", body)
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
