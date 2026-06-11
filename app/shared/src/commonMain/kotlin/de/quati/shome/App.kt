package de.quati.shome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.quati.shome.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val UNNAMED_SHELLY = "Unnamed Shelly"

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

                            WheelPicker(
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
            WheelPicker(
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
                            totalDurationOpen = openDuration.takeIf { it != openDurationInit }
                                ?.toDoubleOrNull()?.seconds,
                            totalDurationClose = closeDuration.takeIf { it != closeDurationInit }
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
fun WheelPicker(
    selectedPosition: PositionPercent,
    onSelectedPositionChange: (PositionPercent) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: Float = 2f,
    itemHeightDp: Dp = 48.dp,
) {
    val itemHeightPx = with(LocalDensity.current) { itemHeightDp.toPx() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedPosition.value,
    )
    val centerOffset = (visibleItems / 2)
    val verticalPadding = itemHeightDp * visibleItems / 2 - itemHeightDp / 2

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // snap to nearest item when scroll ends
            val rawOffset = listState.firstVisibleItemScrollOffset
            val idx = listState.firstVisibleItemIndex
            val snapped = if (rawOffset > itemHeightPx / 2) idx + 1 else idx
            val target = snapped.coerceIn(0, 100)
            listState.animateScrollToItem(target)
            onSelectedPositionChange(PositionPercent(target))
        }
    }

    // keep list in sync when value changes externally
    LaunchedEffect(selectedPosition) {
        if (listState.firstVisibleItemIndex != selectedPosition.value) {
            listState.animateScrollToItem(selectedPosition.value)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeightDp * visibleItems)
            .width(80.dp)
            .clipToBounds(),
    ) {
        // selection highlight band
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                )
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = verticalPadding),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count = 101) { item ->
                val isSelected = item == selectedPosition.value
                Box(
                    modifier = Modifier
                        .height(itemHeightDp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.toString(),
                        fontSize = if (isSelected) 28.sp else 22.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        }

        // top + bottom fade overlays
        listOf(Alignment.TopCenter, Alignment.BottomCenter).forEach { alignment ->
            Box(
                modifier = Modifier
                    .align(alignment)
                    .fillMaxWidth()
                    .height(itemHeightDp * centerOffset)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (alignment == Alignment.TopCenter)
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    Color.Transparent,
                                )
                            else
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface,
                                ),
                        )
                    )
            )
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