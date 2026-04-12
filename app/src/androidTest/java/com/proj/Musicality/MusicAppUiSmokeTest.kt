package com.proj.Musicality

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicAppUiSmokeTest {

    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavNavigatesAcrossTopLevelScreens() {
        composeRule.onNodeWithText("Home").assertExists()

        composeRule.onNodeWithContentDescription("Explore").performClick()
        composeRule.onNodeWithText("Explore").assertExists()

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNode(hasSetTextAction()).assertExists()

        composeRule.onNodeWithContentDescription("Library").performClick()
        composeRule.onNodeWithText("Library").assertExists()

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.onNodeWithText("Home").assertExists()
    }

    @Test
    fun searchAcceptsQueryAndShowsResultTabs() {
        composeRule.onNodeWithContentDescription("Search").performClick()

        val searchField = composeRule.onNode(hasSetTextAction())
        searchField.assertExists()
        searchField.performTextInput("sanam")
        searchField.performImeAction()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Songs").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Videos").assertExists()
        composeRule.onNodeWithText("Artists").assertExists()
        composeRule.onNodeWithText("Albums").assertExists()
    }
}
