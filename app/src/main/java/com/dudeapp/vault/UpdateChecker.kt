package com.dudeapp.vault

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val UPDATE_REPO = "muhammadshanp/p-vault"
private const val APK_URL = "https://github.com/muhammadshanp/p-vault/releases/latest/download/P-Vault.apk"

data class AppUpdate(val version: String, val url: String)

/** Reads the latest public GitHub release. Returns null when offline or no release exists. */
suspend fun checkForUpdate(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("https://api.github.com/repos/$UPDATE_REPO/releases/latest")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "PVault")
        try {
            if (connection.responseCode != 200) return@runCatching null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = Regex("""\d+(?:\.\d+)*""")
                .find(json.optString("tag_name"))
                ?.value
                ?: return@runCatching null
            AppUpdate(version, apkDownloadUrl(json))
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

internal fun updatePageUrl(currentVersion: String): String =
    "https://muhammadshanp.github.io/p-vault-website/update.html?v=${URLEncoder.encode(currentVersion, "UTF-8")}"

/** Direct APK download from the release; falls back to the /latest/download link. */
internal fun apkDownloadUrl(json: JSONObject): String {
    val assets = json.optJSONArray("assets") ?: return APK_URL
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
            return asset.optString("browser_download_url").takeIf { it.isNotEmpty() } ?: APK_URL
        }
    }
    return APK_URL
}

/** True when the release version is newer than the installed version. */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(value: String) = Regex("""\d+""").findAll(value).map { it.value.toInt() }.toList()
    val newer = parts(latest)
    val installed = parts(current)
    for (index in 0 until maxOf(newer.size, installed.size)) {
        val a = newer.getOrElse(index) { 0 }
        val b = installed.getOrElse(index) { 0 }
        if (a != b) return a > b
    }
    return false
}
