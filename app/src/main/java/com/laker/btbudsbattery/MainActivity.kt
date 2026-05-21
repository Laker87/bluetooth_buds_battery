package com.laker.btbudsbattery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laker.btbudsbattery.core.AppLanguage
import com.laker.btbudsbattery.core.AppAccentColor
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.AppTheme
import com.laker.btbudsbattery.core.HeadphoneHistoryEntry
import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot
import com.laker.btbudsbattery.presentation.MainViewModel
import com.laker.btbudsbattery.presentation.MainUiState
import com.laker.btbudsbattery.service.BluetoothBatteryService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val appPreferences by lazy { AppPreferences(applicationContext) }

    private val viewModel by viewModels<MainViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(appPreferences) as T
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        updatePermissionState()
        if (hasAllPermissions && !initialSetupCompleted) {
            completeInitialSetup()
        }
        if (hasAllPermissions && pendingEnableMonitoringAfterPermission) {
            pendingEnableMonitoringAfterPermission = false
            viewModel.onMonitoringChanged(this, true)
        }
    }

    private var hasAllPermissions by mutableStateOf(false)
    private var showPermissionsRationale by mutableStateOf(false)
    private var permissionUiItems by mutableStateOf<List<PermissionUiItem>>(emptyList())
    private var permissionRequestAttempts by mutableStateOf<Map<String, Int>>(emptyMap())
    private var pendingEnableMonitoringAfterPermission = false
    private var initialSetupCompleted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyAppLanguage(appPreferences.appLanguage)
        updatePermissionState()
        initialSetupCompleted = appPreferences.initialSetupCompleted
        if (hasAllPermissions && !initialSetupCompleted) {
            completeInitialSetup()
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                updatePermissionState()
            }
            LaunchedEffect(uiState.monitoringEnabled, hasAllPermissions) {
                if (uiState.monitoringEnabled && hasAllPermissions) {
                    // Keep monitoring alive without forcing foreground notification on app launch.
                    viewModel.ensureMonitoringRunning(this@MainActivity)
                }
            }

            BluetoothBatteryApp(
                uiState = uiState,
                initialSetupCompleted = initialSetupCompleted,
                hasAllPermissions = hasAllPermissions,
                showPermissionsRationale = showPermissionsRationale,
                permissionItems = permissionUiItems,
            onRequestPermissions = { launchPermissionRequest(requiredPermissions()) },
            onRequestSinglePermission = { permission ->
                launchPermissionRequest(permissionStepRequest(permission))
            },
            onOpenAppSettings = { openAppSettingsWithHint() },
            onCompleteInitialSetup = { completeInitialSetup() },
            onOpenBluetoothSettings = {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                onMonitoringChanged = { enabled ->
                    if (enabled && !hasAllPermissions) {
                        pendingEnableMonitoringAfterPermission = true
                        launchPermissionRequest(requiredPermissions())
                    } else {
                        pendingEnableMonitoringAfterPermission = false
                        viewModel.onMonitoringChanged(this@MainActivity, enabled)
                    }
                },
                onThemeChanged = { theme ->
                    viewModel.onThemeChanged(theme)
                    refreshWidgetAppearance()
                },
                onLanguageChanged = { language ->
                    viewModel.onLanguageChanged(language)
                    applyAppLanguage(language)
                },
                onAccentColorChanged = { accentColor ->
                    viewModel.onAccentColorChanged(accentColor)
                    refreshNotificationAppearance()
                },
            )
        }
    }

    private fun completeInitialSetup() {
        appPreferences.initialSetupCompleted = true
        initialSetupCompleted = true
        if (appPreferences.monitoringEnabled && hasAllPermissions) {
            viewModel.ensureMonitoringRunning(this)
        }
    }

    private fun updatePermissionState() {
        val denied = mutableListOf<String>()
        permissionUiItems = requiredPermissionSpecs().map { spec ->
            val granted = ContextCompat.checkSelfPermission(this, spec.permission) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                denied += spec.permission
            }
            PermissionUiItem(
                permission = spec.permission,
                labelRes = spec.labelRes,
                granted = granted,
                showRationale = !granted && shouldShowRequestPermissionRationale(spec.permission),
                requestedCount = permissionRequestAttempts[spec.permission] ?: 0,
            )
        }
        hasAllPermissions = denied.isEmpty()
        showPermissionsRationale = permissionUiItems.any { it.showRationale }
    }

    private fun requiredPermissionSpecs(): List<RequiredPermissionSpec> {
        val specs = mutableListOf(
            RequiredPermissionSpec(
                permission = Manifest.permission.BLUETOOTH_CONNECT,
                labelRes = R.string.permission_bluetooth_connect,
            ),
            RequiredPermissionSpec(
                permission = Manifest.permission.BLUETOOTH_SCAN,
                labelRes = R.string.permission_bluetooth_scan,
            ),
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            specs += RequiredPermissionSpec(
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                labelRes = R.string.permission_location_coarse,
            )
            specs += RequiredPermissionSpec(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                labelRes = R.string.permission_location_fine,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            specs += RequiredPermissionSpec(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                labelRes = R.string.permission_notifications,
            )
        }
        return specs
    }

    private fun requiredPermissions(): Array<String> {
        return requiredPermissionSpecs()
            .map { it.permission }
            .toTypedArray()
    }

    private fun permissionStepRequest(permission: String): Array<String> {
        return if (permission == Manifest.permission.ACCESS_COARSE_LOCATION ||
            permission == Manifest.permission.ACCESS_FINE_LOCATION
        ) {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(permission)
        }
    }

    private fun launchPermissionRequest(permissions: Array<String>) {
        val nextAttempts = permissionRequestAttempts.toMutableMap()
        permissions.forEach { permission ->
            nextAttempts[permission] = (nextAttempts[permission] ?: 0) + 1
        }
        permissionRequestAttempts = nextAttempts
        permissionLauncher.launch(permissions)
    }

    private fun openAppSettingsWithHint() {
        Toast.makeText(
            this,
            getString(R.string.permission_system_blocked_hint),
            Toast.LENGTH_LONG,
        ).show()
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(intent)
    }

    private fun applyAppLanguage(language: AppLanguage) {
        val tag = when (language) {
            AppLanguage.ENGLISH -> "en"
            AppLanguage.RUSSIAN -> "ru"
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun refreshNotificationAppearance() {
        if (!appPreferences.monitoringEnabled || !hasAllPermissions) return
        val intent = Intent(this, BluetoothBatteryService::class.java).apply {
            action = BluetoothBatteryService.ACTION_REFRESH_NOTIFICATION
        }
        startService(intent)
    }

    private fun refreshWidgetAppearance() {
        if (!appPreferences.monitoringEnabled || !hasAllPermissions) return
        val intent = Intent(this, BluetoothBatteryService::class.java).apply {
            action = BluetoothBatteryService.ACTION_REFRESH_WIDGET
        }
        startService(intent)
    }
}

private data class RequiredPermissionSpec(
    val permission: String,
    val labelRes: Int,
)

private data class PermissionUiItem(
    val permission: String,
    val labelRes: Int,
    val granted: Boolean,
    val showRationale: Boolean,
    val requestedCount: Int,
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BluetoothBatteryApp(
    uiState: MainUiState,
    initialSetupCompleted: Boolean,
    hasAllPermissions: Boolean,
    showPermissionsRationale: Boolean,
    permissionItems: List<PermissionUiItem>,
    onRequestPermissions: () -> Unit,
    onRequestSinglePermission: (String) -> Unit,
    onOpenAppSettings: () -> Unit,
    onCompleteInitialSetup: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    onAccentColorChanged: (AppAccentColor) -> Unit,
) {
    BTBatteryTheme(
        appTheme = uiState.appTheme,
        appAccentColor = uiState.appAccentColor,
    ) {
    if (!initialSetupCompleted) {
        InitialSetupScreen(
            hasAllPermissions = hasAllPermissions,
            permissionItems = permissionItems,
            onRequestPermissions = onRequestPermissions,
            onRequestSinglePermission = onRequestSinglePermission,
            onOpenAppSettings = onOpenAppSettings,
            onContinue = onCompleteInitialSetup,
        )
        return@BTBatteryTheme
    }

    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = isSettingsOpen) {
        isSettingsOpen = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSettingsOpen) {
                            stringResource(R.string.settings_title)
                        } else {
                            stringResource(R.string.app_name)
                        },
                    )
                },
                navigationIcon = {
                    if (isSettingsOpen) {
                        IconButton(onClick = { isSettingsOpen = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                actions = {
                    if (!isSettingsOpen) {
                        IconButton(onClick = onOpenBluetoothSettings) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = stringResource(R.string.bluetooth_settings_title),
                            )
                        }
                        IconButton(onClick = { isSettingsOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = appTopBarContainerColor(),
                    scrolledContainerColor = appTopBarContainerColor(),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        val settingsScrollState = rememberScrollState()
        val contentModifier = remember(isSettingsOpen, settingsScrollState, padding) {
            if (isSettingsOpen) {
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(settingsScrollState)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            }
        }
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSettingsOpen) {
                PermissionsCard(
                    showRationale = showPermissionsRationale,
                    permissionItems = permissionItems,
                    onRequestPermissions = onRequestPermissions,
                    onRequestSinglePermission = onRequestSinglePermission,
                )

                SettingSwitchCard(
                    title = stringResource(R.string.monitoring_enabled),
                    checked = uiState.monitoringEnabled,
                    enabled = hasAllPermissions || !uiState.monitoringEnabled,
                    onCheckedChange = onMonitoringChanged,
                )

                AppearanceAndLanguageCard(
                    selectedTheme = uiState.appTheme,
                    selectedLanguage = uiState.appLanguage,
                    selectedAccentColor = uiState.appAccentColor,
                    onThemeChanged = onThemeChanged,
                    onLanguageChanged = onLanguageChanged,
                    onAccentColorChanged = onAccentColorChanged,
                )
            } else {
                StatusCard(
                    snapshot = uiState.lastSnapshot,
                    monitoringEnabled = uiState.monitoringEnabled,
                )
                HeadphoneHistoryCard(
                    history = uiState.headphoneHistory,
                )
            }
        }
    }
    }
}

@Composable
private fun InitialSetupScreen(
    hasAllPermissions: Boolean,
    permissionItems: List<PermissionUiItem>,
    onRequestPermissions: () -> Unit,
    onRequestSinglePermission: (String) -> Unit,
    onOpenAppSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    val totalPermissions = permissionItems.size
    val grantedCount = permissionItems.count { it.granted }
    val currentItem = permissionItems.firstOrNull { !it.granted }
    val currentStep = (grantedCount + 1).coerceAtMost(totalPermissions)
    val isSystemBlocked = currentItem != null &&
        currentItem.requestedCount >= 2 &&
        !currentItem.showRationale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.initial_setup_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (currentItem != null) {
                    Text(
                        text = stringResource(
                            R.string.initial_setup_step_counter,
                            currentStep,
                            totalPermissions,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(currentItem.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isSystemBlocked) {
                            stringResource(R.string.permission_system_blocked_message)
                        } else if (currentItem.requestedCount > 0) {
                            stringResource(R.string.initial_setup_denied_message)
                        } else {
                            stringResource(R.string.initial_setup_step_message)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.initial_setup_all_permissions_granted),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    permissionItems.forEach { item ->
                        val statusText = if (item.granted) {
                            stringResource(R.string.initial_setup_status_granted)
                        } else {
                            stringResource(R.string.initial_setup_status_pending)
                        }
                        Text(
                            text = "${stringResource(item.labelRes)}: $statusText",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.granted) {
                                Color(0xFF22C55E)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Button(
                    onClick = {
                        if (hasAllPermissions) {
                            onContinue()
                        } else if (currentItem != null) {
                            onRequestSinglePermission(currentItem.permission)
                        } else {
                            onRequestPermissions()
                        }
                    },
                ) {
                    Text(
                        text = if (hasAllPermissions) {
                            stringResource(R.string.initial_setup_continue)
                        } else if (isSystemBlocked) {
                            stringResource(R.string.initial_setup_retry_permission)
                        } else if ((currentItem?.requestedCount ?: 0) > 0) {
                            stringResource(R.string.initial_setup_retry_permission)
                        } else {
                            stringResource(R.string.initial_setup_grant_current_permission)
                        },
                    )
                }
                if (isSystemBlocked) {
                    Button(
                        onClick = onOpenAppSettings,
                    ) {
                        Text(text = stringResource(R.string.open_app_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    showRationale: Boolean,
    permissionItems: List<PermissionUiItem>,
    onRequestPermissions: () -> Unit,
    onRequestSinglePermission: (String) -> Unit,
) {
    val hasMissingPermissions = permissionItems.any { !it.granted }
    val grantedCount = permissionItems.count { it.granted }
    val totalCount = permissionItems.size

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.permissions_status_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (hasMissingPermissions) {
                    stringResource(
                        R.string.permissions_granted_count,
                        grantedCount,
                        totalCount,
                    )
                } else {
                    stringResource(R.string.permission_granted)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasMissingPermissions && showRationale) {
                Text(
                    text = stringResource(R.string.permissions_needed_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            permissionItems.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (item.granted) {
                            stringResource(R.string.permission_granted)
                        } else {
                            stringResource(R.string.permission_not_granted)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.granted) {
                            Color(0xFF22C55E)
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }

                if (!item.granted) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { onRequestSinglePermission(item.permission) }) {
                            Text(text = stringResource(R.string.grant_permission_short))
                        }
                    }
                }
            }
            if (hasMissingPermissions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onRequestPermissions) {
                        Text(text = stringResource(R.string.grant_permissions))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceAndLanguageCard(
    selectedTheme: AppTheme,
    selectedLanguage: AppLanguage,
    selectedAccentColor: AppAccentColor,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit,
    onAccentColorChanged: (AppAccentColor) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleMedium,
            )
            SelectionDropdownRow(
                title = stringResource(R.string.settings_theme),
                value = when (selectedTheme) {
                    AppTheme.LIGHT -> stringResource(R.string.theme_light)
                    AppTheme.DARK -> stringResource(R.string.theme_dark)
                },
                options = listOf(
                    DropdownOption(
                        label = stringResource(R.string.theme_light),
                        enabled = true,
                        onClick = { onThemeChanged(AppTheme.LIGHT) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.theme_dark),
                        enabled = true,
                        onClick = { onThemeChanged(AppTheme.DARK) },
                    ),
                ),
            )
            SelectionDropdownRow(
                title = stringResource(R.string.settings_accent_color),
                value = when (selectedAccentColor) {
                    AppAccentColor.BLUE -> stringResource(R.string.accent_blue)
                    AppAccentColor.GREEN -> stringResource(R.string.accent_green)
                    AppAccentColor.PURPLE -> stringResource(R.string.accent_purple)
                    AppAccentColor.LIME -> stringResource(R.string.accent_lime)
                    AppAccentColor.BROWN -> stringResource(R.string.accent_brown)
                    AppAccentColor.PINK -> stringResource(R.string.accent_pink)
                    AppAccentColor.TEAL -> stringResource(R.string.accent_teal)
                    AppAccentColor.CYAN -> stringResource(R.string.accent_cyan)
                    AppAccentColor.INDIGO -> stringResource(R.string.accent_indigo)
                    AppAccentColor.AMBER -> stringResource(R.string.accent_amber)
                },
                options = listOf(
                    DropdownOption(
                        label = stringResource(R.string.accent_blue),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.BLUE) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_green),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.GREEN) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_lime),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.LIME) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_purple),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.PURPLE) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_brown),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.BROWN) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_pink),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.PINK) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_teal),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.TEAL) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_cyan),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.CYAN) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_indigo),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.INDIGO) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.accent_amber),
                        enabled = true,
                        onClick = { onAccentColorChanged(AppAccentColor.AMBER) },
                    ),
                ),
            )

            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
            )
            SelectionDropdownRow(
                title = stringResource(R.string.settings_language),
                value = when (selectedLanguage) {
                    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                    AppLanguage.RUSSIAN -> stringResource(R.string.language_russian)
                },
                options = listOf(
                    DropdownOption(
                        label = stringResource(R.string.language_english),
                        enabled = true,
                        onClick = { onLanguageChanged(AppLanguage.ENGLISH) },
                    ),
                    DropdownOption(
                        label = stringResource(R.string.language_russian),
                        enabled = true,
                        onClick = { onLanguageChanged(AppLanguage.RUSSIAN) },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun BTBatteryTheme(
    appTheme: AppTheme,
    appAccentColor: AppAccentColor,
    content: @Composable () -> Unit,
) {
    val baseScheme = when (appTheme) {
        AppTheme.DARK -> darkColorScheme()
        AppTheme.LIGHT -> lightColorScheme()
    }
    val accent = when (appAccentColor) {
        AppAccentColor.BLUE -> Color(0xFF3B82F6)
        AppAccentColor.GREEN -> Color(0xFF22C55E)
        AppAccentColor.PURPLE -> Color(0xFF8B5CF6)
        AppAccentColor.LIME -> Color(0xFF84CC16)
        AppAccentColor.BROWN -> Color(0xFF8B5E34)
        AppAccentColor.PINK -> Color(0xFFEC4899)
        AppAccentColor.TEAL -> Color(0xFF14B8A6)
        AppAccentColor.CYAN -> Color(0xFF06B6D4)
        AppAccentColor.INDIGO -> Color(0xFF6366F1)
        AppAccentColor.AMBER -> Color(0xFFF59E0B)
    }
    val colorScheme = baseScheme.copy(
        primary = accent,
        secondary = accent,
        tertiary = accent,
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private data class DropdownOption(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionDropdownRow(
    title: String,
    value: String,
    options: List<DropdownOption>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        if (option.enabled) {
                            option.onClick()
                            expanded = false
                        }
                    },
                    enabled = option.enabled,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchCard(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = MaterialTheme.colorScheme.background

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = if (isDarkTheme) {
                    SwitchDefaults.colors(
                        checkedThumbColor = background,
                        uncheckedThumbColor = background,
                        disabledCheckedThumbColor = background.copy(alpha = 0.38f),
                        disabledUncheckedThumbColor = background.copy(alpha = 0.38f),
                    )
                } else {
                    SwitchDefaults.colors()
                },
            )
        }
    }
}

@Composable
private fun StatusCard(
    snapshot: BluetoothBatterySnapshot?,
    monitoringEnabled: Boolean,
) {
    if (!monitoringEnabled) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.current_status),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                )
                Text(text = stringResource(R.string.monitoring_disabled_status))
            }
        }
        return
    }

    val isConnected = snapshot?.isConnected == true
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.current_status),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            if (!isConnected) {
                Text(text = stringResource(R.string.waiting_for_headphones))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    BatteryCircleItem(
                        label = stringResource(R.string.main_battery),
                        level = null,
                    )
                }
            } else {
                Text(text = stringResource(R.string.device_connected_only, snapshot.deviceName))
                BatteryCirclesPanel(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun HeadphoneHistoryCard(
    history: List<HeadphoneHistoryEntry>,
) {
    if (history.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.previously_connected_headphones),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
            history.forEachIndexed { index, entry ->
                val hasSplitBattery = entry.lastLeftLevel != null ||
                    entry.lastRightLevel != null ||
                    entry.lastCaseLevel != null
                val battery = if (hasSplitBattery) {
                    buildList {
                        entry.lastLeftLevel?.let { add("${stringResource(R.string.left_short)} $it%") }
                        entry.lastCaseLevel?.let { add("${stringResource(R.string.case_short)} $it%") }
                        entry.lastRightLevel?.let { add("${stringResource(R.string.right_short)} $it%") }
                    }.joinToString(separator = " • ")
                } else {
                    entry.lastBatteryLevel?.let { "$it%" }
                        ?: stringResource(R.string.battery_not_available)
                }
                val disconnectedAt = entry.lastDisconnectedAt?.let { formatHistoryDate(it) }
                    ?: stringResource(R.string.history_date_unknown)
                var expanded by rememberSaveable(entry.deviceName) { mutableStateOf(false) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.deviceName,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    if (expanded) {
                        Text(
                            text = stringResource(R.string.history_last_battery, battery),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.history_last_disconnected, disconnectedAt),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (index < history.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberElapsedTimeLabel(disconnectedSinceMillis: Long?): String? {
    if (disconnectedSinceMillis == null) return null
    val now by produceState(initialValue = System.currentTimeMillis(), disconnectedSinceMillis) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000)
        }
    }
    val elapsed = (now - disconnectedSinceMillis).coerceAtLeast(0L)
    val minutes = elapsed / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.elapsed_just_now)
        minutes < 60 -> stringResource(R.string.elapsed_minutes, minutes)
        minutes < 1_440 -> stringResource(R.string.elapsed_hours, minutes / 60)
        else -> stringResource(R.string.elapsed_days, minutes / 1_440)
    }
}

@Composable
private fun BatteryCirclesPanel(snapshot: BluetoothBatterySnapshot) {
    val hasSplit = snapshot.leftLevel != null || snapshot.rightLevel != null || snapshot.caseLevel != null
    if (hasSplit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            BatteryCircleItem(
                label = stringResource(R.string.left_short),
                level = snapshot.leftLevel,
            )
            BatteryCircleItem(
                label = stringResource(R.string.case_short),
                level = snapshot.caseLevel,
            )
            BatteryCircleItem(
                label = stringResource(R.string.right_short),
                level = snapshot.rightLevel,
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BatteryCircleItem(
                label = stringResource(R.string.main_battery),
                level = snapshot.primaryLevel,
            )
        }
    }
}

@Composable
private fun BatteryCircleItem(
    label: String,
    level: Int?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { ((level ?: 0).coerceIn(0, 100)) / 100f },
                modifier = Modifier.size(76.dp),
                strokeWidth = 7.dp,
                trackColor = batteryTrackColor(),
                color = batteryProgressColor(level),
            )
            Text(
                text = level?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun batteryProgressColor(level: Int?): Color {
    val safeLevel = level ?: return MaterialTheme.colorScheme.primary
    return when {
        safeLevel <= 10 -> Color(0xFFEF4444)
        safeLevel <= 30 -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun formatHistoryDate(millis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    return formatter.format(Date(millis))
}

@Composable
private fun batteryTrackColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val isLightTheme = scheme.background.luminance() > 0.5f
    return if (isLightTheme) Color(0xFF8C93A2) else scheme.surfaceVariant
}

@Composable
private fun appTopBarContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val isLightTheme = background.luminance() > 0.5f
    return if (isLightTheme) {
        lerp(background, Color.Black, 0.08f)
    } else {
        lerp(background, Color.White, 0.08f)
    }
}

