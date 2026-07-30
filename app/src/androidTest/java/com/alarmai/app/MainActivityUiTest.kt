package com.alarmai.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alarmai.app.ui.theme.AlarmAITheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Clear preferences before rendering the screen to ensure clean state
        val sharedPreferences = context.getSharedPreferences("AlarmAIPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        
        viewModel = MainViewModel(context as Application)
    }

    @Test
    fun testDayTogglesClickableAndSelectionState() {
        // Render the MainScreen
        composeTestRule.setContent {
            AlarmAITheme {
                MainScreen(viewModel = viewModel)
            }
        }

        // Check MONDAY (Calendar.MONDAY = 2) and FRIDAY (Calendar.FRIDAY = 6) are initially not selected
        composeTestRule.onNodeWithTag("day_toggle_2").assertIsNotSelected()
        composeTestRule.onNodeWithTag("day_toggle_6").assertIsNotSelected()

        // Click Monday toggle
        composeTestRule.onNodeWithTag("day_toggle_2").performClick()

        // Verify Monday is now selected
        composeTestRule.onNodeWithTag("day_toggle_2").assertIsSelected()
        
        // Click Friday toggle
        composeTestRule.onNodeWithTag("day_toggle_6").performClick()
        
        // Verify Friday is now selected
        composeTestRule.onNodeWithTag("day_toggle_6").assertIsSelected()

        // Toggle Monday off
        composeTestRule.onNodeWithTag("day_toggle_2").performClick()

        // Verify Monday is not selected, and Friday is still selected
        composeTestRule.onNodeWithTag("day_toggle_2").assertIsNotSelected()
        composeTestRule.onNodeWithTag("day_toggle_6").assertIsSelected()
    }
}
