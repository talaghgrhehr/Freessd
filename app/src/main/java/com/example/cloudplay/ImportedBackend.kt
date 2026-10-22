package com.example.cloudplay

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

class ImportedBackend(private val ctx: Context) : CloudBackend {
    override fun list(parentId: String): List<CloudEntry> {
        if (parentId != "/") return emptyList()
        return ImportStore.all(ctx).map { CloudEntry("/" + it.name, it.name, false, it.size, it.modified) }
    }

    override fun stat(id: String): CloudEntry? {
        if (id == "/") return CloudEntry("/", "TeraBox", true, 0L, 0L)
        val item = ImportStore.all(ctx).firstOrNull { "/" + it.name == id } ?: return null
        return CloudEntry(id, item.name, false, item.size, item.modified)
    }

    override fun download(entry: CloudEntry, dest: File) {
        val item = ImportStore.all(ctx).firstOrNull { "/" + it.name == entry.id }
            ?: throw IOException("फ़ाइल सूची में नहीं है")
        val input = ctx.contentResolver.openInputStream(Uri.parse(item.uri))
            ?: throw IOException("TeraBox ऐप से फ़ाइल नहीं खुली")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
    }
}
