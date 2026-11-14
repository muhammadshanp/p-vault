package com.dudeapp.vault

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** "Remind me about this" for one chat message. */
data class Reminder(
    val id: Int,
    /** The message file; identifies the message the reminder belongs to. */
    val messagePath: String,
    val chatDir: String,
    val chatName: String,
    /** What to show when the time comes (a copy, so it survives edits and renames). */
    val text: String,
    val at: Long,
    /** The time has come; waiting for the user to see it. */
    val fired: Boolean = false,
)

/** One ready-made choice in the "Remind me" dialog. */
data class ReminderOption(val label: String, val at: Long)

/** The quick choices offered for a reminder, counted from [now]. */
internal fun reminderOptions(now: Long, zone: TimeZone = TimeZone.getDefault()): List<ReminderOption> {
    fun at(daysAhead: Int, hour: Int): Long = Calendar.getInstance(zone).apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, daysAhead)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val options = mutableListOf(
        ReminderOption("In 10 minutes", now + 10 * 60_000L),
        ReminderOption("In 1 hour", now + 60 * 60_000L),
    )
    // "This evening" only while it is still at least half an hour away.
    val evening = at(0, 20)
    if (evening - now >= 30 * 60_000L) options += ReminderOption("This evening, 8:00 pm", evening)
    options += ReminderOption("Tomorrow morning, 9:00 am", at(1, 9))
    return options
}

/** What lets the open app know that a reminder just came due. */
object ReminderCenter {
    /** True while P Vault is on screen. */
    @Volatile
    var appVisible = false

    /** Raised whenever the list of due reminders may have changed. */
    val tick = mutableIntStateOf(0)

    fun bump() {
        tick.intValue += 1
    }
}

/** Reminders are a short list kept in the app's private settings. */
object ReminderStore {
    private const val PREFS = "dude_reminders"
    private const val KEY = "items"
    private const val NEXT_ID = "next_id"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun list(context: Context): List<Reminder> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            Reminder(
                id = o.getInt("id"),
                messagePath = o.optString("message"),
                chatDir = o.optString("chatDir"),
                chatName = o.optString("chatName"),
                text = o.optString("text"),
                at = o.getLong("at"),
                fired = o.optBoolean("fired"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, items: List<Reminder>) {
        val array = JSONArray()
        items.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("message", r.messagePath)
                    .put("chatDir", r.chatDir)
                    .put("chatName", r.chatName)
                    .put("text", r.text)
                    .put("at", r.at)
                    .put("fired", r.fired),
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).commit()
    }

    /** Sets (or replaces) the reminder of one message. */
    @Synchronized
    fun set(context: Context, messagePath: String, chatDir: String, chatName: String, text: String, at: Long): Reminder {
        val items = list(context)
        val old = items.firstOrNull { it.messagePath == messagePath }
        val id = old?.id ?: run {
            val p = prefs(context)
            val next = p.getInt(NEXT_ID, 1)
            p.edit().putInt(NEXT_ID, if (next >= 1_000_000) 1 else next + 1).commit()
            next
        }
        val reminder = Reminder(id, messagePath, chatDir, chatName, text.take(300), at)
        save(context, items.filterNot { it.id == id } + reminder)
        return reminder
    }

    @Synchronized
    fun markFired(context: Context, id: Int): Reminder? {
        val items = list(context)
        val target = items.firstOrNull { it.id == id } ?: return null
        val fired = target.copy(fired = true)
        save(context, items.map { if (it.id == id) fired else it })
        return fired
    }

    @Synchronized
    fun remove(context: Context, id: Int) {
        save(context, list(context).filterNot { it.id == id })
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).commit()
    }
}

private const val REMINDER_CHANNEL = "p_vault_reminders"
private const val ACTION_FIRE = "com.dudeapp.vault.REMINDER_DUE"
private const val EXTRA_ID = "reminder_id"
private const val NOTIFICATION_BASE = 5000

object Reminders {
    /** Sets a reminder for a message and arms the alarm. */
    fun set(context: Context, message: ChatMessage, chat: ChatThread, at: Long): Reminder {
        val text = when (message.type) {
            ChatMessageType.TEXT -> message.text.ifBlank { "Message" }
            ChatMessageType.PHOTO -> "Photo"
            ChatMessageType.VOICE -> "Voice message"
        }
        val reminder = ReminderStore.set(
            context,
            message.file.absolutePath,
            chat.dir.absolutePath,
            chat.name,
            text,
            at,
        )
        schedule(context, reminder)
        return reminder
    }

    /** Moves an existing reminder to a new time (a due one becomes upcoming again). */
    fun reschedule(context: Context, reminder: Reminder, at: Long): Reminder {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + reminder.id)
        val moved = ReminderStore.set(
            context,
            reminder.messagePath,
            reminder.chatDir,
            reminder.chatName,
            reminder.text,
            at,
        )
        schedule(context, moved)
        return moved
    }

    fun cancel(context: Context, reminder: Reminder) {
        runCatching {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, reminder.id))
        }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + reminder.id)
        ReminderStore.remove(context, reminder.id)
    }

    /** Removes the reminder of a message that is being deleted. */
    fun cancelFor(context: Context, messagePath: String) {
        ReminderStore.list(context).filter { it.messagePath == messagePath }.forEach { cancel(context, it) }
    }

    fun cancelAll(context: Context) {
        ReminderStore.list(context).forEach { cancel(context, it) }
        ReminderStore.clear(context)
    }

    /**
     * Android forgets alarms when the phone restarts and when the app is updated, so they
     * are armed again then and at every start. Reminders whose time passed meanwhile fire now.
     */
    fun rearmAll(context: Context) {
        val now = System.currentTimeMillis()
        ReminderStore.list(context).filterNot { it.fired }.forEach { reminder ->
            if (reminder.at <= now) fire(context, reminder.id) else schedule(context, reminder)
        }
    }

    /** The time has come: a pop-up if P Vault is on screen, otherwise a notification. */
    fun fire(context: Context, id: Int) {
        val reminder = ReminderStore.markFired(context, id) ?: return
        if (ReminderCenter.appVisible) {
            ReminderCenter.bump()
        } else {
            notify(context, reminder)
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun schedule(context: Context, reminder: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context, reminder.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        val armed = exactAllowed && runCatching {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
        }.isSuccess
        if (!armed) {
            // Exact alarms are switched off for P Vault: Android may deliver a little late.
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent) }
        }
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notify(context: Context, reminder: Reminder) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set on chat messages"
            },
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_BASE + reminder.id,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // On a locked phone that hides sensitive content, only this version is shown.
        val hidden = Notification.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE8633A.toInt())
            .setContentTitle("P Vault reminder")
            .setContentText("Open P Vault to see it.")
            .build()
        val notification = Notification.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE8633A.toInt())
            .setContentTitle("Reminder · ${reminder.chatName}")
            .setContentText(reminder.text)
            .setStyle(Notification.BigTextStyle().bigText(reminder.text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(reminder.at)
            .setShowWhen(true)
            .build()
        manager.notify(NOTIFICATION_BASE + reminder.id, notification)
    }

    fun clearNotification(context: Context, reminder: Reminder) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + reminder.id)
    }

    internal fun idOf(intent: Intent): Int? =
        if (intent.action == ACTION_FIRE) intent.getIntExtra(EXTRA_ID, -1).takeIf { it >= 0 } else null
}

/** Woken by the alarm, by a phone restart and by an app update. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = Reminders.idOf(intent)
        runCatching {
            if (id != null) Reminders.fire(context, id) else Reminders.rearmAll(context)
        }
    }
}
