package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.util.qr.QrImageGenerator
import sg.org.bcc.attendance.util.qr.QrInfo
import java.io.File

@RunWith(RobolectricTestRunner::class)
class QrImageGeneratorTest {

    @Test
    fun `should create bitmap with all info`() {
        val info = QrInfo(
            personId = "P123",
            personName = "John Doe",
            groupId = "G456",
            groupName = "Grace Group"
        )
        val bitmap = QrImageGenerator.createQrWithText(info, "John Doe")

        bitmap shouldNotBe null
        bitmap.width shouldBe 632 // QR_SIZE (512) + MARGIN (60) * 2
        bitmap.height shouldNotBe 0
    }

    @Test
    fun `should save bitmap and return uri`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = QrInfo(personId = "P123")
        val bitmap = QrImageGenerator.createQrWithText(info, "Test User")
        
        val uri = QrImageGenerator.saveAndGetUri(context, bitmap, "test_qr.png")

        uri shouldNotBe null
        uri.toString() shouldBe "content://sg.org.bcc.attendance.fileprovider/qr_images/test_qr.png"

        val cacheDir = File(context.cacheDir, "qrs")
        val file = File(cacheDir, "test_qr.png")
        file.exists() shouldBe true
    }
}
