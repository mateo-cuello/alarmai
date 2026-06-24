package com.mateocuello.alarmai.data.local

import android.content.Context
import android.content.SharedPreferences
import com.mateocuello.alarmai.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

class PreferencesManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var preferencesManager: PreferencesManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        `when`(sharedPreferences.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.putInt(anyString(), anyInt())).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), any())).thenReturn(editor)
        `when`(editor.putLong(anyString(), any())).thenReturn(editor)
        `when`(editor.remove(anyString())).thenReturn(editor)
        
        preferencesManager = PreferencesManager(context)
    }

    @Test
    fun testDefaultLanguage() {
        `when`(sharedPreferences.getString(eq("assistant_language"), eq("es"))).thenReturn("es")
        assertEquals("es", preferencesManager.getLanguage())
    }

    @Test
    fun testSaveLanguage() {
        preferencesManager.saveLanguage("en")
        verify(editor).putString(eq("assistant_language"), eq("en"))
        verify(editor).apply()
    }

    @Test
    fun testDefaultVoiceName() {
        `when`(sharedPreferences.getString(eq("assistant_voice_name"), eq(""))).thenReturn("")
        assertEquals("", preferencesManager.getVoiceName())
    }

    @Test
    fun testSaveVoiceName() {
        preferencesManager.saveVoiceName("en-US-custom-voice")
        verify(editor).putString(eq("assistant_voice_name"), eq("en-US-custom-voice"))
        verify(editor).apply()
    }

    @Test
    fun testDefaultTonePreference() {
        `when`(sharedPreferences.getString(eq("assistant_tone_preference"), eq("warm, helpful, and energetic")))
            .thenReturn("warm, helpful, and energetic")
        assertEquals("warm, helpful, and energetic", preferencesManager.getTonePreference())
    }

    @Test
    fun testSaveTonePreference() {
        preferencesManager.saveTonePreference("formal, concise")
        verify(editor).putString(eq("assistant_tone_preference"), eq("formal, concise"))
        verify(editor).apply()
    }

    @Test
    fun testGetGeminiKey_returnsSavedWhenNotEmpty() {
        `when`(sharedPreferences.getString(eq("gemini_key"), eq(""))).thenReturn("user_gemini_key")
        assertEquals("user_gemini_key", preferencesManager.getGeminiKey())
    }

    @Test
    fun testGetGeminiKey_fallsBackToBuildConfigWhenEmpty() {
        `when`(sharedPreferences.getString(eq("gemini_key"), eq(""))).thenReturn("")
        assertEquals(BuildConfig.GEMINI_API_KEY, preferencesManager.getGeminiKey())
    }

    @Test
    fun testGetNewsKey_returnsSavedWhenNotEmpty() {
        `when`(sharedPreferences.getString(eq("news_key"), eq(""))).thenReturn("user_news_key")
        assertEquals("user_news_key", preferencesManager.getNewsKey())
    }

    @Test
    fun testGetNewsKey_fallsBackToBuildConfigWhenEmpty() {
        `when`(sharedPreferences.getString(eq("news_key"), eq(""))).thenReturn("")
        assertEquals(BuildConfig.NEWS_API_KEY, preferencesManager.getNewsKey())
    }
}
