package com.mateocuello.alarmai.receiver

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mateocuello.alarmai.data.local.PreferencesManager
import com.mateocuello.alarmai.data.model.Alarm
import org.junit.After
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
    fun testOnReceive_AlarmActive() {
        val receiver = AlarmReceiver()
        receiver.onReceive(context, mock())

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
