package com.mateocuello.alarmai.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mateocuello.alarmai.data.local.PreferencesManager
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.*

class AlarmServiceTest {

    private lateinit var logMock: MockedStatic<Log>
    private lateinit var pendingIntentMock: MockedStatic<PendingIntent>
    private lateinit var notificationBuilderConstruction: MockedConstruction<NotificationCompat.Builder>
    private lateinit var prefsConstruction: MockedConstruction<PreferencesManager>
    private lateinit var mediaPlayerConstruction: MockedConstruction<MediaPlayer>

    private val context: Context = mock()
    private val notificationManager: NotificationManager = mock()
    private val mockNotification: Notification = mock()

    @Before
    fun setUp() {
        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)

        pendingIntentMock = Mockito.mockStatic(PendingIntent::class.java)
        pendingIntentMock.`when`<PendingIntent?> {
            PendingIntent.getActivity(any(), anyInt(), any(), anyInt())
        }.thenReturn(mock())

        prefsConstruction = Mockito.mockConstruction(PreferencesManager::class.java) { mockPrefs, _ ->
            whenever(mockPrefs.getAlarmRingtoneUri()).thenReturn("")
            whenever(mockPrefs.getAlarmVolume()).thenReturn(50)
        }

        mediaPlayerConstruction = Mockito.mockConstruction(MediaPlayer::class.java) { mockPlayer, _ ->
            // Stub preparation and execution
        }

        notificationBuilderConstruction = Mockito.mockConstruction(NotificationCompat.Builder::class.java) { mockBuilder, _ ->
            whenever(mockBuilder.setSmallIcon(any<Int>())).thenReturn(mockBuilder)
            whenever(mockBuilder.setContentTitle(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setContentText(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setPriority(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setCategory(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setContentIntent(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setFullScreenIntent(any(), any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setOngoing(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.build()).thenReturn(mockNotification)
        }
    }

    @After
    fun tearDown() {
        logMock.close()
        pendingIntentMock.close()
        notificationBuilderConstruction.close()
        prefsConstruction.close()
        mediaPlayerConstruction.close()
    }

    @Test
    fun testOnStartCommand_CreatesNotification() {
        val service = spy(AlarmService())
        
        // Mock context and services
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doReturn(context).whenever(service).applicationContext
        doReturn("com.mateocuello.alarmai").whenever(service).packageName
        
        // Mock startForeground because it can throw or run logic on the base class
        doNothing().whenever(service).startForeground(any(), any())
        doNothing().whenever(service).startForeground(any(), any(), any())

        // Setup notification manager full screen intent permission stub
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            whenever(notificationManager.canUseFullScreenIntent()).thenReturn(true)
        }

        // Run
        service.onStartCommand(null, 0, 1)

        // Verify the Builder was configured
        val builderMocks = notificationBuilderConstruction.constructed()
        assertTrue(builderMocks.isNotEmpty())
        val builder = builderMocks[0]

        // Content intent must be set (Bug 4 fix)
        verify(builder).setContentIntent(any())
        
        // Full screen intent must be set because permission is true (Bug 1 fix)
        verify(builder).setFullScreenIntent(any(), eq(true))
    }
}
