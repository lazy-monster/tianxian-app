package com.scholar.app.reader.ingest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.scholar.app.model.Block
import com.scholar.app.model.BookDocument
import com.scholar.app.model.BookFormat
import com.scholar.app.model.Chapter
import com.scholar.app.model.ImageBlock
import com.scholar.app.model.TextBlock
import java.io.File
import java.util.UUID

/** Try the PDF's text layer first (PDFBox). If a PDF is scanned images (little or
 *  no extractable Han text), fall back to rasterising pages and OCRing them. */
class PdfExtractor(private val context: Context) {

    fun parse(file: File, title: String): BookDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper()
            val pageCount = doc.numberOfPages
            val chapters = ArrayList<Chapter>()
            var hanTotal = 0
            for (p in 1..pageCount) {
                stripper.startPage = p; stripper.endPage = p
                val text = stripper.getText(doc).trim()
                hanTotal += text.count { it.code in 0x4E00..0x9FFF }
                val paras = text.split(Regex("\\r?\\n")).map { it.trim() }.filter { it.isNotEmpty() }
                if (paras.isNotEmpty())
                    chapters.add(Chapter(chapters.size, "Page $p", paras.map { TextBlock(it) }))
            }
            // Heuristic: a real Chinese PDF text layer has many Han chars. If almost none,
            // it's scanned → OCR.
            return if (hanTotal >= pageCount.coerceAtLeast(1) * 5 && chapters.isNotEmpty())
                BookDocument(UUID.randomUUID().toString(), title, null, BookFormat.PDF_TEXT, chapters)
            else
                Ocr(context).parsePdf(file, title)
        }
    }
}

/** On-device Chinese OCR via ML Kit — for scanned PDFs and photos of physical books. */
class Ocr(private val context: Context) {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    fun parseImage(file: File, title: String): BookDocument {
        val id = UUID.randomUUID().toString()
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path) ?: error("Could not read image")
        // keep the original page image, then OCR its text so words stay tappable below it
        val ext = file.extension.ifBlank { "jpg" }.lowercase().take(4)
        val name = ImageStore.saveBytes(context, id, "page_0000.$ext", file.readBytes())
        val blocks = ArrayList<Block>()
        blocks.add(ImageBlock(name))
        recognize(bmp).forEach { blocks.add(TextBlock(it)) }
        return BookDocument(id, title, null, BookFormat.IMAGES, listOf(Chapter(0, null, blocks)))
    }

    fun parsePdf(file: File, title: String): BookDocument {
        val id = UUID.randomUUID().toString()
        val chapters = ArrayList<Chapter>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = 2                                  // upscale for OCR accuracy
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        // render the page as an image, then OCR it for tappable text underneath
                        val name = ImageStore.saveBitmap(context, id, "page_%04d.jpg".format(i), bmp)
                        val blocks = ArrayList<Block>()
                        blocks.add(ImageBlock(name))
                        recognize(bmp).forEach { blocks.add(TextBlock(it)) }
                        chapters.add(Chapter(chapters.size, "Page ${i + 1}", blocks))
                    }
                }
            }
        }
        if (chapters.isEmpty()) error("Could not render any pages from this PDF")
        return BookDocument(id, title, null, BookFormat.PDF_SCANNED, chapters)
    }

    private fun recognize(bmp: Bitmap): List<String> {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))  // on IO dispatcher
        return result.textBlocks.map { it.text.replace("\n", "") }.filter { it.isNotBlank() }
    }
}
