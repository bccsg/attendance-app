package sg.org.bcc.attendance

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.ui.main.MainListScreen
import sg.org.bcc.attendance.ui.main.MainListViewModel
import sg.org.bcc.attendance.ui.main.CloudResolutionScreen
import sg.org.bcc.attendance.ui.event.EventManagementScreen
import sg.org.bcc.attendance.ui.queue.SyncLogsScreen
import sg.org.bcc.attendance.ui.theme.AttendanceTheme
import androidx.navigation.navArgument
import androidx.navigation.NavType

import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb
import sg.org.bcc.attendance.ui.theme.LightColorScheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var viewModelReference: MainListViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(LightColorScheme.primary.toArgb())
        )
        super.onCreate(savedInstanceState)
        Log.d("AttendanceAuth", "MainActivity onCreate - Version 2 (Web Auth)")
        
        setContent {
            AttendanceTheme {
                val navController = rememberNavController()
                val mainViewModel: MainListViewModel = hiltViewModel()
                viewModelReference = mainViewModel
                
                val currentEventId by mainViewModel.currentEventId.collectAsState()
                val textScale by mainViewModel.textScale.collectAsState()

                // Observe login events
                LaunchedEffect(Unit) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        mainViewModel.loginRequestEvent.collect {
                            launchWebLogin(mainViewModel)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        mainViewModel.navigateToResolutionScreenEvent.collect {
                            if (navController.currentDestination?.route != "cloud_resolution") {
                                navController.navigate("cloud_resolution") {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "main_list") {
                    composable("main_list") {
                        MainListScreen(
                            viewModel = mainViewModel,
                            onNavigateToEventManagement = {
                                if (navController.currentDestination?.route == "main_list") {
                                    navController.navigate("event_management") {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onNavigateToSyncLogs = {
                                if (navController.currentDestination?.route == "main_list") {
                                    navController.navigate("sync_logs") {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                    composable("cloud_resolution") {
                        CloudResolutionScreen(
                            onBack = {
                                if (navController.currentDestination?.route == "cloud_resolution") {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                    composable("event_management") {
                        EventManagementScreen(
                            currentEventId = currentEventId,
                            textScale = textScale,
                            onTextScaleChange = { scale ->
                                mainViewModel.setTextScale(scale)
                            },
                            onEventSelected = { id ->
                                mainViewModel.onSwitchEvent(id)
                            },
                            onBack = {
                                if (navController.currentDestination?.route == "event_management") {
                                    navController.popBackStack()
                                }
                            },
                            onLogout = { mainViewModel.setShowCloudStatusDialog(true) }
                        )
                    }
                    composable("sync_logs") {
                        SyncLogsScreen(
                            onBack = {
                                if (navController.currentDestination?.route == "sync_logs") {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Handle the intent if activity was started by deep link
        intent?.let { handleIntent(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val data: Uri? = intent.data
        Log.d("AttendanceAuth", "Handling intent with data: $data")
        if (data != null && data.scheme == "sg.org.bcc.attendance" && data.path == "/oauth2redirect") {
            val code = data.getQueryParameter("code")
            val error = data.getQueryParameter("error")
            Log.d("AttendanceAuth", "Extracted code: ${code?.take(5)}... error: $error")
            
            if (code != null) {
                if (viewModelReference != null) {
                    viewModelReference?.handleOAuthCode(code)
                } else {
                    Log.e("AttendanceAuth", "ViewModel reference is NULL!")
                }
            } else if (error != null) {
                viewModelReference?.onLoginError("Login failed: $error")
            }
        }
    }

    private fun launchWebLogin(viewModel: MainListViewModel) {
        val authUrl = viewModel.getAuthUrl()
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(this, authUrl.toUri())
    }
}
