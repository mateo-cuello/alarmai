package com.mateocuello.alarmai.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.mateocuello.alarmai.data.model.Alarm
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.util.Calendar

class AlarmSchedulerTest {

    private lateinit var logMock: MockedStatic<Log>
    private lateinit var pendingIntentMock: MockedStatic<PendingIntent>

    private val context: Context = mock()
    private val alarmManager: AlarmManager = mock()

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)

        pendingIntentMock = Mockito.mockStatic(PendingIntent::class.java)
        pendingIntentMock.`when`<PendingIntent?> {
            PendingIntent.getBroadcast(any(), anyInt(), any(), anyInt())
        }.thenReturn(mock())

        whenever(context.getSystemService(Context.ALARM_SERVICE)).thenReturn(alarmManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            whenever(alarmManager.canScheduleExactAlarms()).thenReturn(true)
        }
    }

    @After
    fun tearDown() {
        logMock.close()
        pendingIntentMock.close()
    }

    @Test
    fun testSchedule_ActiveAlarm() {
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            isActive = true,
            daysOfWeek = setOf(Calendar.MONDAY)
        )
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(alarm)

        // Verify main alarm set via setAlarmClock, pre-alarm via setExactAndAllowWhileIdle
        verify(alarmManager).setAlarmClock(any(), any())
        verify(alarmManager).setExactAndAllowWhileIdle(any(), any(), any())
    }

    @Test
    fun testSchedule_InactiveAlarm_Cancels() {
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            isActive = false
        )
        val scheduler = AlarmScheduler(context)
        scheduler.schedule(alarm)

        // Since it's inactive, it should cancel existing intents
        // The cancel logic calls PendingIntent.getBroadcast with FLAG_NO_CREATE
        pendingIntentMock.verify {
            PendingIntent.getBroadcast(eq(context), eq(1001), any(), eq(PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    @Test
    fun testCancel() {
        val scheduler = AlarmScheduler(context)
        scheduler.cancel()

        pendingIntentMock.verify {
            PendingIntent.getBroadcast(eq(context), eq(1001), any(), eq(PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            PendingIntent.getBroadcast(eq(context), eq(1002), any(), eq(PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    @Test
    fun testSchedule_ActiveAlarm_PreAlarmInPast_CallsStartPrefetch() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
        }
        val alarm = Alarm(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            isActive = true
        )

        val workManagerMock = mock<WorkManager>()
        Mockito.mockStatic(WorkManager::class.java).use { workManagerStatic ->
            workManagerStatic.`when`<WorkManager> {
                WorkManager.getInstance(any())
            }.thenReturn(workManagerMock)

            val scheduler = AlarmScheduler(context)
            scheduler.schedule(alarm)

            // Verify main alarm set via setAlarmClock, pre-alarm not scheduled via setExactAndAllowWhileIdle
            verify(alarmManager).setAlarmClock(any(), any())
            verify(alarmManager, never()).setExactAndAllowWhileIdle(any(), any(), any())
            verify(workManagerMock).enqueue(any<WorkRequest>())
        }
    }

    @Test
    fun testSchedule_ActiveAlarm_PreAlarmInFuture_SchedulesPreAlarm() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 3)
        }
        val alarm = Alarm(
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            isActive = true
        )

        val scheduler = AlarmScheduler(context)
        scheduler.schedule(alarm)

        // Verify main alarm scheduled with setAlarmClock, pre-alarm with setExactAndAllowWhileIdle
        verify(alarmManager).setAlarmClock(any(), any())
        verify(alarmManager).setExactAndAllowWhileIdle(any(), any(), any())
    }

    @Test
    fun testSchedule_AlarmManagerThrowsException_LogsError() {
        val alarm = Alarm(
            hour = 8,
            minute = 0,
            isActive = true
        )
        whenever(alarmManager.setAlarmClock(any(), any())).thenThrow(RuntimeException("Mock AlarmManager Exception"))

        val scheduler = AlarmScheduler(context)
        scheduler.schedule(alarm)

        logMock.verify {
            Log.e(eq("AlarmScheduler"), eq("Failed to schedule alarm: Mock AlarmManager Exception"))
        }
    }
}
