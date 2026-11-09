package com.lampda.app

import java.util.UUID

private val OptionalBatteryNotification = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

internal fun optionalBleNotification(characteristic: UUID): Boolean = characteristic == OptionalBatteryNotification

// A refused CCCD write will not improve by repeatedly sending the same operation.
internal fun requiresManualBleRepair(status: Int): Boolean = status == 3
