package com.dudeapp.vault

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var locked by mutableStateOf(true)
    private var needsSetup by mutableStateOf(false)
    private var pendingShare by mutableStateOf<String?>(null)
    private var external = false // a system picker is open; don't lock on that trip
    private var backgroundAt = 0L
    private var biometricPromptKey by mutableIntStateOf(0)
    private var autoPromptPending = false
    private val themeStore by lazy { ThemeStore(this) }
    private val settingsStore by lazy { SettingsStore(this) }
    private var theme by mutableStateOf(ThemePreset.WHITE)
    // shortcut: backgrounding the app while a picker is open keeps it unlocked on return.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        theme = themeStore.load()
        needsSetup = !Auth.hasPin(this)
        locked = !needsSetup
        autoPromptPending = locked
        pendingShare = extractShare(intent)

        setContent {
            val dark = theme.dark
            DisposableEffect(dark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                onDispose { }
            }
            PVaultTheme(theme) {
                when {
                    needsSetup -> CreatePinScreen(
                        onDone = {
                            needsSetup = false
                            locked = false
                        },
                    )

                    locked -> LockScreen(
                        autoPromptKey = biometricPromptKey,
                        onUnlocked = { locked = false },
                    )

                    else -> VaultApp(
                        sharedText = pendingShare,
                        onShareConsumed = { pendingShare = null },
                        onExternal = { external = true },
                        theme = theme,
                        onThemeChange = {
                            theme = it
                            themeStore.save(it)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = extractShare(intent)
    }

    private fun extractShare(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        return intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    }

    override fun onResume() {
        super.onResume()
        external = false
        if (!locked && backgroundAt > 0L) {
            val delayMs = settingsStore.load().lockDelaySeconds * 1000L
            if (System.currentTimeMillis() - backgroundAt >= delayMs) {
                biometricPromptKey = 0
                autoPromptPending = true
                locked = true
            }
            backgroundAt = 0L
        }
        if (locked && autoPromptPending) {
            autoPromptPending = false
            biometricPromptKey += 1
        }
    }

    override fun onStop() {
        super.onStop()
        if (!external && !needsSetup) {
            if (settingsStore.load().lockDelaySeconds == 0) {
                if (!locked) {
                    biometricPromptKey = 0
                    autoPromptPending = true
                }
                locked = true
            } else if (!locked) {
                backgroundAt = System.currentTimeMillis()
            }
        }
    }
}
