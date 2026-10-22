package com.example.cloudplay

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException

class CloudDocumentsProvider : DocumentsProvider() {
    private lateinit var demo: CloudBackend
    private lateinit var cache: CacheManager

    private val rootCols = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID)
    private val docCols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)

    override fun onCreate(): Boolean {
        demo = DemoBackend(context!!)
        cache = CacheManager(context!!)
        return true
    }

    private var tb: TeraBoxBackend? = null
    private var tbKey: String? = null

    private fun backend(): CloudBackend {
        val n = context!!.getSharedPreferences("cfg", Context.MODE_PRIVATE).getString("ndus", null)
        if (n.isNullOrEmpty()) return demo
        if (tb == null || tbKey != n) {
            tb = TeraBoxBackend(context!!, n)
            tbKey = n
        }
        return tb!!
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: rootCols)
        c.newRow().apply {
            add(Root.COLUMN_ROOT_ID, "cloud")
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_ICON, android.R.drawable.ic_menu_upload)
            add(Root.COLUMN_TITLE, "CloudPlay (TeraBox)")
            add(Root.COLUMN_DOCUMENT_ID, "/")
        }
        return c
    }

    private fun MatrixCursor.addEntry(e: CloudEntry) {
        newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, e.id)
            add(Document.COLUMN_DISPLAY_NAME, e.name)
            add(Document.COLUMN_SIZE, e.size)
            add(Document.COLUMN_MIME_TYPE, if (e.isDir) Document.MIME_TYPE_DIR else mime(e.name))
            add(Document.COLUMN_LAST_MODIFIED, e.modified)
            add(Document.COLUMN_FLAGS, 0)
        }
    }

    private fun mime(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: docCols)
        c.addEntry(backend().stat(documentId) ?: throw FileNotFoundException(documentId))
        return c
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val c = MatrixCursor(projection ?: docCols)
        backend().list(parentDocumentId).forEach { c.addEntry(it) }
        return c
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val b = backend()
        val e = b.stat(documentId) ?: throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(cache.get(b, e), ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
