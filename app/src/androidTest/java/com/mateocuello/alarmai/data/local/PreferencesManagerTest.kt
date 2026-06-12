package com.mateocuello.alarmai.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mateocuello.alarmai.data.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class PreferencesManagerTest {

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Clear preferences before each test to ensure test isolation
        val sharedPreferences = context.getSharedPreferences("AlarmAIPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        
        preferencesManager = PreferencesManager(context)
    }

    @Test
    fun testAlarmDefaultValues() {
        val alarm = preferencesManager.getAlarm()
        // Default hour is 7, minute is 0, active is false, daysOfWeek is empty
        assertEquals(7, alarm.hour)
        assertEquals(0, alarm.minute)
        assertFalse(alarm.isActive)
        assertTrue(alarm.daysOfWeek.isEmpty())
    }

    @Test
    fun testSaveAndGetAlarm() {
        val days = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
        val alarmToSave = Alarm(hour = 8, minute = 30, isActive = true, daysOfWeek = days)
        
        preferencesManager.saveAlarm(alarmToSave)
        
        val retrievedAlarm = preferencesManager.getAlarm()
        
        assertEquals(8, retrievedAlarm.hour)
        assertEquals(30, retrievedAlarm.minute)
        assertTrue(retrievedAlarm.isActive)
        assertEquals(days, retrievedAlarm.daysOfWeek)
    }

    @Test
    fun testDefaultCoordinates() {
        val (lat, lon) = preferencesManager.getLocation()
        
        // Default coordinates must fall back to CABA, Argentina: (-34.6037, -58.3816)
        assertEquals(-34.6037, lat, 0.001)
        assertEquals(-58.3816, lon, 0.001)
    }

    @Test
    fun testSaveAndGetCustomCoordinates() {
        val customLat = 40.7128
        val customLon = -74.0060
        
        preferencesManager.saveLocation(customLat, customLon)
        
        val (retrievedLat, retrievedLon) = preferencesManager.getLocation()
        
        assertEquals(customLat, retrievedLat, 0.001)
        assertEquals(customLon, retrievedLon, 0.001)
    }
}
