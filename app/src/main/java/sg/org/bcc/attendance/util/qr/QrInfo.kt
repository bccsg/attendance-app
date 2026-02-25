package sg.org.bcc.attendance.util.qr

data class QrInfo(
    val personId: String? = null,
    val personName: String? = null,
    val groupId: String? = null,
    val groupName: String? = null
) {
    /**
     * Checks if this QrInfo contains enough information to be useful.
     */
    fun isValid(): Boolean {
        return !personId.isNullOrBlank() || !groupId.isNullOrBlank()
    }
}
