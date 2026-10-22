package com.example.cloudplay

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.IOException

class SafBackend(ctx: Context, private val tree: Uri) : CloudBackend {
    private val cr = ctx.contentResolver
    private val rootDocId = DocumentsContract.getTreeDocumentId(tree)
    private val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)

    private fun real(id: String) = if (id == "/") rootDocId else id
    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, real(id))

    private fun row(c: Cursor, isRoot: Boolean = false) = CloudEntry(
        id = if (isRoot) "/" else c.getString(0),
        name = if (isRoot) "TeraBox" else (c.getString(1) ?: "?"),
        isDir = c.getString(2) == Document.MIME_TYPE_DIR,
        size = if (c.isNull(3)) 0L else c.getLong(3),
        modified = if (c.isNull(4)) 0L else c.getLong(4)
    )

    override fun list(parentId: String): List<CloudEntry> {
        val out = ArrayList<CloudEntry>()
        try {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, real(parentId))
            cr.query(uri, cols, null, null, null)?.use { c -> while (c.moveToNext()) out.add(row(c)) }
        } catch (e: Exception) { }
        return out
    }

    override fun stat(id: String): CloudEntry? = try {
        cr.query(docUri(id), cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) row(c, id == "/") else null
        }
    } catch (e: Exception) { null }

    override fun download(entry: CloudEntry, dest: File) {
        val input = cr.openInputStream(docUri(entry.id)) ?: throw IOException("open failed")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
    }
}
