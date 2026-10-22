package com.example.cloudplay

import android.content.Context
import java.io.File

class CacheManager(private val ctx: Context, private val backend: CloudBackend) {
    private val dir = File(ctx.cacheDir, "cloud").apply { mkdirs() }

    private fun limitBytes(): Long =
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).getInt("cache_gb", 5) * 1024L * 1024L * 1024L

    @Synchronized
    fun get(e: CloudEntry): File {
        val f = File(dir, "${e.id.hashCode()}_${e.size}")
        if (f.exists() && f.length() == e.size) {
            f.setLastModified(System.currentTimeMillis())
            return f
        }
        val tmp = File(dir, f.name + ".part")
        backend.download(e, tmp)
        tmp.renameTo(f)
        evict(f)
        return f
    }

    private fun evict(keep: File) {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && it != keep }
            ?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() } + keep.length()
        val limit = limitBytes()
        for (x in files) {
            if (total <= limit) break
            total -= x.length()
            x.delete()
        }
    }
}
