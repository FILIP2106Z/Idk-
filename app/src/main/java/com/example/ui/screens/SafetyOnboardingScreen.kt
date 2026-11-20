package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.model.SafetyAndPermissionState
import com.example.ui.components.PermissionChecklistCard
import com.example.ui.components.SafetyNoticeContentCard
import com.example.ui.theme.LocalHearLinkColors

@Composable
fun SafetyOnboardingScreen(
    safetyState: SafetyAndPermissionState,
    onRequestMicrophone: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onAcceptAndContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var acknowledged by remember { mutableStateOf(safetyState.hasAcceptedSafetyNotice) }
    val semantic = LocalHearLinkColors.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Logo HearLink",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "HearLink",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Wspomaganie słyszenia w terapii",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SafetyNoticeContentCard()

            PermissionChecklistCard(
                safetyState = safetyState,
                onRequestMicrophone = onRequestMicrophone,
                onRequestBluetooth = onRequestBluetooth,
                onRequestNotifications = onRequestNotifications,
                onOpenBatterySettings = onOpenBatterySettings,
            )

            if (!safetyState.criticalPermissionsGranted) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = semantic.warningContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Uwaga: aby rozpocząć transmisję dźwięku, niezbędne będzie przyznanie dostępu do mikrofonu oraz Bluetooth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantic.warning,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (acknowledged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { acknowledged = !acknowledged }
                    .testTag("safety_acknowledge_checkbox"),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zapoznałem/am się z informacją o bezpieczeństwie i rozumiem, że HearLink jest narzędziem wspomagającym, a nie wyrobem medycznym.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Button(
                onClick = onAcceptAndContinue,
                enabled = acknowledged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("onboarding_continue_button"),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Przejdź do panelu terapeuty",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
