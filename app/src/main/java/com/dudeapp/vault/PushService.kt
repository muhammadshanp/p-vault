package com.dudeapp.vault

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Every install listens on this topic, so one message can reach all users. */
const val PUSH_TOPIC = "all"

/**
 * Receives Firebase Cloud Messaging pushes. The admin panel sends "data" messages, which
 * Firebase always hands to this service, open app or not, so each one can be saved for
 * the in-app list as well as shown. (A message composed in the Firebase console is shown
 * by Android itself while the app is closed and only reaches this service when it is open.)
 */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        val shownTitle = title?.takeIf { it.isNotBlank() } ?: "P Vault"
        // Keep it for the list behind the bell, then show it in the status bar.
        PushInbox(this).add(PushItem(shownTitle, body.orEmpty(), System.currentTimeMillis()))
        showPushNotification(this, shownTitle, body.orEmpty())
    }

    override fun onNewToken(token: String) {
        subscribeToPushes()
    }
}

fun subscribeToPushes() {
    runCatching { FirebaseMessaging.getInstance().subscribeToTopic(PUSH_TOPIC) }
}
