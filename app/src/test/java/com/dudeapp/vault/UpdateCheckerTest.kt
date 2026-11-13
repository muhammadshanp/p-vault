package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun passesInstalledVersionToTheUpdatePage() {
        assertEquals(
            "https://muhammadshanp.github.io/p-vault-website/update.html?v=0.4.2",
            updatePageUrl("0.4.2"),
        )
    }

    @Test
    fun updateNowOpensTheApkDirectly() {
        val json = org.json.JSONObject(
            """{"tag_name":"v0.4.2","assets":[
               {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"},
               {"name":"P-Vault.apk","browser_download_url":"https://example.com/P-Vault.apk"}]}""",
        )
        assertEquals("https://example.com/P-Vault.apk", apkDownloadUrl(json))
        assertEquals(
            "https://github.com/muhammadshanp/p-vault/releases/latest/download/P-Vault.apk",
            apkDownloadUrl(org.json.JSONObject("""{"tag_name":"v0.4.2","assets":[]}""")),
        )
    }

    @Test
    fun comparesVersionParts() {
        assertTrue(isNewerVersion("1.9", "1.8"))
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertTrue(isNewerVersion("2.0.1", "1.99.9"))
        assertTrue(isNewerVersion("0.3.1", "0.3"))
        assertFalse(isNewerVersion("0.3", "0.3.1"))
        assertFalse(isNewerVersion("1.8", "1.8"))
        assertFalse(isNewerVersion("1.8", "1.9"))
    }

    @Test
    fun pushRequestTargetsEveryInstall() {
        val json = org.json.JSONObject(pushRequestJson(" Hello ", "World", validateOnly = true))
        assertTrue(json.getBoolean("validate_only"))
        val message = json.getJSONObject("message")
        assertEquals("all", message.getString("topic"))
        assertEquals("Hello", message.getJSONObject("notification").getString("title"))
        assertEquals("World", message.getJSONObject("notification").getString("body"))
    }
}
