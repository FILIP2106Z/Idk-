package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DspConfig
import com.example.model.HardwareSpecificationReport
import com.example.model.LatencyProfile
import com.example.model.MicInputPreset
import com.example.model.MicrophoneOption
import com.example.model.OutputUsagePreference
import com.example.ui.theme.LocalHearLinkColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun LowLatencyAndMicCard(
    dspConfig: DspConfig,
    microphones: List<MicrophoneOption>,
    onSelectLatencyProfile: (LatencyProfile) -> Unit,
    onSelectMicrophone: (Int) -> Unit,
    onSelectMicInputPreset: (MicInputPreset) -> Unit,
    onSelectA2dpUsagePreference: (OutputUsagePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val micOptions = remember(microphones) {
        if (microphones.isNotEmpty()) {
            microphones
        } else {
            listOf(
                MicrophoneOption(
                    deviceId = 0,
                    labelPl = "Automatyczny (główny mikrofon telefonu)",
                    locationLabelPl = "Wybór automatyczny wbudowanego mikrofonu",
                    directionalityLabelPl = "Domyślna dla modelu",
                    isBuiltIn = true,
                ),
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Niskie opóźnienie i wybór mikrofonu",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Zmiany zastosują się natychmiast (aktywny strumień zrestartuje się automatycznie bez rozłączania słuchawek).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tryb buforowania Oboe / Auto-XRun Guard",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LatencyProfile.entries.forEach { profile ->
                SelectableOptionRow(
                    selected = dspConfig.latencyProfile == profile,
                    title = profile.labelPl,
                    subtitle = profile.descriptionPl,
                    onClick = { onSelectLatencyProfile(profile) },
                    testTag = "latency_profile_${profile.name}",
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Przechwytywany mikrofon",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            micOptions.forEach { mic ->
                val details = buildString {
                    append(mic.directionalityLabelPl)
                    mic.frequencyRangeHz?.let { append(" · $it") }
                    if (mic.sampleRatesHz.isNotEmpty()) {
                        append(" · ${mic.sampleRatesHz.last()} Hz")
                    }
                }
                SelectableOptionRow(
                    selected = dspConfig.selectedMicDeviceId == mic.deviceId,
                    title = mic.labelPl,
                    subtitle = details,
                    onClick = { onSelectMicrophone(mic.deviceId) },
                    testTag = "mic_option_${mic.deviceId}",
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Tryb wejścia HAL (InputPreset)",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            MicInputPreset.entries.forEach { preset ->
                SelectableOptionRow(
                    selected = dspConfig.micInputPreset == preset,
                    title = preset.labelPl,
                    subtitle = preset.descriptionPl,
                    onClick = { onSelectMicInputPreset(preset) },
                    testTag = "input_preset_${preset.name}",
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Profil wyjścia A2DP (gdy słuchawki nie używają LE Audio/HFP)",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutputUsagePreference.entries.forEach { pref ->
                SelectableOptionRow(
                    selected = dspConfig.a2dpUsagePreference == pref,
                    title = pref.labelPl,
                    subtitle = if (pref == OutputUsagePreference.GAME_LOW_LATENCY) {
                        "Wykorzystuje ścieżkę AAudio Game o niższym buforze niż tryb Media"
                    } else {
                        "Klasyczny bufor multimedialny przy niestabilnych słuchawkach A2DP"
                    },
                    onClick = { onSelectA2dpUsagePreference(pref) },
                    testTag = "a2dp_usage_${pref.name}",
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SelectableOptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun HardwareSpecificationCard(
    report: HardwareSpecificationReport,
    onRefreshReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val semantic = LocalHearLinkColors.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(2500L)
            copiedFeedback = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hardware_spec_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.DeveloperBoard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Specyfikacja sprzętu i standardów",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = onRefreshReport,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_hardware_spec_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież specyfikację sprzętu",
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SpecSectionHeader("Smartfon i system")
            SpecRow("Model", "${report.manufacturer} ${report.model} (${report.deviceCodename})".trim())
            SpecRow("SoC / Płyta", "${report.socModel} (${report.hardwareBoard})")
            SpecRow("Android / ABI", "Android ${report.androidVersion} (API ${report.apiLevel}) · ${report.supportedAbis}")

            Spacer(modifier = Modifier.height(10.dp))
            SpecSectionHeader("Podsystem Audio HAL i Oboe")
            SpecRow(
                "Cechy Low-Latency / Pro",
                "Low-Latency: ${if (report.hasLowLatencyAudioFeature) "Tak" else "Nie"} · Pro: ${if (report.hasProAudioFeature) "Tak" else "Nie"}",
            )
            SpecRow(
                "Unprocessed Audio HAL",
                if (report.supportsUnprocessedAudioSource) "Wspierane natywnie" else "Brak deklaracji OEM",
            )
            SpecRow(
                "Natywny HAL wyjścia",
                "${report.halNativeOutputSampleRateHz?.let { "$it Hz" } ?: "—"} / ${report.halNativeOutputFramesPerBurst?.let { "$it kl./burst" } ?: "—"}",
            )
            SpecRow("Konfiguracja wejścia", report.activeMicConfigSummary.ifBlank { "Domyślna" })
            SpecRow("Aktywne Oboe", report.activeOboeSummary)
            SpecRow(
                "Opóźnienie aplikacja+telefon",
                report.lastPhonePipelineLatencyMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "Nieaktywny",
            )

            Spacer(modifier = Modifier.height(10.dp))
            SpecSectionHeader("Wykryte mikrofony fizyczne (${report.microphones.size})")
            if (report.microphones.isEmpty()) {
                SpecRow("Mikrofony", "Brak danych lub odśwież powyżej")
            } else {
                report.microphones.forEach { mic ->
                    val desc = buildString {
                        append(mic.directionalityLabelPl)
                        mic.frequencyRangeHz?.let { append(" · $it") }
                        if (mic.sampleRatesHz.isNotEmpty()) {
                            append(" · ${mic.sampleRatesHz.joinToString("/")} Hz")
                        }
                    }
                    SpecRow("[ID ${mic.deviceId}] ${mic.labelPl}", desc)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                SpecSectionHeader("Bluetooth telefonu i słuchawek")
            }
            SpecRow("LE Audio (Android API)", report.leAudioSupportedLabel)
            SpecRow(
                "BLE PHY / Adv",
                "2M PHY: ${if (report.le2MPhySupported) "Tak" else "Nie"} · Coded: ${if (report.leCodedPhySupported) "Tak" else "Nie"} · ExtAdv: ${if (report.leExtendedAdvSupported) "Tak" else "Nie"}",
            )
            SpecRow(
                "Podłączone słuchawki",
                report.connectedHeadsetName?.let { name ->
                    val mac = report.connectedHeadsetAddress ?: "brak MAC"
                    "$name ($mac)"
                } ?: "Brak połączenia",
            )
            SpecRow(
                "Profil / Kodek A2DP",
                buildString {
                    append(report.connectedProfileLabel ?: "Brak")
                    report.negotiatedA2dpCodec?.let { codec ->
                        append(" · $codec (${report.negotiatedA2dpSampleRate ?: "?"}, ${report.negotiatedA2dpBitsPerSample ?: "?"}, ${report.negotiatedA2dpChannelMode ?: "?"})")
                    }
                },
            )
            if (report.selectableA2dpCodecs.isNotEmpty()) {
                SpecRow("Kodeki słuchawek (A2DP)", report.selectableA2dpCodecs.joinToString(", "))
            }
            if (report.outputHalSampleRates.isNotEmpty()) {
                SpecRow("Częstotliwości HAL wyjścia", report.outputHalSampleRates.joinToString(", ") { "$it Hz" })
            }
            if (report.outputHalEncodings.isNotEmpty()) {
                SpecRow("Formaty HAL wyjścia", report.outputHalEncodings.joinToString(", "))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(report.toClipboardReportText()))
                    copiedFeedback = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("copy_hardware_spec_button"),
            ) {
                Icon(
                    imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (copiedFeedback) "Skopiowano raport do schowka" else "Kopiuj pełną specyfikację",
                )
            }

            AnimatedVisibility(visible = copiedFeedback) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = semantic.excellentContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = "Pełny raport tekstowy (Markdown) jest gotowy do wklejenia.",
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.excellent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SpecRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.44f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.56f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}
