package com.dudeapp.vault

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

/** One notification that reached this phone. */
data class PushItem(val title: String, val body: String, val time: Long)

private const val INBOX_LIMIT = 50

/** Newest first, capped, and without the duplicate Firebase can deliver on a retry. */
internal fun inboxWithNew(existing: List<PushItem>, item: PushItem, limit: Int = INBOX_LIMIT): List<PushItem> {
    val duplicate = existing.firstOrNull()?.let {
        it.title == item.title && it.body == item.body && item.time - it.time in 0..60_000L
    } == true
    if (duplicate) return existing
    return (listOf(item) + existing).take(limit)
}

internal fun inboxToJson(items: List<PushItem>): String {
    val array = JSONArray()
    items.forEach { array.put(JSONObject().put("title", it.title).put("body", it.body).put("time", it.time)) }
    return array.toString()
}

internal fun inboxFromJson(json: String): List<PushItem> {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    val items = mutableListOf<PushItem>()
    for (i in 0 until array.length()) {
        val entry = array.optJSONObject(i) ?: continue
        val title = entry.optString("title")
        val body = entry.optString("body")
        if (title.isBlank() && body.isBlank()) continue
        items += PushItem(title, body, entry.optLong("time", 0L))
    }
    return items
}

/** The notifications behind the bell icon. Kept on this phone only. */
class PushInbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dude_inbox", Context.MODE_PRIVATE)

    fun items(): List<PushItem> = inboxFromJson(prefs.getString("items", "[]").orEmpty())

    fun unreadCount(): Int {
        val seen = prefs.getLong("seen", 0L)
        return items().count { it.time > seen }
    }

    /** Time up to which notifications have been read. */
    fun seenTime(): Long = prefs.getLong("seen", 0L)

    @Synchronized
    fun add(item: PushItem) {
        prefs.edit().putString("items", inboxToJson(inboxWithNew(items(), item))).commit()
        notifyChanged()
    }

    fun markAllRead() {
        prefs.edit().putLong("seen", System.currentTimeMillis()).apply()
        notifyChanged()
    }

    fun clear() {
        prefs.edit().putString("items", "[]").apply()
        notifyChanged()
    }

    companion object {
        /** Bumped on every change so the open app redraws the bell and the list. */
        val changes = mutableIntStateOf(0)

        private fun notifyChanged() {
            Handler(Looper.getMainLooper()).post { changes.intValue += 1 }
        }
    }
}
