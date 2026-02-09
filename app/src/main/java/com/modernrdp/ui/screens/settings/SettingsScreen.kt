package com.modernrdp.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        ChoiceDialog(
            title = "Theme",
            options = listOf("system" to "System default", "light" to "Light", "dark" to "Dark"),
            selected = state.themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showResolutionDialog) {
        ChoiceDialog(
            title = "Default Resolution",
            options = listOf(
                "match_device" to "Match device screen",
                "fit_screen" to "Fit to screen",
                "custom" to "Custom",
            ),
            selected = state.defaultResolutionMode,
            onSelect = { viewModel.setDefaultResolutionMode(it); showResolutionDialog = false },
            onDismiss = { showResolutionDialog = false },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Appearance ---
            SectionHeader("Appearance")

            SettingsClickRow(
                title = "Theme",
                subtitle = when (state.themeMode) {
                    "light" -> "Light"
                    "dark" -> "Dark"
                    else -> "System default"
                },
                onClick = { showThemeDialog = true },
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Connection ---
            SectionHeader("Connection Defaults")

            SettingsClickRow(
                title = "Default Resolution",
                subtitle = when (state.defaultResolutionMode) {
                    "fit_screen" -> "Fit to screen"
                    "custom" -> "Custom"
                    else -> "Match device screen"
                },
                onClick = { showResolutionDialog = true },
            )

            SettingsSwitchRow(
                title = "Clipboard Sync",
                subtitle = "Share clipboard between local and remote",
                checked = state.clipboardSync,
                onCheckedChange = viewModel::setClipboardSync,
            )

            SettingsSwitchRow(
                title = "Confirm Disconnect",
                subtitle = "Show confirmation before disconnecting",
                checked = state.confirmDisconnect,
                onCheckedChange = viewModel::setConfirmDisconnect,
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Security ---
            SectionHeader("Security")

            SettingsSwitchRow(
                title = "Certificate Verification",
                subtitle = "Prompt when server certificate is untrusted",
                checked = state.certVerification,
                onCheckedChange = viewModel::setCertVerification,
            )

            SettingsSwitchRow(
                title = "Require Biometric",
                subtitle = "Authenticate before connecting to servers",
                checked = state.requireBiometric,
                onCheckedChange = viewModel::setRequireBiometric,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
