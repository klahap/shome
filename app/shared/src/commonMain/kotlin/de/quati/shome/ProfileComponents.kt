package de.quati.shome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.quati.shome.model.*

@Composable
fun ProfileSection(
    profiles: Map<ProfileName, Map<Mac, Position>>,
    shellys: List<ShellyState>,
    onIntent: (BackendIntent) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Profiles", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 300.dp) // Limit height to avoid filling the whole screen
        ) {
            items(profiles.keys.toList()) { profileName ->
                ProfileCard(
                    name = profileName,
                    positions = profiles[profileName] ?: emptyMap(),
                    shellys = shellys,
                    onIntent = onIntent
                )
            }
        }
    }

    if (showAddDialog) {
        ProfileEditDialog(
            name = null,
            initialPositions = emptyMap(),
            shellys = shellys,
            onDismiss = { showAddDialog = false },
            onSave = { name, positions ->
                onIntent(BackendIntent.UpsertProfile(name, positions))
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProfileCard(
    name: ProfileName,
    positions: Map<Mac, Position>,
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
                    name.value,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Row(modifier = Modifier.wrapContentSize()) {
                    IconButton(onClick = { onIntent(BackendIntent.ExecuteProfile(name)) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Execute Profile")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                    }
                    IconButton(onClick = { onIntent(BackendIntent.DeleteProfile(name)) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Profile")
                    }
                }
            }
            Text("${positions.size} Shellys", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showEditDialog) {
        ProfileEditDialog(
            name = name,
            initialPositions = positions,
            shellys = shellys,
            onDismiss = { showEditDialog = false },
            onSave = { newName, newPositions ->
                // If name changed, delete old one
                if (newName != name) {
                    onIntent(BackendIntent.DeleteProfile(name))
                }
                onIntent(BackendIntent.UpsertProfile(newName, newPositions))
                showEditDialog = false
            }
        )
    }
}

@Composable
fun ProfileEditDialog(
    name: ProfileName?,
    initialPositions: Map<Mac, Position>,
    shellys: List<ShellyState>,
    onDismiss: () -> Unit,
    onSave: (ProfileName, Map<Mac, Position>) -> Unit
) {
    var profileName by remember { mutableStateOf(name?.value ?: "") }
    var selectedPositions by remember { mutableStateOf(initialPositions.mapValues { it.value.percent }) }

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
                    text = if (name == null) "Add Profile" else "Edit Profile",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                val isProfileNameError = profileName.isNotEmpty() && !ProfileName(profileName).isValid
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    isError = isProfileNameError,
                    supportingText = {
                        if (isProfileNameError) Text("Profile name is to long")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

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
                            onSave(ProfileName(profileName), selectedPositions.mapValues { it.value.position })
                        },
                        enabled = profileName.isNotBlank() && !isProfileNameError && selectedPositions.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
