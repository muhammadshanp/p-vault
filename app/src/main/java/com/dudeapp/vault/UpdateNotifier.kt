package com.dudeapp.vault

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

private const val UPDATE_CHANNEL = "p_vault_updates"
private const val UPDATE_NOTIFICATION_ID = 1001
private const val NEWS_CHANNEL = "p_vault_news"

/** Posts one tappable notification for the latest release. */
fun showUpdateNotification(context: Context, update: AppUpdate) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            UPDATE_CHANNEL,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A new P Vault version is available"
        },
    )

    val open = PendingIntent.getActivity(
        context,
        0,
        // Open P Vault itself; its update dialog downloads and installs the release.
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse(update.url)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(context, UPDATE_CHANNEL)
        .setSmallIcon(R.drawable.ic_update_notification)
        .setColor(0xFFE8633A.toInt())
        .setContentTitle("P Vault ${update.version} is available")
        .setContentText("Tap to open P Vault and update.")
        .setContentIntent(open)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .build()
    manager.notify(UPDATE_NOTIFICATION_ID, notification)
}

/** Creates the channel pushes are shown in. Safe to call on every launch. */
fun ensurePushChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(NEWS_CHANNEL, "News", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Messages from the P Vault team"
        },
    )
}

/** Shows a push that arrived while the app was open; tapping it opens P Vault. */
fun showPushNotification(context: Context, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    ensurePushChannel(context)
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val open = PendingIntent.getActivity(
        context,
        1,
        launch,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(context, NEWS_CHANNEL)
        .setSmallIcon(R.drawable.ic_update_notification)
        .setColor(0xFFE8633A.toInt())
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(Notification.BigTextStyle().bigText(body))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    // A different id per message, so a second push does not replace the first.
    context.getSystemService(NotificationManager::class.java)
        .notify((System.currentTimeMillis() % 100_000).toInt() + 2000, notification)
}

fun clearUpdateNotification(context: Context) {
    context.getSystemService(NotificationManager::class.java).cancel(UPDATE_NOTIFICATION_ID)
}
