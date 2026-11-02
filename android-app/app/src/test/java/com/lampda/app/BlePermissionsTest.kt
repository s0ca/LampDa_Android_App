package com.lampda.app

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class BlePermissionsTest {
    @Test fun android12AndLaterNeverRequestLocation() {
        for (api in listOf(31, 32, 33, 34, 35, 36)) {
            assertArrayEquals(arrayOf(Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT), requiredBlePermissions(api))
        }
    }

    @Test fun classicScanKeepsRequiredLocationPermissionOnAndroid8To11() {
        for (api in 26..30) {
            assertArrayEquals(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requiredBlePermissions(api))
        }
    }
}
