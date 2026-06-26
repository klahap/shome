package de.quati.shome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.quati.shome.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


@Composable
fun ShellySection(
    state: BackendState,
    onIntent: (BackendIntent) -> Unit,
) {
    if (state.shellySearchState is BackendState.ShellySearchState.Searching) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        Button(
            onClick = { onIntent(BackendIntent.StartSearchShellysInSubnet) },
        ) {
            Text("Search Shellys")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(state.shellys.values.toList()) { shelly ->
            ShellyCard(
                shelly = shelly,
                onIntent = { intent -> onIntent(BackendIntent.Shelly(shelly.mac, intent)) },
            )
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Status:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            when (shelly) {
                                is ShellyState.Valid -> LabelValid()
                                is ShellyState.Invalid -> LabelInValid()
                            }
                            IconButton(onClick = { showConfig = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Settings"
                                )
                            }
                        }

                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ShellyConfigSection(
                        shelly = shelly,
                        onIntent = { intent ->
                            onIntent(intent)
                            /*if (intent is ShellyIntent.Update || intent is ShellyIntent.Delete) {
                                showConfig = false
                            }*/
                        },
                        onDismiss = { showConfig = false },
                    )
                }
            }
        }
}

@Composable
fun ShellyTitle(shelly: ShellyState) {
    Column {
        Text(
            text = shelly.name ?: UNNAMED_SHELLY,  // TODO highlight if not unnamed
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
    var wheelPos by remember { mutableStateOf(PositionPercent(0)) }
    var coolingDown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Column(
            modifier = Modifier.border(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)),
        ) {
            FilledIconButton(
                onClick = { onIntent(ShellyIntent.MoveTo(Position.OPENED)) },
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Open"
                )
            }

            FilledIconButton(
                onClick = { onIntent(ShellyIntent.MoveTo(Position.CLOSED)) },
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Close"
                )
            }
        }

        Row(
            modifier = Modifier.border(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PositionPicker(
                visibleItems = 2f,
                selectedPosition = wheelPos,
                onSelectedPositionChange = { wheelPos = it },
            )
            IconButton(
                enabled = !coolingDown,
                onClick = {
                    coolingDown = true
                    scope.launch {
                        delay(1000.milliseconds)
                        coolingDown = false
                    }
                    val position = wheelPos.position
                    onIntent(ShellyIntent.MoveTo(position))
                },
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "MoveTo"
                )
            }
        }
    }
}

@Composable
fun ShellyConfigSection(
    shelly: ShellyState,
    onIntent: (ShellyIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val nameInit = shelly.name ?: ""
    val openDurationInit = shelly.totalDurationOpen?.toDouble(DurationUnit.SECONDS)?.toString() ?: ""
    val closeDurationInit = shelly.totalDurationClose?.toDouble(DurationUnit.SECONDS)?.toString() ?: ""
    val maxOpenDurationInit = shelly.configCover?.maxtimeOpenDuration?.toDouble(DurationUnit.SECONDS)?.toString() ?: ""
    val maxCloseDurationInit =
        shelly.configCover?.maxtimeCloseDuration?.toDouble(DurationUnit.SECONDS)?.toString() ?: ""
    val swapInputsInit = shelly.configCover?.swapInputs
    val invertDirectionsInit = shelly.configCover?.invertDirections

    var name by remember(shelly.name) { mutableStateOf(nameInit) }
    var openDuration by remember(shelly.totalDurationOpen) { mutableStateOf(openDurationInit) }
    var closeDuration by remember(shelly.totalDurationClose) { mutableStateOf(closeDurationInit) }
    var maxOpenDuration by remember(shelly.configCover) { mutableStateOf(maxOpenDurationInit) }
    var maxCloseDuration by remember(shelly.configCover) { mutableStateOf(maxCloseDurationInit) }
    var fixWebhooks by remember(shelly.webhooksValid) { mutableStateOf(false) }
    var swapInputs by remember(shelly.configCover) { mutableStateOf(swapInputsInit ?: false) }
    var invertDirections by remember(shelly.configCover) { mutableStateOf(invertDirectionsInit ?: false) }

    fun String.isValidTotalDuration() = toDoubleOrNull()?.let { it > 0.0 } ?: false
    fun String.isValidMaxDuration() = toDoubleOrNull()?.let { it in 0.1..300.0 } ?: false

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!shelly.isCoverProfile) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Shelly cannot be used with this profile, please use a 'cover' profile")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onIntent(ShellyIntent.Reload) },
                ) {
                    Text("Reload")
                }
            }
            return
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shelly.webhooksValid) {
                Text("Webhooks:")
                LabelValid()
            } else {
                Text("Webhooks:")
                LabelInValid()
                Spacer(Modifier.width(4.dp))
                Text("Fix it:")
                Switch(
                    checked = fixWebhooks,
                    onCheckedChange = { fixWebhooks = it }
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Swap Inputs:")
                Switch(
                    checked = swapInputs,
                    onCheckedChange = { swapInputs = it }
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Invert Directions:")
                Switch(
                    checked = invertDirections,
                    onCheckedChange = { invertDirections = it }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = openDuration,
                onValueChange = { openDuration = it },
                label = { Text("Open Duration (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !openDuration.isValidTotalDuration(),
            )
            OutlinedTextField(
                value = closeDuration,
                onValueChange = { closeDuration = it },
                label = { Text("Close Duration (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !closeDuration.isValidTotalDuration(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val supportText = "maxtime should be between 0.1 and 300s"
            OutlinedTextField(
                value = maxOpenDuration,
                onValueChange = { maxOpenDuration = it },
                label = { Text("Max time open signal (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !maxOpenDuration.isValidMaxDuration(),
                supportingText = {
                    if (!maxOpenDuration.isValidMaxDuration()) Text(supportText)
                }
            )
            OutlinedTextField(
                value = maxCloseDuration,
                onValueChange = { maxCloseDuration = it },
                label = { Text("Max time close signal (s)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !maxCloseDuration.isValidMaxDuration(),
                supportingText = {
                    if (!maxCloseDuration.isValidMaxDuration()) Text(supportText)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            /*Button(
                onClick = { onIntent(ShellyIntent.Delete) },
            ) {
                Text("Delete")
            }*/
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onIntent(ShellyIntent.Reload) },
            ) {
                Text("Reload")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onIntent(
                        ShellyIntent.Update(
                            name = name.takeIf { it != nameInit },
                            swapInputs = swapInputs.takeIf { it != swapInputsInit },
                            invertDirections = invertDirections.takeIf { it != invertDirectionsInit },
                            maxOpenDuration = maxOpenDuration
                                .takeIf { it != maxOpenDurationInit }
                                ?.takeIf { it.isValidMaxDuration() }
                                ?.toDoubleOrNull()?.seconds,
                            maxCloseDuration = maxCloseDuration
                                .takeIf { it != maxCloseDurationInit }
                                ?.takeIf { it.isValidMaxDuration() }
                                ?.toDoubleOrNull()?.seconds,
                            totalDurationOpen = openDuration
                                .takeIf { it != openDurationInit }
                                ?.takeIf { it.isValidTotalDuration() }
                                ?.toDoubleOrNull()?.seconds,
                            totalDurationClose = closeDuration
                                .takeIf { it != closeDurationInit }
                                ?.takeIf { it.isValidTotalDuration() }
                                ?.toDoubleOrNull()?.seconds,
                            fixWebhooks = fixWebhooks,
                        )
                    )
                },
            ) {
                Text("Update")
            }
        }
    }
}

@Composable
fun LabelValid() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Valid",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun LabelInValid() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Invalid",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
