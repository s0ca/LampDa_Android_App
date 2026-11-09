package com.lampda.app

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class BleSetupPolicyTest {
    @Test fun refusedNotificationWritesStopAutomaticRetries() {
        assertTrue(requiresManualBleRepair(3))
        assertFalse(requiresManualBleRepair(0))
        assertFalse(requiresManualBleRepair(133))
    }
    @Test fun batteryNotificationFailureDoesNotRequireDroppingUart() {
        assertTrue(optionalBleNotification(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")))
    }

    @Test fun uartAndUnknownNotificationFailuresRemainFatal() {
        assertFalse(optionalBleNotification(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")))
        assertFalse(optionalBleNotification(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")))
    }
}
