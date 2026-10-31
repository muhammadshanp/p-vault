package com.dudeapp.vault

import android.content.Context

data class AppSettings(
    val autoBackup: Boolean = true,
    val reminderDays: Int = 7,
    val keepBackups: Int = 2,
    val appLock: Boolean = true,
    val lockChoiceMade: Boolean = false,
    val lockDelaySeconds: Int = 0,
    val biometricUnlock: Boolean = true,
)

internal val REMINDER_DAY_OPTIONS = listOf(1, 7, 14, 30)
internal val KEEP_BACKUP_OPTIONS = listOf(1, 2, 3, 5, 10)
internal val LOCK_DELAY_OPTIONS = listOf(0, 30, 60, 300)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("dude_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        autoBackup = prefs.getBoolean("auto_backup", true),
        reminderDays = prefs.getInt("reminder_days", 7).coerceIn(1, 90),
        keepBackups = prefs.getInt("keep_backups", 2).coerceIn(1, 10),
        appLock = prefs.getBoolean("app_lock", true),
        lockChoiceMade = prefs.getBoolean("lock_choice_made", false),
        lockDelaySeconds = prefs.getInt("lock_delay_seconds", 0).coerceIn(0, 300),
        biometricUnlock = prefs.getBoolean("biometric_unlock", true),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean("auto_backup", settings.autoBackup)
            .putInt("reminder_days", settings.reminderDays.coerceIn(1, 90))
            .putInt("keep_backups", settings.keepBackups.coerceIn(1, 10))
            .putBoolean("app_lock", settings.appLock)
            .putBoolean("lock_choice_made", settings.lockChoiceMade)
            .putInt("lock_delay_seconds", settings.lockDelaySeconds.coerceIn(0, 300))
            .putBoolean("biometric_unlock", settings.biometricUnlock)
            .apply()
    }
}
