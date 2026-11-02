package com.lampda.app

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectLinksTest {
    @Test fun sourcesPointToTheRequestedRepositories() {
        assertEquals("/s0ca/LampDa_Android_App", URI(ProjectLinks.AppSource).path)
        assertEquals("/BaptisteHudyma/Lamp-Da", URI(ProjectLinks.FirmwareSource).path)
        listOf(ProjectLinks.AppSource, ProjectLinks.FirmwareSource).forEach { url ->
            val uri = URI(url)
            assertEquals("https", uri.scheme)
            assertEquals("github.com", uri.host)
        }
    }
}
