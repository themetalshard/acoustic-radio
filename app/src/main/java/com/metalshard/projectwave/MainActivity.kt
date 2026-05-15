package com.metalshard.projectwave

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.SvgDecoder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import androidx.core.content.edit
import kotlinx.coroutines.launch

private val GreenPrimary = Color(0xFF4CAF50)
private val GreenSecondary = Color(0xFF81C784)

private val LightGreenScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF002107),
    secondary = GreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF002107),
    tertiary = Color(0xFF388E3C),
    surface = Color(0xFFFDFDFD)
)

private val DarkGreenScheme = darkColorScheme(
    primary = GreenSecondary,
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = GreenPrimary,
    onSecondary = Color(0xFF00390A),
    secondaryContainer = Color(0xFF1B2E1C),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFF81C784),
    surface = Color(0xFF121212)
)

class MainActivity : ComponentActivity() {
    private lateinit var radioPlayer: RadioPlayer
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        radioPlayer = RadioPlayer(this)

        val imageLoader = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val systemDark = isSystemInDarkTheme()

            var isDarkMode by remember { mutableStateOf(systemDark) }
            var useDynamicColors by remember { mutableStateOf(false) }
            var selectedTab by remember { mutableIntStateOf(0) }

            var loadingMessage by remember { mutableStateOf<String?>(null) }

            val colorScheme = when {
                useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDarkMode) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                isDarkMode -> DarkGreenScheme
                else -> LightGreenScheme
            }

            MaterialTheme(colorScheme = colorScheme) {
                var stations by remember { mutableStateOf(loadStations()) }
                var showAddDialog by remember { mutableStateOf(false) }
                var stationToEdit by remember { mutableStateOf<RadioStation?>(null) }
                var currentStation by remember { mutableStateOf<RadioStation?>(null) }
                var secondsListened by remember { mutableIntStateOf(0) }

                val playbackStats by radioPlayer.playbackInfo.collectAsState()
                val currentTitle by radioPlayer.streamTitle.collectAsState()

                if (loadingMessage != null) {
                    LoadingDialog(loadingMessage!!)
                }

                val m3uImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        scope.launch {
                            loadingMessage = "Importing & caching..."
                            val imported = M3UParser.parse(context, it)

                            val existingUrls = stations.map { s -> s.streamUrl }.toSet()
                            val newStations = imported.filter { s -> s.streamUrl !in existingUrls }

                            if (newStations.isNotEmpty()) {
                                stations = stations + newStations
                                delay(800)
                            }
                            loadingMessage = null
                        }
                    }
                }

                val m3uExportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
                ) { uri ->
                    uri?.let {
                        scope.launch {
                            loadingMessage = "Creating backup..."
                            M3UExporter.export(context, it, stations)
                            delay(800)
                            loadingMessage = null
                        }
                    }
                }

                LaunchedEffect(stations) { saveStations(stations) }

                LaunchedEffect(currentStation) {
                    secondsListened = 0
                    while (currentStation != null) {
                        delay(1000)
                        secondsListened++
                    }
                }

                if (showAddDialog || stationToEdit != null) {
                    StationDialog(
                        initialStation = stationToEdit,
                        onDismiss = { showAddDialog = false; stationToEdit = null },
                        onConfirm = { name, url, icon ->
                            stations = if (stationToEdit != null) {
                                stations.map { if (it.id == stationToEdit!!.id) it.copy(name = name, streamUrl = url, imageUrl = icon) else it }
                            } else {
                                stations + RadioStation(System.currentTimeMillis().toInt(), name, url, icon)
                            }
                            showAddDialog = false; stationToEdit = null
                        },
                        onDelete = {
                            stations = stations.filter { it.id != stationToEdit?.id }
                            stationToEdit = null
                        }
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        if (selectedTab == 0) {
                            FloatingActionButton(
                                onClick = { showAddDialog = true },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(bottom = if (currentStation != null) 80.dp else 0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        }
                    },
                    bottomBar = {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            if (currentStation != null) {
                                BottomPlayerBar(
                                    station = currentStation!!,
                                    stats = playbackStats,
                                    streamTitle = currentTitle,
                                    timer = formatTime(secondsListened),
                                    onStop = { radioPlayer.stop(); currentStation = null }
                                )
                            }
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    label = { Text("Radio") },
                                    icon = { Icon(Icons.Default.Radio, null) }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    label = { Text("Settings") },
                                    icon = { Icon(Icons.Default.Settings, null) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        if (selectedTab == 0) {
                            RadioGrid(
                                modifier = Modifier.fillMaxSize(),
                                stations = stations,
                                onStationSelected = { station ->
                                    currentStation = station
                                    radioPlayer.play(station)
                                },
                                onStationLongClick = { station ->
                                    stationToEdit = station
                                }
                            )
                        } else {
                            SettingsScreen(
                                isDarkMode = isDarkMode,
                                onDarkModeChange = { isDarkMode = it },
                                useDynamicColors = useDynamicColors,
                                onDynamicColorsChange = { useDynamicColors = it },
                                onImportM3U = { m3uImportLauncher.launch(arrayOf("*/*")) },
                                onExportM3U = { m3uExportLauncher.launch("Acoustic_Backup.m3u") }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveStations(stations: List<RadioStation>) {
        val prefs = getSharedPreferences("wave_prefs", Context.MODE_PRIVATE)
        prefs.edit { putString("stations_list", gson.toJson(stations)) }
    }

    private fun loadStations(): List<RadioStation> {
        val prefs = getSharedPreferences("wave_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("stations_list", null) ?: return emptyList()
        val type = object : TypeToken<List<RadioStation>>() {}.type
        return gson.fromJson(json, type)
    }
}

@Composable
fun LoadingDialog(message: String) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(200.dp, 120.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    useDynamicColors: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit,
    onImportM3U: () -> Unit,
    onExportM3U: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("Data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onImportM3U() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Import M3U Playlist", style = MaterialTheme.typography.titleMedium)
                        Text("Add stations from a file", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onExportM3U() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Export M3U Playlist", style = MaterialTheme.typography.titleMedium)
                        Text("Backup your stations as M3U", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Mode", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Dynamic Colors", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = useDynamicColors, onCheckedChange = onDynamicColorsChange)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Credits", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val creditsText = "Acoustic v1.0\n\n" +
                        "Developed by:\n" +
                        "TheNextAtlas (Formerly TheMetalShard)\n\n" +
                        "Special thanks:\n" +
                        "NexGenDriven\nEveryone in the Steel Project"

                Text(
                    text = creditsText,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomPlayerBar(
    station: RadioStation,
    stats: String,
    streamTitle: String,
    timer: String,
    onStop: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showTimerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            while (sleepTimerMinutes > 0) {
                delay(60000)
                sleepTimerMinutes--
            }
            onStop()
        }
    }

    if (showTimerDialog) {
        TimerInputDialog(
            onDismiss = { showTimerDialog = false },
            onConfirm = { minutes ->
                sleepTimerMinutes = minutes
                showTimerDialog = false
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { isExpanded = !isExpanded },
        tonalElevation = 12.dp,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = station.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = station.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, fontSize = 15.sp)
                    if (streamTitle.isNotBlank()) {
                        Text(
                            text = streamTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            fontSize = 12.sp,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 2000)
                        )
                    }
                    Text(
                        text = if (sleepTimerMinutes > 0) "Closing in ${sleepTimerMinutes}m • $stats" else "$stats • $timer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Sleep Timer", style = MaterialTheme.typography.titleSmall)
                        if (sleepTimerMinutes > 0) {
                            Text("Active: ${sleepTimerMinutes}m left", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    IconButton(
                        onClick = { showTimerDialog = true },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Set Timer",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimerInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Sleep Timer") },
        text = {
            Column {
                Text("Enter minutes until stop:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { if (it.all { char -> char.isDigit() }) textValue = it },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. 30") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val mins = textValue.toIntOrNull() ?: 0
                onConfirm(mins)
            }) { Text("Set Timer") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(0) }) { Text("Disable", color = Color.Red) }
        }
    )
}

@Composable
fun StationDialog(
    initialStation: RadioStation?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initialStation?.name ?: "") }
    var url by remember { mutableStateOf(initialStation?.streamUrl ?: "") }
    var icon by remember { mutableStateOf(initialStation?.imageUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialStation == null) "Add Station" else "Edit Station") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Stream URL") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = icon, onValueChange = { icon = it }, label = { Text("Icon URL") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if(name.isNotBlank() && url.isNotBlank()) onConfirm(name, url, icon) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (initialStation != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) }
                }
            }
        }
    )
}

fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}