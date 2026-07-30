package com.alarmai.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarRepository(private val context: Context) {

    suspend fun getTodayEvents(): String = withContext(Dispatchers.IO) {
        if (!androidx.core.os.UserManagerCompat.isUserUnlocked(context)) {
            return@withContext "Your calendar is unavailable because the device is locked under Direct Boot."
        }

        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return@withContext "Calendar access permission is not granted. Prompt the user to grant permission."
        }

        val events = mutableListOf<String>()
        val contentResolver = context.contentResolver

        // Define start and end of today
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Build the URI for instances query
        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, todayStart)
        ContentUris.appendId(builder, todayEnd)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )

        val selection = "${CalendarContract.Instances.VISIBLE} = 1"

        try {
            val cursor = contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

                while (it.moveToNext()) {
                    val title = it.getString(titleIdx)
                    val begin = it.getLong(beginIdx)
                    val end = it.getLong(endIdx)
                    val allDay = it.getInt(allDayIdx) == 1

                    val formattedTime = if (allDay) {
                        "All day"
                    } else {
                        val beginTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), ZoneId.systemDefault())
                        val endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneId.systemDefault())
                        val formatter = DateTimeFormatter.ofPattern("HH:mm")
                        "${beginTime.format(formatter)} - ${endTime.format(formatter)}"
                    }
                    events.add("- $title ($formattedTime)")
                }
            }

            if (events.isEmpty()) {
                "Your calendar is clear today. No events scheduled."
            } else {
                events.joinToString("\n")
            }
        } catch (e: Exception) {
            "Unable to read calendar events: ${e.localizedMessage}"
        }
    }
}
