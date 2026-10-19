package com.audioplayer.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.audioplayer.app.Library
import com.audioplayer.app.PlaybackExtras
import com.audioplayer.app.Playlist
import com.audioplayer.app.Store
import com.audioplayer.app.Track
import com.audioplayer.app.formatTime
import com.audioplayer.app.matches

data class AppActions(
    val onAddFiles: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (List<Track>, Int) -> Unit,
    val onShare: (Track) -> Unit,
)

class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Запрос имени плейлиста: создать новый (playlistId = null) или переименовать. */
private data class NameRequest(val playlistId: String?, val initial: String, val tracks: List<Track>)

fun tracksCount(n: Int): String {
    val word = when {
        n % 100 in 11..14 -> "треков"
        n % 10 == 1 -> "трек"
        n % 10 in 2..4 -> "трека"
        else -> "треков"
    }
    return "$n $word"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(player: Player?, library: Library, store: Store, actions: AppActions) {
    val context = LocalContext.current
    val ui = rememberPlayerUi(player)
    val sleepEnd by PlaybackExtras.sleepEndAt.collectAsState()
    val sleepAtEnd by PlaybackExtras.sleepAtTrackEnd.collectAsState()

    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerFor by rememberSaveable { mutableStateOf<String?>(null) }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }

    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf<Track?>(null) }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    val all = remember(library.scanned.value, library.extras.value, library.sortByDate.value) {
        library.allTracks()
    }
    val playlists = library.playlists.value
    val openPlaylist = library.playlist(openId)
    val picker = pickerFor?.let { library.playlist(it) }

    fun closePlaylist() {
        openId = null
        query = ""
    }

    fun closePicker() {
        pickerFor = null
        query = ""
    }

    BackHandler(enabled = openPlaylist != null && picker == null) { closePlaylist() }
    BackHandler(enabled = picker != null) { closePicker() }
    BackHandler(enabled = panelExpanded) { panelExpanded = false }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (picker != null || openPlaylist != null) {
                        IconButton(onClick = { if (picker != null) closePicker() else closePlaylist() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    }
                },
                title = {
                    Text(
                        when {
                            picker != null -> "Добавить в «${picker.name}»"
                            openPlaylist != null -> openPlaylist.name
                            else -> "Аудиоплеер"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (picker == null) {
                        val sleepLabel = when {
                            sleepAtEnd -> "конец"
                            sleepEnd > 0 -> formatTime(sleepEnd - SystemClock.elapsedRealtime())
                            else -> null
                        }
                        if (sleepLabel != null) {
                            Text(
                                sleepLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showSleep = true }) {
                            Icon(
                                Icons.Default.Bedtime, "Таймер сна",
                                tint = if (sleepLabel != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "Меню")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (openPlaylist != null) {
                                    DropdownMenuItem(
                                        text = { Text("Переименовать") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = {
                                            showMenu = false
                                            nameRequest = NameRequest(openPlaylist.id, openPlaylist.name, emptyList())
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить плейлист") },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                        onClick = { showMenu = false; deleteId = openPlaylist.id }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Добавить файлы вручную") },
                                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                        onClick = { showMenu = false; actions.onAddFiles() }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (library.sortByDate.value) "Сортировать по названию" else "Сначала новые")
                                        },
                                        leadingIcon = { Icon(Icons.Default.Sort, null) },
                                        onClick = {
                                            showMenu = false
                                            library.setSortByDate(!library.sortByDate.value)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Обновить список") },
                                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                        onClick = { showMenu = false; actions.onRefresh() }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (player != null && ui.mediaId != null && picker == null) {
                if (panelExpanded) {
                    PlayerPanel(
                        player = player,
                        ui = ui,
                        onCollapse = { panelExpanded = false },
                        onSpeed = { showSpeed = true },
                        onBookmarks = { showBookmarks = true },
                        onShare = {
                            val t = all.firstOrNull { it.uri == ui.mediaId }
                                ?: playlists.flatMap { it.tracks }.firstOrNull { it.uri == ui.mediaId }
                            t?.let(actions.onShare)
                        },
                    )
                } else {
                    MiniPlayer(player, ui, onExpand = { panelExpanded = true })
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchField(
                query = query,
                onChange = { query = it },
                placeholder = when {
                    picker != null || openPlaylist != null || tab == 0 -> "Поиск по названию"
                    else -> "Поиск плейлистов и треков"
                }
            )
            if (picker == null && openPlaylist == null) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Все аудио") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Плейлисты") })
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    picker != null -> TrackPicker(
                        all = all,
                        query = query,
                        already = picker.tracks.map { it.uri }.toSet(),
                        onSelectAll = { it },
                        onDone = { selected ->
                            val n = library.addToPlaylist(picker.id, selected)
                            Toast.makeText(context, "Добавлено: ${tracksCount(n)}", Toast.LENGTH_SHORT).show()
                            closePicker()
                        }
                    )

                    openPlaylist != null -> PlaylistDetail(
                        playlist = openPlaylist,
                        query = query,
                        ui = ui,
                        store = store,
                        onPlay = actions.onPlay,
                        onShuffle = {
                            if (openPlaylist.tracks.isNotEmpty()) {
                                player?.shuffleModeEnabled = true
                                actions.onPlay(openPlaylist.tracks, openPlaylist.tracks.indices.random())
                            }
                        },
                        onAdd = { pickerFor = openPlaylist.id; query = "" },
                        onShare = actions.onShare,
                        onMove = { from, to -> library.moveInPlaylist(openPlaylist.id, from, to) },
                        onRemove = { t -> library.removeFromPlaylist(openPlaylist.id, t.uri) },
                    )

                    tab == 0 -> AllAudioList(
                        all = all,
                        query = query,
                        hasPermission = library.hasPermission.value,
                        scanning = library.scanning.value,
                        ui = ui,
                        store = store,
                        isExtra = library::isExtra,
                        onRequestPermission = actions.onRequestPermission,
                        onAddFiles = actions.onAddFiles,
                        onPlay = actions.onPlay,
                        onAddToPlaylist = { addToPlaylist = it },
                        onShare = actions.onShare,
                        onRemoveExtra = library::removeExtra,
                    )

                    else -> PlaylistsList(
                        playlists = playlists,
                        query = query,
                        onOpen = { openId = it.id; query = "" },
                        onPlay = { p -> if (p.tracks.isNotEmpty()) actions.onPlay(p.tracks, 0) },
                        onNew = { nameRequest = NameRequest(null, "", emptyList()) },
                    )
                }
            }
        }
    }

    // ---------- Диалоги ----------

    if (showSpeed && player != null) {
        SpeedDialog(
            current = ui.speed,
            onPick = { s -> player.setPlaybackSpeed(s); store.speed = s; showSpeed = false },
            onDismiss = { showSpeed = false }
        )
    }
    if (showSleep) {
        SleepDialog(active = sleepEnd > 0 || sleepAtEnd, onDismiss = { showSleep = false })
    }
    val bookmarkId = ui.mediaId
    if (showBookmarks && player != null && bookmarkId != null) {
        BookmarksDialog(
            store = store,
            mediaId = bookmarkId,
            currentPosition = ui.position,
            onSeek = { player.seekTo(it); if (!player.isPlaying) player.play() },
            onDismiss = { showBookmarks = false }
        )
    }

    addToPlaylist?.let { track ->
        AlertDialog(
            onDismissRequest = { addToPlaylist = null },
            title = { Text("Добавить в плейлист") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    addToPlaylist = null
                                    nameRequest = NameRequest(null, "", listOf(track))
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(12.dp))
                            Text("Новый плейлист…", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    items(playlists, key = { it.id }) { p ->
                        val inside = p.tracks.any { it.uri == track.uri }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !inside) {
                                    library.addToPlaylist(p.id, listOf(track))
                                    Toast.makeText(context, "Добавлено в «${p.name}»", Toast.LENGTH_SHORT).show()
                                    addToPlaylist = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LibraryMusic, null)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name)
                                Text(
                                    if (inside) "Уже здесь" else tracksCount(p.tracks.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addToPlaylist = null }) { Text("Отмена") } }
        )
    }

    nameRequest?.let { req ->
        var name by remember(req) { mutableStateOf(req.initial) }
        AlertDialog(
            onDismissRequest = { nameRequest = null },
            title = { Text(if (req.playlistId == null) "Новый плейлист" else "Переименовать") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    placeholder = { Text("Например: Лекции, Коран, Мавлиды") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (req.playlistId == null) {
                            val p = library.createPlaylist(name, req.tracks)
                            if (req.tracks.isNotEmpty()) {
                                Toast.makeText(context, "Добавлено в «${p.name}»", Toast.LENGTH_SHORT).show()
                            } else {
                                openId = p.id
                                query = ""
                            }
                        } else {
                            library.renamePlaylist(req.playlistId, name)
                        }
                        nameRequest = null
                    }
                ) { Text(if (req.playlistId == null) "Создать" else "Сохранить") }
            },
            dismissButton = { TextButton(onClick = { nameRequest = null }) { Text("Отмена") } }
        )
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить плейлист?") },
            text = { Text("Сами аудиофайлы на телефоне останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    library.deletePlaylist(id)
                    deleteId = null
                    closePlaylist()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Clear, "Очистить поиск") }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TrackRow(
    track: Track,
    ui: PlayerUi,
    store: Store,
    onClick: () -> Unit,
    menu: List<MenuAction>,
) {
    val isCurrent = track.uri == ui.mediaId
    val isPlaying = isCurrent && ui.isPlaying
    var open by remember { mutableStateOf(false) }

    val details = buildList {
        if (track.artist.isNotEmpty()) add(track.artist)
        if (track.durationMs > 0) add(formatTime(track.durationMs))
        val saved = if (isCurrent) 0L else store.getPosition(track.uri)
        if (saved > 0) add("остановлено на ${formatTime(saved)}")
    }.joinToString(" · ")
    val supporting: (@Composable () -> Unit)? =
        if (details.isNotEmpty()) {
            { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = if (isCurrent) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            Icon(
                if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = {
            Text(
                track.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null
            )
        },
        supportingContent = supporting,
        trailingContent = {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Действия") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, null) },
                            onClick = { open = false; action.onClick() }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CenterMessage(text: String) {
    Text(
        text,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
    )
}

@Composable
private fun AllAudioList(
    all: List<Track>,
    query: String,
    hasPermission: Boolean,
    scanning: Boolean,
    ui: PlayerUi,
    store: Store,
    isExtra: (Track) -> Boolean,
    onRequestPermission: () -> Unit,
    onAddFiles: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onRemoveExtra: (Track) -> Unit,
) {
    val filtered = remember(all, query) { all.filter { it.matches(query) } }
    LazyColumn(Modifier.fillMaxSize()) {
        if (!hasPermission) {
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Нужен доступ к аудиофайлам", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Чтобы плеер сам показал все записи на телефоне — лекции, уроки, " +
                                "чтение Корана, голосовые сообщения.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(12.dp))
                        Button(onClick = onRequestPermission) { Text("Разрешить доступ") }
                    }
                }
            }
        }
        if (scanning && all.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (filtered.isNotEmpty()) {
            item {
                Text(
                    if (query.isBlank()) "Всего: ${tracksCount(filtered.size)}" else "Найдено: ${tracksCount(filtered.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        itemsIndexed(filtered, key = { _, t -> t.uri }) { index, track ->
            TrackRow(
                track = track,
                ui = ui,
                store = store,
                onClick = { onPlay(filtered, index) },
                menu = buildList {
                    add(MenuAction("В плейлист", Icons.Default.Add) { onAddToPlaylist(track) })
                    add(MenuAction("Поделиться / отправить", Icons.Default.Share) { onShare(track) })
                    if (isExtra(track)) {
                        add(MenuAction("Убрать из списка", Icons.Default.Delete) { onRemoveExtra(track) })
                    }
                }
            )
        }
        if (filtered.isEmpty() && !scanning) {
            item {
                if (query.isNotBlank()) {
                    CenterMessage("По запросу «$query» ничего не найдено")
                } else if (hasPermission) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CenterMessage("Аудиофайлы на телефоне не найдены")
                        OutlinedButton(onClick = onAddFiles) { Text("Выбрать файлы вручную") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsList(
    playlists: List<Playlist>,
    query: String,
    onOpen: (Playlist) -> Unit,
    onPlay: (Playlist) -> Unit,
    onNew: () -> Unit,
) {
    val filtered = playlists.filter { p ->
        query.isBlank() || p.name.contains(query.trim(), ignoreCase = true) || p.tracks.any { it.matches(query) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                modifier = Modifier.clickable(onClick = onNew),
                leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text("Новый плейлист", color = MaterialTheme.colorScheme.primary) }
            )
        }
        items(filtered, key = { it.id }) { p ->
            ListItem(
                modifier = Modifier.clickable { onOpen(p) },
                leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(tracksCount(p.tracks.size)) },
                trailingContent = {
                    IconButton(onClick = { onPlay(p) }, enabled = p.tracks.isNotEmpty()) {
                        Icon(Icons.Default.PlayArrow, "Играть плейлист")
                    }
                }
            )
        }
        if (playlists.isEmpty()) {
            item { CenterMessage("Плейлистов пока нет. Создайте, например, «Лекции», «Коран» или «Мавлиды».") }
        } else if (filtered.isEmpty()) {
            item { CenterMessage("По запросу «$query» ничего не найдено") }
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    query: String,
    ui: PlayerUi,
    store: Store,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: () -> Unit,
    onAdd: () -> Unit,
    onShare: (Track) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Track) -> Unit,
) {
    val filtering = query.isNotBlank()
    val filtered = playlist.tracks.filter { it.matches(query) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onPlay(playlist.tracks, 0) },
                    enabled = playlist.tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.size(4.dp))
                    Text("Играть")
                }
                FilledTonalIconButton(onClick = onShuffle, enabled = playlist.tracks.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, "Вперемешку")
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.size(4.dp))
                    Text("Добавить")
                }
            }
        }
        itemsIndexed(filtered, key = { _, t -> t.uri }) { index, track ->
            val realIndex = playlist.tracks.indexOfFirst { it.uri == track.uri }
            TrackRow(
                track = track,
                ui = ui,
                store = store,
                onClick = { onPlay(filtered, index) },
                menu = buildList {
                    add(MenuAction("Поделиться / отправить", Icons.Default.Share) { onShare(track) })
                    if (!filtering && realIndex > 0) {
                        add(MenuAction("Выше", Icons.Default.KeyboardArrowUp) { onMove(realIndex, realIndex - 1) })
                    }
                    if (!filtering && realIndex < playlist.tracks.size - 1) {
                        add(MenuAction("Ниже", Icons.Default.KeyboardArrowDown) { onMove(realIndex, realIndex + 1) })
                    }
                    add(MenuAction("Убрать из плейлиста", Icons.Default.Delete) { onRemove(track) })
                }
            )
        }
        if (playlist.tracks.isEmpty()) {
            item { CenterMessage("В плейлисте пока нет треков. Нажмите «Добавить».") }
        } else if (filtered.isEmpty()) {
            item { CenterMessage("По запросу «$query» ничего не найдено") }
        }
    }
}

@Composable
private fun TrackPicker(
    all: List<Track>,
    query: String,
    already: Set<String>,
    onSelectAll: (List<Track>) -> List<Track>,
    onDone: (List<Track>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(all, query) { all.filter { it.matches(query) } }
    val selectable = filtered.filter { it.uri !in already }

    Column(Modifier.fillMaxSize()) {
        if (selectable.isNotEmpty()) {
            TextButton(
                onClick = {
                    val uris = onSelectAll(selectable).map { it.uri }
                    selected = if (selected.containsAll(uris)) selected - uris.toSet() else selected + uris
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                val allChosen = selected.containsAll(selectable.map { it.uri })
                Text(if (allChosen) "Снять выбор" else "Выбрать все найденные (${selectable.size})")
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.uri }) { t ->
                val inList = t.uri in already
                val checked = inList || t.uri in selected
                ListItem(
                    modifier = Modifier.clickable(enabled = !inList) {
                        selected = if (t.uri in selected) selected - t.uri else selected + t.uri
                    },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = !inList) },
                    headlineContent = { Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (inList) "Уже в плейлисте"
                            else listOf(t.artist, if (t.durationMs > 0) formatTime(t.durationMs) else "")
                                .filter { it.isNotEmpty() }.joinToString(" · ")
                        )
                    }
                )
            }
            if (filtered.isEmpty()) {
                item { CenterMessage(if (query.isBlank()) "Нет аудиофайлов" else "По запросу «$query» ничего не найдено") }
            }
        }
        Button(
            onClick = { onDone(all.filter { it.uri in selected }) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Добавить: ${tracksCount(selected.size)}")
        }
    }
}
