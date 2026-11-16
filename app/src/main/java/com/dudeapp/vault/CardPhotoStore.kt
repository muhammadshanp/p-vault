package com.dudeapp.vault

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The card photos on this phone, as the screens read them. A photo of the user's own always
 * wins over the picture a theme puts in a card, and the card keeps its size and its label.
 */
object CardPhotoFiles {
    /** The photo of each card that has one; read by the cards themselves. */
    val current = mutableStateOf<Map<CardPicture, File>>(emptyMap())
}

/**
 * The photos this phone chose for its cards: one per card, saved in the app's own folder so
 * they work offline and survive a restart. Nothing is uploaded anywhere.
 *
 * The card keeps its size and its labels; the photo is only behind them.
 */
class CardPhotoStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("dude_card_photos", Context.MODE_PRIVATE)

    private val dir: File
        get() = File(context.filesDir, "card_photos").apply { mkdirs() }

    /** The photo chosen for [kind], or null when the card keeps its theme picture. */
    fun file(kind: CardPicture): File? =
        prefs.getString(kind.name, null)?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }

    /** The card kinds that have a photo of their own. */
    fun chosen(): Set<CardPicture> =
        CardPicture.entries.filter { file(it) != null }.toSet()

    /** Copies the picked picture into the app and remembers it for [kind]. */
    suspend fun save(kind: CardPicture, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(dir, kind.name.lowercase() + ".img")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (copied && target.length() > 0) {
                prefs.edit().putString(kind.name, target.absolutePath).apply()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /** Forgets the photo of [kind]; the card goes back to what the theme says. */
    fun clear(kind: CardPicture) {
        prefs.getString(kind.name, null)?.let { File(it).delete() }
        prefs.edit().remove(kind.name).apply()
    }

    fun clearAll() {
        CardPicture.entries.forEach { clear(it) }
        CardPhotoFiles.current.value = emptyMap()
    }

    /** Reads what is saved and makes it the choice every card sees. */
    fun publish() {
        CardPhotoFiles.current.value = chosen().associateWith { file(it)!! }
    }
}
