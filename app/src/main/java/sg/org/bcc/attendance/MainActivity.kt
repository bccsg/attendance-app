package sg.org.bcc.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import sg.org.bcc.attendance.ui.main.MainListScreen
import sg.org.bcc.attendance.ui.queue.QueueScreen
import sg.org.bcc.attendance.ui.theme.AttendanceTheme

import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.dao.AttendeeDao
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.util.DemoData
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject lateinit var attendeeDao: AttendeeDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Seed database with dummy data for demo
        lifecycleScope.launch {
            val count = attendeeDao.getAttendeeById("D01")
            if (count == null) {
                attendeeDao.insertAll(DemoData.disneyCharacters)
            }
        }

        setContent {
            AttendanceTheme {
                AttendanceAppContent()
            }
        }
    }
}

@Composable
fun AttendanceAppContent() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "main_list") {
        composable("main_list") {
            MainListScreen()
        }
    }
}
