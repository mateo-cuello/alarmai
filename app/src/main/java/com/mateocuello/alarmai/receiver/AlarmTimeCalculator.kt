package com.mateocuello.alarmai.receiver

import com.mateocuello.alarmai.data.model.Alarm
import java.util.Calendar

object AlarmTimeCalculator {
    fun calculateNextAlarmTime(alarm: Alarm, nowTime: Long, fromReceiver: Boolean = false): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowTime
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.daysOfWeek.isNotEmpty()) {
            val startOffset = if (fromReceiver) 1 else 0
            var found = false
            for (offset in startOffset..7) {
                val testCalendar = (calendar.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                val testDayOfWeek = testCalendar.get(Calendar.DAY_OF_WEEK)
                if (alarm.daysOfWeek.contains(testDayOfWeek)) {
                    if (offset > 0 || testCalendar.timeInMillis > nowTime) {
                        calendar.timeInMillis = testCalendar.timeInMillis
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                if (calendar.timeInMillis <= nowTime) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        } else {
            if (calendar.timeInMillis <= nowTime) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }
}
