package com.lampda.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

private const val FirmwareReleaseApi = "https://api.github.com/repos/BaptisteHudyma/Lamp-Da/releases/latest"
private val FirmwareTag = Regex("""v[0-9]+\.[0-9]+(?:\.[0-9]+)?""")
private val ChecksumLine = Regex("""([A-Fa-f0-9]{64})[ \t]+\*?([^\s]+)""")

internal fun officialFirmwareName(tag: String, type: LampType): String {
    require(FirmwareTag.matches(tag)) { "Unsupported official firmware release." }
    val variant = if (type == LampType.Simple) "simple" else "indexable"
    return "${variant}_${tag.drop(1).replace('.', '_')}.uf2"
}

internal fun officialFirmwareChecksum(manifest: String, name: String): String? =
    manifest.lineSequence().mapNotNull { ChecksumLine.matchEntire(it.trim()) }
        .filter { it.groupValues[2] == name }.singleOrNull()?.groupValues?.get(1)?.lowercase()

internal fun InputStream.readFirmwareBytes(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(result.size() + count <= limit) { "Invalid or oversized UF2 file." }
        result.write(buffer, 0, count)
    }
    return result.toByteArray()
}

private fun download(url: String, limit: Int, accept: String? = null): ByteArray {
    val connection = URL(url).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        if (accept != null) connection.setRequestProperty("Accept", accept)
        check(connection.responseCode == 200) { "Could not download the official firmware." }
        return connection.inputStream.use { it.readFirmwareBytes(limit) }
    } finally {
        connection.disconnect()
    }
}

internal fun downloadOfficialFirmware(type: LampType): LocalFirmware {
    val release = JSONObject(download(FirmwareReleaseApi, 256 * 1024, "application/vnd.github+json").toString(Charsets.UTF_8))
    require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Unsupported official firmware release." }
    val tag = release.getString("tag_name")
    val name = officialFirmwareName(tag, type)
    val assets = release.getJSONArray("assets")
    val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }
    require(name in names && "SHA256SUMS" in names) { "No compatible UF2 with a checksum in the latest release." }
    val base = "${ProjectLinks.FirmwareSource}/releases/download/$tag"
    val manifest = download("$base/SHA256SUMS", 16 * 1024).toString(Charsets.UTF_8)
    val expected = officialFirmwareChecksum(manifest, name)
        ?: error("No compatible UF2 with a checksum in the latest release.")
    val bytes = download("$base/$name", LampUf2.MaxFileBytes)
    val metadata = LampUf2.inspect(name, bytes)
    require(metadata.variant == type && metadata.sha256 == expected) { "Official firmware checksum mismatch." }
    return LocalFirmware(name, bytes, metadata)
}
