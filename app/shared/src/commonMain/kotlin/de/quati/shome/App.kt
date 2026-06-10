package de.quati.shome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.quati.shome.model.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

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
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.shellys.values.toList()) { shelly ->
                        ShellyCard(shelly, onIntent = { intent ->
                            viewModel.sendIntent(BackendIntent.Shelly(shelly.mac, intent))
                        })
                    }
                }
            }
        }
    }
}

data class RolloStyle(
    val progressBarColor: Color,
    val rolloBarColor: Color,
) {
    fun draw(scope: DrawScope, progress: Float) = with(scope) {
        drawRect(
            color = progressBarColor,
            size = Size(size.width, size.height * progress)
        )
        var y = progress
        while (y > 0f) {
            drawLine(
                color = rolloBarColor,
                start = Offset(0f, size.height * y),
                end = Offset(size.width, size.height * y)
            )
            y -= 0.05f
        }
    }
}


@Composable
fun ShellyCard(shelly: ShellyState, onIntent: (ShellyIntent) -> Unit) {
    var showConfig by remember { mutableStateOf(false) }
    val progress = if (shelly is ShellyState.Valid) shelly.latestEvent.position.value else null
    val rolloStyle = RolloStyle(
        progressBarColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        rolloBarColor = Color.Gray,
    )

    Card(
        modifier = Modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    if (progress != null)
                        rolloStyle.draw(scope = this, progress = progress.toFloat())
                }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShellyTitle(shelly)
                IconButton(onClick = { showConfig = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            ShellyControlSection(shelly, onIntent)
        }
    }

    if (showConfig)
        Dialog(onDismissRequest = { showConfig = false }) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShellyTitle(shelly)
                        IconButton(onClick = { showConfig = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Settings"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ShellyConfigSection(shelly, onIntent = { intent ->
                        onIntent(intent)
                        if (intent is ShellyIntent.Update || intent is ShellyIntent.Delete) {
                            showConfig = false
                        }
                    })
                }
            }
        }
}

@Composable
fun ShellyTitle(shelly: ShellyState) {
    Column {
        Text(
            text = shelly.name ?: "Unnamed Shelly",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = shelly.mac.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ShellyControlSection(shelly: ShellyState, onIntent: (ShellyIntent) -> Unit) {
    if (shelly !is ShellyState.Valid) {
        Column {
            Text("Shelly is not valid configured")
        }
        return
    }
    val currentPos = shelly.latestEvent.position.value
    val direction = shelly.latestEvent.direction
    var moveToValue by remember { mutableStateOf("") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Position: ${(currentPos * 100).roundToInt()}%",
            fontWeight = FontWeight.Bold
        )
        if (direction != null) {
            Text(
                text = " (Driving ${direction.name})",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        FilledIconButton(
            onClick = { onIntent(ShellyIntent.MoveTo(Position.OPENED)) },
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Open"
            )
        }

        OutlinedTextField(
            value = moveToValue,
            onValueChange = { moveToValue = it },
            label = { Text("MoveTo in %") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = moveToValue.isNotEmpty() && moveToValue.toIntOrNull()?.let { it !in 0..100 } ?: true,
            trailingIcon = {
                val value = moveToValue.toIntOrNull()?.takeIf { it in 0..100 }?.let { it / 100.0 }
                if (value != null)
                    IconButton(
                        onClick = {
                            onIntent(ShellyIntent.MoveTo(Position(value)))
                            moveToValue = ""
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "MoveTo"
                        )
                    }
            }
        )

        FilledIconButton(
            onClick = { onIntent(ShellyIntent.MoveTo(Position.CLOSED)) },
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Close"
            )
        }
    }
}

@Composable
fun ShellyConfigSection(shelly: ShellyState, onIntent: (ShellyIntent) -> Unit) {
    val nameInit = shelly.name ?: ""
    val openDurationInit = shelly.totalDurationOpen?.inWholeSeconds?.toString() ?: ""
    val closeDurationInit = shelly.totalDurationClose?.inWholeSeconds?.toString() ?: ""

    var name by remember(shelly.name) { mutableStateOf(nameInit) }
    var openDuration by remember(shelly.totalDurationOpen) { mutableStateOf(openDurationInit) }
    var closeDuration by remember(shelly.totalDurationClose) { mutableStateOf(closeDurationInit) }
    var fixWebhooks by remember(shelly.webhooksValid) { mutableStateOf(false) }

    if (!shelly.isCoverProfile) {
        Column {
            Text("Shelly cannot be used with this profile, please use a 'cover' profile")
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )

        Switch(
            checked = shelly.webhooksValid || fixWebhooks,
            enabled = !shelly.webhooksValid,
            onCheckedChange = {
                fixWebhooks = it
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = openDuration,
                onValueChange = { openDuration = it },
                label = { Text("Open Duration (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = openDuration.isNotEmpty() && openDuration.toDoubleOrNull() == null,
            )
            OutlinedTextField(
                value = closeDuration,
                onValueChange = { closeDuration = it },
                label = { Text("Close Duration (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = closeDuration.isNotEmpty() && closeDuration.toDoubleOrNull() == null,
            )
        }

        Row {
            Button(
                onClick = { onIntent(ShellyIntent.Delete) },
            ) {
                Text("Delete")
            }
            Button(
                onClick = { onIntent(ShellyIntent.Reload) },
            ) {
                Text("Reload")
            }
            Button(
                onClick = {
                    onIntent(
                        ShellyIntent.Update(
                            name = name.takeIf { it != nameInit },
                            totalDurationOpen = openDuration.takeIf { it != openDurationInit }
                                ?.toDoubleOrNull()?.seconds,
                            totalDurationClose = closeDuration.takeIf { it != closeDurationInit }
                                ?.toDoubleOrNull()?.seconds,
                            fixWebhooks = fixWebhooks,
                        )
                    )
                },
            ) {
                Text("Save")
            }
        }
    }
}