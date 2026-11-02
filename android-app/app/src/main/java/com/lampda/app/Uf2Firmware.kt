package com.lampda.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal data class Uf2Metadata(val blocks: Int, val start: Long, val end: Long, val variant: LampType, val sha256: String)

internal object LampUf2 {
    const val MaxFileBytes = 2 * 1024 * 1024
    const val BoardId = "nRF52840-Lampda-v1.0"
    const val ApplicationStart = 0x26000L
    const val ApplicationEnd = 0xEC000L

    fun inspect(name: String, bytes: ByteArray): Uf2Metadata {
        require(name.endsWith(".uf2", ignoreCase = true)) { "Select an application UF2 file." }
        val lower = name.lowercase()
        require(listOf("bootloader", "softdevice", "current.uf2").none { it in lower }) {
            "Bootloader and SoftDevice updates are not supported."
        }
        val variant = when {
            Regex("^simple(?:[_ .-]|$)").containsMatchIn(lower) -> LampType.Simple
            Regex("^(?:indexable|rgb)(?:[_ .-]|$)").containsMatchIn(lower) -> LampType.Rgb
            else -> error("The filename must identify a Simple or RGB / Indexable firmware.")
        }
        require(bytes.isNotEmpty() && bytes.size <= MaxFileBytes && bytes.size % 512 == 0) {
            "Invalid or oversized UF2 file."
        }
        val count = bytes.size / 512
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val seen = BooleanArray(count)
        for (offset in bytes.indices step 512) {
            fun uint(field: Int) = buffer.getInt(offset + field).toLong() and 0xFFFFFFFFL
            require(uint(0) == 0x0A324655L && uint(4) == 0x9E5D5157L && uint(508) == 0x0AB16F30L) {
                "Invalid UF2 block signature."
            }
            require(uint(8) == 0x2000L && uint(28) == 0xADA52840L && uint(16) == 256L) {
                "Unsupported UF2 family, flags or payload size."
            }
            val index = uint(20)
            require(uint(24) == count.toLong() && index < count && !seen[index.toInt()]) {
                "UF2 blocks are missing, duplicated or inconsistent."
            }
            val address = uint(12)
            require(address == ApplicationStart + index * 256 && address + 256 <= ApplicationEnd) {
                "Firmware addresses are outside the supported application region."
            }
            seen[index.toInt()] = true
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Uf2Metadata(count, ApplicationStart, ApplicationStart + count * 256L, variant, hash)
    }

    fun validateBootInfo(info: String) {
        val fields = info.lineSequence().filter { ':' in it }.associate { line ->
            line.substringBefore(':').trim() to line.substringAfter(':').trim()
        }
        require(fields["Board-ID"] == BoardId && info.startsWith("UF2 Bootloader 0.9.2")) {
            "This is not the supported Lamp-Da v1.0 / UF2 0.9.2 bootloader."
        }
    }
}
