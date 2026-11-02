package com.lampda.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class UiLanguageTest {
    @Test fun languageChoiceRestoresAndDefaultsToExistingEnglishUi() {
        assertEquals(AppLanguage.English, AppLanguage.fromStored(null))
        assertEquals(AppLanguage.English, AppLanguage.fromStored("unknown"))
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromStored(it.name)) }
    }

    @Test fun localizedResourcesHaveMatchingCompleteKeys() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val english = strings(File(res, "values/strings.xml"))
        val french = strings(File(res, "values-fr/strings.xml"))
        assertEquals(english.keys, french.keys)
        assertTrue(english.size > 150)
        assertTrue(french.values.all { it.isNotBlank() })
        assertEquals("Connecter", french["ui_connect"])
        assertEquals("Connect", english["ui_connect"])
        english.forEach { (key, value) ->
            assertEquals("Placeholders for $key", Regex("%[0-9]+[$]s").findAll(value).map { it.value }.toSet(),
                Regex("%[0-9]+[$]s").findAll(french.getValue(key)).map { it.value }.toSet())
        }
    }

    @Test fun dynamicMessagesKeepTheirArgumentsAndCommandsAreNotTranslated() {
        assertEquals(R.string.ui_connecting to listOf("My-Lamp"), uiMessage("Connecting to My-Lamp"))
        assertEquals(R.string.ui_battery_value to listOf("35%"), uiMessage("Battery 35%"))
        assertEquals(R.string.ui_slot to listOf("8"), uiMessage("Slot 8"))
        assertNull(uiMessage("set mode 2 0"))
        assertNull(uiMessage("RX 120> sunset timer set to 5 minutes"))
    }

    private fun strings(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }
}
