package com.alarmai.app.receiver

import com.alarmai.app.data.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlarmTimeCalculatorTest {

    // Helper to get a fixed time on a specific day of week (starting June 1, 2026, which is a Monday)
    private fun getFixedTime(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JUNE) // June 2026
            set(Calendar.DAY_OF_MONTH, 1) // June 1st, 2026 was a Monday
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Advance to the correct day of week
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // Helper to get a future occurrence of a dayOfWeek after fromTime
    private fun getFutureFixedTime(fromTime: Long, dayOfWeek: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = fromTime
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Advance day-by-day until we find the target day of week strictly after fromTime
        while (cal.timeInMillis <= fromTime || cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    @Test
    fun testSingleShotAlarm_BeforeScheduledTime() {
        // Alarm set for 08:00 AM, no repeating days
        val alarm = Alarm(hour = 8, minute = 0, isActive = true, daysOfWeek = emptySet())
        
        // Monday, 07:30 AM
        val nowTime = getFixedTime(Calendar.MONDAY, 7, 30)
        val expectedTime = getFixedTime(Calendar.MONDAY, 8, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testSingleShotAlarm_AfterScheduledTime() {
        // Alarm set for 08:00 AM, no repeating days
        val alarm = Alarm(hour = 8, minute = 0, isActive = true, daysOfWeek = emptySet())
        
        // Monday, 08:30 AM
        val nowTime = getFixedTime(Calendar.MONDAY, 8, 30)
        val expectedTime = getFixedTime(Calendar.TUESDAY, 8, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testWeekendsOnlyAlarm_FridayBeforeScheduledTime() {
        // Alarm set for 09:00 AM on Saturday and Sunday
        val alarm = Alarm(
            hour = 9,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        
        // Friday, 12:00 PM
        val nowTime = getFixedTime(Calendar.FRIDAY, 12, 0)
        val expectedTime = getFixedTime(Calendar.SATURDAY, 9, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testWeekendsOnlyAlarm_SaturdayBeforeScheduledTime() {
        val alarm = Alarm(
            hour = 9,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        
        // Saturday, 08:00 AM
        val nowTime = getFixedTime(Calendar.SATURDAY, 8, 0)
        val expectedTime = getFixedTime(Calendar.SATURDAY, 9, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testWeekendsOnlyAlarm_SaturdayAfterScheduledTime() {
        val alarm = Alarm(
            hour = 9,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        
        // Saturday, 10:00 AM
        val nowTime = getFixedTime(Calendar.SATURDAY, 10, 0)
        val expectedTime = getFixedTime(Calendar.SUNDAY, 9, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testWeekendsOnlyAlarm_SundayAfterScheduledTime() {
        val alarm = Alarm(
            hour = 9,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        
        // Sunday, 10:00 AM
        val nowTime = getFixedTime(Calendar.SUNDAY, 10, 0)
        val expectedTime = getFutureFixedTime(nowTime, Calendar.SATURDAY, 9, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testWeekdaysOnlyAlarm_FridayAfterScheduledTime() {
        // Alarm set for 07:00 AM, Monday to Friday
        val alarm = Alarm(
            hour = 7,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
        )
        
        // Friday, 08:00 AM
        val nowTime = getFixedTime(Calendar.FRIDAY, 8, 0)
        val expectedTime = getFutureFixedTime(nowTime, Calendar.MONDAY, 7, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testWeekdaysOnlyAlarm_Sunday() {
        val alarm = Alarm(
            hour = 7,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
        )
        
        // Sunday, 05:00 PM
        val nowTime = getFixedTime(Calendar.SUNDAY, 17, 0)
        val expectedTime = getFutureFixedTime(nowTime, Calendar.MONDAY, 7, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime)
        assertEquals(expectedTime, actualTime)
    }

    @Test
    fun testFromReceiverRescheduling() {
        val alarm = Alarm(
            hour = 9,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        
        // Rescheduling exactly when it fires on Saturday at 09:00 AM
        val nowTime = getFixedTime(Calendar.SATURDAY, 9, 0)
        val expectedTime = getFixedTime(Calendar.SUNDAY, 9, 0)
        
        val actualTime = AlarmTimeCalculator.calculateNextAlarmTime(alarm, nowTime, fromReceiver = true)
        assertEquals(expectedTime, actualTime)
    }
}
