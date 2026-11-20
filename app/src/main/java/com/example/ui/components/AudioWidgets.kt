package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AudioOutputDevice
import com.example.model.BluetoothProfileType
import com.example.model.CompatibilityRating
import com.example.ui.theme.LocalHearLinkColors
import kotlin.math.roundToInt

@Composable
fun CompatibilityBadge(rating: CompatibilityRating, modifier: Modifier = Modifier) {
    val semantic = LocalHearLinkColors.current
    val (bg, fg) = when (rating) {
        CompatibilityRating.EXCELLENT -> semantic.excellentContainer to semantic.excellent
        CompatibilityRating.ACCEPTABLE -> semantic.warningContainer to semantic.warning
        CompatibilityRating.NOT_RECOMMENDED -> semantic.errorContainer to semantic.error
        CompatibilityRating.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.testTag("compatibility_badge"),
        shape = MaterialTheme.shapes.small,
        color = bg,
    ) {
        Text(
            text = rating.labelPl,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun LiveAudioLevelMeter(
    inputLevelDbfs: Float,
    outputLevelDbfs: Float,
    limiterActive: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalHearLinkColors.current
    val normalizedIn = if (isActive) ((inputLevelDbfs + 60f) / 60f).coerceIn(0f, 1f) else 0f
    val normalizedOut = if (isActive) ((outputLevelDbfs + 60f) / 60f).coerceIn(0f, 1f) else 0f
    val animIn by animateFloatAsState(
        targetValue = normalizedIn,
        animationSpec = spring(stiffness = 500f),
        label = "micLevel",
    )
    val animOut by animateFloatAsState(
        targetValue = normalizedOut,
        animationSpec = spring(stiffness = 500f),
        label = "outLevel",
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val goodColor = semantic.excellent
    val warnColor = semantic.warning
    val dangerColor = semantic.error

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Poziom sygnału (Mikrofon / Wyjście)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (limiterActive && isActive) {
                    Surface(
                        color = semantic.warningContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            text = "LIMITER",
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.warning,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = if (isActive) "${inputLevelDbfs.roundToInt()} dBFS" else "— dBFS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SegmentedMeterBar(
            progress = animIn,
            trackColor = trackColor,
            lowColor = goodColor,
            midColor = warnColor,
            highColor = dangerColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SegmentedMeterBar(
            progress = animOut,
            trackColor = trackColor,
            lowColor = primaryColor,
            midColor = warnColor,
            highColor = dangerColor,
        )
    }
}

@Composable
private fun SegmentedMeterBar(
    progress: Float,
    trackColor: Color,
    lowColor: Color,
    midColor: Color,
    highColor: Color,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val segments = 24
        val gap = 3.dp.toPx()
        val segWidth = (size.width - gap * (segments - 1)) / segments
        val activeSegments = (progress * segments).roundToInt()

        for (i in 0 until segments) {
            val x = i * (segWidth + gap)
            val ratio = (i + 1).toFloat() / segments
            val color = when {
                i >= activeSegments -> trackColor
                ratio > 0.85f -> highColor
                ratio > 0.68f -> midColor
                else -> lowColor
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(segWidth, size.height),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDevicePickerSheet(
    availableDevices: List<AudioOutputDevice>,
    selectedDevice: AudioOutputDevice?,
    isBluetoothEnabled: Boolean,
    onSelectDevice: (AudioOutputDevice) -> Unit,
    onRefreshDevices: () -> Unit,
    onOpenSystemBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val semantic = LocalHearLinkColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Wybierz słuchawki pacjenta",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_devices_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Odśwież listę urządzeń audio",
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SpeakerNotesOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Głośnik wbudowany telefonu jest ukryty dla bezpieczeństwa pacjenta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isBluetoothEnabled) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = semantic.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Moduł Bluetooth jest wyłączony",
                            style = MaterialTheme.typography.labelLarge,
                            color = semantic.error,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Włącz Bluetooth i połącz słuchawki z przewodnictwem kostnym w ustawieniach telefonu.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (availableDevices.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothAudio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Brak podłączonych słuchawek pacjenta",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sparuj i połącz słuchawki Bluetooth (preferowane LE Audio lub HFP). Jeśli Bluetooth nie wspiera niskich opóźnień, możesz podłączyć również słuchawki przewodowe / USB-C.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    availableDevices.forEach { device ->
                        val isSelected = selectedDevice?.id == device.id
                        val isA2dp = device.profileType == BluetoothProfileType.A2DP
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectDevice(device) }
                                .testTag("device_option_${device.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headset,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = device.profileType.labelPl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isA2dp) semantic.warning else MaterialTheme.colorScheme.primary,
                                    )
                                    if (isA2dp) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = semantic.warning,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Tryb awaryjny — możliwe wyższe opóźnienie",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = semantic.warning,
                                            )
                                        }
                                    }
                                    device.batteryPercent?.let { pct ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BatteryStd,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Bateria słuchawek: $pct%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Wybrane urządzenie",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenSystemBluetoothSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("open_bt_settings_button"),
            ) {
                Icon(Icons.Default.SettingsBluetooth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Otwórz ustawienia Bluetooth Androida")
            }
        }
    }
}
