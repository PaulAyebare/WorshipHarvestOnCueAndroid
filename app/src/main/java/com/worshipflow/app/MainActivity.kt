package com.worshipflow.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds

private val Navy = Color(0xFF07101D)
private val CardColor = Color(0xFF111B2A)
private val Amber = Color(0xFFFFB855)
private val Teal = Color(0xFF94D1C5)
private val BluePad = Color(0xFFA0C8FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(
            primary = Amber, secondary = Teal, background = Navy, surface = CardColor,
            onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
        )) { WorshipFlowApp() } }
    }
}

@Composable
private fun WorshipFlowApp() {
    val context = LocalContext.current
    val repository = remember { ServiceRepository(context) }
    val importScope = rememberCoroutineScope()
    val services = remember { mutableStateListOf<ServicePlan>().apply { addAll(repository.load()) } }
    val player = remember { PlaybackController() }
    var selectedServiceId by remember { mutableStateOf<String?>(null) }
    var showServiceDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var editingServiceId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ServicePlan?>(null) }
    var slotTarget by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var slotLabel by remember { mutableStateOf("") }
    var slotEditDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgressPercent by remember { mutableStateOf<Int?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importGeneration by remember { mutableIntStateOf(0) }
    var audioRevision by remember { mutableIntStateOf(0) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf<String?>(null) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            slotLabel = displayName(context, uri).substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "Audio"
            importError = null
            importProgressPercent = null
        }
    }

    fun savePlans() {
        services.sortWith(compareBy<ServicePlan> { daySortKey(it.date) }.thenBy { it.timeMinutes })
        repository.save(services)
        refreshTick++
    }
    fun serviceById(id: String?): ServicePlan? = services.firstOrNull { it.id == id }
    fun openAudio(group: String, index: Int, plan: ServicePlan) {
        slotTarget = Triple(group, index, plan.id)
        val slots = when (group) { "loops" -> plan.loops; "pads" -> plan.pads; else -> plan.songs }
        slotLabel = slots.getOrNull(index)?.label.orEmpty()
        pendingUri = null
        importError = null
        slotEditDialog = true
    }
    LaunchedEffect(selectedServiceId) {
        if (selectedServiceId != null) while (true) {
            delay(250.milliseconds)
            refreshTick++
        }
    }

    val selectedPlan = serviceById(selectedServiceId)
    if (selectedPlan == null) {
        ServicesScreen(
            services = services.toList(),
            onNew = { editingServiceId = null; showServiceDialog = true },
            onOpen = { selectedServiceId = it.id },
            onEdit = { editingServiceId = it.id; showServiceDialog = true },
            onDelete = { deleteTarget = it },
            onPrivacyPolicy = { showPrivacyPolicy = true },
            onDuplicate = { plan ->
                if (services.size >= 8) {
                    Toast.makeText(context, "You can have up to 8 upcoming services.", Toast.LENGTH_SHORT).show()
                } else {
                    runCatching { repository.duplicateService(plan) }
                        .onSuccess { services.add(it); savePlans() }
                        .onFailure { Toast.makeText(context, "Could not duplicate service: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                }
            }
        )
    } else {
        ServiceScreen(
            plan = selectedPlan,
            player = player,
            refreshTick = refreshTick,
            audioRevision = audioRevision,
            statusText = statusText,
            onBack = { selectedServiceId = null },
            onChanged = { savePlans() },
            onChooseAudio = { group, index -> openAudio(group, index, selectedPlan) }
        )
    }

    if (showServiceDialog) {
        val existing = serviceById(editingServiceId)
        ServiceEditorDialog(
            initialName = existing?.name.orEmpty(),
            initialDate = existing?.date ?: nextSunday(),
            initialTimeMinutes = existing?.timeMinutes ?: 9 * 60,
            isEditing = existing != null,
            onDismiss = { showServiceDialog = false },
            onSave = { name, date, timeMinutes ->
                if (existing == null) {
                    if (services.size < 8) services.add(repository.newService(name, date, timeMinutes))
                } else {
                    existing.name = name
                    existing.date = date
                    existing.timeMinutes = timeMinutes
                }
                savePlans()
                showServiceDialog = false
            }
        )
    }

    if (showPrivacyPolicy) {
        val policy = remember(context) { runCatching { context.assets.open("privacy_policy.txt").bufferedReader().use { it.readText() } }.getOrDefault("Privacy information is included in the project README.") }
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Privacy policy") },
            text = { Text(policy, Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showPrivacyPolicy = false }) { Text("Done") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this service?") },
            text = { Text("${target.name}, its imported audio files, service order, and notes will be permanently deleted from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteService(target)
                    services.removeAll { it.id == target.id }
                    savePlans()
                    deleteTarget = null
                }) { Text("Delete service and audio", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    if (slotEditDialog && slotTarget != null) {
        val target = slotTarget!!
        val targetPlan = serviceById(target.third)
        val targetSlots = targetPlan?.let { when (target.first) { "loops" -> it.loops; "pads" -> it.pads; else -> it.songs } }
        val targetSlot = targetSlots?.getOrNull(target.second)
        val title = when (target.first) { "loops" -> "Edit loop"; "pads" -> "Edit pad"; else -> "Edit song" }
        AlertDialog(
            onDismissRequest = { if (!isImporting) { slotEditDialog = false; pendingUri = null; slotTarget = null } },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = slotLabel, onValueChange = { slotLabel = it.take(80) }, label = { Text("Button label") }, singleLine = true, enabled = !isImporting)
                    OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !isImporting) {
                        Text(if (pendingUri != null) "Choose a different audio file" else if (targetSlot?.loaded == true) "Replace audio file" else "Choose audio file")
                    }
                    Text(pendingUri?.let { displayName(context, it) } ?: targetSlot?.sourceName ?: "No audio file selected", style = MaterialTheme.typography.labelSmall, color = Color.LightGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    importError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (isImporting) {
                        if (importProgressPercent == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = ((importProgressPercent ?: 0) / 100f).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                        Text(importProgressPercent?.let { "Importing audio… $it%" } ?: "Importing audio…", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val plan = serviceById(target.third)
                    val uri = pendingUri
                    if (plan != null) {
                        if (!isImporting) {
                            if (uri != null) {
                                val chosenLabel = slotLabel
                                val generation = ++importGeneration
                                isImporting = true
                                importProgressPercent = null
                                importError = null
                                importScope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            repository.importFile(plan, uri, target.first, target.second, chosenLabel) { copied, total ->
                                                val percent = total?.takeIf { it > 0L }?.let { ((copied * 100L) / it).toInt().coerceIn(0, 100) }
                                                importScope.launch { if (generation == importGeneration) importProgressPercent = percent }
                                            }
                                        }
                                    }
                                    isImporting = false
                                    result.onSuccess {
                                        savePlans()
                                        audioRevision++
                                        statusText = null
                                        slotEditDialog = false
                                        pendingUri = null
                                        slotTarget = null
                                    }.onFailure {
                                        importError = "Could not import audio: ${it.localizedMessage ?: "Please choose another file."}"
                                        statusText = importError
                                    }
                                }
                            } else {
                                targetSlots?.getOrNull(target.second)?.label = slotLabel.ifBlank { targetSlot?.label.orEmpty() }
                                savePlans()
                                audioRevision++
                                slotEditDialog = false
                                pendingUri = null
                                slotTarget = null
                            }
                        }
                    }
                }, enabled = !isImporting) { Text(if (isImporting) "Importing…" else "Save") }
            },
            dismissButton = {
                Row {
                    if (targetSlot?.loaded == true) TextButton(onClick = {
                        targetPlan?.let { repository.removeAudio(it, target.first, target.second); savePlans(); audioRevision++ }
                        slotEditDialog = false; pendingUri = null; slotTarget = null
                    }, enabled = !isImporting) { Text("Remove audio", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { slotEditDialog = false; pendingUri = null; slotTarget = null }, enabled = !isImporting) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServicesScreen(
    services: List<ServicePlan>,
    onNew: () -> Unit,
    onOpen: (ServicePlan) -> Unit,
    onEdit: (ServicePlan) -> Unit,
    onDelete: (ServicePlan) -> Unit,
    onDuplicate: (ServicePlan) -> Unit,
    onPrivacyPolicy: () -> Unit
) {
    var menuServiceId by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = Navy, topBar = { TopAppBar(title = { Text("Services", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF142B47), Color(0xFF0D1828))), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Waves, null, tint = Amber, modifier = Modifier.size(34.dp))
                    Column {
                        Text("WORSHIP TEAM SERVICE FLOW", fontSize = 10.sp, letterSpacing = 1.2.sp, color = Color.LightGray)
                        Text("Worship Harvest OnCue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Your upcoming services", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Plan service flow, audio, and notes on this device. No internet connection is needed during use.", color = Color.LightGray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${services.size} of 8 services", color = Teal, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onNew, enabled = services.size < 8) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("New service")
                    }
                }
            }
            if (services.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = CardColor)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, null, tint = Amber, modifier = Modifier.size(32.dp))
                        Text("No services yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Create a service with a name and date.", color = Color.LightGray)
                    }
                }
            }
            services.sortedWith(compareBy<ServicePlan> { daySortKey(it.date) }.thenBy { it.timeMinutes }).forEach { plan ->
                Card(colors = CardDefaults.cardColors(containerColor = CardColor), onClick = { onOpen(plan) }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.width(58.dp).background(Amber.copy(alpha = .12f), RoundedCornerShape(12.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(SimpleDateFormat("MMM", Locale.getDefault()).format(Date(plan.date)).uppercase(), color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(plan.date)), color = Amber, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(plan.date))} · ${formatClock(plan.timeMinutes)}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            Surface(shape = CircleShape, color = Color.White.copy(alpha = .04f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .14f))) {
                                IconButton(onClick = { menuServiceId = plan.id }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.MoreHoriz, "Service options", tint = Color.LightGray)
                                }
                            }
                            DropdownMenu(expanded = menuServiceId == plan.id, onDismissRequest = { menuServiceId = null }) {
                                DropdownMenuItem(
                                    text = { Text("Edit service") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menuServiceId = null; onEdit(plan) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate service") },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    onClick = { menuServiceId = null; onDuplicate(plan) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete service") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menuServiceId = null; onDelete(plan) }
                                )
                            }
                        }
                    }
                }
            }
            Text("Tap a service to open its flow. Audio, service order, and notes are stored separately for each service.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onPrivacyPolicy) { Text("Privacy policy") }
        }
    }
}

@Composable
private fun ServiceEditorDialog(initialName: String, initialDate: Long, initialTimeMinutes: Int, isEditing: Boolean, onDismiss: () -> Unit, onSave: (String, Long, Int) -> Unit) {
    val context = LocalContext.current
    var name by remember(initialName, isEditing) { mutableStateOf(initialName) }
    var date by remember(initialDate, isEditing) { mutableLongStateOf(initialDate) }
    var timeMinutes by remember(initialTimeMinutes, isEditing) { mutableIntStateOf(initialTimeMinutes.coerceIn(0, 1439)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit service" else "New service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Service name") }, singleLine = true)
                OutlinedButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(context, { _, y, m, d ->
                        val selected = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
                        date = selected.timeInMillis
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text("Date: ${SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(date))}") }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute -> timeMinutes = hour * 60 + minute }, timeMinutes / 60, timeMinutes % 60, false).show()
                }) { Text("Time: ${formatClock(timeMinutes)}") }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), date, timeMinutes) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val padKeyLabels = listOf("C", "C♯\nD♭", "D", "D♯\nE♭", "E", "F", "F♯\nG♭", "G", "G♯\nA♭", "A", "A♯\nB♭", "B")

@Composable
private fun AddServiceSectionDialog(onDismiss: () -> Unit, onAdd: (String, SectionKind, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SectionKind.SONGS) }
    var songCount by remember { mutableIntStateOf(4) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add service section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Section name") }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(kind.title, Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDownward, "Choose section type")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        SectionKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.title) }, onClick = { kind = option; expanded = false })
                        }
                    }
                }
                when (kind) {
                    SectionKind.SONGS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Song slots: $songCount", Modifier.weight(1f))
                        IconButton(onClick = { songCount = (songCount - 1).coerceAtLeast(1) }, enabled = songCount > 1) { Text("−") }
                        IconButton(onClick = { songCount = (songCount + 1).coerceAtMost(4) }, enabled = songCount < 4) { Text("+") }
                    }
                    SectionKind.LOOP -> Text("One repeating loop slot will be added.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    SectionKind.SPECIAL_MUSIC -> Text("One song slot will be added.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    SectionKind.RESPONSE_MINISTRY -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("One repeating loop and $songCount song slots will be added.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Song slots: $songCount", Modifier.weight(1f))
                            IconButton(onClick = { songCount = (songCount - 1).coerceAtLeast(1) }, enabled = songCount > 1) { Text("−") }
                            IconButton(onClick = { songCount = (songCount + 1).coerceAtMost(4) }, enabled = songCount < 4) { Text("+") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, kind, songCount) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditSectionDialog(initialTitle: String, initialSongCount: Int, canEditSongCount: Boolean, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var songCount by remember(initialTitle, initialSongCount) { mutableIntStateOf(initialSongCount.coerceIn(1, 4)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit section") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text("Section name") }, singleLine = true)
                if (canEditSongCount) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Song slots: $songCount", Modifier.weight(1f))
                    IconButton(onClick = { songCount = (songCount - 1).coerceAtLeast(1) }, enabled = songCount > 1) { Text("−") }
                    IconButton(onClick = { songCount = (songCount + 1).coerceAtMost(4) }, enabled = songCount < 4) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, songCount) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServiceScreen(
    plan: ServicePlan,
    player: PlaybackController,
    refreshTick: Int,
    audioRevision: Int,
    statusText: String?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onChooseAudio: (String, Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    BackHandler { player.stopAllNow(); onBack() }
    var selectedMode by remember(plan.id) { mutableStateOf(plan.playbackMode) }
    var showAddSection by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ServiceSection?>(null) }
    var deleteTarget by remember { mutableStateOf<ServiceSection?>(null) }

    fun addSection(name: String, kind: SectionKind, songCount: Int) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val count = when (kind) {
            SectionKind.LOOP -> 0
            SectionKind.SPECIAL_MUSIC -> 1
            SectionKind.RESPONSE_MINISTRY -> songCount.coerceIn(1, 4)
            SectionKind.SONGS -> songCount.coerceIn(1, 4)
        }
        val songIndices = (1..count).map { number ->
            plan.songs.add(AudioSlot("$cleanName $number"))
            plan.songs.lastIndex
        }.toMutableList()
        val loopIndex = if (kind == SectionKind.LOOP || kind == SectionKind.RESPONSE_MINISTRY) {
            plan.loops.add(AudioSlot("$cleanName Loop"))
            plan.loops.lastIndex
        } else null
        plan.sections.add(ServiceSection(title = cleanName, kind = kind, songIndices = songIndices, loopIndex = loopIndex))
        onChanged()
    }

    fun deleteSection(section: ServiceSection) {
        player.stopSection(section)
        section.songIndices.forEach { index ->
            plan.songs.getOrNull(index)?.let { slot -> slot.path?.let { java.io.File(it).delete() }; slot.path = null; slot.sourceName = null }
        }
        section.loopIndex?.let { index ->
            plan.loops.getOrNull(index)?.let { slot -> slot.path?.let { java.io.File(it).delete() }; slot.path = null; slot.sourceName = null }
        }
        plan.sections.removeAll { it.id == section.id }
        onChanged()
    }

    fun updateSection(section: ServiceSection, title: String, songCount: Int) {
        section.title = title.trim()
        if (section.kind == SectionKind.SONGS || section.kind == SectionKind.RESPONSE_MINISTRY) {
            val desired = songCount.coerceIn(1, 4)
            player.stopSection(section)
            while (section.songIndices.size > desired) {
                val index = section.songIndices.removeAt(section.songIndices.lastIndex)
                plan.songs.getOrNull(index)?.let { slot -> slot.path?.let { java.io.File(it).delete() }; slot.path = null; slot.sourceName = null }
            }
            while (section.songIndices.size < desired) {
                plan.songs.add(AudioSlot("${section.title} ${section.songIndices.size + 1}"))
                section.songIndices.add(plan.songs.lastIndex)
            }
        }
        onChanged()
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                title = { Column { Text(plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(plan.date))} · ${formatClock(plan.timeMinutes)}", fontSize = 12.sp, color = Color.LightGray) } },
                navigationIcon = { IconButton(onClick = { player.stopAllNow(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to services") } }
            )
        },
        bottomBar = {
            if (player.currentSongIndex != null) {
                SongPlayerBar(player, refreshTick)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(player.status, Modifier.weight(1f), color = Teal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${plan.notes.length}/600 notes", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }

            Button(onClick = { showAddSection = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add service section")
            }
            plan.sections.forEachIndexed { position, section ->
                key(section.id, audioRevision) {
                    SegmentCard(
                        section = section,
                        plan = plan,
                        player = player,
                        onChanged = onChanged,
                        onChooseAudio = onChooseAudio,
                        onRename = { renameTarget = section },
                        onDelete = { deleteTarget = section },
                        onMoveUp = { if (position > 0) { val item = plan.sections.removeAt(position); plan.sections.add(position - 1, item); onChanged() } },
                        onMoveDown = { if (position < plan.sections.lastIndex) { val item = plan.sections.removeAt(position); plan.sections.add(position + 1, item); onChanged() } },
                        onMoveSong = { from, to ->
                            if (from in section.songIndices.indices && to in section.songIndices.indices) {
                                val item = section.songIndices.removeAt(from)
                                section.songIndices.add(to, item)
                                onChanged()
                            }
                        }
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = CardColor)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("Key pads", "Play with songs, or on their own", BluePad)
                    plan.pads.chunked(6).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEachIndexed { colIndex, slot ->
                                val index = rowIndex * 6 + colIndex
                                PadButton(padKeyLabels[index], player.isLoadedTrack("pads", index), player.isPlaying("pads", index), Modifier.weight(1f), onLongClick = { onChooseAudio("pads", index) }) {
                                    if (slot.loaded) player.togglePad(plan, index) { onChanged() } else onChooseAudio("pads", index)
                                }
                            }
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CardColor)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeading("Song playback", "Crossfade, continuous, or individual", Amber)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Crossfade", "Continuous", "Individual").forEachIndexed { i, mode ->
                            SegmentedButton(selected = selectedMode == mode, onClick = {
                                selectedMode = mode; plan.playbackMode = mode; onChanged()
                            }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(mode, fontSize = 10.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fade out", color = Color.LightGray)
                        Spacer(Modifier.width(8.dp))
                        listOf(2, 4, 6).forEach { sec ->
                            FilterChip(selected = plan.fadeSeconds == sec, onClick = { plan.fadeSeconds = sec; onChanged() }, label = { Text("${sec}s") })
                            Spacer(Modifier.width(4.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = { player.fadeAll(plan.fadeSeconds) { onChanged() } }) { Icon(Icons.Default.Stop, "Fade out") }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CardColor)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading("Service notes", "Saved on this device · 600 characters maximum", Amber)
                    OutlinedTextField(
                        value = plan.notes,
                        onValueChange = { plan.notes = it.take(600); onChanged() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 125.dp),
                        placeholder = { Text("Add cues, reminders, or details for the team…") },
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    Text("Swipe down or tap outside the notes field to hide the keyboard.", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
            statusText?.let { Text(it, color = Color(0xFFFFAA88), style = MaterialTheme.typography.bodySmall) }
            Text("Imported audio stays in this app on this device. The app can be used offline.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(5.dp))
        }
    }

    if (showAddSection) {
        AddServiceSectionDialog(onDismiss = { showAddSection = false }, onAdd = { name, kind, count ->
            addSection(name, kind, count)
            showAddSection = false
        })
    }
    renameTarget?.let { section ->
        EditSectionDialog(section.title, section.songIndices.size,
            section.kind == SectionKind.SONGS || section.kind == SectionKind.RESPONSE_MINISTRY,
            onDismiss = { renameTarget = null }, onSave = { title, count ->
            updateSection(section, title, count)
            renameTarget = null
        })
    }
    deleteTarget?.let { section ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this section and its audio?") },
            text = { Text("${section.title}, its audio slots, and imported audio files will be removed from this service.") },
            confirmButton = { TextButton(onClick = { deleteSection(section); deleteTarget = null }) { Text("Delete section and audio", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SegmentCard(
    section: ServiceSection,
    plan: ServicePlan,
    player: PlaybackController,
    onChanged: () -> Unit,
    onChooseAudio: (String, Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveSong: (Int, Int) -> Unit
) {
    var menuOpen by remember(section.id) { mutableStateOf(false) }
    val sectionTint = if (section.kind == SectionKind.LOOP) Teal else Amber
    Card(colors = CardDefaults.cardColors(containerColor = CardColor)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = sectionTint)
                    Text(section.description, color = Color.LightGray, fontSize = 11.sp)
                }
                Box {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .04f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .14f))) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreHoriz, "Section options", tint = Color.LightGray)
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Move earlier") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { menuOpen = false; onMoveUp() })
                        DropdownMenuItem(text = { Text("Move later") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { menuOpen = false; onMoveDown() })
                        DropdownMenuItem(text = { Text("Edit section") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete section and audio") }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            section.loopIndex?.let { index ->
                plan.loops.getOrNull(index)?.let { slot ->
                    AudioSlotTile(plan, slot, "loops", index, Teal, player, onChooseAudio, onChanged, Modifier.fillMaxWidth(), null, 0, { _, _ -> })
                }
            }
            val songIndices = section.songIndices.toList()
            if (songIndices.isNotEmpty()) {
                if (section.kind == SectionKind.RESPONSE_MINISTRY) {
                    Text("RESPONSE SONGS · REORDER WITH SECTION MENU", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                val columnCount = if (section.kind == SectionKind.SPECIAL_MUSIC) 1 else 2
                songIndices.chunked(columnCount).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEachIndexed { columnIndex, index ->
                                val position = rowIndex * columnCount + columnIndex
                                plan.songs.getOrNull(index)?.let { slot ->
                                    AudioSlotTile(
                                        plan, slot, "songs", index, Amber, player, onChooseAudio, onChanged,
                                        Modifier.weight(1f), position.takeIf { columnCount > 1 }, songIndices.size,
                                        onMoveSong
                                    )
                                }
                            }
                            repeat(columnCount - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AudioSlotTile(
    plan: ServicePlan,
    slot: AudioSlot,
    group: String,
    index: Int,
    tint: Color,
    player: PlaybackController,
    onChooseAudio: (String, Int) -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier,
    songPosition: Int?,
    songCount: Int,
    onMoveSong: (Int, Int) -> Unit
) {
    val isPlaying = player.isPlaying(group, index)
    val loaded = slot.loaded
    var popped by remember(group, index) { mutableStateOf(false) }
    var optionsOpen by remember(group, index) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(if (popped) 1.045f else 1f, animationSpec = spring(), label = "audio-button-pop")
    Row(modifier.heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier.weight(1f).graphicsLayer(scaleX = scale, scaleY = scale)
                .then(if (loaded) Modifier else Modifier.drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = Color.White.copy(alpha = .42f),
                        cornerRadius = CornerRadius(13.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())))
                    )
                })
                .combinedClickable(
                    onClick = {
                        if (loaded) {
                            popped = true
                            scope.launch { delay(160.milliseconds); popped = false }
                            when (group) {
                                "loops" -> player.playLoop(plan, index) { onChanged() }
                                "songs" -> player.playSong(plan, index) { onChanged() }
                                else -> player.togglePad(plan, index) { onChanged() }
                            }
                        } else onChooseAudio(group, index)
                    },
                    onLongClick = { onChooseAudio(group, index) }
                ),
            shape = RoundedCornerShape(13.dp),
            color = if (loaded) tint.copy(alpha = if (isPlaying) .27f else .14f) else Color.White.copy(alpha = .025f),
            border = if (loaded) androidx.compose.foundation.BorderStroke(1.5.dp, tint.copy(alpha = .62f)) else null,
            shadowElevation = if (loaded) 5.dp else 0.dp
        ) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (group == "loops") Icons.Default.Waves else if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = tint)
                Column(Modifier.weight(1f)) {
                    Text(slot.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (loaded) if (isPlaying) "PLAYING" else "READY" else "TAP TO ADD AUDIO", fontSize = 8.sp, color = Color.LightGray, maxLines = 1)
                }
            }
        }
        if (songPosition != null) {
            Box {
                IconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreHoriz, "Song options", tint = Color.LightGray, modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit song") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { optionsOpen = false; onChooseAudio(group, index) })
                    if (songCount > 1 && songPosition > 0) DropdownMenuItem(text = { Text("Move earlier") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { optionsOpen = false; onMoveSong(songPosition, songPosition - 1) })
                    if (songCount > 1 && songPosition < songCount - 1) DropdownMenuItem(text = { Text("Move later") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { optionsOpen = false; onMoveSong(songPosition, songPosition + 1) })
                }
            }
        } else {
            IconButton(onClick = { onChooseAudio(group, index) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Edit ${slot.label}", tint = Color.LightGray, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PadButton(label: String, loaded: Boolean, playing: Boolean, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    var popped by remember(label) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scale by animateFloatAsState(if (popped) 1.055f else 1f, animationSpec = spring(), label = "pad-button-pop")
    fun pop() {
        popped = true
        scope.launch { delay(160.milliseconds); popped = false }
    }
    Surface(
        modifier = modifier.heightIn(min = 51.dp).graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(onClick = { pop(); onClick() }, onLongClick = { pop(); onLongClick() }),
        shape = RoundedCornerShape(10.dp),
        color = if (playing) BluePad.copy(alpha = .24f) else if (loaded) BluePad.copy(alpha = .10f) else Color.White.copy(alpha = .04f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (loaded) BluePad.copy(alpha = .5f) else Color.White.copy(alpha = .28f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, color = Color.White, fontSize = if (label.contains('\n')) 9.sp else 13.sp, lineHeight = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (loaded) Text("•", modifier = Modifier.align(Alignment.BottomCenter), color = BluePad, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String, tint: Color) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = tint.copy(alpha = .75f))
    }
}

@Composable
private fun SongPlayerBar(player: PlaybackController, refresh: Int) {
    val duration = remember(player, refresh) { player.duration().coerceAtLeast(1) }
    val elapsed = remember(player, refresh) { player.elapsed().coerceIn(0, duration) }
    Surface(color = Color(0xFF142336), shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, null, tint = Amber)
                Spacer(Modifier.width(8.dp))
                Text(player.status, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text("SONG PLAYER", color = Color.Gray, fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(elapsed), fontSize = 10.sp, color = Color.LightGray)
                Slider(value = elapsed.toFloat(), onValueChange = { player.seek(it.toInt()) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.weight(1f))
                Text(formatTime(duration), fontSize = 10.sp, color = Color.LightGray)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.skip(-15) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Replay10, "Rewind 15 seconds", tint = Color.White)
                        Text("15", color = Color.White, fontSize = 9.sp)
                    }
                }
                FilledIconButton(onClick = { player.toggleCurrentSong { } }) {
                    Icon(if (player.paused()) Icons.Default.PlayArrow else Icons.Default.Pause, "Play or pause")
                }
                IconButton(onClick = { player.skip(15) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("15", color = Color.White, fontSize = 9.sp)
                        Icon(Icons.Default.Forward10, "Forward 15 seconds", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun nextSunday(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, (8 - get(Calendar.DAY_OF_WEEK)) % 7 + if (get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else 0)
    set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
}.timeInMillis

private fun daySortKey(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun formatClock(minutes: Int): String {
    val hour24 = minutes.coerceIn(0, 1439) / 60
    val minute = minutes.coerceIn(0, 1439) % 60
    val hour12 = when (val hour = hour24 % 12) { 0 -> 12; else -> hour }
    val suffix = if (hour24 < 12) "AM" else "PM"
    return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, suffix)
}

private fun displayName(context: android.content.Context, uri: Uri): String {
    val cursor = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) }.getOrNull()
        ?: return uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"
    return cursor.use { if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null }
        ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"
}

private fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
