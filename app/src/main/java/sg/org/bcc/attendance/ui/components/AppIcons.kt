package sg.org.bcc.attendance.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import sg.org.bcc.attendance.R

object AppIcons {
    val Person = R.drawable.ic_person
    val PersonCheck = R.drawable.ic_person_check
    val PersonCancel = R.drawable.ic_person_cancel
    val PersonRemove = R.drawable.ic_person_remove
    val PlaylistRemove = R.drawable.ic_playlist_remove
    val PersonSearch = R.drawable.ic_person_search
    val PlaylistAdd = R.drawable.ic_playlist_add
    val Groups = R.drawable.ic_groups
    val Close = R.drawable.ic_close
    val Check = R.drawable.ic_check
    val BookmarkAdded = R.drawable.ic_bookmark_added
    val Visibility = R.drawable.ic_visibility
    val VisibilityOff = R.drawable.ic_visibility_off
    val ClearQueue = R.drawable.ic_playlist_remove
    val ArrowBack = R.drawable.ic_arrow_back
    val CloudDone = R.drawable.ic_cloud_done
    val CloudUpload = R.drawable.ic_cloud_upload
    val CloudOff = R.drawable.ic_cloud_off
    val Cloud = R.drawable.ic_cloud
    val CloudAlert = R.drawable.ic_cloud_alert
    val Warning = R.drawable.ic_warning
    val QrCodeScanner = R.drawable.ic_qr_code_scanner
    val MoreVert = R.drawable.ic_more_vert
    val TextFields = R.drawable.ic_text_fields
    val PushPin = R.drawable.ic_push_pin
    val Checklist = R.drawable.ic_checklist
    val CalendarMonth = R.drawable.ic_calendar_month
    val Schedule = R.drawable.ic_schedule
    
    object Filter {
        val None = R.drawable.ic_filter_none
        val One = R.drawable.ic_filter_1
        val Two = R.drawable.ic_filter_2
        val Three = R.drawable.ic_filter_3
        val Four = R.drawable.ic_filter_4
        val Five = R.drawable.ic_filter_5
        val Six = R.drawable.ic_filter_6
        val Seven = R.drawable.ic_filter_7
        val Eight = R.drawable.ic_filter_8
        val Nine = R.drawable.ic_filter_9
        val NinePlus = R.drawable.ic_filter_9_plus
    }
}

@Composable
fun AppIcon(
    resourceId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Icon(
        painter = painterResource(id = resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint
    )
}
