package sg.org.bcc.attendance.util.qr

import android.net.Uri

object QrUrlParser {
    private const val BASE_URL = "https://m.bethany.sg/"
    private const val PARAM_PERSON_ID = "pi"
    private const val PARAM_PERSON_NAME = "pn"
    private const val PARAM_GROUP_ID = "gi"
    private const val PARAM_GROUP_NAME = "gn"

    /**
     * Parses a QR code URL into a QrInfo object.
     * Expected format: https://m.bethany.sg/?pi=ID&pn=NAME&gi=GID&gn=GNAME
     */
    fun parse(url: String): QrInfo? {
        if (!url.startsWith(BASE_URL)) {
            return null
        }

        return try {
            val uri = Uri.parse(url)
            val personId = uri.getQueryParameter(PARAM_PERSON_ID)
            val personName = uri.getQueryParameter(PARAM_PERSON_NAME)
            val groupId = uri.getQueryParameter(PARAM_GROUP_ID)
            val groupName = uri.getQueryParameter(PARAM_GROUP_NAME)

            if (personId == null && personName == null && groupId == null && groupName == null) {
                null
            } else {
                QrInfo(
                    personId = personId,
                    personName = personName,
                    groupId = groupId,
                    groupName = groupName
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a QR code URL from a QrInfo object.
     */
    fun generate(info: QrInfo): String {
        val builder = Uri.parse(BASE_URL).buildUpon()
        info.personId?.let { builder.appendQueryParameter(PARAM_PERSON_ID, it) }
        info.personName?.let { builder.appendQueryParameter(PARAM_PERSON_NAME, it) }
        info.groupId?.let { builder.appendQueryParameter(PARAM_GROUP_ID, it) }
        info.groupName?.let { builder.appendQueryParameter(PARAM_GROUP_NAME, it) }
        return builder.build().toString()
    }
}
