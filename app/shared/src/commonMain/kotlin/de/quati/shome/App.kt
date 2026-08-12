package de.quati.shome

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.quati.shome.model.BackendIntent
import de.quati.shome.model.BackendMessage
import de.quati.shome.model.OtfState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance

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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val colorScheme = when (isSystemInDarkTheme()) {
        true -> darkColorScheme()
        false -> lightColorScheme()
    }
    LaunchedEffect(Unit) {
        viewModel.notifications.filterIsInstance<BackendMessage.Error>().collectLatest { error ->
            snackbarHostState.showSnackbar(error.msg)
        }
    }
    MaterialTheme(
        colorScheme = colorScheme
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(currentScreen.title) },
                    actions = {
                        if (currentScreen == Screen.ShellyControl) {
                            IconButton(onClick = { showSearchDialog = true }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search Shellys",
                                )
                            }
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (state.latestVersion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
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
        if (showSearchDialog) {
            ShellySearchDialog(
                viewModel = viewModel,
                state = state,
                onIntent = { viewModel.sendIntent(it) },
                onDismiss = { showSearchDialog = false },
            )
        }
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Settings") },
                text = {
                    Column {
                        Text(
                            "Logs",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = { viewModel.downloadLogs() }) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Download Server Logs")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { viewModel.showTodaysLogs() }) {
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
                                "Update Backend",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

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
                                    "Updating will RESTART the server. Please wait until the server is back online.",
                                    color = MaterialTheme.colorScheme.error
                                )
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
                                    onClick = { viewModel.sendIntent(BackendIntent.OTFSearchLatestVersion) },
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
                                if (state.latestVersion != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.sendIntent(BackendIntent.OTFRun) },
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
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
