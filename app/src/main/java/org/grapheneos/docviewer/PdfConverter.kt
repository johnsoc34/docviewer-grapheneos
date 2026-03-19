package org.grapheneos.docviewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object PdfConverter {

    fun convert(inputStream: InputStream, cacheDir: File): String {
        val tempFile = File(cacheDir, "temp_doc.pdf")
        try {
            tempFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }

            val fd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val body = StringBuilder()
            val totalPages = renderer.pageCount

            for (i in 0 until totalPages) {
                val page = renderer.openPage(i)

                val scale = 2
                val width = page.width * scale
                val height = page.height * scale
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                bitmap.recycle()
                val imgData = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                val pageNum = i + 1
                body.append("<div class=\"pdf-page\">\n")
                body.append("<div class=\"page-number\">Page ")
                body.append(pageNum.toString())
                body.append(" of ")
                body.append(totalPages.toString())
                body.append("</div>\n")
                body.append("<img src=\"data:image/png;base64,")
                body.append(imgData)
                body.append("\" alt=\"Page ")
                body.append(pageNum.toString())
                body.append("\" style=\"width:100%\">\n")
                body.append("</div>\n")
            }

            renderer.close()
            fd.close()

            val extraCss = ".pdf-page { margin: 8px 0; box-shadow: 0 1px 4px rgba(0,0,0,0.15); border-radius: 4px; overflow: hidden; } " +
                ".page-number { font-size: 0.75rem; color: #999; text-align: center; padding: 6px; background: #f5f5f5; } " +
                "@media (prefers-color-scheme: dark) { .page-number { background: #2a2a2a; color: #888; } .pdf-page { box-shadow: 0 1px 4px rgba(0,0,0,0.4); } }"

            return HtmlWrapper.wrap("PDF", body.toString(), extraCss)
        } finally {
            tempFile.delete()
        }
    }
}
