package com.mateocuello.alarmai.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.model.Alarm
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

class AlarmReceiverTest {

    private lateinit var logMock: MockedStatic<Log>
    private lateinit var prefsConstruction: MockedConstruction<PreferencesManager>
    private lateinit var schedulerConstruction: MockedConstruction<AlarmScheduler>

    private val context: Context = mock()
    private val mockAlarm = Alarm(hour = 7, minute = 30, isActive = true)

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any(), any()) }.thenReturn(0)

        // Mock PreferencesManager
        prefsConstruction = Mockito.mockConstruction(PreferencesManager::class.java) { mockPrefs, _ ->
            whenever(mockPrefs.getAlarm()).thenReturn(mockAlarm)
        }

        // Mock AlarmScheduler
        schedulerConstruction = Mockito.mockConstruction(AlarmScheduler::class.java) { _, _ -> }
    }

    @After
    fun tearDown() {
        logMock.close()
        prefsConstruction.close()
        schedulerConstruction.close()
    }

    @Test
    fun testOnReceive_AlarmActive_NonRecurring() {
        // Since mockAlarm has daysOfWeek = emptySet(), it should update isActive to false
        val receiver = AlarmReceiver()
        receiver.onReceive(context, mock())

        // Verify preferences saved the updated alarm with isActive = false
        val prefsMocks = prefsConstruction.constructed()
        assertTrue(prefsMocks.isNotEmpty())
        val prefs = prefsMocks[0]
        verify(prefs).saveAlarm(eq(mockAlarm.copy(isActive = false)))

        // Verify service was started
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(context).startForegroundService(any())
        } else {
            verify(context).startService(any())
        }
    }

    @Test
    fun testOnReceive_AlarmActive_Recurring() {
        // Recreate PreferencesManager construction to return a recurring alarm
        prefsConstruction.close()
        val recurringAlarm = mockAlarm.copy(daysOfWeek = setOf(2)) // Monday is usually 2 (Calendar.MONDAY)
        prefsConstruction = Mockito.mockConstruction(PreferencesManager::class.java) { mockPrefs, _ ->
            whenever(mockPrefs.getAlarm()).thenReturn(recurringAlarm)
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, mock())

        // Verify scheduler is invoked with fromReceiver = true
        val schedulerMocks = schedulerConstruction.constructed()
        assertTrue(schedulerMocks.isNotEmpty())
        val scheduler = schedulerMocks[0]
        verify(scheduler).schedule(eq(recurringAlarm), eq(true))

        // Verify preferences did NOT save the inactive alarm because it's recurring
        val prefsMocks = prefsConstruction.constructed()
        assertTrue(prefsMocks.isNotEmpty())
        val prefs = prefsMocks[0]
        verify(prefs, never()).saveAlarm(any())

        // Verify service was started
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(context).startForegroundService(any())
        } else {
            verify(context).startService(any())
        }
    }

    @Test
    fun testOnReceive_AlarmInactive_DoesNotStartService() {
        // Close and recreate PreferencesManager mock to return an inactive alarm
        prefsConstruction.close()
        prefsConstruction = Mockito.mockConstruction(PreferencesManager::class.java) { mockPrefs, _ ->
            whenever(mockPrefs.getAlarm()).thenReturn(mockAlarm.copy(isActive = false))
        }

        val receiver = AlarmReceiver()
        receiver.onReceive(context, mock())

        // Verify service was NOT started
        verify(context, never()).startForegroundService(any())
        verify(context, never()).startService(any())
    }
}
