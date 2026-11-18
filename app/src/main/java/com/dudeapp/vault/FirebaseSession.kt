package com.dudeapp.vault

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/*
 * A Firebase session for the signed-in Google account.
 *
 * Everything P Vault writes with a raw Google token is treated by Firestore as a trusted
 * backend: the rules are skipped and project permissions are checked instead. Only the owner's
 * account has those, so nobody else could claim a username. Signing the same person into
 * Firebase gives an ID token instead, and the rules then apply to the person.
 *
 * The web client ID below is meant to be public: it only says "this is P Vault" to Google.
 */
internal object FirebaseSession {
    /** The OAuth web client ID of the p-vault-app project. */
    const val WEB_CLIENT_ID =
        "161802196335-g7unbms6ncajc5c6tjg106ljsklgo4ki.apps.googleusercontent.com"

    fun isAvailable(): Boolean =
        runCatching { FirebaseApp.getApps(null).isNotEmpty() }.getOrDefault(false)

    private fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    fun email(): String? = runCatching { auth().currentUser?.email }.getOrNull()

    /** True when a Firebase session belongs to this address. */
    fun signedInAs(email: String): Boolean =
        runCatching { auth().currentUser?.email.equals(email, ignoreCase = true) }.getOrDefault(false)

    /** Exchanges a Google pass for a Firebase session, creating the person's account if new. */
    suspend fun signIn(context: Context, googleAccessToken: String): FirebaseUser? {
        // Firebase needs the app to be initialised before it is asked anything.
        runCatching { FirebaseApp.initializeApp(context) }
        return runCatching {
            val existing = auth().currentUser
            if (existing != null) return@runCatching existing
            val credential = GoogleAuthProvider.getCredential(googleAccessToken, null)
            auth().signInWithCredential(credential).await()?.user
        }.getOrNull()
    }

    /** A fresh ID token for database writes; null when there is no session. */
    suspend fun idToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        runCatching {
            val user = auth().currentUser ?: return@runCatching null
            user.getIdToken(forceRefresh).await()?.token
        }.getOrNull()
    }

    fun signOut() {
        runCatching { auth().signOut() }
    }
}

/** Waits for a Firebase task without blocking. */
internal suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
}
