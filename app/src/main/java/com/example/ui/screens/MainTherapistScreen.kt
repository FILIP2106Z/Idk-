package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AudioPreset
import com.example.model.CompatibilityRating
import com.example.model.TransmissionStatus
import com.example.ui.HearLinkUiState
import com.example.ui.components.CompatibilityBadge
import com.example.ui.components.CompactSafetyStatusBanner
import com.example.ui.components.LiveAudioLevelMeter
import com.example.ui.theme.LocalHearLinkColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MainTherapistScreen(
    uiState: HearLinkUiState,
    onStartTransmission: () -> Unit,
    onStopTransmission: () -> Unit,
    onOpenDeviceSelector: () -> Unit,
    onSelectPreset: (AudioPreset) -> Unit,
    onMicSensitivityChange: (Float) -> Unit,
    onOutputVolumeChange: (Float) -> Unit,
    onNoiseSuppressionToggle: (Boolean) -> Unit,
    onSpeechClarityToggle: (Boolean) -> Unit,
    onRequestMissingPermissions: () -> Unit,
    onOpenSafetyInfo: () -> Unit,
    onOpenMicSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val semantic = LocalHearLinkColors.current
    val route = uiState.routeState
    val telemetry = uiState.telemetry
    val isActive = uiState.isTransmissionActiveOrStarting

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CompactSafetyStatusBanner(
                safetyState = uiState.safetyState,
                onRequestMissingPermissions = onRequestMissingPermissions,
                onOpenSafetyInfo = onOpenSafetyInfo,
            )

            // Primary Status & Bluetooth Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Transmission & Microphone Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            val statusColor = when (uiState.transmissionStatus) {
                                TransmissionStatus.ACTIVE -> semantic.excellent
                                TransmissionStatus.RECONNECTING,
                                TransmissionStatus.PAUSED_CALL_OR_FOCUS,
                                TransmissionStatus.STARTING -> semantic.warning
                                TransmissionStatus.ERROR -> semantic.error
                                TransmissionStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Icon(
                                imageVector = if (uiState.safetyState.microphoneGranted) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Status mikrofonu",
                                tint = statusColor,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = uiState.transmissionStatus.labelPl,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = statusColor,
                                    modifier = Modifier.testTag("transmission_status_text"),
                                )
                                val selectedMicLabel = uiState.microphones
                                    .firstOrNull { it.deviceId == uiState.dspConfig.selectedMicDeviceId }
                                    ?.labelPl ?: "Mikrofon automatyczny"
                                Text(
                                    text = when {
                                        !uiState.safetyState.microphoneGranted -> "Mikrofon: brak uprawnienia"
                                        isActive && telemetry.inputSampleRate > 0 ->
                                            "$selectedMicLabel (${telemetry.inputSampleRate} Hz → ${telemetry.outputSampleRate} Hz)"
                                        else -> "$selectedMicLabel · ${uiState.dspConfig.micInputPreset.labelPl.substringBefore(" (")}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        CompatibilityBadge(rating = route.compatibilityRating)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bluetooth & Selected Headphones Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val hasDevice = route.selectedDevice != null
                        Icon(
                            imageVector = when {
                                !route.isBluetoothEnabled -> Icons.Default.BluetoothDisabled
                                hasDevice -> Icons.Default.BluetoothConnected
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "Status połączenia Bluetooth",
                            tint = if (hasDevice) MaterialTheme.colorScheme.primary else semantic.error,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = route.selectedDevice?.name ?: "Brak wybranych słuchawek",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("selected_headphone_name"),
                            )
                            Text(
                                text = route.selectedDevice?.profileType?.labelPl
                                    ?: "Podłącz słuchawki kostne Bluetooth pacjenta",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = route.compatibilityDetailPl,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (route.compatibilityRating) {
                                    CompatibilityRating.EXCELLENT -> semantic.excellent
                                    CompatibilityRating.ACCEPTABLE -> semantic.warning
                                    CompatibilityRating.NOT_RECOMMENDED,
                                    CompatibilityRating.UNAVAILABLE -> semantic.error
                                },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    // Device Battery, Latency Guard + Speaker Lock Indicators
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SpeakerNotesOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Głośnik zablokowany",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isActive) {
                                        "XRun Guard ${telemetry.autoGuardBufferMultiplier}×"
                                    } else if (uiState.dspConfig.latencyProfile.isUltraLow) {
                                        "Tryb Ultra-Niski"
                                    } else {
                                        "Tryb Zrównoważony"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        route.selectedDevice?.batteryPercent?.let { btBat ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryStd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$btBat%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // Explicit A2DP Fallback Warning
                    AnimatedVisibility(visible = route.isA2dpFallbackWarning) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = semantic.warningContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Ostrzeżenie o trybie A2DP",
                                    tint = semantic.warning,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Aktywny profil A2DP (tryb awaryjny): opóźnienie może być wyższe i wpływać na synchronizację mowy.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = semantic.warning,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOpenDeviceSelector,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("select_audio_device_button"),
                        ) {
                            Icon(Icons.Default.Headset, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Słuchawki BT", maxLines = 1)
                        }
                        TextButton(
                            onClick = onOpenMicSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("shortcut_mic_settings_button"),
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Mikrofon i HAL")
                        }
                    }
                }
            }

            // Live Audio Level Meter & Latency Telemetry Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LiveAudioLevelMeter(
                        inputLevelDbfs = telemetry.inputLevelDbfs,
                        outputLevelDbfs = telemetry.outputLevelDbfs,
                        limiterActive = telemetry.limiterActive,
                        isActive = uiState.transmissionStatus == TransmissionStatus.ACTIVE,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LatencyStatPill(
                            label = "Zmierzone z Bluetooth",
                            value = uiState.loopbackMeasurement.measuredEndToEndMs?.let {
                                "${it.roundToInt()} ms"
                            } ?: "Niedostępne",
                            subtext = if (uiState.loopbackMeasurement.measuredEndToEndMs != null) {
                                "Test akustyczny end-to-end"
                            } else {
                                "Wymaga testu w Diagnostyce"
                            },
                            modifier = Modifier.weight(1f),
                        )
                        LatencyStatPill(
                            label = "Aplikacja i telefon (cel 10–20 ms)",
                            value = telemetry.estimatedPipelineLatencyMs?.let {
                                String.format(Locale.US, "%.1f ms", it)
                            } ?: "Nieaktywny",
                            subtext = if (telemetry.isRunning) {
                                "Guard ${telemetry.autoGuardBufferMultiplier}× · ${telemetry.inputSampleRate}/${telemetry.outputSampleRate} Hz"
                            } else {
                                uiState.dspConfig.latencyProfile.labelPl
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Error Alert if any
            AnimatedVisibility(visible = !uiState.errorMessagePl.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = semantic.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = uiState.errorMessagePl.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantic.error,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Prominent One-Handed START / STOP Button
            Button(
                onClick = {
                    if (isActive) onStopTransmission() else onStartTransmission()
                },
                enabled = isActive || uiState.canStartTransmission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) semantic.error else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .testTag("main_transmission_button"),
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isActive) "ZATRZYMAJ TRANSMISJĘ" else "ROZPOCZNIJ TRANSMISJĘ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Presets & Core Clinical Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preset audio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AudioPreset.entries.forEach { preset ->
                            val selected = uiState.dspConfig.preset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.displayNamePl, maxLines = 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("preset_chip_${preset.name}"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Microphone sensitivity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Czułość mikrofonu",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${if (uiState.dspConfig.micSensitivityDb >= 0) "+" else ""}${uiState.dspConfig.micSensitivityDb.roundToInt()} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = uiState.dspConfig.micSensitivityDb,
                        onValueChange = onMicSensitivityChange,
                        valueRange = -12f..24f,
                        modifier = Modifier.testTag("mic_sensitivity_slider"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Output volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Głośność wyjściowa (limit ${uiState.dspConfig.maxOutputLimitDb.roundToInt()} dBFS)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${(uiState.dspConfig.outputVolumeGain * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = uiState.dspConfig.outputVolumeGain,
                        onValueChange = onOutputVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("output_volume_slider"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Noise suppression & Speech clarity toggles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Redukcja szumów",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Łagodne tłumienie wentylacji i szumu tła",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.dspConfig.noiseSuppressionEnabled,
                            onCheckedChange = onNoiseSuppressionToggle,
                            modifier = Modifier.testTag("noise_suppression_switch"),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wyraźność mowy",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Korekta pasma spółgłosek bez ostrości",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.dspConfig.speechClarityEnabled,
                            onCheckedChange = onSpeechClarityToggle,
                            modifier = Modifier.testTag("speech_clarity_switch"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LatencyStatPill(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
