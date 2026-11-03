package com.lampda.app

import org.junit.Assert.*
import org.junit.Test

class UartLogFilterTest {
    @Test fun offersOnlyAllRxAndTx() {
        assertEquals(listOf("All", "RX", "TX"), UartLogFilter.entries.map { it.label })
    }

    @Test fun legacyAppSelectionFallsBackToAll() {
        assertEquals(UartLogFilter.All, UartLogFilter.fromStored("App"))
        assertEquals(UartLogFilter.All, UartLogFilter.fromStored(null))
        assertEquals(UartLogFilter.Rx, UartLogFilter.fromStored("Rx"))
    }

    @Test fun allKeepsDiagnosticsAndRxKeepsFirmwareMessages() {
        val logs = listOf("RX 123> APP_MODE 1", "RX simple", "TX t", "APP scan failed: 1")
        assertEquals(logs, logs.filter(UartLogFilter.All::includes))
        assertEquals(logs.take(2), logs.filter(UartLogFilter.Rx::includes))
        assertEquals(listOf("TX t"), logs.filter(UartLogFilter.Tx::includes))
    }
}
