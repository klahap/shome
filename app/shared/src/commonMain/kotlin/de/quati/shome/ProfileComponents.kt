package de.quati.shome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.quati.shome.model.*

@Composable
fun ProfileSection(
    profiles: List<Profile>,
    shellys: List<ShellyState>,
    onIntent: (BackendIntent) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAddDialog = true },
            ) {
                Text("Add Profile")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 300.dp) // Limit height to avoid filling the whole screen
        ) {
            items(profiles) { profile ->
                ProfileCard(
                    profile = profile,
                    shellys = shellys,
                    onIntent = onIntent
                )
            }
        }
    }

    if (showAddDialog) {
        ProfileEditDialog(
            profile = Profile(
                id = ProfileId(),
            ),
            shellys = shellys,
            onDismiss = { showAddDialog = false },
            onSave = { profile ->
                onIntent(BackendIntent.UpsertProfile(profile))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProfileCard(
    profile: Profile,
    shellys: List<ShellyState>,
    onIntent: (BackendIntent) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    profile.name ?: "<unnamed>",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Row(modifier = Modifier.wrapContentSize()) {
                    IconButton(onClick = { onIntent(BackendIntent.ExecuteProfile(profile.id)) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Execute Profile")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                    }
                    IconButton(onClick = { onIntent(BackendIntent.DeleteProfile(profile.id)) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Profile")
                    }
                }
            }
            Text("${profile.positions.size} Shellys", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showEditDialog) {
        ProfileEditDialog(
            profile = profile,
            shellys = shellys,
            onDismiss = { showEditDialog = false },
            onSave = { profile ->
                onIntent(BackendIntent.UpsertProfile(profile))
                showEditDialog = false
            }
        )
    }
}

@Composable
fun ProfileEditDialog(
    profile: Profile,
    shellys: List<ShellyState>,
    onDismiss: () -> Unit,
    onSave: (Profile) -> Unit
) {
    var profileName by remember { mutableStateOf(profile.name ?: "") }
    var selectedPositions by remember { mutableStateOf(profile.positions.mapValues { it.value.percent }) }
    var hour by remember { mutableStateOf(profile.cronJobTime?.hour?.toString() ?: "") }
    var minute by remember { mutableStateOf(profile.cronJobTime?.minute?.toString() ?: "") }
    var isScheduledEnabled by remember { mutableStateOf(profile.cronJobTime != null) }

    val isHourValid = hour.toIntOrNull()?.let { it in 0..23 } ?: false
    val isMinuteValid = minute.toIntOrNull()?.let { it in 0..59 } ?: false
    val isScheduledTimeValid = !isScheduledEnabled || (isHourValid && isMinuteValid)
    val isSaveEnabled = profileName.isNotBlank() && isScheduledTimeValid

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (profile.name == null) "Add Profile" else "Edit Profile",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    isError =  profileName.isBlank(),
                    singleLine = true,
                    supportingText = {
                        if ( profileName.isBlank()) Text("Profile name is to long")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isScheduledEnabled,
                        onCheckedChange = { isScheduledEnabled = it }
                    )
                    Text("Enable Scheduled Time", style = MaterialTheme.typography.titleMedium)
                }

                if (isScheduledEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { if (it.length <= 2) hour = it.filter { char -> char.isDigit() } },
                            label = { Text("Hour") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !isHourValid,
                            supportingText = { if (!isHourValid) Text("0-23") }
                        )
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { if (it.length <= 2) minute = it.filter { char -> char.isDigit() } },
                            label = { Text("Minute") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !isMinuteValid,
                            supportingText = { if (!isMinuteValid) Text("0-59") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Shelly Positions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shellys.filterIsInstance<ShellyState.Valid>().forEach { shelly ->
                        var isSelected by remember { mutableStateOf(selectedPositions.containsKey(shelly.mac)) }
                        var positionValue by remember {
                            mutableStateOf(selectedPositions[shelly.mac] ?: PositionPercent(0))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    isSelected = it
                                    selectedPositions = if (it)
                                        selectedPositions + (shelly.mac to positionValue)
                                    else
                                        selectedPositions - shelly.mac
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(shelly.name ?: UNNAMED_SHELLY) // TODO highlight if not unnamed
                                Text(shelly.mac.value, style = MaterialTheme.typography.bodySmall)
                            }

                            PositionPicker(
                                visibleItems = 2f,
                                itemHeightDp = 30.dp,
                                selectedPosition = positionValue,
                                onSelectedPositionChange = { pos ->
                                    positionValue = pos
                                    if (isSelected)
                                        selectedPositions = selectedPositions + (shelly.mac to pos)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val h = hour.toIntOrNull()
                            val m = minute.toIntOrNull()
                            val cronJobTime = if (isScheduledEnabled && h != null && m != null) CronJobTime(h, m) else null
                            onSave(
                                profile.copy(
                                    name = profileName,
                                    positions = selectedPositions.mapValues { it.value.position },
                                    cronJobTime = cronJobTime
                                )
                            )
                        },
                        enabled = isSaveEnabled
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
