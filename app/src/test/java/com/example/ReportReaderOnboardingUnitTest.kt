package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.data.MockDataProvider
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReportReaderOnboardingScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportReaderOnboardingUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `home screen shows report reader guide card and triggers navigation callback`() {
    var guideClicked = false

    composeTestRule.setContent {
      MyApplicationTheme {
        HomeScreen(
          recentReports = MockDataProvider.sampleHistory,
          onScanReportClick = {},
          onUploadReportClick = {},
          onSampleExplanationClick = {},
          onLanguageClick = {},
          onHelpClick = {},
          onViewAllReportsClick = {},
          onReportItemClick = {},
          onSubscriptionClick = {},
          onReportReaderGuideClick = { guideClicked = true }
        )
      }
    }

    composeTestRule.onNode(hasScrollAction())
      .performScrollToNode(hasTestTag("card_report_reader_guide"))
    composeTestRule.onNodeWithTag("card_report_reader_guide")
      .assertIsDisplayed()
      .performClick()

    assertTrue(guideClicked)
  }

  @Test
  fun `onboarding carousel navigates across all four slides and triggers final CTAs`() {
    var uploadClicked = 0
    var scanClicked = 0
    var demoClicked = 0
    var backClicked = 0

    composeTestRule.setContent {
      MyApplicationTheme {
        ReportReaderOnboardingScreen(
          onNavigateBack = { backClicked += 1 },
          onStartUploadClick = { uploadClicked += 1 },
          onStartScanClick = { scanClicked += 1 },
          onTryDemoClick = { demoClicked += 1 }
        )
      }
    }

    composeTestRule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
    composeTestRule.onNodeWithText("Scan or Upload Any Lab Report")
      .performScrollTo()
      .assertIsDisplayed()

    // Advance to Slide 2
    composeTestRule.onNodeWithTag("btn_onboarding_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Step 2 of 4").assertIsDisplayed()
    composeTestRule.onNodeWithText("Verify Values & Safety Flags")
      .performScrollTo()
      .assertIsDisplayed()

    // Go back to Slide 1 and advance forward again
    composeTestRule.onNodeWithTag("btn_onboarding_prev").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Step 1 of 4").assertIsDisplayed()

    // Jump to Slide 3 via indicator, then Next to Slide 4
    composeTestRule.onNodeWithTag("onboarding_indicator_2").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Step 3 of 4").assertIsDisplayed()
    composeTestRule.onNodeWithText("Plain-Language & Voice Explanations")
      .performScrollTo()
      .assertIsDisplayed()

    composeTestRule.onNodeWithTag("btn_onboarding_next").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Step 4 of 4").assertIsDisplayed()
    composeTestRule.onNodeWithText("Designed for Privacy & Doctor Visits")
      .performScrollTo()
      .assertIsDisplayed()

    // Verify all final-slide action buttons work
    composeTestRule.onNodeWithTag("btn_onboarding_start_upload").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("btn_onboarding_start_scan").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("btn_onboarding_try_demo").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("btn_onboarding_skip").assertIsDisplayed().performClick()

    assertEquals(1, uploadClicked)
    assertEquals(1, scanClicked)
    assertEquals(1, demoClicked)
    assertEquals(1, backClicked)
  }
}
