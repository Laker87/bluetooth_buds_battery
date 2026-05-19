package com.laker.btbudsbattery.presentation

import com.laker.btbudsbattery.core.AppLanguage
import com.laker.btbudsbattery.core.AppAccentColor
import com.laker.btbudsbattery.core.HeadphoneHistoryEntry
import com.laker.btbudsbattery.core.AppTheme
import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot

data class MainUiState(
    val monitoringEnabled: Boolean = true,
    val appTheme: AppTheme = AppTheme.LIGHT,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val appAccentColor: AppAccentColor = AppAccentColor.BLUE,
    val lastSnapshot: BluetoothBatterySnapshot? = null,
    val lastKnownSnapshot: BluetoothBatterySnapshot? = null,
    val disconnectedSinceMillis: Long? = null,
    val headphoneHistory: List<HeadphoneHistoryEntry> = emptyList(),
)

