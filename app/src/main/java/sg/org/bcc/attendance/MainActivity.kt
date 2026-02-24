package sg.org.bcc.attendance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
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
import sg.org.bcc.attendance.ui.event.EventManagementScreen
import sg.org.bcc.attendance.ui.theme.AttendanceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var viewModelReference: MainListViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
                            onTextScaleChange = { scale ->
                                mainViewModel.setTextScale(scale)
                            },
                            onEventSelected = { id ->
                                mainViewModel.onSwitchEvent(id)
                            },
                            onBack = {
                                navController.popBackStack()
                            },
                            onLogout = { mainViewModel.setShowCloudStatusDialog(true) }
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
        customTabsIntent.launchUrl(this, Uri.parse(authUrl))
    }
}
