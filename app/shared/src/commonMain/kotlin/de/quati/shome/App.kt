package de.quati.shome

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.quati.shome.model.BackendMessage
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
        if (showSearchDialog)
            ShellySearchDialog(
                viewModel = viewModel,
                state = state,
                onIntent = { viewModel.sendIntent(it) },
                onDismiss = { showSearchDialog = false },
            )
        if (showSettingsDialog)
            SHomeSettingsDialog(
                state = state,
                onIntent = { viewModel.sendIntent(it) },
                onDismissRequest = { showSettingsDialog = false },
            )
    }
}
