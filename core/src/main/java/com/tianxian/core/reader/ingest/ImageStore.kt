package com.tianxian.core.reader.ingest

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * On-disk store for book page images — one directory per book under `filesDir`, mirroring how
 * [com.tianxian.core.data.repo.BookCache] stores the normalised text. [ImageBlock]s reference images
 * by filename only; this resolves them to real files and handles cleanup when a book is removed.
 */
object ImageStore {

    /** The book's image directory. Not created here — only [saveBytes]/[saveBitmap] create it, so
        text-only books leave no empty directories behind. */
    fun dir(context: Context, bookId: String): File =
        File(context.filesDir, "book_${bookId}_img")

    fun file(context: Context, bookId: String, name: String): File =
        File(dir(context, bookId), name)

    /** Save raw image bytes (used for EPUB-embedded and CBZ pages — keeps the original encoding). */
    fun saveBytes(context: Context, bookId: String, name: String, bytes: ByteArray): String {
        val f = file(context, bookId, name).also { it.parentFile?.mkdirs() }
        f.outputStream().use { it.write(bytes) }
        return name
    }

    /** Save a rendered bitmap as JPEG (used for rasterised scanned-PDF pages). */
    fun saveBitmap(context: Context, bookId: String, name: String, bmp: Bitmap, quality: Int = 85): String {
        val f = file(context, bookId, name).also { it.parentFile?.mkdirs() }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return name
    }

    /** Remove every image for a book (on delete / full reset). Best-effort. */
    fun delete(context: Context, bookId: String) {
        runCatching { dir(context, bookId).deleteRecursively() }
    }
}
