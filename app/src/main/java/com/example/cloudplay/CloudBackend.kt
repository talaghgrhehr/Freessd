package com.example.cloudplay

import android.content.Context
import java.io.File

data class CloudEntry(val id: String, val name: String, val isDir: Boolean, val size: Long, val modified: Long)

interface CloudBackend {
    fun list(parentId: String): List<CloudEntry>
    fun stat(id: String): CloudEntry?
    fun download(entry: CloudEntry, dest: File)
}

// अभी का डेमो बैकएंड: फ़ोन के अंदर एक फ़ोल्डर। TeraBox बैकएंड बाद में इसी की जगह जुड़ेगा।
class DemoBackend(ctx: Context) : CloudBackend {
    private val base = File(ctx.filesDir, "demo_cloud").apply {
        mkdirs()
        val f = File(this, "readme.txt")
        if (!f.exists()) f.writeText("TeraBox backend yahan judega.\n")
    }

    private fun file(id: String): File {
        val f = File(base, id.trimStart('/'))
        if (!f.canonicalPath.startsWith(base.canonicalPath)) throw SecurityException("bad path")
        return f
    }

    private fun entry(f: File) = CloudEntry(
        id = "/" + f.relativeTo(base).path,
        name = if (f == base) "CloudPlay" else f.name,
        isDir = f.isDirectory,
        size = if (f.isDirectory) 0 else f.length(),
        modified = f.lastModified()
    )

    override fun list(parentId: String) = file(parentId).listFiles()?.map { entry(it) } ?: emptyList()
    override fun stat(id: String) = file(id).takeIf { it.exists() }?.let { entry(it) }
    override fun download(entry: CloudEntry, dest: File) { file(entry.id).copyTo(dest, overwrite = true) }
}
