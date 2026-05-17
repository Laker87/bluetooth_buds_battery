package com.example.btbattery.presentation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btbattery.core.AppLanguage
import com.example.btbattery.core.AppAccentColor
import com.example.btbattery.core.AppPreferences
import com.example.btbattery.core.AppTheme
import com.example.btbattery.core.FastPairEventBus
import com.example.btbattery.domain.model.BluetoothBatterySnapshot
import com.example.btbattery.service.BluetoothBatteryService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MainUiState(
            monitoringEnabled = appPreferences.monitoringEnabled,
            showInAppFastPair = appPreferences.showInAppFastPair,
            appTheme = appPreferences.appTheme,
            appLanguage = appPreferences.appLanguage,
            appAccentColor = appPreferences.appAccentColor,
            lastKnownSnapshot = appPreferences.lastKnownSnapshot,
            disconnectedSinceMillis = appPreferences.disconnectedSinceMillis,
            headphoneHistory = appPreferences.headphoneHistory,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            FastPairEventBus.events.collect { snapshot ->
                val previousSnapshot = _uiState.value.lastSnapshot
                val merged = mergeWithLastKnownBattery(previousSnapshot, snapshot)
                _uiState.update { state ->
                    val wasConnected = state.lastSnapshot?.isConnected == true
                    val disconnectedSinceMillis = when {
                        merged.isConnected -> null
                        wasConnected && !merged.isConnected -> System.currentTimeMillis()
                        else -> state.disconnectedSinceMillis
                    }
                    val lastKnownSnapshot = when {
                        merged.isConnected -> merged
                        state.lastKnownSnapshot != null -> state.lastKnownSnapshot
                        merged.primaryLevel != null || merged.hasSplitLevels -> merged
                        else -> null
                    }
                    state.copy(
                        lastSnapshot = merged,
                        lastKnownSnapshot = lastKnownSnapshot,
                        disconnectedSinceMillis = disconnectedSinceMillis,
                        headphoneHistory = state.headphoneHistory,
                        showFastPairCard = state.showInAppFastPair && shouldShowFastPairCard(
                            previous = state.lastSnapshot,
                            current = merged,
                        ),
                    )
                }
                _uiState.value.lastKnownSnapshot?.let { appPreferences.lastKnownSnapshot = it }
                appPreferences.disconnectedSinceMillis = _uiState.value.disconnectedSinceMillis
                val disconnectedAt = if (
                    _uiState.value.lastSnapshot?.isConnected == false &&
                    _uiState.value.disconnectedSinceMillis != null
                ) {
                    _uiState.value.disconnectedSinceMillis
                } else {
                    null
                }
                appPreferences.upsertHeadphoneHistory(
                    deviceAddress = merged.deviceAddress,
                    deviceName = merged.deviceName,
                    lastBatteryLevel = _uiState.value.lastKnownSnapshot?.primaryLevel ?: merged.primaryLevel,
                    lastDisconnectedAt = disconnectedAt,
                )
                _uiState.update { state -> state.copy(headphoneHistory = appPreferences.headphoneHistory) }
                if (_uiState.value.showFastPairCard) {
                    delay(6500)
                    _uiState.update { state -> state.copy(showFastPairCard = false) }
                }
            }
        }
    }

    fun onMonitoringChanged(context: Context, enabled: Boolean) {
        appPreferences.monitoringEnabled = enabled
        _uiState.update { it.copy(monitoringEnabled = enabled) }
        if (enabled) {
            val serviceIntent = Intent(context, BluetoothBatteryService::class.java).apply {
                action = BluetoothBatteryService.ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            val stopIntent = Intent(context, BluetoothBatteryService::class.java).apply {
                action = BluetoothBatteryService.ACTION_STOP_MONITORING
            }
            context.startService(stopIntent)
        }
    }

    fun ensureMonitoringRunning(context: Context) {
        if (!appPreferences.monitoringEnabled) return
        val serviceIntent = Intent(context, BluetoothBatteryService::class.java).apply {
            action = BluetoothBatteryService.ACTION_BOOT_RESTORE_MONITORING
        }
        context.startService(serviceIntent)
    }

    fun onShowInAppFastPairChanged(enabled: Boolean) {
        appPreferences.showInAppFastPair = enabled
        _uiState.update { it.copy(showInAppFastPair = enabled) }
    }

    fun dismissFastPairCard() {
        _uiState.update { it.copy(showFastPairCard = false) }
    }

    fun onThemeChanged(theme: AppTheme) {
        appPreferences.appTheme = theme
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun onLanguageChanged(language: AppLanguage) {
        appPreferences.appLanguage = language
        _uiState.update { it.copy(appLanguage = language) }
    }

    fun onAccentColorChanged(accentColor: AppAccentColor) {
        appPreferences.appAccentColor = accentColor
        _uiState.update { it.copy(appAccentColor = accentColor) }
    }

    private fun mergeWithLastKnownBattery(
        previous: BluetoothBatterySnapshot?,
        current: BluetoothBatterySnapshot,
    ): BluetoothBatterySnapshot {
        if (previous == null || !previous.isSameUserVisibleDevice(current)) return current
        return current.copy(
            batteryLevel = current.batteryLevel ?: previous.batteryLevel,
            leftLevel = current.leftLevel ?: previous.leftLevel,
            rightLevel = current.rightLevel ?: previous.rightLevel,
            caseLevel = current.caseLevel ?: previous.caseLevel,
        )
    }

    private fun shouldShowFastPairCard(
        previous: BluetoothBatterySnapshot?,
        current: BluetoothBatterySnapshot,
    ): Boolean {
        if (!current.isConnected || current.primaryLevel == null) return false
        if (previous == null || !previous.isSameUserVisibleDevice(current)) return true
        if (!previous.isConnected && current.isConnected) return true
        if (previous.primaryLevel != current.primaryLevel) return true
        if (previous.leftLevel != current.leftLevel) return true
        if (previous.rightLevel != current.rightLevel) return true
        if (previous.caseLevel != current.caseLevel) return true
        return false
    }

    private fun BluetoothBatterySnapshot.isSameUserVisibleDevice(
        other: BluetoothBatterySnapshot,
    ): Boolean {
        return deviceAddress == other.deviceAddress ||
            deviceName.normalizedDeviceName() == other.deviceName.normalizedDeviceName()
    }

    private fun String.normalizedDeviceName(): String {
        return lowercase().filter { it.isLetterOrDigit() }
    }
}
