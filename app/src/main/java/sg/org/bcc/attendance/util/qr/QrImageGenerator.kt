package sg.org.bcc.attendance.util.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.util.EnumMap

object QrImageGenerator {
    private const val QR_SIZE = 512
    private const val MARGIN = 60
    private const val TITLE_TEXT_SIZE = 28f
    private const val NAME_TEXT_SIZE = 36f
    private const val SUB_TEXT_SIZE = 24f
    private const val LINE_SPACING = 8

    /**
     * Generates a Bitmap containing a QR code and formatted text.
     */
    fun createQrWithText(info: QrInfo, attendeeName: String): Bitmap {
        val url = QrUrlParser.generate(info)
        val qrBitmap = generateQrCode(url, QR_SIZE)

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = TITLE_TEXT_SIZE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val namePaint = Paint().apply {
            color = Color.BLACK
            textSize = NAME_TEXT_SIZE
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = SUB_TEXT_SIZE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val title = "Bethany Attendance QR"
        val name = attendeeName
        val footer = buildString {
            info.personId?.let { append("ID: $it") }
            info.groupName?.let { 
                if (isNotEmpty()) append(", ")
                append(it)
            }
        }

        val textBounds = Rect()
        
        // Calculate heights
        titlePaint.getTextBounds(title, 0, title.length, textBounds)
        val titleHeight = textBounds.height()
        
        namePaint.getTextBounds(name, 0, name.length, textBounds)
        val nameHeight = textBounds.height()
        
        subPaint.getTextBounds(footer, 0, footer.length, textBounds)
        val footerHeight = textBounds.height()

        val topTextAreaHeight = titleHeight + nameHeight + (LINE_SPACING * 2)
        val bottomTextAreaHeight = footerHeight + LINE_SPACING
        
        val width = QR_SIZE + (MARGIN * 2)
        val height = QR_SIZE + topTextAreaHeight + bottomTextAreaHeight + (MARGIN * 1.5f).toInt()

        val combinedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        canvas.drawColor(Color.WHITE)

        val centerX = width / 2f
        var currentY = (MARGIN * 0.6f)

        // Draw Top Text
        currentY += titleHeight
        canvas.drawText(title, centerX, currentY, titlePaint)
        currentY += nameHeight + LINE_SPACING
        canvas.drawText(name, centerX, currentY, namePaint)

        // Draw QR code
        currentY += (LINE_SPACING * 1.5f)
        canvas.drawBitmap(qrBitmap, MARGIN.toFloat(), currentY, null)

        // Draw Footer Text
        currentY += QR_SIZE + (LINE_SPACING * 2f) + footerHeight
        canvas.drawText(footer, centerX, currentY, subPaint)

        return combinedBitmap
    }

    /**
     * Saves a QR bitmap to a cache file and returns its FileProvider Uri.
     */
    fun saveAndGetUri(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "qrs")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun generateQrCode(content: String, size: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
