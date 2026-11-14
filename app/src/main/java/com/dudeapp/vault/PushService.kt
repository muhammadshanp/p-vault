package com.dudeapp.vault

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Every install listens on this topic, so one message can reach all users. */
const val PUSH_TOPIC = "all"

/**
 * Receives Firebase Cloud Messaging pushes. The admin panel sends "data" messages, which
 * Firebase always hands to this service, open app or not, so each one can be saved for
 * the in-app list as well as shown. A message composed in the Firebase console is shown
 * by Android itself while the app is closed; [handleIntent] saves those for the list.
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

    /**
     * Firebase passes every incoming message through here first, including the ones
     * composed in the Firebase console that Android shows by itself while the app is
     * closed. Saving those here puts them in the list behind the bell as well.
     * (A message that also reaches [onMessageReceived] is recognised as the same one.)
     */
    override fun handleIntent(intent: Intent) {
        runCatching {
            if (intent.action == "com.google.android.c2dm.intent.RECEIVE") {
                val extras = intent.extras
                val title = extras?.getString("gcm.notification.title") ?: extras?.getString("gcm.n.title")
                val body = extras?.getString("gcm.notification.body") ?: extras?.getString("gcm.n.body")
                if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
                    PushInbox(this).add(
                        PushItem(
                            title?.takeIf { it.isNotBlank() } ?: "P Vault",
                            body.orEmpty(),
                            System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
        super.handleIntent(intent)
    }

    override fun onNewToken(token: String) {
        subscribeToPushes()
    }
}

fun subscribeToPushes() {
    runCatching { FirebaseMessaging.getInstance().subscribeToTopic(PUSH_TOPIC) }
}
