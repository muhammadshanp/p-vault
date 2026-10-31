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
        Intent(Intent.ACTION_VIEW, Uri.parse(update.url)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(context, UPDATE_CHANNEL)
        .setSmallIcon(R.drawable.ic_update_notification)
        .setColor(0xFFE8633A.toInt())
        .setContentTitle("P Vault ${update.version} is available")
        .setContentText("Tap to download the new version from the official website.")
        .setContentIntent(open)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .build()
    manager.notify(UPDATE_NOTIFICATION_ID, notification)
}

fun clearUpdateNotification(context: Context) {
    context.getSystemService(NotificationManager::class.java).cancel(UPDATE_NOTIFICATION_ID)
}
