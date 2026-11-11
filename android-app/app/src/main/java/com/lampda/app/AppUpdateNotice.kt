package com.lampda.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

private const val LatestReleaseApi =
    "https://api.github.com/repos/s0ca/LampDa_Android_App/releases/latest"

private val StableVersion = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""")

private fun versionParts(version: String): List<Int>? {
    val match = StableVersion.matchEntire(version) ?: return null
    return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}

internal sealed interface AppUpdateResult {
    data class Available(val tag: String) : AppUpdateResult
    data object UpToDate : AppUpdateResult
    data object Unavailable : AppUpdateResult
}

internal fun compareStableRelease(installed: String, tag: String): AppUpdateResult {
    val current = versionParts(installed) ?: return AppUpdateResult.Unavailable
    if (!tag.startsWith("v")) return AppUpdateResult.Unavailable
    val latest = versionParts(tag.removePrefix("v")) ?: return AppUpdateResult.Unavailable
    for (index in current.indices) {
        if (latest[index] > current[index]) return AppUpdateResult.Available(tag)
        if (latest[index] < current[index]) return AppUpdateResult.UpToDate
    }
    return AppUpdateResult.UpToDate
}

internal suspend fun checkForAppUpdate(installed: String): AppUpdateResult = withContext(Dispatchers.IO) {
    var connection: HttpsURLConnection? = null
    try {
        connection = URL(LatestReleaseApi).openConnection() as HttpsURLConnection
        connection.useCaches = false
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("Cache-Control", "no-cache")
        if (connection.responseCode != 200) return@withContext AppUpdateResult.Unavailable
        val release = connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            return@withContext AppUpdateResult.Unavailable
        }
        compareStableRelease(installed, release.getString("tag_name"))
    } catch (_: IOException) {
        AppUpdateResult.Unavailable
    } catch (_: JSONException) {
        AppUpdateResult.Unavailable
    } finally {
        connection?.disconnect()
    }
}

@Composable
internal fun AppUpdateNotice() {
    if (BuildConfig.DEBUG || versionParts(BuildConfig.VERSION_NAME) == null) return

    var checked by rememberSaveable { mutableStateOf(false) }
    var updateTag by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!checked) {
            updateTag = (checkForAppUpdate(BuildConfig.VERSION_NAME) as? AppUpdateResult.Available)?.tag
            checked = true
        }
    }

    val tag = updateTag ?: return
    val context = LocalContext.current
    val noBrowser = uiString("No browser available")
    AlertDialog(
        onDismissRequest = { updateTag = null },
        title = { AppText("App update available") },
        text = { Text("v${BuildConfig.VERSION_NAME} → $tag") },
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${ProjectLinks.AppSource}/releases/tag/$tag")))
                    updateTag = null
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                }
            }) { AppText("View release on GitHub") }
        },
        dismissButton = { TextButton(onClick = { updateTag = null }) { AppText("Later") } },
    )
}
