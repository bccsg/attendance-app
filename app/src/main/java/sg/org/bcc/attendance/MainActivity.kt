package sg.org.bcc.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import sg.org.bcc.attendance.ui.main.MainListScreen
import sg.org.bcc.attendance.ui.main.MainListViewModel
import sg.org.bcc.attendance.ui.event.EventManagementScreen
import sg.org.bcc.attendance.ui.theme.AttendanceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
    val mainViewModel: MainListViewModel = hiltViewModel()
    val currentEventId by mainViewModel.currentEventId.collectAsState()
    val textScale by mainViewModel.textScale.collectAsState()

    NavHost(navController = navController, startDestination = "main_list") {
        composable("main_list") {
            MainListScreen(
                viewModel = mainViewModel,
                onNavigateToEventManagement = {
                    navController.navigate("event_management")
                }
            )
        }
        composable("event_management") {
            EventManagementScreen(
                currentEventId = currentEventId,
                textScale = textScale,
                onEventSelected = { id ->
                    mainViewModel.onSwitchEvent(id)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
