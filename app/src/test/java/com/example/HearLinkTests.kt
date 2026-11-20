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
import com.example.model.HardwareSpecificationReport
import com.example.model.LatencyProfile
import com.example.model.LoopbackMeasurement
import com.example.model.LoopbackTestPhase
import com.example.model.MicInputPreset
import com.example.model.MicrophoneOption
import com.example.model.OutputUsagePreference
import com.example.model.SafetyAndPermissionState
import com.example.model.ThemeMode
import com.example.ui.HearLinkUiState
import com.example.ui.screens.AdvancedSettingsScreen
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
    fun dspConfig_presetsAndLowLatencyDefaultsAreApplied() {
        val defaults = DspConfig()
        assertEquals(LatencyProfile.ULTRA_LOW, defaults.latencyProfile)
        assertEquals(MicInputPreset.UNPROCESSED, defaults.micInputPreset)
        assertEquals(OutputUsagePreference.GAME_LOW_LATENCY, defaults.a2dpUsagePreference)
        assertEquals(0, defaults.selectedMicDeviceId)

        val clarity = DspConfig.forPreset(AudioPreset.SPEECH_CLARITY)
        assertTrue(clarity.noiseSuppressionEnabled)
        assertTrue(clarity.speechClarityEnabled)
        assertTrue(clarity.maxOutputLimitDb <= 0f)

        val quiet = DspConfig.forPreset(AudioPreset.QUIET_ROOM)
        assertFalse(quiet.noiseSuppressionEnabled)
        assertEquals(70f, quiet.highPassCutoffHz, 0.01f)
    }

    @Test
    fun hardwareSpecificationReport_formatsCompleteMarkdownClipboardReport() {
        val report = HardwareSpecificationReport(
            manufacturer = "Xiaomi",
            model = "Redmi Note 11",
            deviceCodename = "spesn",
            hardwareBoard = "bengal",
            socModel = "SM6225",
            androidVersion = "13",
            apiLevel = 33,
            securityPatch = "2024-05-01",
            supportedAbis = "arm64-v8a, armeabi-v7a",
            hasLowLatencyAudioFeature = true,
            hasProAudioFeature = false,
            supportsUnprocessedAudioSource = true,
            halNativeOutputSampleRateHz = 48000,
            halNativeOutputFramesPerBurst = 192,
            microphones = listOf(
                MicrophoneOption(
                    deviceId = 101,
                    labelPl = "Mikrofon dolny (główny, #101)",
                    locationLabelPl = "Mikrofon dolny (główny, #101)",
                    directionalityLabelPl = "Dookólny (Omni)",
                    frequencyRangeHz = "100–8000 Hz",
                    sampleRatesHz = listOf(48000),
                ),
            ),
            bluetoothEnabled = true,
            leAudioSupportedLabel = "Nie wspierane przez telefon",
            le2MPhySupported = true,
            leCodedPhySupported = false,
            connectedHeadsetName = "S10",
            connectedHeadsetAddress = "AA:BB:CC:DD:EE:FF",
            connectedProfileLabel = "Bluetooth A2DP (Tryb awaryjny)",
            negotiatedA2dpCodec = "AAC",
            negotiatedA2dpSampleRate = "44,1 kHz",
            negotiatedA2dpBitsPerSample = "16-bit",
            negotiatedA2dpChannelMode = "Stereo",
            selectableA2dpCodecs = listOf("SBC", "AAC"),
            lastPhonePipelineLatencyMs = 14.5f,
            lastAcousticEndToEndMs = 198f,
        )

        val text = report.toClipboardReportText()
        assertTrue(text.contains("Xiaomi Redmi Note 11 (spesn)"))
        assertTrue(text.contains("SM6225"))
        assertTrue(text.contains("48000 Hz / 192 klatek/burst"))
        assertTrue(text.contains("Mikrofon dolny (główny, #101)"))
        assertTrue(text.contains("AAC"))
        assertTrue(text.contains("14.5 ms"))
        assertTrue(text.contains("198 ms"))
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

    @Test
    fun advancedSettings_selectsMicrophoneAndCopiesHardwareReport() {
        var selectedMicId = 0
        var selectedPreset = MicInputPreset.UNPROCESSED
        val mics = listOf(
            MicrophoneOption(
                deviceId = 0,
                labelPl = "Automatyczny (główny mikrofon telefonu)",
                locationLabelPl = "Auto",
                directionalityLabelPl = "Domyślna",
            ),
            MicrophoneOption(
                deviceId = 102,
                labelPl = "Mikrofon górny (pomocniczy, #102)",
                locationLabelPl = "Góra",
                directionalityLabelPl = "Dookólny (Omni)",
            ),
        )
        val report = HardwareSpecificationReport(
            manufacturer = "Samsung",
            model = "Galaxy A53 5G",
            deviceCodename = "a53x",
            socModel = "Exynos 1280",
            androidVersion = "14",
            apiLevel = 34,
            microphones = mics.filter { it.deviceId != 0 },
            connectedHeadsetName = "S10",
            negotiatedA2dpCodec = "SBC",
        )

        composeRule.setContent {
            HearLinkTheme {
                AdvancedSettingsScreen(
                    dspConfig = DspConfig(),
                    themeMode = ThemeMode.SYSTEM,
                    safetyState = SafetyAndPermissionState(
                        hasAcceptedSafetyNotice = true,
                        microphoneGranted = true,
                        bluetoothGranted = true,
                    ),
                    microphones = mics,
                    hardwareReport = report,
                    onSelectMicrophone = { selectedMicId = it },
                    onSelectMicInputPreset = { selectedPreset = it },
                    onUpdateMaxOutputLimit = {},
                    onUpdateSoftStartRamp = {},
                    onUpdateHighPass = { _, _ -> },
                    onUpdateNoiseStrength = {},
                    onUpdateClarityStrength = {},
                    onUpdateAgc = { _, _ -> },
                    onSelectTheme = {},
                    onRequestMicrophone = {},
                    onRequestBluetooth = {},
                    onRequestNotifications = {},
                    onOpenBatterySettings = {},
                    onOpenSafetyInfo = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mic_option_102").performScrollTo().performClick()
        composeRule.onNodeWithTag("input_preset_VOICE_COMMUNICATION").performScrollTo().performClick()
        composeRule.onNodeWithTag("hardware_spec_card").performScrollTo().assertExists()
        composeRule.onNodeWithTag("copy_hardware_spec_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(102, selectedMicId)
        assertEquals(MicInputPreset.VOICE_COMMUNICATION, selectedPreset)
        composeRule.onNodeWithText("Skopiowano raport do schowka").assertExists()
    }
}
