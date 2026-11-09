package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class FirmwareFeedbackTest {
    @Test fun parsesOfficialSignalsWithoutTimestampOrWithOne() {
        assertEquals(FirmwareFeedback.Brightness(1024), firmwareFeedback("\$brgt:1024\r\n"))
        assertEquals(FirmwareFeedback.Ramp(255), firmwareFeedback("123> \$ramp:255"))
        assertEquals(FirmwareFeedback.Mode(2, 1), firmwareFeedback("\$mode:2:1"))
        assertEquals(FirmwareFeedback.Indicator(2), firmwareFeedback("\$ind:2"))
    }

    @Test fun rejectsMalformedOrOutOfRangeSignals() {
        listOf("\$brgt:1025", "\$ramp:-1", "\$ind:3", "\$mode:1", "\$mode:1:256",
            "\$mode:a:1", "\$ramp:4:5", "unknown command: '7'").forEach {
            assertNull(it, firmwareFeedback(it))
        }
    }

    @Test fun readsActualNameWithoutTreatingRenameAcknowledgmentAsActiveName() {
        assertEquals(FirmwareFeedback.BleName("s0ca-LampDa"), firmwareFeedback("32> ble_name: s0ca-LampDa"))
        assertNull(firmwareFeedback("New ble name set to: New-Name, will be set after a reboot"))
        assertNull(firmwareFeedback("ble_name: "))
    }

    @Test fun respectsNegotiatedAttPayloadAndFirmwareMaximum() {
        assertEquals(20, uartPayloadSize(23))
        assertEquals(97, uartPayloadSize(100))
        assertEquals(244, uartPayloadSize(247))
        assertEquals(244, uartPayloadSize(517))
        assertEquals(20, uartPayloadSize(0))
        val bytes = (Firmware19.color(RgbColor(229, 255, 217)) + "\n").toByteArray()
        assertEquals(1, bytes.toList().chunked(uartPayloadSize(247)).size)
        assertArrayEquals(bytes, bytes.toList().chunked(uartPayloadSize(23)).flatten().toByteArray())
    }
}
