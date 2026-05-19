package com.laker.btbudsbattery.core

import android.content.Context
import android.content.SharedPreferences
import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot
import org.json.JSONArray
import org.json.JSONObject

enum class AppTheme {
    LIGHT,
    DARK,
}

enum class AppLanguage {
    ENGLISH,
    RUSSIAN,
}

enum class AppAccentColor {
    BLUE,
    GREEN,
    ORANGE,
    PURPLE,
    RED,
    PINK,
    TEAL,
    CYAN,
    INDIGO,
    AMBER,
}

data class HeadphoneHistoryEntry(
    val deviceAddress: String,
    val deviceName: String,
    val lastBatteryLevel: Int?,
    val lastLeftLevel: Int?,
    val lastRightLevel: Int?,
    val lastCaseLevel: Int?,
    val lastDisconnectedAt: Long?,
)

class AppPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var monitoringEnabled: Boolean
        get() = preferences.getBoolean(KEY_MONITORING_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var initialSetupCompleted: Boolean
        get() = preferences.getBoolean(KEY_INITIAL_SETUP_COMPLETED, false)
        set(value) = preferences.edit().putBoolean(KEY_INITIAL_SETUP_COMPLETED, value).apply()

    var appTheme: AppTheme
        get() = preferences.getString(KEY_APP_THEME, AppTheme.LIGHT.name)
            ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
            ?: AppTheme.LIGHT
        set(value) = preferences.edit().putString(KEY_APP_THEME, value.name).apply()

    var appLanguage: AppLanguage
        get() = preferences.getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.name)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: AppLanguage.ENGLISH
        set(value) = preferences.edit().putString(KEY_APP_LANGUAGE, value.name).apply()

    var appAccentColor: AppAccentColor
        get() = preferences.getString(KEY_APP_ACCENT_COLOR, AppAccentColor.BLUE.name)
            ?.let { runCatching { AppAccentColor.valueOf(it) }.getOrNull() }
            ?: AppAccentColor.BLUE
        set(value) = preferences.edit().putString(KEY_APP_ACCENT_COLOR, value.name).apply()

    var lastKnownSnapshot: BluetoothBatterySnapshot?
        get() {
            val deviceAddress = preferences.getString(KEY_LAST_DEVICE_ADDRESS, null) ?: return null
            val deviceName = preferences.getString(KEY_LAST_DEVICE_NAME, null) ?: return null
            val timestamp = preferences.getLong(KEY_LAST_TIMESTAMP, System.currentTimeMillis())
            val battery = preferences.getIntOrNull(KEY_LAST_BATTERY_LEVEL)
            val left = preferences.getIntOrNull(KEY_LAST_LEFT_LEVEL)
            val right = preferences.getIntOrNull(KEY_LAST_RIGHT_LEVEL)
            val case = preferences.getIntOrNull(KEY_LAST_CASE_LEVEL)
            return BluetoothBatterySnapshot(
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                batteryLevel = battery,
                leftLevel = left,
                rightLevel = right,
                caseLevel = case,
                isConnected = false,
                timestamp = timestamp,
            )
        }
        set(value) {
            if (value == null) {
                preferences.edit()
                    .remove(KEY_LAST_DEVICE_ADDRESS)
                    .remove(KEY_LAST_DEVICE_NAME)
                    .remove(KEY_LAST_BATTERY_LEVEL)
                    .remove(KEY_LAST_LEFT_LEVEL)
                    .remove(KEY_LAST_RIGHT_LEVEL)
                    .remove(KEY_LAST_CASE_LEVEL)
                    .remove(KEY_LAST_TIMESTAMP)
                    .apply()
                return
            }
            preferences.edit()
                .putString(KEY_LAST_DEVICE_ADDRESS, value.deviceAddress)
                .putString(KEY_LAST_DEVICE_NAME, value.deviceName)
                .putIntOrRemove(KEY_LAST_BATTERY_LEVEL, value.batteryLevel)
                .putIntOrRemove(KEY_LAST_LEFT_LEVEL, value.leftLevel)
                .putIntOrRemove(KEY_LAST_RIGHT_LEVEL, value.rightLevel)
                .putIntOrRemove(KEY_LAST_CASE_LEVEL, value.caseLevel)
                .putLong(KEY_LAST_TIMESTAMP, value.timestamp)
                .apply()
        }

    var disconnectedSinceMillis: Long?
        get() = preferences.getLongOrNull(KEY_DISCONNECTED_SINCE)
        set(value) = preferences.edit().putLongOrRemove(KEY_DISCONNECTED_SINCE, value).apply()

    var headphoneHistory: List<HeadphoneHistoryEntry>
        get() {
            val raw = preferences.getString(KEY_HEADPHONE_HISTORY, null) ?: return emptyList()
            val json = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            return buildList {
                for (index in 0 until json.length()) {
                    val entry = json.optJSONObject(index) ?: continue
                    val address = entry.optString(JSON_DEVICE_ADDRESS, "")
                    val name = entry.optString(JSON_DEVICE_NAME, "")
                    if (address.isBlank() || name.isBlank()) continue
                    add(
                        HeadphoneHistoryEntry(
                            deviceAddress = address,
                            deviceName = name,
                            lastBatteryLevel = if (entry.has(JSON_LAST_BATTERY_LEVEL)) {
                                entry.optInt(JSON_LAST_BATTERY_LEVEL).takeIf { it in 0..100 }
                            } else {
                                null
                            },
                            lastLeftLevel = if (entry.has(JSON_LAST_LEFT_LEVEL)) {
                                entry.optInt(JSON_LAST_LEFT_LEVEL).takeIf { it in 0..100 }
                            } else {
                                null
                            },
                            lastRightLevel = if (entry.has(JSON_LAST_RIGHT_LEVEL)) {
                                entry.optInt(JSON_LAST_RIGHT_LEVEL).takeIf { it in 0..100 }
                            } else {
                                null
                            },
                            lastCaseLevel = if (entry.has(JSON_LAST_CASE_LEVEL)) {
                                entry.optInt(JSON_LAST_CASE_LEVEL).takeIf { it in 0..100 }
                            } else {
                                null
                            },
                            lastDisconnectedAt = if (entry.has(JSON_LAST_DISCONNECTED_AT)) {
                                entry.optLong(JSON_LAST_DISCONNECTED_AT).takeIf { it > 0L }
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }
        set(value) {
            val json = JSONArray()
            value.forEach { item ->
                val entry = JSONObject()
                    .put(JSON_DEVICE_ADDRESS, item.deviceAddress)
                    .put(JSON_DEVICE_NAME, item.deviceName)
                item.lastBatteryLevel?.let { entry.put(JSON_LAST_BATTERY_LEVEL, it) }
                item.lastLeftLevel?.let { entry.put(JSON_LAST_LEFT_LEVEL, it) }
                item.lastRightLevel?.let { entry.put(JSON_LAST_RIGHT_LEVEL, it) }
                item.lastCaseLevel?.let { entry.put(JSON_LAST_CASE_LEVEL, it) }
                item.lastDisconnectedAt?.let { entry.put(JSON_LAST_DISCONNECTED_AT, it) }
                json.put(entry)
            }
            preferences.edit().putString(KEY_HEADPHONE_HISTORY, json.toString()).apply()
        }

    fun upsertHeadphoneHistory(
        deviceAddress: String,
        deviceName: String,
        lastBatteryLevel: Int?,
        lastLeftLevel: Int?,
        lastRightLevel: Int?,
        lastCaseLevel: Int?,
        lastDisconnectedAt: Long?,
    ) {
        if (deviceAddress.isBlank() || deviceName.isBlank()) return
        val current = headphoneHistory.toMutableList()
        val existingIndex = current.indexOfFirst { it.deviceAddress.equals(deviceAddress, ignoreCase = true) }
        val existing = current.getOrNull(existingIndex)
        val updated = HeadphoneHistoryEntry(
            deviceAddress = deviceAddress,
            deviceName = deviceName,
            lastBatteryLevel = lastBatteryLevel ?: existing?.lastBatteryLevel,
            lastLeftLevel = lastLeftLevel ?: existing?.lastLeftLevel,
            lastRightLevel = lastRightLevel ?: existing?.lastRightLevel,
            lastCaseLevel = lastCaseLevel ?: existing?.lastCaseLevel,
            lastDisconnectedAt = lastDisconnectedAt ?: existing?.lastDisconnectedAt,
        )
        if (existingIndex >= 0) current.removeAt(existingIndex)
        current.add(updated)
        headphoneHistory = current
            .sortedWith(
                compareByDescending<HeadphoneHistoryEntry> { it.lastDisconnectedAt ?: Long.MIN_VALUE }
                    .thenBy { it.deviceName.lowercase() },
            )
            .take(MAX_HEADPHONE_HISTORY)
    }

    companion object {
        private const val PREFS_NAME = "bt_fast_pair_prefs"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_INITIAL_SETUP_COMPLETED = "initial_setup_completed"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_APP_ACCENT_COLOR = "app_accent_color"
        private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"
        private const val KEY_LAST_DEVICE_NAME = "last_device_name"
        private const val KEY_LAST_BATTERY_LEVEL = "last_battery_level"
        private const val KEY_LAST_LEFT_LEVEL = "last_left_level"
        private const val KEY_LAST_RIGHT_LEVEL = "last_right_level"
        private const val KEY_LAST_CASE_LEVEL = "last_case_level"
        private const val KEY_LAST_TIMESTAMP = "last_timestamp"
        private const val KEY_DISCONNECTED_SINCE = "disconnected_since"
        private const val KEY_HEADPHONE_HISTORY = "headphone_history"
        private const val JSON_DEVICE_ADDRESS = "deviceAddress"
        private const val JSON_DEVICE_NAME = "deviceName"
        private const val JSON_LAST_BATTERY_LEVEL = "lastBatteryLevel"
        private const val JSON_LAST_LEFT_LEVEL = "lastLeftLevel"
        private const val JSON_LAST_RIGHT_LEVEL = "lastRightLevel"
        private const val JSON_LAST_CASE_LEVEL = "lastCaseLevel"
        private const val JSON_LAST_DISCONNECTED_AT = "lastDisconnectedAt"
        private const val MAX_HEADPHONE_HISTORY = 20
    }
}

private fun SharedPreferences.getIntOrNull(key: String): Int? {
    return if (contains(key)) getInt(key, 0) else null
}

private fun SharedPreferences.getLongOrNull(key: String): Long? {
    return if (contains(key)) getLong(key, 0L) else null
}

private fun SharedPreferences.Editor.putIntOrRemove(key: String, value: Int?): SharedPreferences.Editor {
    return if (value == null) remove(key) else putInt(key, value)
}

private fun SharedPreferences.Editor.putLongOrRemove(key: String, value: Long?): SharedPreferences.Editor {
    return if (value == null) remove(key) else putLong(key, value)
}

