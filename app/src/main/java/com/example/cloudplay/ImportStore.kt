package com.example.cloudplay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

object ImportStore {
    data class Item(val uri: String, val name: String, val size: Long, val modified: Long)

    private fun prefs(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun all(c: Context): MutableList<Item> {
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(prefs(c).getString("imports", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.getString("uri"), o.getString("name"), o.optLong("size"), o.optLong("modified")))
            }
        } catch (e: Exception) { }
        return out
    }

    fun save(c: Context, items: List<Item>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().put("uri", item.uri).put("name", item.name).put("size", item.size).put("modified", item.modified))
        }
        prefs(c).edit().putString("imports", arr.toString()).apply()
    }

    fun remove(c: Context, index: Int) {
        val items = all(c)
        if (index in items.indices) {
            items.removeAt(index)
            save(c, items)
        }
    }

    fun add(c: Context, uris: List<Uri>): Int {
        val items = all(c)
        var added = 0
        for (u in uris) {
            val s = u.toString()
            if (items.any { it.uri == s }) continue
            try {
                c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            var name = "file"
            var size = 0L
            var mod = 0L
            try {
                c.contentResolver.query(u, null, null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) {
                        val ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = cur.getColumnIndex(OpenableColumns.SIZE)
                        val mi = cur.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        if (ni >= 0 && !cur.isNull(ni)) name = cur.getString(ni)
                        if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si)
                        if (mi >= 0 && !cur.isNull(mi)) mod = cur.getLong(mi)
                    }
                }
            } catch (e: Exception) { }
            if (size <= 0L) {
                try {
                    c.contentResolver.openAssetFileDescriptor(u, "r")?.use { size = maxOf(it.length, 0L) }
                } catch (e: Exception) { }
            }
            if (mod <= 0L) mod = System.currentTimeMillis()
            var uniq = name
            var n = 2
            while (items.any { it.name == uniq }) {
                val dot = name.lastIndexOf('.')
                uniq = if (dot > 0) name.substring(0, dot) + " (" + n + ")" + name.substring(dot) else name + " (" + n + ")"
                n++
            }
            items.add(Item(s, uniq, size, mod))
            added++
        }
        save(c, items)
        return added
    }
}
