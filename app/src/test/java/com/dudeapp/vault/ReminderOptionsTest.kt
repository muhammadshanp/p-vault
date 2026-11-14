package com.dudeapp.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderOptionsTest {
    private val zone = TimeZone.getTimeZone("Asia/Kolkata")

    private fun time(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }.timeInMillis

    @Test
    fun morningOffersEveningAndTomorrow() {
        val now = time(15, 10, 30)
        val options = reminderOptions(now, zone)
        assertEquals(
            listOf("In 10 minutes", "In 1 hour", "This evening, 8:00 pm", "Tomorrow morning, 9:00 am"),
            options.map { it.label },
        )
        assertEquals(now + 600_000L, options[0].at)
        assertEquals(now + 3_600_000L, options[1].at)
        assertEquals(time(15, 20, 0), options[2].at)
        assertEquals(time(16, 9, 0), options[3].at)
    }

    @Test
    fun lateEveningDropsThisEvening() {
        val options = reminderOptions(time(15, 19, 45), zone)
        assertEquals(listOf("In 10 minutes", "In 1 hour", "Tomorrow morning, 9:00 am"), options.map { it.label })
        assertEquals(time(16, 9, 0), options.last().at)
    }

    @Test
    fun everyOptionIsInTheFuture() {
        val now = time(30, 23, 59)
        assertTrue(reminderOptions(now, zone).all { it.at > now })
        // The month rolls over: tomorrow is 1 December.
        val tomorrow = Calendar.getInstance(zone).apply { timeInMillis = reminderOptions(now, zone).last().at }
        assertEquals(Calendar.DECEMBER, tomorrow.get(Calendar.MONTH))
        assertEquals(1, tomorrow.get(Calendar.DAY_OF_MONTH))
    }
}
