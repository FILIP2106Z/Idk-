package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.BluetoothDevicePickerSheet
import com.example.ui.components.SafetyNoticeContentCard
import com.example.ui.screens.AdvancedSettingsScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.MainTherapistScreen
import com.example.ui.screens.SafetyOnboardingScreen
import com.example.ui.theme.HearLinkTheme

private const val ROUTE_SESSION = "session"
private const val ROUTE_DIAGNOSTICS = "diagnostics"
private const val ROUTE_SETTINGS = "settings"

private data class HearLinkNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
)

private val navItems = listOf(
    HearLinkNavItem(ROUTE_SESSION, "Sesja", Icons.Default.Hearing, "nav_session"),
    HearLinkNavItem(ROUTE_DIAGNOSTICS, "Diagnostyka", Icons.Default.Analytics, "nav_diagnostics"),
    HearLinkNavItem(ROUTE_SETTINGS, "Ustawienia", Icons.Default.Tune, "nav_settings"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearLinkApp(viewModel: HearLinkViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFullSafetyDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionsAndDevices()
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionsAndDevices()
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissionsAndDevices()
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissionsAndDevices()
    }

    val requestMic = {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val requestBluetooth = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            viewModel.refreshPermissionsAndDevices()
        }
    }

    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshPermissionsAndDevices()
        }
    }

    val requestMissingCritical: () -> Unit = {
        val toRequest = mutableListOf<String>()
        if (!uiState.safetyState.microphoneGranted) {
            toRequest += Manifest.permission.RECORD_AUDIO
        }
        if (!uiState.safetyState.bluetoothGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            toRequest += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (toRequest.isNotEmpty()) {
            multiPermissionLauncher.launch(toRequest.toTypedArray())
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    val openBatterySettings = {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { context.startActivity(intent) }
        Unit
    }

    val openBluetoothSettings = {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        runCatching { context.startActivity(intent) }
        Unit
    }

    HearLinkTheme(themeMode = uiState.themeMode) {
        if (!uiState.settingsLoaded) {
            Box(modifier = Modifier.fillMaxSize())
            return@HearLinkTheme
        }

        if (!uiState.safetyState.hasAcceptedSafetyNotice) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { innerPadding ->
                SafetyOnboardingScreen(
                    safetyState = uiState.safetyState,
                    onRequestMicrophone = requestMic,
                    onRequestBluetooth = requestBluetooth,
                    onRequestNotifications = requestNotifications,
                    onOpenBatterySettings = openBatterySettings,
                    onAcceptAndContinue = { viewModel.acceptSafetyDisclaimer() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            return@HearLinkTheme
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_SESSION

        val navigateToTab = { route: String ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 640.dp

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    ROUTE_DIAGNOSTICS -> "Diagnostyka audio"
                                    ROUTE_SETTINGS -> "Ustawienia zaawansowane"
                                    else -> "HearLink"
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { showFullSafetyDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_safety_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = "Informacje o bezpieczeństwie",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            navItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateToTab(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag),
                                )
                            }
                        }
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            Spacer(Modifier.height(12.dp))
                            navItems.forEach { item ->
                                NavigationRailItem(
                                    selected = currentRoute == item.route,
                                    onClick = { navigateToTab(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag),
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_SESSION,
                        modifier = Modifier.weight(1f),
                    ) {
                        composable(ROUTE_SESSION) {
                            MainTherapistScreen(
                                uiState = uiState,
                                onStartTransmission = { viewModel.startTransmission() },
                                onStopTransmission = { viewModel.stopTransmission() },
                                onOpenDeviceSelector = { viewModel.openDeviceSelector() },
                                onSelectPreset = { viewModel.selectPreset(it) },
                                onMicSensitivityChange = { viewModel.updateMicSensitivity(it) },
                                onOutputVolumeChange = { viewModel.updateOutputVolume(it) },
                                onNoiseSuppressionToggle = { viewModel.updateNoiseSuppression(it) },
                                onSpeechClarityToggle = { viewModel.updateSpeechClarity(it) },
                                onRequestMissingPermissions = requestMissingCritical,
                                onOpenSafetyInfo = { showFullSafetyDialog = true },
                            )
                        }
                        composable(ROUTE_DIAGNOSTICS) {
                            DiagnosticsScreen(
                                telemetry = uiState.telemetry,
                                routeState = uiState.routeState,
                                loopbackMeasurement = uiState.loopbackMeasurement,
                                isTransmissionActive = uiState.isTransmissionActiveOrStarting,
                                onStartLoopbackTest = { viewModel.startLoopbackTest() },
                                onCancelLoopbackTest = { viewModel.cancelLoopbackTest() },
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            AdvancedSettingsScreen(
                                dspConfig = uiState.dspConfig,
                                themeMode = uiState.themeMode,
                                safetyState = uiState.safetyState,
                                onUpdateMaxOutputLimit = { viewModel.updateMaxOutputLimit(it) },
                                onUpdateSoftStartRamp = { viewModel.updateSoftStartRampMs(it) },
                                onUpdateHighPass = { enabled, cutoff -> viewModel.updateHighPass(enabled, cutoff) },
                                onUpdateNoiseStrength = { viewModel.updateNoiseSuppressionStrength(it) },
                                onUpdateClarityStrength = { viewModel.updateSpeechClarityStrength(it) },
                                onUpdateAgc = { enabled, target -> viewModel.updateAgc(enabled, target) },
                                onSelectTheme = { viewModel.setThemeMode(it) },
                                onRequestMicrophone = requestMic,
                                onRequestBluetooth = requestBluetooth,
                                onRequestNotifications = requestNotifications,
                                onOpenBatterySettings = openBatterySettings,
                                onOpenSafetyInfo = { showFullSafetyDialog = true },
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showDeviceSheet) {
            BluetoothDevicePickerSheet(
                availableDevices = uiState.routeState.availableDevices,
                selectedDevice = uiState.routeState.selectedDevice,
                isBluetoothEnabled = uiState.routeState.isBluetoothEnabled,
                onSelectDevice = { viewModel.selectAudioDevice(it) },
                onRefreshDevices = { viewModel.refreshPermissionsAndDevices() },
                onOpenSystemBluetoothSettings = openBluetoothSettings,
                onDismiss = { viewModel.closeDeviceSelector() },
            )
        }

        if (showFullSafetyDialog) {
            AlertDialog(
                onDismissRequest = { showFullSafetyDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = { showFullSafetyDialog = false },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Zamknij")
                    }
                },
                title = { Text("Bezpieczeństwo i prywatność HearLink") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SafetyNoticeContentCard()
                    }
                },
            )
        }
    }
}
