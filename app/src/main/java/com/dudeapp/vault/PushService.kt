package com.dudeapp.vault

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Every install listens on this topic, so one message can reach all users. */
const val PUSH_TOPIC = "all"

/**
 * Receives Firebase Cloud Messaging pushes. While P Vault is closed or in the background,
 * Android shows the notification by itself; this service only has to show the ones that
 * arrive while the app is open.
 */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        showPushNotification(this, title?.takeIf { it.isNotBlank() } ?: "P Vault", body.orEmpty())
    }

    override fun onNewToken(token: String) {
        subscribeToPushes()
    }
}

fun subscribeToPushes() {
    runCatching { FirebaseMessaging.getInstance().subscribeToTopic(PUSH_TOPIC) }
}
