package com.lampda.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LauncherIconTest {
    private val source = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    @Test fun adaptiveLayersHaveTheCorrectDimensionsAndAlphaAtEveryDensity() {
        mapOf("mdpi" to 108, "hdpi" to 162, "xhdpi" to 216, "xxhdpi" to 324, "xxxhdpi" to 432).forEach { (density, size) ->
            val file = File(source, "res/mipmap-$density/ic_launcher_foreground.png")
            val bytes = file.inputStream().use { input -> ByteArray(33).also { input.read(it) } }
            assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), bytes.copyOfRange(0, 8))
            assertEquals("IHDR", String(bytes, 12, 4, Charsets.US_ASCII))
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            assertEquals(size, header.getInt(16))
            assertEquals(size, header.getInt(20))
            assertEquals("$density must have RGBA pixels", 6, bytes[25].toInt())
        }
    }

    @Test fun manifestUsesTheAdaptiveIconAndThemedVariantHasAMonochromeLayer() {
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val manifest = parser.parse(File(source, "AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0).attributes
        assertEquals("@mipmap/ic_launcher", application.getNamedItem("android:icon").nodeValue)
        assertEquals("@mipmap/ic_launcher", application.getNamedItem("android:roundIcon").nodeValue)
        val adaptive = parser.parse(File(source, "res/mipmap-anydpi-v33/ic_launcher.xml"))
        assertEquals(1, adaptive.getElementsByTagName("monochrome").length)
    }
}
