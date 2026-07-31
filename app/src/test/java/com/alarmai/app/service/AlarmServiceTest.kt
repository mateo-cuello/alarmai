package com.alarmai.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alarmai.app.data.local.PreferencesManager
import org.junit.After
import org.junit.Assert.assertEquals
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
    private lateinit var uriMockStatic: MockedStatic<Uri>
    private lateinit var notificationBuilderConstruction: MockedConstruction<NotificationCompat.Builder>
    private lateinit var prefsConstruction: MockedConstruction<PreferencesManager>
    private lateinit var mediaPlayerConstruction: MockedConstruction<MediaPlayer>
    private lateinit var audioAttributesBuilderConstruction: MockedConstruction<AudioAttributes.Builder>
    private lateinit var looperMock: MockedStatic<Looper>
    private lateinit var handlerConstruction: MockedConstruction<Handler>

    private val context: Context = mock()
    private val notificationManager: NotificationManager = mock()
    private val mockNotification: Notification = mock()
    private val mockUri: Uri = mock()

    /** A non-null start intent with no action: the normal "ring now" path. */
    private fun startIntent(): Intent = mock<Intent>().also {
        whenever(it.action).thenReturn(null)
    }

    @Before
    fun setUp() {
        looperMock = Mockito.mockStatic(Looper::class.java)
        looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mock())
        handlerConstruction = Mockito.mockConstruction(Handler::class.java) { _, _ -> }

        logMock = Mockito.mockStatic(Log::class.java)
        logMock.`when`<Int> { Log.d(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>()) }.thenReturn(0)
        logMock.`when`<Int> { Log.e(any<String>(), any<String>(), any()) }.thenReturn(0)

        pendingIntentMock = Mockito.mockStatic(PendingIntent::class.java)
        pendingIntentMock.`when`<PendingIntent?> {
            PendingIntent.getActivity(any(), anyInt(), any(), anyInt())
        }.thenReturn(mock())
        pendingIntentMock.`when`<PendingIntent?> {
            PendingIntent.getActivity(any(), anyInt(), any(), anyInt(), anyOrNull())
        }.thenReturn(mock())
        pendingIntentMock.`when`<PendingIntent?> {
            PendingIntent.getService(any(), anyInt(), any(), anyInt())
        }.thenReturn(mock())

        uriMockStatic = Mockito.mockStatic(Uri::class.java)
        uriMockStatic.`when`<Uri> { Uri.parse(any()) }.thenReturn(mockUri)

        prefsConstruction = Mockito.mockConstruction(PreferencesManager::class.java) { mockPrefs, _ ->
            whenever(mockPrefs.getAlarmRingtoneUri()).thenReturn("content://default")
            whenever(mockPrefs.getAlarmVolume()).thenReturn(50)
        }

        mediaPlayerConstruction = Mockito.mockConstruction(MediaPlayer::class.java) { mockPlayer, _ ->
            // Stub preparation and execution
        }

        val mockAudioAttributes = mock<AudioAttributes>()
        audioAttributesBuilderConstruction = Mockito.mockConstruction(AudioAttributes.Builder::class.java) { mockBuilder, _ ->
            whenever(mockBuilder.setUsage(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.setContentType(any())).thenReturn(mockBuilder)
            whenever(mockBuilder.build()).thenReturn(mockAudioAttributes)
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
            whenever(mockBuilder.addAction(any<Int>(), any(), anyOrNull())).thenReturn(mockBuilder)
            whenever(mockBuilder.build()).thenReturn(mockNotification)
        }
    }

    @After
    fun tearDown() {
        logMock.close()
        pendingIntentMock.close()
        uriMockStatic.close()
        notificationBuilderConstruction.close()
        prefsConstruction.close()
        mediaPlayerConstruction.close()
        audioAttributesBuilderConstruction.close()
        handlerConstruction.close()
        looperMock.close()
    }

    @Test
    fun testOnStartCommand_NullIntent_StopsInsteadOfRinging() {
        // Regression: the service used to return START_STICKY, so a system restart re-delivered a
        // null intent and the alarm rang again at an arbitrary time with nothing scheduled.
        val service = spy(AlarmService())
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doNothing().whenever(service).stopSelf()

        val result = service.onStartCommand(null, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
        verify(service).stopSelf()
        assertTrue(mediaPlayerConstruction.constructed().isEmpty())
    }

    @Test
    fun testOnStartCommand_StopAction_StopsSelfWithoutRinging() {
        val service = spy(AlarmService())
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doNothing().whenever(service).stopSelf()

        val stopIntent = mock<Intent>()
        whenever(stopIntent.action).thenReturn(AlarmService.ACTION_STOP)

        val result = service.onStartCommand(stopIntent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
        verify(service).stopSelf()
        assertTrue(mediaPlayerConstruction.constructed().isEmpty())
    }

    @Test
    fun testOnStartCommand_CreatesNotification() {
        val service = spy(AlarmService())
        
        // Mock context and services
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doReturn(context).whenever(service).applicationContext
        doReturn("com.alarmai.app").whenever(service).packageName
        
        // Mock startForeground because it can throw or run logic on the base class
        doNothing().whenever(service).startForeground(any(), any())
        doNothing().whenever(service).startForeground(any(), any(), any())

        // Setup notification manager full screen intent permission stub
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            whenever(notificationManager.canUseFullScreenIntent()).thenReturn(true)
        }

        // Run
        service.onStartCommand(startIntent(), 0, 1)

        // Verify the Builder was configured
        val builderMocks = notificationBuilderConstruction.constructed()
        assertTrue(builderMocks.isNotEmpty())
        val builder = builderMocks[0]

        // Content intent must be set (Bug 4 fix)
        verify(builder).setContentIntent(any())
        
        // Full screen intent must be set because permission is true (Bug 1 fix)
        verify(builder).setFullScreenIntent(any(), eq(true))
    }

    @Test
    fun testOnStartCommand_PlaysAlarmSound_ConfiguresMediaPlayer() {
        // Mock PreferencesManager to return specific ringtone URI and volume
        prefsConstruction.close()
        prefsConstruction = Mockito.mockConstruction(PreferencesManager::class.java) { mockPrefs, _ ->
            whenever(mockPrefs.getAlarmRingtoneUri()).thenReturn("content://settings/system/alarm_alert")
            whenever(mockPrefs.getAlarmVolume()).thenReturn(80)
        }

        val service = spy(AlarmService())
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doReturn(context).whenever(service).applicationContext
        doReturn("com.alarmai.app").whenever(service).packageName
        doNothing().whenever(service).startForeground(any(), any())
        doNothing().whenever(service).startForeground(any(), any(), any())

        service.onStartCommand(startIntent(), 0, 1)

        // Verify MediaPlayer construction and calls
        val playerMocks = mediaPlayerConstruction.constructed()
        assertTrue(playerMocks.isNotEmpty())
        val player = playerMocks[0]

        verify(player).setDataSource(eq(service), any<android.net.Uri>())
        verify(player).setVolume(eq(0.8f), eq(0.8f))
        verify(player).setLooping(eq(true))
        // Preparation is async so onStartCommand can't block the main thread; playback starts
        // from the prepared callback rather than inline.
        verify(player).prepareAsync()
        verify(player, never()).prepare()

        val listener = argumentCaptor<MediaPlayer.OnPreparedListener>()
        verify(player).setOnPreparedListener(listener.capture())
        listener.firstValue.onPrepared(player)
        verify(player).start()
    }

    @Test
    fun testOnStartCommand_PlayAlarmSoundThrowsException_LogsError() {
        val service = spy(AlarmService())
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doReturn(context).whenever(service).applicationContext
        doReturn("com.alarmai.app").whenever(service).packageName
        doNothing().whenever(service).startForeground(any(), any())
        doNothing().whenever(service).startForeground(any(), any(), any())

        // Recreate MediaPlayer construction to throw exception on prepareAsync()
        mediaPlayerConstruction.close()
        mediaPlayerConstruction = Mockito.mockConstruction(MediaPlayer::class.java) { mockPlayer, _ ->
            whenever(mockPlayer.prepareAsync()).thenThrow(RuntimeException("Mock MediaPlayer Error"))
        }

        service.onStartCommand(startIntent(), 0, 1)

        // Verify exception was caught and logged
        logMock.verify {
            Log.e(eq("AlarmService"), eq("Failed to play alarm sound: Mock MediaPlayer Error"))
        }
    }

    @Test
    fun testOnDestroy_StopsAndReleasesMediaPlayer() {
        val service = spy(AlarmService())
        doReturn(notificationManager).whenever(service).getSystemService(Context.NOTIFICATION_SERVICE)
        doReturn(context).whenever(service).applicationContext
        doReturn("com.alarmai.app").whenever(service).packageName
        doNothing().whenever(service).startForeground(any(), any())
        doNothing().whenever(service).startForeground(any(), any(), any())

        // Start command to initialize MediaPlayer
        service.onStartCommand(startIntent(), 0, 1)

        val playerMocks = mediaPlayerConstruction.constructed()
        assertTrue(playerMocks.isNotEmpty())
        val player = playerMocks[0]

        // Destroy service
        service.onDestroy()

        verify(player).stop()
        verify(player).release()
    }
}
