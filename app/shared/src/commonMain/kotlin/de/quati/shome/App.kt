package de.quati.shome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.quati.shome.model.*
import kotlinx.coroutines.flow.collectLatest

@Composable
@Preview
fun App(viewModel: AppViewModel = viewModel { AppViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errors.collectLatest { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("SHome") },
                    actions = {
                        Button(onClick = { viewModel.sendIntent(BackendIntent.StartSearchShellysInSubnet) }) {
                            Text("Search")
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
                if (state.shellySearchState is BackendState.ShellySearchState.Searching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.shellys.values.toList()) { shelly ->
                        ShellyCard(shelly, onIntent = { intent ->
                            viewModel.sendIntent(BackendIntent.Shelly(shelly.mac, intent))
                        })
                    }
                }

                ProfileSection(
                    profiles = state.profiles,
                    shellys = state.shellys.values.toList(),
                    onIntent = { viewModel.sendIntent(it) }
                )
            }
        }
    }
}