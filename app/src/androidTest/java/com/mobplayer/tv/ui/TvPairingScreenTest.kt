package com.mobplayer.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mobplayer.tv.R
import com.mobplayer.tv.ui.pairing.TvPairingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvPairingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsEachPinDigitAndTitle() {
        composeRule.setContent { TvPairingScreen(pin = "4821", serverIp = "192.168.1.50", onEnterDemoMode = {}) }

        composeRule.onNodeWithText(context.getString(R.string.pairing_title)).assertIsDisplayed()
        "4821".forEach { composeRule.onNodeWithText(it.toString()).assertIsDisplayed() }
        composeRule.onNodeWithText(context.getString(R.string.pairing_waiting)).assertIsDisplayed()
    }

    @Test
    fun showsServerAddress() {
        composeRule.setContent { TvPairingScreen(pin = "1234", serverIp = "192.168.1.50", port = 8080, onEnterDemoMode = {}) }
        composeRule.onNodeWithText(context.getString(R.string.tv_server_ip, "192.168.1.50", 8080)).assertExists()
    }

    @Test
    fun emulatorShowsHostLoopbackAddress() {
        composeRule.setContent { TvPairingScreen(pin = "1234", isEmulator = true, port = 8080, onEnterDemoMode = {}) }
        composeRule.onNodeWithText(context.getString(R.string.tv_server_emulator, 8080)).assertExists()
    }

    @Test
    fun demoButtonInvokesCallback() {
        var demo = false
        composeRule.setContent { TvPairingScreen(pin = "1234", onEnterDemoMode = { demo = true }) }

        composeRule.onNodeWithText(context.getString(R.string.preview_tv_home)).performClick()
        assertTrue(demo)
    }
}
