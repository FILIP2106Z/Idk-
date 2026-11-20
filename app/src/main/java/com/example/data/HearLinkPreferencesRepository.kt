package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioPreset
import com.example.model.DspConfig
import com.example.model.LatencyProfile
import com.example.model.MicInputPreset
import com.example.model.OutputUsagePreference
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hearLinkDataStore: DataStore<Preferences> by preferencesDataStore(name = "hearlink_settings")

data class StoredAppSettings(
    val hasAcceptedSafetyNotice: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dspConfig: DspConfig = DspConfig(),
    val lastPreferredDeviceName: String? = null,
    val lastPreferredDeviceAddress: String? = null,
)

class HearLinkPreferencesRepository(private val context: Context) {

    private object Keys {
        val SAFETY_ACCEPTED = booleanPreferencesKey("safety_notice_accepted")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRESET = stringPreferencesKey("dsp_preset")
        val LATENCY_PROFILE = stringPreferencesKey("latency_profile")
        val MIC_INPUT_PRESET = stringPreferencesKey("mic_input_preset")
        val SELECTED_MIC_ID = intPreferencesKey("selected_mic_id")
        val A2DP_USAGE_PREF = stringPreferencesKey("a2dp_usage_pref")
        val MIC_SENSITIVITY_DB = floatPreferencesKey("mic_sensitivity_db")
        val OUTPUT_VOLUME_GAIN = floatPreferencesKey("output_volume_gain")
        val MAX_OUTPUT_LIMIT_DB = floatPreferencesKey("max_output_limit_db")
        val SOFT_START_RAMP_MS = floatPreferencesKey("soft_start_ramp_ms")
        val HIGH_PASS_ENABLED = booleanPreferencesKey("high_pass_enabled")
        val HIGH_PASS_CUTOFF_HZ = floatPreferencesKey("high_pass_cutoff_hz")
        val NS_ENABLED = booleanPreferencesKey("ns_enabled")
        val NS_STRENGTH = floatPreferencesKey("ns_strength")
        val CLARITY_ENABLED = booleanPreferencesKey("clarity_enabled")
        val CLARITY_STRENGTH = floatPreferencesKey("clarity_strength")
        val AGC_ENABLED = booleanPreferencesKey("agc_enabled")
        val AGC_TARGET_DB = floatPreferencesKey("agc_target_db")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
    }

    val settingsFlow: Flow<StoredAppSettings> = context.hearLinkDataStore.data.map { prefs ->
        val theme = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        val preset = runCatching {
            AudioPreset.valueOf(prefs[Keys.PRESET] ?: AudioPreset.STANDARD.name)
        }.getOrDefault(AudioPreset.STANDARD)

        val latencyProfile = runCatching {
            LatencyProfile.valueOf(prefs[Keys.LATENCY_PROFILE] ?: LatencyProfile.ULTRA_LOW.name)
        }.getOrDefault(LatencyProfile.ULTRA_LOW)

        val micInputPreset = runCatching {
            MicInputPreset.valueOf(prefs[Keys.MIC_INPUT_PRESET] ?: MicInputPreset.UNPROCESSED.name)
        }.getOrDefault(MicInputPreset.UNPROCESSED)

        val a2dpUsage = runCatching {
            OutputUsagePreference.valueOf(
                prefs[Keys.A2DP_USAGE_PREF] ?: OutputUsagePreference.GAME_LOW_LATENCY.name,
            )
        }.getOrDefault(OutputUsagePreference.GAME_LOW_LATENCY)

        val dsp = DspConfig(
            preset = preset,
            latencyProfile = latencyProfile,
            micInputPreset = micInputPreset,
            selectedMicDeviceId = prefs[Keys.SELECTED_MIC_ID] ?: 0,
            a2dpUsagePreference = a2dpUsage,
            micSensitivityDb = prefs[Keys.MIC_SENSITIVITY_DB] ?: 4f,
            outputVolumeGain = prefs[Keys.OUTPUT_VOLUME_GAIN] ?: 0.80f,
            maxOutputLimitDb = prefs[Keys.MAX_OUTPUT_LIMIT_DB] ?: -3f,
            softStartRampMs = prefs[Keys.SOFT_START_RAMP_MS] ?: 800f,
            highPassEnabled = prefs[Keys.HIGH_PASS_ENABLED] ?: true,
            highPassCutoffHz = prefs[Keys.HIGH_PASS_CUTOFF_HZ] ?: 90f,
            noiseSuppressionEnabled = prefs[Keys.NS_ENABLED] ?: true,
            noiseSuppressionStrength = prefs[Keys.NS_STRENGTH] ?: 0.45f,
            speechClarityEnabled = prefs[Keys.CLARITY_ENABLED] ?: true,
            speechClarityStrength = prefs[Keys.CLARITY_STRENGTH] ?: 0.50f,
            agcEnabled = prefs[Keys.AGC_ENABLED] ?: true,
            agcTargetDb = prefs[Keys.AGC_TARGET_DB] ?: -18f,
        )

        StoredAppSettings(
            hasAcceptedSafetyNotice = prefs[Keys.SAFETY_ACCEPTED] ?: false,
            themeMode = theme,
            dspConfig = dsp,
            lastPreferredDeviceName = prefs[Keys.LAST_DEVICE_NAME],
            lastPreferredDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS],
        )
    }

    suspend fun setSafetyNoticeAccepted(accepted: Boolean) {
        context.hearLinkDataStore.edit { it[Keys.SAFETY_ACCEPTED] = accepted }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.hearLinkDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveDspConfig(config: DspConfig) {
        context.hearLinkDataStore.edit { prefs ->
            prefs[Keys.PRESET] = config.preset.name
            prefs[Keys.LATENCY_PROFILE] = config.latencyProfile.name
            prefs[Keys.MIC_INPUT_PRESET] = config.micInputPreset.name
            prefs[Keys.SELECTED_MIC_ID] = config.selectedMicDeviceId
            prefs[Keys.A2DP_USAGE_PREF] = config.a2dpUsagePreference.name
            prefs[Keys.MIC_SENSITIVITY_DB] = config.micSensitivityDb
            prefs[Keys.OUTPUT_VOLUME_GAIN] = config.outputVolumeGain
            prefs[Keys.MAX_OUTPUT_LIMIT_DB] = config.maxOutputLimitDb
            prefs[Keys.SOFT_START_RAMP_MS] = config.softStartRampMs
            prefs[Keys.HIGH_PASS_ENABLED] = config.highPassEnabled
            prefs[Keys.HIGH_PASS_CUTOFF_HZ] = config.highPassCutoffHz
            prefs[Keys.NS_ENABLED] = config.noiseSuppressionEnabled
            prefs[Keys.NS_STRENGTH] = config.noiseSuppressionStrength
            prefs[Keys.CLARITY_ENABLED] = config.speechClarityEnabled
            prefs[Keys.CLARITY_STRENGTH] = config.speechClarityStrength
            prefs[Keys.AGC_ENABLED] = config.agcEnabled
            prefs[Keys.AGC_TARGET_DB] = config.agcTargetDb
        }
    }

    suspend fun saveLastPreferredDevice(name: String, address: String?) {
        context.hearLinkDataStore.edit { prefs ->
            prefs[Keys.LAST_DEVICE_NAME] = name
            if (address != null) {
                prefs[Keys.LAST_DEVICE_ADDRESS] = address
            } else {
                prefs.remove(Keys.LAST_DEVICE_ADDRESS)
            }
        }
    }
}
