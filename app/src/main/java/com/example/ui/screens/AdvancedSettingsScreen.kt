package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.DspConfig
import com.example.model.SafetyAndPermissionState
import com.example.model.ThemeMode
import com.example.ui.components.PermissionChecklistCard
import kotlin.math.roundToInt

@Composable
fun AdvancedSettingsScreen(
    dspConfig: DspConfig,
    themeMode: ThemeMode,
    safetyState: SafetyAndPermissionState,
    onUpdateMaxOutputLimit: (Float) -> Unit,
    onUpdateSoftStartRamp: (Float) -> Unit,
    onUpdateHighPass: (Boolean, Float) -> Unit,
    onUpdateNoiseStrength: (Float) -> Unit,
    onUpdateClarityStrength: (Float) -> Unit,
    onUpdateAgc: (Boolean, Float) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenSafetyInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Patient Hearing Safety Limits
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Bezpieczeństwo słuchu pacjenta",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Maksymalny limit wyjścia (Limiter)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${dspConfig.maxOutputLimitDb.roundToInt()} dBFS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = dspConfig.maxOutputLimitDb,
                        onValueChange = onUpdateMaxOutputLimit,
                        valueRange = -18f..0f,
                        modifier = Modifier.testTag("max_limit_slider"),
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Łagodne narastanie przy starcie", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${dspConfig.softStartRampMs.roundToInt()} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = dspConfig.softStartRampMs,
                        onValueChange = onUpdateSoftStartRamp,
                        valueRange = 200f..2000f,
                        modifier = Modifier.testTag("soft_start_slider"),
                    )
                }
            }

            // Advanced Modular DSP Parameters
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Zaawansowane przetwarzanie DSP (lokalne)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // High-Pass Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Filtr górnoprzepustowy (HPF)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Redukuje dudnienie i szum niskoczęstotliwościowy (${dspConfig.highPassCutoffHz.roundToInt()} Hz)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = dspConfig.highPassEnabled,
                            onCheckedChange = { onUpdateHighPass(it, dspConfig.highPassCutoffHz) },
                            modifier = Modifier.testTag("hpf_switch"),
                        )
                    }
                    Slider(
                        value = dspConfig.highPassCutoffHz,
                        onValueChange = { onUpdateHighPass(dspConfig.highPassEnabled, it) },
                        valueRange = 50f..250f,
                        enabled = dspConfig.highPassEnabled,
                        modifier = Modifier.testTag("hpf_cutoff_slider"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Intensywność redukcji szumów", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${(dspConfig.noiseSuppressionStrength * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = dspConfig.noiseSuppressionStrength,
                        onValueChange = onUpdateNoiseStrength,
                        valueRange = 0f..1f,
                        enabled = dspConfig.noiseSuppressionEnabled,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Podbicie pasma wyraźności mowy", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${(dspConfig.speechClarityStrength * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = dspConfig.speechClarityStrength,
                        onValueChange = onUpdateClarityStrength,
                        valueRange = 0f..1f,
                        enabled = dspConfig.speechClarityEnabled,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatyczna kontrola wzmocnienia (AGC)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Stabilizuje głośność mowy (cel ${dspConfig.agcTargetDb.roundToInt()} dBFS)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = dspConfig.agcEnabled,
                            onCheckedChange = { onUpdateAgc(it, dspConfig.agcTargetDb) },
                            modifier = Modifier.testTag("agc_switch"),
                        )
                    }
                    Slider(
                        value = dspConfig.agcTargetDb,
                        onValueChange = { onUpdateAgc(dspConfig.agcEnabled, it) },
                        valueRange = -26f..-10f,
                        enabled = dspConfig.agcEnabled,
                    )
                }
            }

            // Theme Mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Wygląd interfejsu",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSelectTheme(mode) },
                                label = { Text(mode.displayNamePl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_chip_${mode.name}"),
                            )
                        }
                    }
                }
            }

            PermissionChecklistCard(
                safetyState = safetyState,
                onRequestMicrophone = onRequestMicrophone,
                onRequestBluetooth = onRequestBluetooth,
                onRequestNotifications = onRequestNotifications,
                onOpenBatterySettings = onOpenBatterySettings,
            )

            OutlinedButton(
                onClick = onOpenSafetyInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("review_safety_info_button"),
            ) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pełna informacja o bezpieczeństwie i prywatności")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
