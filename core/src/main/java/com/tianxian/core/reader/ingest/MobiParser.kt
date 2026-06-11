package com.tianxian.core.reader.ingest

import com.tianxian.core.model.BookDocument
import com.tianxian.core.model.BookFormat
import java.io.File

/**
 * MOBI / PRC reader. MOBI is a PalmDB container; the book text is stored across
 * records compressed with PalmDOC (LZ77 variant) or uncompressed. We parse the
 * PalmDB record table, read the PalmDOC header, decompress the text records, and
 * hand the resulting XHTML to the shared HTML stripper.
 *
 * Covers the common case (compression 1 = none, 2 = PalmDOC). HUFF/CDIC (17480)
 * and newer KF8/AZW3 containers are reported as needing conversion — honest limit.
 */
object MobiParser {

    fun parse(file: File, title: String): BookDocument {
        val data = file.readBytes()
        require(data.size > 78) { "File too small to be MOBI" }

        val numRecords = u16(data, 76)
        val recordOffsets = IntArray(numRecords) { i -> u32(data, 78 + i * 8) }

        fun recordBytes(i: Int): ByteArray {
            val start = recordOffsets[i]
            val end = if (i + 1 < numRecords) recordOffsets[i + 1] else data.size
            return data.copyOfRange(start, end)
        }

        val rec0 = recordBytes(0)
        val compression = u16(rec0, 0)
        val textRecordCount = u16(rec0, 8)
        // MOBI header encoding (offset 28 within rec0 after 16-byte PalmDOC header)
        val encoding = if (rec0.size >= 32) u32(rec0, 28) else 65001
        val charsetName = if (encoding == 1252) "windows-1252" else "UTF-8"

        if (compression != 1 && compression != 2)
            error("This MOBI uses HUFF/CDIC or KF8 compression — please convert to EPUB.")

        val sb = StringBuilder()
        for (i in 1..textRecordCount) {
            if (i >= numRecords) break
            val raw = recordBytes(i)
            val text = if (compression == 2) palmDocDecompress(raw) else raw
            sb.append(String(text, charset(charsetName)))
        }
        val paras = Html.toParagraphs(sb.toString())
        if (paras.isEmpty()) error("MOBI had no readable text")
        return paragraphsToDoc(title, BookFormat.MOBI, paras)
    }

    /** PalmDOC LZ77 decompression. */
    private fun palmDocDecompress(input: ByteArray): ByteArray {
        val out = ArrayList<Byte>(input.size * 2)
        var i = 0
        while (i < input.size) {
            val b = input[i].toInt() and 0xFF
            i++
            when {
                b == 0x00 -> out.add(0)
                b in 0x01..0x08 -> { repeat(b) { if (i < input.size) out.add(input[i++]) } }   // literals
                b in 0x09..0x7F -> out.add(b.toByte())                                          // literal
                b in 0xC0..0xFF -> { out.add(' '.code.toByte()); out.add((b xor 0x80).toByte()) } // space + char
                else -> {                                                                        // 0x80..0xBF: LZ77
                    if (i >= input.size) break
                    val b2 = input[i].toInt() and 0xFF; i++
                    val pair = (b shl 8) or b2
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    if (distance == 0) break
                    val startSize = out.size
                    for (k in 0 until length) {
                        val idx = startSize - distance + k
                        if (idx in out.indices) out.add(out[idx]) else out.add(' '.code.toByte())
                    }
                }
            }
        }
        return out.toByteArray()
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
