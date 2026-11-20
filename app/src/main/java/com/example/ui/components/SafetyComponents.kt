package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.SafetyAndPermissionState
import com.example.ui.theme.LocalHearLinkColors

@Composable
fun CompactSafetyStatusBanner(
    safetyState: SafetyAndPermissionState,
    onRequestMissingPermissions: () -> Unit,
    onOpenSafetyInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalHearLinkColors.current
    val hasCriticalGap = !safetyState.criticalPermissionsGranted

    val containerColor = if (hasCriticalGap) semantic.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val accentColor = if (hasCriticalGap) semantic.error else MaterialTheme.colorScheme.primary
    val contentColor = if (hasCriticalGap) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_safety_banner"),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (hasCriticalGap) Icons.Default.Warning else Icons.Default.Shield,
                    contentDescription = if (hasCriticalGap) "Brak krytycznych uprawnień" else "Przetwarzanie lokalne aktywne",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (hasCriticalGap) {
                        "Brak uprawnień mikrofonu lub Bluetooth — transmisja zablokowana"
                    } else {
                        "Przetwarzanie lokalne · Brak nagrywania · Ochrona szczytowa aktywna"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (hasCriticalGap) {
                TextButton(
                    onClick = onRequestMissingPermissions,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("banner_fix_permissions_button"),
                ) {
                    Text("Napraw", color = accentColor, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = onOpenSafetyInfo,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("banner_safety_info_button"),
                ) {
                    Text("Zasady", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SafetyNoticeContentCard(modifier: Modifier = Modifier) {
    val semantic = LocalHearLinkColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = semantic.warningContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.HealthAndSafety,
                        contentDescription = "Informacja o bezpieczeństwie klinicznym",
                        tint = semantic.warning,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Ważna informacja o bezpieczeństwie",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SafetyBulletItem(
                title = "Charakter wspomagający (nie jest wyrobem medycznym)",
                body = "HearLink jest aplikacją wspierającą komunikację podczas terapii. Nie jest certyfikowanym wyrobem medycznym, aparatem słuchowym ani nie zastępuje profesjonalnej diagnozy audiologicznej lub leczenia.",
            )
            Spacer(modifier = Modifier.height(10.dp))
            SafetyBulletItem(
                title = "100% lokalnego przetwarzania — prywatność z założenia",
                body = "Głos terapeuty jest pobierany z mikrofonu smartfona i przesyłany bezpośrednio w czasie rzeczywistym do podłączonych słuchawek pacjenta. Dźwięk nie jest nagrywany, wysyłany do chmury ani poddawany transkrypcji.",
            )
            Spacer(modifier = Modifier.height(10.dp))
            SafetyBulletItem(
                title = "Ochrona słuchu i weryfikacja przed sesją",
                body = "Aplikacja stosuje ogranicznik głośności i łagodny rozruch przy starcie. Przed użyciem klinicznym konfigurację powinni ocenić wykwalifikowani specjaliści ds. słuchu, szczególnie na docelowym zestawie słuchawek kostnych.",
            )
        }
    }
}

@Composable
private fun SafetyBulletItem(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PermissionChecklistCard(
    safetyState: SafetyAndPermissionState,
    onRequestMicrophone: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Lista kontrolna uprawnień i systemu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Uprawnienia są wymagane wyłącznie do bezpiecznej transmisji lokalnej w tle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))

            PermissionCheckRow(
                icon = Icons.Default.Mic,
                title = "Dostęp do mikrofonu (wymagane)",
                subtitle = "Przechwytywanie głosu terapeuty w czasie rzeczywistym",
                granted = safetyState.microphoneGranted,
                actionLabel = "Zezwól",
                testTag = "perm_mic_button",
                onAction = onRequestMicrophone,
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionCheckRow(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth i urządzenia w pobliżu (wymagane)",
                subtitle = "Wykrywanie słuchawek kostnych i stabilne trasowanie LE Audio / HFP",
                granted = safetyState.bluetoothGranted,
                actionLabel = "Zezwól",
                testTag = "perm_bt_button",
                onAction = onRequestBluetooth,
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionCheckRow(
                icon = Icons.Default.Notifications,
                title = "Powiadomienia (zalecane)",
                subtitle = "Stały pasek aktywnej transmisji z przyciskiem natychmiastowego STOP",
                granted = safetyState.notificationsGranted,
                actionLabel = "Zezwól",
                testTag = "perm_notif_button",
                onAction = onRequestNotifications,
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionCheckRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "Ciągłość usługi w tle i bateria (zalecane)",
                subtitle = "Zapobiega usypianiu mikrofonu przez system przy zablokowanym ekranie",
                granted = safetyState.batteryOptimizationIgnored,
                actionLabel = "Sprawdź",
                testTag = "perm_battery_button",
                onAction = onOpenBatterySettings,
            )
        }
    }
}

@Composable
private fun PermissionCheckRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit,
) {
    val semantic = LocalHearLinkColors.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (granted) {
            semantic.excellentContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) semantic.excellent else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
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
            Spacer(modifier = Modifier.width(8.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Przyznane",
                    tint = semantic.excellent,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(testTag),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
