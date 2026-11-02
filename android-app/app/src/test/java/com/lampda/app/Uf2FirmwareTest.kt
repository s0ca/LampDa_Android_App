package com.lampda.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue

class Uf2FirmwareTest {
    private fun uf2(count: Int = 2): ByteArray {
        val bytes = ByteArray(count * 512)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) { index ->
            val offset = index * 512
            listOf(0 to 0x0A324655, 4 to 0x9E5D5157.toInt(), 8 to 0x2000,
                12 to (LampUf2.ApplicationStart.toInt() + index * 256), 16 to 256,
                20 to index, 24 to count, 28 to 0xADA52840.toInt(), 508 to 0x0AB16F30).forEach {
                buffer.putInt(offset + it.first, it.second)
            }
        }
        return bytes
    }

    private fun reject(bytes: ByteArray, name: String = "simple_1_9.uf2") {
        assertThrows(IllegalArgumentException::class.java) { LampUf2.inspect(name, bytes) }
    }

    @Test fun acceptsValidSimpleApplication() {
        val result = LampUf2.inspect("simple_1_9.uf2", uf2())
        assertEquals(LampType.Simple, result.variant)
        assertEquals(2, result.blocks)
        assertEquals(64, result.sha256.length)
    }

    @Test fun acceptsRgbVariant() {
        assertEquals(LampType.Rgb, LampUf2.inspect("indexable_1_9.uf2", uf2()).variant)
    }

    @Test fun rejectsTruncationAndEmptyFiles() {
        reject(byteArrayOf())
        reject(uf2().copyOf(513))
    }

    @Test fun rejectsSignatureAndWrongFamily() {
        reject(uf2().also { it[0] = 0 })
        reject(uf2().also { it[28] = 0 })
    }

    @Test fun rejectsDuplicatesAndWrongCount() {
        reject(uf2().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(512 + 20, 0) })
        reject(uf2().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(24, 3) })
    }

    @Test fun rejectsWritesOutsideApplicationRegion() {
        reject(uf2().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 0) })
        reject(uf2().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 0x27000) })
        reject(uf2(3300))
    }

    @Test fun rejectsBootloaderAndCurrentFlashDumps() {
        reject(uf2(), "simple_bootloader.uf2")
        reject(uf2(), "CURRENT.UF2")
    }

    @Test fun requiresExactBootBoardAndSupportedVersion() {
        LampUf2.validateBootInfo("UF2 Bootloader 0.9.2-dirty\r\nBoard-ID: ${LampUf2.BoardId}\r\n")
        assertThrows(IllegalArgumentException::class.java) {
            LampUf2.validateBootInfo("UF2 Bootloader 0.9.2\nBoard-ID: another-board\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LampUf2.validateBootInfo("UF2 Bootloader 0.8.0\nBoard-ID: ${LampUf2.BoardId}\n")
        }
    }

    @Test fun parsesOnlyUnambiguousExactLampTypeReplies() {
        assertEquals(LampType.Simple, parseUsbLampType("123> simple\r\n"))
        assertEquals(LampType.Rgb, parseUsbLampType("indexable\n"))
        assertNull(parseUsbLampType("not simple"))
        assertNull(parseUsbLampType("simple\nindexable"))
    }

    @Test fun validatesOfficialLocalFirmwareFixture() {
        val file = File("../../firmware/official-v1.9/simple_1_9.uf2")
        assumeTrue("Optional local official v1.9 integration fixture", file.isFile)
        val result = LampUf2.inspect(file.name, file.readBytes())
        assertEquals("2db9aafbdc846862babb6afda8f322074c9f071ca3810720f6dc2b98e0b50cbc", result.sha256)
    }
}
