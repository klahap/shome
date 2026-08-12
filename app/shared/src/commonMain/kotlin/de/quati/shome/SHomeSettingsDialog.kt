package de.quati.shome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendMessage
import de.quati.shome.model.OtfState

@Composable
fun SHomeSettingsDialog(
    state: BackendMessage.State,
    onIntent: (BackendIntent) -> Unit,
    onDismissRequest: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    title = { Text("Settings") },
    text = {
        Column {
            Text(
                "Logs",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = { AppViewModel.downloadLogs() }) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Download Server Logs")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { AppViewModel.showTodaysLogs() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Show Today's Logs")
                }
            }

            if (state.otfState != OtfState.DISABLED) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Update SHome",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Current version: ${state.currentVersion ?: "unknown"}",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (state.latestVersion != null) {
                    if (state.latestVersion == state.currentVersion) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "You are running the latest version.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            "Latest version available: ${state.latestVersion}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Updating will RESTART the server. Please wait until the server is back online.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (state.otfState == OtfState.SEARCHING) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
                if (state.otfState == OtfState.UPDATING) {
                    Text(
                        "Updating...",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onIntent(BackendIntent.OTFSearchLatestVersion) },
                        enabled = state.otfState == OtfState.ENABLED
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Check for Update")
                    }
                    if (state.latestVersion != null && state.latestVersion != state.currentVersion) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onIntent(BackendIntent.OTFRun) },
                            enabled = state.otfState == OtfState.ENABLED
                        ) {
                            Text("Update and Restart")
                        }
                    }
                }
            }
        }
    },
    confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text("Close")
        }
    }
)
