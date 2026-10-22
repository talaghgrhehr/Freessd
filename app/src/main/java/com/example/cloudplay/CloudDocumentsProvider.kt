package com.example.cloudplay

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException

class CloudDocumentsProvider : DocumentsProvider() {
    private lateinit var backend: CloudBackend
    private lateinit var cache: CacheManager

    private val rootCols = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID)
    private val docCols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)

    override fun onCreate(): Boolean {
        backend = DemoBackend(context!!)
        cache = CacheManager(context!!, backend)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: rootCols)
        c.newRow().apply {
            add(Root.COLUMN_ROOT_ID, "cloud")
            add(Root.COLUMN_FLAGS, 0)
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
        c.addEntry(backend.stat(documentId) ?: throw FileNotFoundException(documentId))
        return c
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val c = MatrixCursor(projection ?: docCols)
        backend.list(parentDocumentId).forEach { c.addEntry(it) }
        return c
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val e = backend.stat(documentId) ?: throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(cache.get(e), ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
