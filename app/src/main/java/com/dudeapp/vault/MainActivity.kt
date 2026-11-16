package com.dudeapp.vault

import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
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
    private var needsLockChoice by mutableStateOf(false)
    private var pendingShare by mutableStateOf<String?>(null)
    private var external = false // a system picker is open; don't lock on that trip
    private var backgroundAt = 0L
    private var biometricPromptKey by mutableIntStateOf(0)
    private var autoPromptPending = false
    private val themeStore by lazy { ThemeStore(this) }
    private val settingsStore by lazy { SettingsStore(this) }
    private var theme by mutableStateOf(ThemePreset.WHITE)
    // The looks that came from the theme catalogue, kept here so a picture can be found
    // from the backdrop.
    private var catalogThemes by mutableStateOf<List<DownloadedTheme>>(emptyList())
    // shortcut: backgrounding the app while a picker is open keeps it unlocked on return.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 12+ shows the app icon while P Vault starts and then cuts to the app.
        // Instead, let the icon grow slightly and fade, with the app appearing underneath.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash ->
                val ease = DecelerateInterpolator()
                splash.iconView?.animate()
                    ?.scaleX(1.18f)
                    ?.scaleY(1.18f)
                    ?.alpha(0f)
                    ?.setInterpolator(ease)
                    ?.setDuration(280)
                    ?.start()
                splash.animate()
                    .alpha(0f)
                    .setInterpolator(ease)
                    .setStartDelay(80)
                    .setDuration(320)
                    .withEndAction { splash.remove() }
                    .start()
            }
        }

        // A special look is only shown while this phone still has special access. A look
        // from the catalogue is rebuilt from the stored copy, never from a build-in theme.
        catalogThemes = ThemeCatalogue(this).cached()
        theme = themeStore.load(catalogThemes).forAccess(AccessStore(this).access())
        applyNightMode(theme.dark)
        val settings = settingsStore.load()
        needsLockChoice = !settings.lockChoiceMade && !Auth.hasPin(this)
        needsSetup = !needsLockChoice && settings.appLock && !Auth.hasPin(this)
        locked = !needsLockChoice && settings.appLock && !needsSetup
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
            CompositionLocalProvider(LocalDownloadedThemes provides catalogThemes) {
            PVaultTheme(theme) {
                when {
                    needsLockChoice -> LockChoiceScreen(
                        onEnable = {
                            settingsStore.save(
                                settings.copy(lockChoiceMade = true, appLock = true),
                            )
                            needsLockChoice = false
                            needsSetup = true
                            locked = false
                        },
                        onSkip = {
                            settingsStore.save(
                                settings.copy(lockChoiceMade = true, appLock = false),
                            )
                            needsLockChoice = false
                            needsSetup = false
                            locked = false
                        },
                    )

                    needsSetup -> CreatePinScreen(
                        onDone = {
                            val current = settingsStore.load()
                            settingsStore.save(current.copy(lockChoiceMade = true, appLock = true))
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
                        onPinSetup = {
                            needsSetup = true
                            locked = false
                        },
                        theme = theme,
                        onThemeChange = {
                            theme = it
                            themeStore.save(it)
                            applyNightMode(it.dark)
                        },
                    )
                }
            }
            }
        }
    }

    /**
     * Tells Android which look P Vault uses, so the start-up screen (drawn by the system
     * before any app code runs) is dark when the dark theme is chosen. Android 12+ only;
     * the choice is remembered by the system for the next start.
     */
    private fun applyNightMode(dark: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
            )
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
        // Special access can be taken away while the app is in the background.
        val allowed = themeStore.load(catalogThemes).forAccess(AccessStore(this).access())
        if (allowed != theme) {
            theme = allowed
            applyNightMode(allowed.dark)
        }
        if (needsLockChoice || needsSetup) {
            locked = false
            return
        }
        val settings = settingsStore.load()
        if (!settings.appLock) {
            backgroundAt = 0L
            locked = false
            return
        }
        if (!locked && backgroundAt > 0L) {
            val delayMs = settings.lockDelaySeconds * 1000L
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

    override fun onStart() {
        super.onStart()
        // Reminders that come due while P Vault is on screen show as a pop-up; the bump
        // also brings up any that came due while it was closed.
        ReminderCenter.appVisible = true
        ReminderCenter.bump()
    }

    override fun onStop() {
        super.onStop()
        ReminderCenter.appVisible = false
        val settings = settingsStore.load()
        if (!external && !needsSetup && !needsLockChoice && settings.appLock) {
            if (settings.lockDelaySeconds == 0) {
                // Always arm the auto-prompt, even if the lock screen was already showing
                // when the user backgrounded the app (e.g. while a biometric prompt was up).
                if (!locked) biometricPromptKey = 0
                autoPromptPending = true
                locked = true
            } else if (!locked) {
                backgroundAt = System.currentTimeMillis()
            }
        }
    }
}
