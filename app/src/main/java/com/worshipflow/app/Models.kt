package com.worshipflow.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private val serviceOrder = compareBy<ServicePlan> { serviceDayKey(it.date) }.thenBy { it.timeMinutes }

private fun serviceDayKey(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

data class AudioSlot(var label: String, var path: String? = null, var sourceName: String? = null) {
    val loaded get() = !path.isNullOrBlank()
}

enum class SectionKind(val title: String) {
    LOOP("Loop"), SONGS("Songs"), SPECIAL_MUSIC("Special music"), RESPONSE_MINISTRY("Response / Ministry Moment");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: SONGS }
}

data class ServiceSection(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var kind: SectionKind,
    val songIndices: MutableList<Int> = mutableListOf(),
    var loopIndex: Int? = null,
    val legacyKey: String? = null
) {
    val description: String get() = when (kind) {
        SectionKind.LOOP -> "Background audio · repeats until stopped"
        SectionKind.SONGS -> "${songIndices.size} song slot${if (songIndices.size == 1) "" else "s"}"
        SectionKind.SPECIAL_MUSIC -> "Special music · plays once"
        SectionKind.RESPONSE_MINISTRY -> "Repeating loop and ${songIndices.size} song slots"
    }
}

data class ServicePlan(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var date: Long,
    var timeMinutes: Int = 9 * 60,
    val songs: MutableList<AudioSlot> = defaultSongs(),
    val loops: MutableList<AudioSlot> = defaultLoops(),
    val pads: MutableList<AudioSlot> = defaultPads(),
    val flow: MutableList<String> = FlowSection.entries.map { it.key }.toMutableList(),
    val sections: MutableList<ServiceSection> = defaultSections(),
    var notes: String = "",
    var playbackMode: String = "Crossfade",
    var fadeSeconds: Int = 4
)

enum class FlowSection(
    val key: String,
    val title: String,
    val description: String,
    val songSlots: IntRange? = null,
    val loopSlot: Int? = null
) {
    PRE("pre", "Pre-service", "Ambient music · repeats until stopped", loopSlot = 0),
    PRAISE("praise", "High Praise", "Four song slots", songSlots = 0..3),
    PRAYER("prayer", "Exhortation and Prayer", "Prayer ambience · repeats until stopped", loopSlot = 1),
    ADORATION("adoration", "Adoration", "Four song slots", songSlots = 4..6),
    HOST("host", "Service Host", "Background loop · repeats until stopped", loopSlot = 2),
    SERVING("serving", "Serving Moment / Testimony Time", "Background loop · repeats until stopped", loopSlot = 3),
    DANCE("dance", "Dance Team", "Dance track · plays once", songSlots = 7..7),
    GIVING("giving", "Giving Exhortation", "Background loop · repeats until stopped", loopSlot = 4),
    SPECIAL("special", "Special Song", "Song slot · plays once", songSlots = 8..8),
    WELCOME("welcome", "Welcome the Speaker", "Background loop · repeats until stopped", loopSlot = 5),
    RESPONSE("response", "Response / Ministry Moment", "Repeating loop and four songs", songSlots = 9..11, loopSlot = 6);

    companion object { fun byKey(key: String) = entries.firstOrNull { it.key == key } }
}

fun defaultSongs() = mutableListOf(
    "High Praise 1", "High Praise 2", "High Praise 3", "High Praise 4",
    "Adoration 1", "Adoration 2", "Adoration 3", "Dance Team",
    "Special Song", "Response Song 1", "Response Song 2", "Response Song 3",
    "Adoration 4", "Response Song 4"
).map { AudioSlot(it) }.toMutableList()

fun defaultLoops() = listOf("Pre-service Loop", "Prayer Loop", "Service Host Loop", "Serving / Testimony Loop",
    "Giving Exhortation Loop", "Welcome the Speaker Loop", "Response Moment Loop", "Post-service Loop")
    .map { AudioSlot(it) }.toMutableList()

fun defaultPads() = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    .map { AudioSlot(it) }.toMutableList()

fun defaultSections(songs: List<AudioSlot> = defaultSongs(), loops: List<AudioSlot> = defaultLoops()) = mutableListOf(
    ServiceSection(title = "Pre-service", kind = SectionKind.LOOP, loopIndex = 0, legacyKey = "pre"),
    ServiceSection(title = "High Praise", kind = SectionKind.SONGS, songIndices = mutableListOf(0, 1, 2, 3), legacyKey = "praise"),
    ServiceSection(title = "Exhortation and Prayer", kind = SectionKind.LOOP, loopIndex = 1, legacyKey = "prayer"),
    ServiceSection(title = "Adoration", kind = SectionKind.SONGS, songIndices = mutableListOf(4, 5, 6, 12), legacyKey = "adoration"),
    ServiceSection(title = "Service Host", kind = SectionKind.LOOP, loopIndex = 2, legacyKey = "host"),
    ServiceSection(title = "Serving Moment / Testimony Time", kind = SectionKind.LOOP, loopIndex = 3, legacyKey = "serving"),
    ServiceSection(title = "Dance Team", kind = SectionKind.SPECIAL_MUSIC, songIndices = mutableListOf(7), legacyKey = "dance"),
    ServiceSection(title = "Giving Exhortation", kind = SectionKind.LOOP, loopIndex = 4, legacyKey = "giving"),
    ServiceSection(title = "Special Song", kind = SectionKind.SPECIAL_MUSIC, songIndices = mutableListOf(8), legacyKey = "special"),
    ServiceSection(title = "Welcome the Speaker", kind = SectionKind.LOOP, loopIndex = 5, legacyKey = "welcome"),
    ServiceSection(title = "Response / Ministry Moment", kind = SectionKind.RESPONSE_MINISTRY, songIndices = mutableListOf(9, 10, 11, 13), loopIndex = 6, legacyKey = "response"),
    ServiceSection(title = "Post-service", kind = SectionKind.LOOP, loopIndex = 7)
).also { sections ->
    sections.forEach { section ->
        section.songIndices.removeAll { it !in songs.indices }
        if (section.loopIndex?.let { it !in loops.indices } == true) section.loopIndex = null
    }
}

private fun legacySections(flow: List<String>, songs: List<AudioSlot>, loops: List<AudioSlot>): MutableList<ServiceSection> {
    val defaults = defaultSections(songs, loops)
    val keys = flow.toSet()
    val ordered = flow.mapNotNull { key -> defaults.firstOrNull { it.legacyKey == key } }
    return (ordered + defaults.filter { it.legacyKey == null } + defaults.filter { it.legacyKey != null && it.legacyKey !in keys }).toMutableList()
}

class ServiceRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("worshipflow", Context.MODE_PRIVATE)
    private val servicesDir = File(app.filesDir, "services")

    fun load(): MutableList<ServicePlan> {
        val array = runCatching { JSONArray(prefs.getString("services", "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { runCatching { decode(array.getJSONObject(it)) }.getOrNull() }
            .sortedWith(serviceOrder).take(8).toMutableList()
    }

    fun save(services: List<ServicePlan>) {
        val ordered = services.sortedWith(serviceOrder)
        prefs.edit().putString("services", JSONArray().apply { ordered.forEach { put(encode(it)) } }.toString()).apply()
    }

    fun newService(name: String, date: Long, timeMinutes: Int = 9 * 60): ServicePlan {
        val plan = ServicePlan(name = name, date = date, timeMinutes = timeMinutes.coerceIn(0, 1439))
        File(servicesDir, plan.id).mkdirs()
        return plan
    }

    fun duplicateService(source: ServicePlan): ServicePlan {
        val copy = ServicePlan(
            name = "${source.name} (Copy)", date = source.date, timeMinutes = source.timeMinutes,
            songs = source.songs.map { it.copy(path = null) }.toMutableList(),
            loops = source.loops.map { it.copy(path = null) }.toMutableList(),
            pads = source.pads.map { it.copy(path = null) }.toMutableList(),
            flow = source.flow.toMutableList(),
            sections = source.sections.map { it.copy(id = UUID.randomUUID().toString(), songIndices = it.songIndices.toMutableList()) }.toMutableList(),
            notes = source.notes, playbackMode = source.playbackMode, fadeSeconds = source.fadeSeconds
        )
        val sourceRoot = File(servicesDir, source.id)
        val copyRoot = File(servicesDir, copy.id)
        try {
            copy.songs.forEachIndexed { i, slot -> slot.path = duplicateAudio(sourceRoot, copyRoot, source.songs[i].path) }
            copy.loops.forEachIndexed { i, slot -> slot.path = duplicateAudio(sourceRoot, copyRoot, source.loops[i].path) }
            copy.pads.forEachIndexed { i, slot -> slot.path = duplicateAudio(sourceRoot, copyRoot, source.pads[i].path) }
            return copy
        } catch (error: Throwable) {
            copyRoot.deleteRecursively()
            throw error
        }
    }

    private fun duplicateAudio(sourceRoot: File, copyRoot: File, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val source = File(path)
        require(source.isFile && source.canonicalPath.startsWith(sourceRoot.canonicalPath + File.separator)) {
            "An audio file for this service could not be found on this device."
        }
        val targetFolder = File(copyRoot, "audio").apply { mkdirs() }
        val target = File(targetFolder, "${UUID.randomUUID()}.${source.extension.ifBlank { "audio" }}")
        source.copyTo(target, overwrite = false)
        return target.absolutePath
    }

    fun deleteService(plan: ServicePlan) {
        File(servicesDir, plan.id).deleteRecursively()
    }

    fun importFile(
        plan: ServicePlan,
        uri: Uri,
        group: String,
        index: Int,
        label: String,
        onProgress: (copiedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): AudioSlot {
        val folder = File(File(servicesDir, plan.id), "audio").apply { mkdirs() }
        val metadata = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameColumn >= 0) cursor.getString(nameColumn) else null
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn).takeIf { it > 0L } else null
                    name to size
                } else null
            }
        }.getOrNull()
        val originalName = metadata?.first?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        val totalBytes = metadata?.second
        val namedExtension = originalName.substringAfterLast('.', "").takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        val mimeExtension = app.contentResolver.getType(uri)?.substringAfter('/')?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        val ext = namedExtension ?: mimeExtension ?: "audio"
        val target = File(folder, "${UUID.randomUUID()}.$ext")
        try {
            val input = app.contentResolver.openInputStream(uri)
                ?: error("Android could not read the selected audio file.")
            input.use { stream ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReportedPercent = -1
                    var lastReportedBytes = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        copied += count
                        val percent = totalBytes?.let { ((copied * 100L) / it).toInt() }
                        if (percent != null && percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(copied, totalBytes)
                        } else if (totalBytes == null && copied - lastReportedBytes >= 512 * 1024) {
                            lastReportedBytes = copied
                            onProgress(copied, null)
                        }
                    }
                    onProgress(copied, totalBytes)
                }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        val list = when(group) { "loops" -> plan.loops; "pads" -> plan.pads; else -> plan.songs }
        val old = list[index].path
        list[index] = AudioSlot(label.ifBlank { list[index].label }, target.absolutePath, originalName)
        old?.let { File(it).delete() }
        return list[index]
    }

    fun removeAudio(plan: ServicePlan, group: String, index: Int) {
        val list = when(group) { "loops" -> plan.loops; "pads" -> plan.pads; else -> plan.songs }
        list[index].path?.let { File(it).delete() }
        list[index].path = null
        list[index].sourceName = null
    }

    private fun encode(p: ServicePlan) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("date", p.date); put("timeMinutes", p.timeMinutes); put("notes", p.notes.take(600))
        put("playbackMode", p.playbackMode); put("fadeSeconds", p.fadeSeconds)
        put("songs", slotsJson(p.songs)); put("loops", slotsJson(p.loops)); put("pads", slotsJson(p.pads))
        put("flow", JSONArray(p.sections.mapNotNull { it.legacyKey }))
        put("sections", JSONArray().apply { p.sections.forEach { section -> put(sectionJson(section)) } })
    }

    private fun sectionJson(section: ServiceSection) = JSONObject().apply {
        put("id", section.id); put("title", section.title); put("kind", section.kind.name)
        put("songs", JSONArray(section.songIndices)); put("loop", section.loopIndex ?: JSONObject.NULL)
        put("legacyKey", section.legacyKey ?: JSONObject.NULL)
    }

    private fun decode(o: JSONObject): ServicePlan {
        val songs = readSlots(o.optJSONArray("songs"), defaultSongs())
        val loops = readSlots(o.optJSONArray("loops"), defaultLoops())
        val pads = readSlots(o.optJSONArray("pads"), defaultPads())
        val legacyFlow = o.optJSONArray("flow")?.let { json ->
            (0 until json.length()).map { json.optString(it) }.filter { FlowSection.byKey(it) != null }
        }.orEmpty()
        val savedSections = o.optJSONArray("sections")?.let { readSections(it, songs, loops) }
        return ServicePlan(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Service"),
            date = o.optLong("date", System.currentTimeMillis()),
            timeMinutes = o.optInt("timeMinutes", 9 * 60).coerceIn(0, 1439),
            songs = songs, loops = loops, pads = pads,
            flow = legacyFlow.ifEmpty { FlowSection.entries.map { it.key }.toMutableList() }.toMutableList(),
            sections = savedSections ?: legacySections(legacyFlow.ifEmpty { FlowSection.entries.map { it.key } }, songs, loops),
            notes = o.optString("notes", "").take(600),
            playbackMode = o.optString("playbackMode", "Crossfade"),
            fadeSeconds = o.optInt("fadeSeconds", 4).coerceIn(2, 6)
        )
    }

    private fun readSections(json: JSONArray, songs: List<AudioSlot>, loops: List<AudioSlot>) =
        (0 until json.length()).mapNotNull { index ->
            val o = json.optJSONObject(index) ?: return@mapNotNull null
            val songIndices = o.optJSONArray("songs")?.let { arr ->
                (0 until arr.length()).map { arr.optInt(it, -1) }.filter { it in songs.indices }.toMutableList()
            } ?: mutableListOf()
            val loopIndex = o.optInt("loop", -1).takeIf { it in loops.indices }
            ServiceSection(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "Section"),
                kind = SectionKind.from(o.optString("kind")),
                songIndices = songIndices,
                loopIndex = loopIndex,
                legacyKey = o.optString("legacyKey").takeIf { it.isNotBlank() && it != "null" }
            )
        }.toMutableList()

    private fun slotsJson(slots: List<AudioSlot>) = JSONArray().apply {
        slots.forEach { slot -> put(JSONObject().put("label", slot.label).put("path", slot.path).put("source", slot.sourceName)) }
    }

    private fun readSlots(json: JSONArray?, defaults: List<AudioSlot>): MutableList<AudioSlot> {
        val count = maxOf(defaults.size, json?.length() ?: 0)
        return (0 until count).map { index ->
            val fallback = defaults.getOrNull(index) ?: AudioSlot("Song ${index + 1}")
            val o = json?.optJSONObject(index)
            if (o == null) fallback else AudioSlot(o.optString("label", fallback.label),
                o.optString("path").takeIf { it.isNotBlank() },
                o.optString("source").takeIf { it.isNotBlank() })
        }.toMutableList()
    }
}

data class PlayingTrack(val group: String, val index: Int, val player: MediaPlayer)

class PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracks = mutableListOf<PlayingTrack>()
    private var padSwitchJob: Job? = null
    private var padSwitchGeneration = 0
    private var transitionJob: Job? = null
    private var transitionGeneration = 0
    private var loopTransitionInProgress = false
    var currentSongIndex: Int? = null
        private set
    var status = "Nothing playing"
        private set

    fun isPlaying(group: String, index: Int) = tracks.any { it.group == group && it.index == index && it.player.isPlaying }
    fun isLoadedTrack(group: String, index: Int) = tracks.any { it.group == group && it.index == index }
    fun elapsed(): Int = runCatching { currentPlayer()?.currentPosition?.div(1000) ?: 0 }.getOrDefault(0)
    fun duration(): Int = runCatching { currentPlayer()?.duration?.div(1000) ?: 0 }.getOrDefault(0)
    fun paused() = currentPlayer()?.isPlaying == false && currentSongIndex != null

    fun playSong(plan: ServicePlan, index: Int, onChange: () -> Unit) {
        val slot = plan.songs.getOrNull(index) ?: return
        val path = slot.path ?: return
        if (isLoadedTrack("songs", index)) {
            transitionGeneration++
            transitionJob?.cancel()
            loopTransitionInProgress = false
            stopGroup("songs", index, plan.fadeSeconds, onChange)
            return
        }
        transitionJob?.cancel()
        val generation = ++transitionGeneration
        loopTransitionInProgress = false
        val next = nextSongIndex(plan, index)
        transitionJob = scope.launch {
            val loops = tracks.filter { it.group == "loops" }
            if (loops.isNotEmpty()) fadeAndRemove(loops, plan.fadeSeconds)
            if (generation != transitionGeneration) return@launch
            val old = tracks.filter { it.group == "songs" }
            val crossfading = plan.playbackMode == "Crossfade" && old.isNotEmpty()
            if (!crossfading && old.isNotEmpty()) fadeAndRemove(old, plan.fadeSeconds)
            if (generation != transitionGeneration) return@launch
            val new = createTrack("songs", index, path, false, if (crossfading) 0f else 1f)
            if (new == null) { status = "This Android device can't play this audio format. Try MP3, WAV, or AAC."; onChange(); return@launch }
            if (generation != transitionGeneration) { tracks.remove(new); release(new); return@launch }
            currentSongIndex = index
            status = slot.label
            onChange()
            if (crossfading) {
                fadePair(old, new, plan.fadeSeconds)
            }
            if (generation != transitionGeneration) return@launch
            new.player.setOnCompletionListener {
                tracks.remove(new); release(new)
                if (currentSongIndex == index) {
                    currentSongIndex = null
                    status = "Nothing playing"
                    if (plan.playbackMode == "Continuous" && next != null) playSong(plan, next, onChange)
                }
                onChange()
            }
            status = slot.label
            onChange()
        }
    }

    fun playLoop(plan: ServicePlan, index: Int, onChange: () -> Unit) {
        padSwitchGeneration++
        padSwitchJob?.cancel()
        if (isLoadedTrack("loops", index)) {
            transitionGeneration++
            transitionJob?.cancel()
            loopTransitionInProgress = false
            stopGroup("loops", index, plan.fadeSeconds, onChange)
            return
        }
        transitionJob?.cancel()
        val generation = ++transitionGeneration
        loopTransitionInProgress = true
        transitionJob = scope.launch {
            fadeAndRemove(tracks.toList(), plan.fadeSeconds)
            if (generation != transitionGeneration) return@launch
            val path = plan.loops.getOrNull(index)?.path ?: run { loopTransitionInProgress = false; return@launch }
            val track = createTrack("loops", index, path, true, 1f)
            if (generation != transitionGeneration) { track?.let { tracks.remove(it); release(it) }; return@launch }
            loopTransitionInProgress = false
            if (track != null) { status = plan.loops[index].label; currentSongIndex = null }
            else status = "This Android device can't play this audio format. Try MP3, WAV, or AAC."
            onChange()
        }
    }

    fun togglePad(plan: ServicePlan, index: Int, onChange: () -> Unit) {
        if (tracks.any { it.group == "loops" } || loopTransitionInProgress) { status = "Stop the service loop before playing pads"; onChange(); return }
        if (isLoadedTrack("pads", index)) {
            padSwitchGeneration++
            padSwitchJob?.cancel()
            stopGroup("pads", index, plan.fadeSeconds, onChange)
            return
        }
        val path = plan.pads.getOrNull(index)?.path ?: return
        padSwitchJob?.cancel()
        val generation = ++padSwitchGeneration
        padSwitchJob = scope.launch {
            val previousPads = tracks.filter { it.group == "pads" }
            if (previousPads.isNotEmpty()) fadeAndRemove(previousPads, plan.fadeSeconds)
            if (generation != padSwitchGeneration || tracks.any { it.group == "loops" } || loopTransitionInProgress) return@launch
            if (createTrack("pads", index, path, true, 1f) == null) status = "This Android device can't play this audio format. Try MP3, WAV, or AAC."
            onChange()
        }
    }

    fun toggleCurrentSong(onChange: () -> Unit) {
        val player = currentPlayer() ?: return
        if (player.isPlaying) player.pause() else player.start()
        onChange()
    }
    fun seek(seconds: Int) { currentPlayer()?.let { it.seekTo((seconds * 1000).coerceIn(0, it.duration)) } }
    fun skip(seconds: Int) { currentPlayer()?.let { it.seekTo((it.currentPosition + seconds * 1000).coerceIn(0, it.duration)) } }

    fun fadeAll(seconds: Int, onChange: () -> Unit) {
        transitionGeneration++
        val generation = transitionGeneration
        transitionJob?.cancel()
        loopTransitionInProgress = false
        padSwitchGeneration++
        padSwitchJob?.cancel()
        transitionJob = scope.launch {
            fadeAndRemove(tracks.toList(), seconds)
            if (generation == transitionGeneration) {
                currentSongIndex = null
                status = "Nothing playing"
                onChange()
            }
        }
    }

    fun stopAllNow() {
        transitionGeneration++
        transitionJob?.cancel()
        loopTransitionInProgress = false
        padSwitchGeneration++
        padSwitchJob?.cancel()
        tracks.toList().forEach { tracks.remove(it); release(it) }
        currentSongIndex = null
        status = "Nothing playing"
    }

    fun stopSection(section: ServiceSection) {
        val indices = section.songIndices.toSet()
        val active = tracks.filter { (it.group == "songs" && it.index in indices) || (it.group == "loops" && it.index == section.loopIndex) }
        active.forEach { tracks.remove(it); release(it) }
        if (currentSongIndex?.let { it in indices } == true) currentSongIndex = null
        if (tracks.isEmpty()) status = "Nothing playing"
    }

    private fun stopGroup(group: String, index: Int, seconds: Int, changed: () -> Unit) {
        scope.launch {
            val found = tracks.filter { it.group == group && it.index == index }
            fadeAndRemove(found, seconds)
            if (group == "songs" && currentSongIndex == index) currentSongIndex = null
            if (tracks.isEmpty()) status = "Nothing playing"
            changed()
        }
    }

    private suspend fun createTrack(group: String, index: Int, path: String, looping: Boolean, volume: Float): PlayingTrack? =
        suspendCancellableCoroutine { continuation ->
            val mp = MediaPlayer()
            fun fail() {
                runCatching { mp.release() }
                if (continuation.isActive) continuation.resume(null)
            }
            mp.setOnPreparedListener { prepared ->
                if (!continuation.isActive) { runCatching { prepared.release() }; return@setOnPreparedListener }
                runCatching {
                    prepared.start()
                    val track = PlayingTrack(group, index, prepared)
                    tracks.add(track)
                    continuation.resume(track)
                }.onFailure { fail() }
            }
            mp.setOnErrorListener { _, _, _ -> fail(); true }
            continuation.invokeOnCancellation { runCatching { mp.release() } }
            runCatching {
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                mp.setDataSource(path)
                mp.isLooping = looping
                mp.setVolume(volume, volume)
                mp.prepareAsync()
            }.onFailure { fail() }
        }

    private suspend fun fadeAndRemove(list: List<PlayingTrack>, seconds: Int) {
        if (list.isEmpty()) return
        val starts = list.map { runCatching { it.player.getVolumeCompat() }.getOrDefault(1f) }
        val steps = maxOf(1, seconds * 20)
        repeat(steps) { step ->
            val level = 1f - (step + 1).toFloat() / steps
            list.forEachIndexed { i, track -> runCatching { track.player.setVolume(starts[i] * level, starts[i] * level) } }
            delay(50.milliseconds)
        }
        list.forEach { tracks.remove(it); release(it) }
    }

    private suspend fun fadePair(old: List<PlayingTrack>, fresh: PlayingTrack, seconds: Int) {
        val steps = maxOf(1, seconds * 20)
        repeat(steps) { step ->
            val p = (step + 1).toFloat() / steps
            old.forEach { t -> runCatching { t.player.setVolume(1f - p, 1f - p) } }
            runCatching { fresh.player.setVolume(p, p) }
            delay(50.milliseconds)
        }
        old.forEach { tracks.remove(it); release(it) }
    }

    private fun currentPlayer() = currentSongIndex?.let { i -> tracks.firstOrNull { it.group == "songs" && it.index == i }?.player }
    private fun nextSongIndex(p: ServicePlan, current: Int): Int? {
        val ordered = p.sections.flatMap { it.songIndices }
        val pos = ordered.indexOf(current)
        if (pos < 0) return null
        return ordered.drop(pos + 1).firstOrNull { p.songs.getOrNull(it)?.loaded == true }
    }
    private fun release(t: PlayingTrack) { runCatching { t.player.stop() }; runCatching { t.player.release() } }
    private fun MediaPlayer.getVolumeCompat() = 1f
}
