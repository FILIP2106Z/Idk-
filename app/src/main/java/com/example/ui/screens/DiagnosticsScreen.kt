package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AudioRouteState
import com.example.model.EngineTelemetry
import com.example.model.LoopbackMeasurement
import com.example.model.LoopbackTestPhase
import com.example.ui.theme.LocalHearLinkColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DiagnosticsScreen(
    telemetry: EngineTelemetry,
    routeState: AudioRouteState,
    loopbackMeasurement: LoopbackMeasurement,
    isTransmissionActive: Boolean,
    onStartLoopbackTest: () -> Unit,
    onCancelLoopbackTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalHearLinkColors.current
    var showTestPlan by remember { mutableStateOf(false) }

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
            // Latency Separation & Guided Acoustic Loopback Card
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            text = "Pomiar opóźnień (rozdzielone etapy)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticMetricRow(
                        label = "1. Czas przetwarzania DSP aplikacji",
                        value = if (telemetry.isRunning) {
                            String.format(
                                Locale.US,
                                "%.2f ms (śr. %.0f µs / maks. %.0f µs)",
                                telemetry.dspAvgTimeUs / 1000f,
                                telemetry.dspAvgTimeUs,
                                telemetry.dspMaxTimeUs,
                            )
                        } else {
                            "Nieaktywny"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "2. Bufor wejściowy Oboe (Mikrofon)",
                        value = if (telemetry.isRunning) {
                            String.format(
                                Locale.US,
                                "%.1f ms (%d kl./burst @ %d Hz)",
                                telemetry.inputBufferLatencyMs,
                                telemetry.inputFramesPerBurst,
                                telemetry.inputSampleRate,
                            )
                        } else {
                            "Nieaktywny"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "3. Kolejka międzywątkowa SPSC",
                        value = if (telemetry.isRunning) {
                            String.format(
                                Locale.US,
                                "%.1f ms (%d klatek)",
                                telemetry.ringBufferLatencyMs,
                                telemetry.ringBufferedFrames,
                            )
                        } else {
                            "Nieaktywny"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "4. Bufor wyjściowy Oboe (Audio HAL)",
                        value = if (telemetry.isRunning) {
                            String.format(
                                Locale.US,
                                "%.1f ms (%d kl. / burst %d @ %d Hz)",
                                telemetry.outputBufferLatencyMs,
                                telemetry.outputBufferSizeFrames,
                                telemetry.outputFramesPerBurst,
                                telemetry.outputSampleRate,
                            )
                        } else {
                            "Nieaktywny"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "5. Suma aplikacja + telefon (cel 10–20 ms)",
                        value = telemetry.estimatedPipelineLatencyMs?.let {
                            String.format(Locale.US, "%.1f ms (przed radiem Bluetooth)", it)
                        } ?: "Niedostępne (strumień zatrzymany)",
                        highlightColor = telemetry.estimatedPipelineLatencyMs?.let { ms ->
                            when {
                                ms <= 20f -> semantic.excellent
                                ms <= 35f -> semantic.warning
                                else -> semantic.error
                            }
                        },
                    )
                    DiagnosticMetricRow(
                        label = "6. Zmierzone opóźnienie z Bluetooth (end-to-end)",
                        value = loopbackMeasurement.measuredEndToEndMs?.let {
                            "${it.roundToInt()} ms (pewność ${(loopbackMeasurement.confidence * 100).roundToInt()}%)"
                        } ?: "Niedostępne — wymaga testu poniżej",
                        highlightColor = loopbackMeasurement.measuredEndToEndMs?.let { ms ->
                            when {
                                ms < 100f -> semantic.excellent
                                ms <= 150f -> semantic.warning
                                else -> semantic.error
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Instrukcja testu akustycznego (Loopback Ping):",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Uruchom transmisję do słuchawek.\n" +
                                    "2. Zbliż przetworniki słuchawek kostnych na ok. 2–5 cm do mikrofonu telefonu w cichym pomieszczeniu.\n" +
                                    "3. Naciśnij przycisk poniżej — aplikacja wyemituje krótki impuls 1800 Hz i zmierzy rzeczywisty czas powrotu.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = loopbackMeasurement.messagePl,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (loopbackMeasurement.phase) {
                                    LoopbackTestPhase.COMPLETED -> semantic.excellent
                                    LoopbackTestPhase.FAILED -> semantic.error
                                    LoopbackTestPhase.MEASURING -> MaterialTheme.colorScheme.primary
                                    LoopbackTestPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (loopbackMeasurement.phase == LoopbackTestPhase.MEASURING) {
                        OutlinedButton(
                            onClick = onCancelLoopbackTest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_loopback_button"),
                        ) {
                            Text("Anuluj pomiar")
                        }
                    } else {
                        Button(
                            onClick = onStartLoopbackTest,
                            enabled = isTransmissionActive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("start_loopback_button"),
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isTransmissionActive) "Uruchom test opóźnienia"
                                else "Uruchom transmisję, aby zmierzyć",
                            )
                        }
                    }
                }
            }

            // Live Stream & System Diagnostics Report
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Raport wydajności silnika audio",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    DiagnosticMetricRow(
                        label = "Strumień wejściowy (Mikrofon)",
                        value = if (telemetry.inputStreamOpen) {
                            val api = if (telemetry.inputIsAAudio) "AAudio" else "OpenSL ES"
                            val mode = if (telemetry.inputIsExclusive) "Exclusive" else "Shared"
                            "Otwarty ($api, $mode)"
                        } else {
                            "Zamknięty"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "Strumień wyjściowy (Słuchawki)",
                        value = if (telemetry.outputStreamOpen) {
                            val api = if (telemetry.outputIsAAudio) "AAudio" else "OpenSL ES"
                            val mode = if (telemetry.outputIsExclusive) "Exclusive" else "Shared"
                            "Otwarty ($api, $mode)"
                        } else {
                            "Zamknięty"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "Częstotliwość próbkowania (Wej. / Wyj.)",
                        value = if (telemetry.inputSampleRate > 0 && telemetry.outputSampleRate > 0) {
                            val resamplerTag = if (telemetry.inputSampleRate == telemetry.outputSampleRate) {
                                "bez resamplera"
                            } else {
                                "resampler liniowy"
                            }
                            "${telemetry.inputSampleRate} Hz → ${telemetry.outputSampleRate} Hz ($resamplerTag)"
                        } else {
                            "—"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "Auto-XRun Guard / Bufor wyjściowy",
                        value = if (telemetry.outputFramesPerBurst > 0) {
                            "Mnożnik ${telemetry.autoGuardBufferMultiplier}× (korekt: ${telemetry.autoGuardAdjustments}) · ${telemetry.outputFramesPerBurst}/${telemetry.outputBufferSizeFrames} kl."
                        } else {
                            "Mnożnik ${telemetry.autoGuardBufferMultiplier}×"
                        },
                    )
                    DiagnosticMetricRow(
                        label = "Underruny (Wyjście / Kolejka SPSC)",
                        value = "${telemetry.outputXRunCount} / ${telemetry.ringUnderruns}",
                    )
                    DiagnosticMetricRow(
                        label = "Overruny (Wejście / Kolejka SPSC)",
                        value = "${telemetry.inputXRunCount} / ${telemetry.ringOverruns}",
                    )
                    DiagnosticMetricRow(
                        label = "Aktywna trasa audio",
                        value = routeState.activeRouteLabelPl,
                    )
                    DiagnosticMetricRow(
                        label = "Obciążenie CPU procesu",
                        value = telemetry.cpuLoadPercent?.let {
                            String.format(Locale.US, "%.1f%%", it)
                        } ?: "Niedostępne",
                    )
                    DiagnosticMetricRow(
                        label = "Bateria i czas sesji",
                        value = buildString {
                            append(telemetry.batteryPercent?.let { "$it%" } ?: "—")
                            if (telemetry.isRunning) {
                                val min = telemetry.sessionDurationSeconds / 60
                                val sec = telemetry.sessionDurationSeconds % 60
                                append(String.format(Locale.US, " (zużycie: -%d%%, %02d:%02d)", telemetry.batteryDrainPercent, min, sec))
                            }
                        },
                    )
                }
            }

            // Collapsible 12-Scenario Clinical & Hardware Validation Plan
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { showTestPlan = !showTestPlan }
                            .testTag("toggle_test_plan_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Plan testów klinicznych i sprzętowych (12 punktów)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (showTestPlan) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Rozwiń plan testów",
                        )
                    }

                    AnimatedVisibility(visible = showTestPlan) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val testCases = listOf(
                                "1. Różni producenci smartfonów (Pixel, Samsung, Xiaomi, Motorola) — weryfikacja AAudio Exclusive/Shared.",
                                "2. Różne modele słuchawek z przewodnictwem kostnym (LE Audio, HFP, A2DP) — pomiar opóźnienia dla każdej pary.",
                                "3. Rozłączenie i ponowne połączenie Bluetooth — kontrola blokady głośnika oraz automatycznego wznowienia.",
                                "4. Blokada ekranu — ciągłość transmisji i brak wstrzymania mikrofonu.",
                                "5. Praca w tle i zmiana aplikacji — trwałe powiadomienie z przyciskiem STOP.",
                                "6. Niski poziom baterii (< 15%) i tryb oszczędzania energii.",
                                "7. Wysoki hałas otoczenia (wentylacja, ruch uliczny) — skuteczność redukcji szumów bez ucinania spółgłosek.",
                                "8. Cichy pokój — brak pompowania tła przez AGC i naturalność brzmienia.",
                                "9. Różne odległości od mikrofonu oraz dystans roboczy 3–5 m między terapeutą a pacjentem.",
                                "10. Ciągła praca min. 60 minut — kontrola XRun, dryftu zegara i zużycia baterii.",
                                "11. Nagłe głośne dźwięki / klaskanie — skuteczność limitera szczytowego i brak przesterowania.",
                                "12. Zrozumiałość mowy i ocena komfortu przez wykwalifikowanego specjalistę przed użyciem z pacjentem.",
                            )
                            testCases.forEach { item ->
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DiagnosticMetricRow(
    label: String,
    value: String,
    highlightColor: androidx.compose.ui.graphics.Color? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
