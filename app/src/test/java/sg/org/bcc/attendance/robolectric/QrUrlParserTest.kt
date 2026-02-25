package sg.org.bcc.attendance.robolectric

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.util.qr.QrInfo
import sg.org.bcc.attendance.util.qr.QrUrlParser

@RunWith(RobolectricTestRunner::class)
class QrUrlParserTest {

    @Test
    fun `should parse valid URL with all parameters`() {
        val url = "https://m.bethany.sg/?pi=P123&pn=John%20Doe&gi=G456&gn=Grace%20Group"
        val info = QrUrlParser.parse(url)

        info shouldBe QrInfo(
            personId = "P123",
            personName = "John Doe",
            groupId = "G456",
            groupName = "Grace Group"
        )
    }

    @Test
    fun `should parse valid URL with partial parameters`() {
        val url = "https://m.bethany.sg/?pi=P123"
        val info = QrUrlParser.parse(url)

        info shouldBe QrInfo(
            personId = "P123",
            personName = null,
            groupId = null,
            groupName = null
        )
    }

    @Test
    fun `should return null for invalid URL base`() {
        val url = "https://example.com/?pi=P123"
        val info = QrUrlParser.parse(url)

        info shouldBe null
    }

    @Test
    fun `should generate valid URL from QrInfo`() {
        val info = QrInfo(
            personId = "P123",
            personName = "John Doe",
            groupId = "G456",
            groupName = "Grace Group"
        )
        val url = QrUrlParser.generate(info)

        url shouldBe "https://m.bethany.sg/?pi=P123&pn=John%20Doe&gi=G456&gn=Grace%20Group"
    }

    @Test
    fun `isValid should return true if pi or gi is present`() {
        QrInfo(personId = "P1").isValid() shouldBe true
        QrInfo(groupId = "G1").isValid() shouldBe true
        QrInfo(personName = "Name").isValid() shouldBe false
        QrInfo().isValid() shouldBe false
    }
}
