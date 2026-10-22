package com.example

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.ScannerViewModel
import com.example.ui.screens.DiscoveryScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveScannerScreen
import com.example.ui.screens.ManualPairScreen
import com.example.ui.screens.PairingQrScreen
import com.example.ui.screens.RemoteDashboardScreen
import com.example.ui.screens.RemoteFinancialReportScreen
import com.example.ui.screens.RemoteLoginScreen
import com.example.ui.screens.RemotePairingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TechDeskScannerTheme
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ScannerViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !uiState.isDarkMode
                    insetsController.isAppearanceLightNavigationBars = !uiState.isDarkMode
                }
            }

            val locale = remember(uiState.appLanguage) {
                Locale(uiState.appLanguage)
            }
            val layoutDirection = if (uiState.appLanguage.startsWith("ar")) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            val currentConfig = LocalConfiguration.current
            val localizedConfiguration = remember(uiState.appLanguage, currentConfig) {
                Configuration(currentConfig).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }

            val baseContext = LocalContext.current

            LaunchedEffect(uiState.appLanguage) {
                try {
                    Locale.setDefault(locale)
                    val res = baseContext.resources
                    val conf = res.configuration
                    conf.setLocale(locale)
                    conf.setLayoutDirection(locale)
                    @Suppress("DEPRECATION")
                    res.updateConfiguration(conf, res.displayMetrics)
                } catch (_: Exception) {}
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalLayoutDirection provides layoutDirection
            ) {
                TechDeskScannerTheme(isDarkTheme = uiState.isDarkMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        TechDeskScannerApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun TechDeskScannerApp(viewModel: ScannerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.currentScreen) {
        AppScreen.REMOTE_LOGIN -> RemoteLoginScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.HOME -> HomeScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.SCAN_PAIR_QR -> PairingQrScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.MANUAL_PAIR -> ManualPairScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.LIVE_SCANNER -> LiveScannerScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.HISTORY -> HistoryScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.DIAGNOSTICS -> DiscoveryScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.SETTINGS -> SettingsScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.REMOTE_PAIRING -> RemotePairingScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.REMOTE_DASHBOARD -> RemoteDashboardScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.REMOTE_FINANCIAL_REPORT -> RemoteFinancialReportScreen(uiState = uiState, viewModel = viewModel)
    }
}
