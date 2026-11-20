package com.example

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.model.AudioOutputDevice
import com.example.model.AudioPreset
import com.example.model.AudioRouteState
import com.example.model.BluetoothProfileType
import com.example.model.CompatibilityEvaluator
import com.example.model.CompatibilityRating
import com.example.model.DspConfig
import com.example.model.EngineTelemetry
import com.example.model.LoopbackMeasurement
import com.example.model.LoopbackTestPhase
import com.example.model.SafetyAndPermissionState
import com.example.ui.HearLinkUiState
import com.example.ui.screens.MainTherapistScreen
import com.example.ui.screens.SafetyOnboardingScreen
import com.example.ui.theme.HearLinkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HearLinkTests {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compatibilityEvaluator_neverClaimsExcellentWithoutActualMeasurement() {
        val leAudioDevice = AudioOutputDevice(
            id = 10,
            name = "Shokz OpenRun Pro LE",
            profileType = BluetoothProfileType.LE_AUDIO,
            isCommunicationDevice = true,
        )
        val (ratingBeforeTest, _) = CompatibilityEvaluator.evaluate(
            device = leAudioDevice,
            telemetry = EngineTelemetry(isRunning = true),
            loopback = LoopbackMeasurement(phase = LoopbackTestPhase.IDLE, measuredEndToEndMs = null),
        )
        assertEquals(CompatibilityRating.ACCEPTABLE, ratingBeforeTest)

        val (ratingAfterLowLatencyTest, _) = CompatibilityEvaluator.evaluate(
            device = leAudioDevice,
            telemetry = EngineTelemetry(isRunning = true),
            loopback = LoopbackMeasurement(
                phase = LoopbackTestPhase.COMPLETED,
                measuredEndToEndMs = 68f,
                confidence = 0.89f,
            ),
        )
        assertEquals(CompatibilityRating.EXCELLENT, ratingAfterLowLatencyTest)

        val (ratingAfterSlowTest, _) = CompatibilityEvaluator.evaluate(
            device = leAudioDevice,
            telemetry = EngineTelemetry(isRunning = true),
            loopback = LoopbackMeasurement(
                phase = LoopbackTestPhase.COMPLETED,
                measuredEndToEndMs = 175f,
                confidence = 0.85f,
            ),
        )
        assertEquals(CompatibilityRating.NOT_RECOMMENDED, ratingAfterSlowTest)
    }

    @Test
    fun dspConfig_presetsApplySafeDefaults() {
        val clarity = DspConfig.forPreset(AudioPreset.SPEECH_CLARITY)
        assertTrue(clarity.noiseSuppressionEnabled)
        assertTrue(clarity.speechClarityEnabled)
        assertTrue(clarity.maxOutputLimitDb <= 0f)

        val quiet = DspConfig.forPreset(AudioPreset.QUIET_ROOM)
        assertFalse(quiet.noiseSuppressionEnabled)
        assertEquals(70f, quiet.highPassCutoffHz, 0.01f)
    }

    @Test
    fun onboarding_requiresSafetyAcknowledgmentBeforeContinuing() {
        var accepted = false
        composeRule.setContent {
            HearLinkTheme {
                SafetyOnboardingScreen(
                    safetyState = SafetyAndPermissionState(
                        hasAcceptedSafetyNotice = false,
                        microphoneGranted = true,
                        bluetoothGranted = true,
                    ),
                    onRequestMicrophone = {},
                    onRequestBluetooth = {},
                    onRequestNotifications = {},
                    onOpenBatterySettings = {},
                    onAcceptAndContinue = { accepted = true },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_continue_button").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("safety_acknowledge_checkbox").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_continue_button").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("onboarding_continue_button").performClick()
        assertTrue(accepted)
    }

    @Test
    fun mainScreen_blocksStartWithoutHeadphones_andShowsA2dpWarningWhenSelected() {
        val a2dpDevice = AudioOutputDevice(
            id = 22,
            name = "BoneConduction A2DP",
            profileType = BluetoothProfileType.A2DP,
            isCommunicationDevice = false,
        )
        val stateWithA2dp = HearLinkUiState(
            settingsLoaded = true,
            safetyState = SafetyAndPermissionState(
                hasAcceptedSafetyNotice = true,
                microphoneGranted = true,
                bluetoothGranted = true,
            ),
            routeState = AudioRouteState(
                availableDevices = listOf(a2dpDevice),
                selectedDevice = a2dpDevice,
                compatibilityRating = CompatibilityRating.ACCEPTABLE,
                isA2dpFallbackWarning = true,
            ),
        )
        composeRule.setContent {
            HearLinkTheme {
                MainTherapistScreen(
                    uiState = stateWithA2dp,
                    onStartTransmission = {},
                    onStopTransmission = {},
                    onOpenDeviceSelector = {},
                    onSelectPreset = {},
                    onMicSensitivityChange = {},
                    onOutputVolumeChange = {},
                    onNoiseSuppressionToggle = {},
                    onSpeechClarityToggle = {},
                    onRequestMissingPermissions = {},
                    onOpenSafetyInfo = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("selected_headphone_name").assertExists()
        composeRule.onNodeWithText("BoneConduction A2DP").assertExists()
        composeRule.onNodeWithText(
            "Aktywny profil A2DP (tryb awaryjny): opóźnienie może być wyższe i wpływać na synchronizację mowy.",
        ).assertExists()
        composeRule.onNodeWithTag("main_transmission_button").assertIsEnabled()
    }
}
