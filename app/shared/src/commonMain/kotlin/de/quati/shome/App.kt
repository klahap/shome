package de.quati.shome

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendState
import kotlinx.coroutines.flow.collectLatest

enum class Screen {
    ShellyControl,
    ProfileCrud;

    val title: String
        get() = when (this) {
            ShellyControl -> "Shellys"
            ProfileCrud -> "Profiles"
        }
}

@Composable
@Preview
fun App(viewModel: AppViewModel = viewModel { AppViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by remember { mutableStateOf(Screen.ShellyControl) }
    var showOtfDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.errors.collectLatest { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(currentScreen.title) },
                    actions = {
                        if (state.otfState != BackendState.OtfState.DISABLED) {
                            IconButton(onClick = { showOtfDialog = true }) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = "Update Backend",
                                    tint = if (state.latestVersion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        Row {
                            IconButton(onClick = { currentScreen = Screen.ShellyControl }) {
                                Icon(
                                    Icons.Default.Devices,
                                    contentDescription = "Shelly Control",
                                    tint = if (currentScreen == Screen.ShellyControl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { currentScreen = Screen.ProfileCrud }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Profile CRUD",
                                    tint = if (currentScreen == Screen.ProfileCrud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (currentScreen) {
                    Screen.ShellyControl -> ShellySection(
                        state = state,
                        onIntent = { viewModel.sendIntent(it) },
                    )

                    Screen.ProfileCrud -> ProfileSection(
                        profiles = state.profiles.values.toList(),
                        shellys = state.shellys.values.toList(),
                        onIntent = { viewModel.sendIntent(it) },
                    )
                }
            }
        }
    }

    if (showOtfDialog) {
        AlertDialog(
            onDismissRequest = { showOtfDialog = false },
            title = { Text("Update Backend") },
            text = {
                Column {
                    Text(
                        "Current version: ${state.currentVersion ?: "unknown"}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (state.latestVersion != null) {
                        Text(
                            "Latest version available: ${state.latestVersion}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Updating will download the new JAR and RESTART the server. Please wait until the server is back online.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (state.otfState == BackendState.OtfState.SEARCHING) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    }
                    if (state.otfState == BackendState.OtfState.UPDATING) {
                        Text(
                            "Updating...",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendIntent(BackendIntent.OTFRun) },
                    enabled = state.latestVersion != null && state.otfState == BackendState.OtfState.ENABLED
                ) {
                    Text("Update and Restart")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showOtfDialog = false }) {
                        Text("Close")
                    }
                    TextButton(
                        onClick = { viewModel.sendIntent(BackendIntent.OTFSearchLatestVersion) },
                        enabled = state.otfState == BackendState.OtfState.ENABLED
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Check for Update")
                    }
                }
            }
        )
    }
}
