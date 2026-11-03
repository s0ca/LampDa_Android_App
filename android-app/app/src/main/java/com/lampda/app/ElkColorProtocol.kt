package com.lampda.app

import java.util.UUID

internal object ElkColorProtocol {
    val ServiceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val CharacteristicUuid: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")

    fun frame(color: RgbColor): ByteArray = byteArrayOf(
        0x7E, 0x00, 0x05, 0x03,
        color.red.toByte(), color.green.toByte(), color.blue.toByte(),
        0x00, 0xEF.toByte(),
    )
}
