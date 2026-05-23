package com.laker.btbudsbattery.core

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePermissionGateTest {
    @Test
    fun requiredMonitoringPermissions_containsBluetoothCorePermissions() {
        val permissions = RuntimePermissionGate.requiredMonitoringPermissions()

        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun requiredMonitoringPermissions_doesNotRequirePostNotifications() {
        val permissions = RuntimePermissionGate.requiredMonitoringPermissions()

        assertFalse(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }
}
