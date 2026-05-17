package com.example.btbattery.presentation

import com.example.btbattery.core.AppLanguage
import com.example.btbattery.core.AppAccentColor
import com.example.btbattery.core.HeadphoneHistoryEntry
import com.example.btbattery.core.AppTheme
import com.example.btbattery.domain.model.BluetoothBatterySnapshot

data class MainUiState(
    val monitoringEnabled: Boolean = false,
    val appTheme: AppTheme = AppTheme.LIGHT,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val appAccentColor: AppAccentColor = AppAccentColor.BLUE,
    val lastSnapshot: BluetoothBatterySnapshot? = null,
    val lastKnownSnapshot: BluetoothBatterySnapshot? = null,
    val disconnectedSinceMillis: Long? = null,
    val headphoneHistory: List<HeadphoneHistoryEntry> = emptyList(),
)
