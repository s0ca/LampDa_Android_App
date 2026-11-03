package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class ElkColorProtocolTest {
    @Test fun paleColorFitsOneDefaultBleWriteWithoutUartFraming() {
        val packet = ElkColorProtocol.frame(RgbColor(229, 255, 217))
        assertArrayEquals(byteArrayOf(0x7E, 0, 5, 3, 229.toByte(), 255.toByte(), 217.toByte(), 0, 0xEF.toByte()), packet)
        assertEquals(9, packet.size)
    }

    @Test fun preservesEveryChannelValueWithoutPercentageQuantization() {
        for (value in 0..255) {
            listOf(RgbColor(value, 255, 217), RgbColor(229, value, 217), RgbColor(229, 255, value)).forEach { color ->
                val packet = ElkColorProtocol.frame(color)
                assertEquals(color.red, packet[4].toInt() and 255)
                assertEquals(color.green, packet[5].toInt() and 255)
                assertEquals(color.blue, packet[6].toInt() and 255)
                assertEquals(9, packet.size)
            }
        }
    }

    @Test fun targetsTheFirmwareElkColorCharacteristic() {
        assertEquals("0000fff0-0000-1000-8000-00805f9b34fb", ElkColorProtocol.ServiceUuid.toString())
        assertEquals("0000fff3-0000-1000-8000-00805f9b34fb", ElkColorProtocol.CharacteristicUuid.toString())
    }
}
