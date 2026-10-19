package com.audioplayer.app.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.audioplayer.app.Bookmark
import com.audioplayer.app.PlaybackExtras
import com.audioplayer.app.Store
import com.audioplayer.app.formatTime
import com.audioplayer.app.speedLabel
import kotlinx.coroutines.delay

data class PlayerUi(
    val isPlaying: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val position: Long = 0L,
    val duration: Long = 0L,
    val speed: Float = 1f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
)

private fun Player.snapshot() = PlayerUi(
    isPlaying = isPlaying,
    mediaId = currentMediaItem?.mediaId,
    title = currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
    position = currentPosition,
    duration = duration.coerceAtLeast(0L),
    speed = playbackParameters.speed,
    repeatMode = repeatMode,
    shuffle = shuffleModeEnabled,
)

@Composable
fun rememberPlayerUi(player: Player?): PlayerUi {
    var ui by remember { mutableStateOf(PlayerUi()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                ui = player.snapshot()
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            ui = player.snapshot()
            delay(300)
        }
    }
    return ui
}

fun Player.togglePlay(isPlayingNow: Boolean) {
    if (isPlayingNow) {
        pause()
    } else {
        if (playbackState == Player.STATE_IDLE) prepare()
        if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
        play()
    }
}

/** Свёрнутый плеер внизу экрана. */
@Composable
fun MiniPlayer(player: Player, ui: PlayerUi, onExpand: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ui.title.ifEmpty { "—" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${formatTime(ui.position)} / ${formatTime(ui.duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { player.togglePlay(ui.isPlaying) }) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ui.isPlaying) "Пауза" else "Играть"
                    )
                }
                IconButton(onClick = { player.seekToNext() }) {
                    Icon(Icons.Default.SkipNext, "Следующий")
                }
                IconButton(onClick = onExpand) {
                    Icon(Icons.Default.KeyboardArrowUp, "Развернуть плеер")
                }
            }
        }
    }
}

/** Развёрнутый плеер со всеми кнопками. */
@Composable
fun PlayerPanel(
    player: Player,
    ui: PlayerUi,
    onCollapse: () -> Unit,
    onSpeed: () -> Unit,
    onBookmarks: () -> Unit,
    onShare: () -> Unit,
) {
    val abA by PlaybackExtras.abStart.collectAsState()
    val abB by PlaybackExtras.abEnd.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.title.ifEmpty { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, "Свернуть плеер")
                }
            }

            val dur = ui.duration.toFloat().coerceAtLeast(1f)
            Slider(
                value = (dragging ?: ui.position.toFloat()).coerceIn(0f, dur),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { player.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..dur,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(dragging?.toLong() ?: ui.position),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.weight(1f))
                if (abA >= 0) {
                    val abText = "A ${formatTime(abA)}" + if (abB >= 0) "  →  B ${formatTime(abB)}" else ""
                    Text(abText, style = MaterialTheme.typography.labelSmall, color = primary)
                }
                Spacer(Modifier.weight(1f))
                Text(formatTime(ui.duration), style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.shuffleModeEnabled = !ui.shuffle }) {
                    Icon(Icons.Default.Shuffle, "Перемешать", tint = if (ui.shuffle) primary else muted)
                }
                IconButton(onClick = { player.seekToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, "Предыдущий")
                }
                IconButton(onClick = { player.seekBack() }) {
                    Icon(Icons.Default.Replay10, "Назад 10 секунд")
                }
                FilledIconButton(
                    onClick = { player.togglePlay(ui.isPlaying) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ui.isPlaying) "Пауза" else "Играть",
                        Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { player.seekForward() }) {
                    Icon(Icons.Default.Forward10, "Вперёд 10 секунд")
                }
                IconButton(onClick = { player.seekToNext() }) {
                    Icon(Icons.Default.SkipNext, "Следующий")
                }
                IconButton(onClick = {
                    player.repeatMode = when (ui.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }) {
                    Icon(
                        if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Повтор",
                        tint = if (ui.repeatMode == Player.REPEAT_MODE_OFF) muted else primary
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onSpeed) { Text("Скорость ${speedLabel(ui.speed)}") }
                TextButton(onClick = {
                    val pos = player.currentPosition
                    when {
                        abA < 0 -> PlaybackExtras.abStart.value = pos
                        abB < 0 -> if (pos > abA + 500) PlaybackExtras.abEnd.value = pos else PlaybackExtras.clearAb()
                        else -> PlaybackExtras.clearAb()
                    }
                }) {
                    Text(
                        when {
                            abA < 0 -> "A–B"
                            abB < 0 -> "Точка B"
                            else -> "Сброс A–B"
                        },
                        color = if (abA >= 0) primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onBookmarks) { Icon(Icons.Default.Bookmarks, "Закладки") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Поделиться") }
            }
        }
    }
}

@Composable
fun SpeedDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Скорость воспроизведения") },
        text = {
            Column {
                speeds.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(s) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == s, onClick = null)
                        Spacer(Modifier.size(12.dp))
                        Text(speedLabel(s))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
fun SleepDialog(active: Boolean, onDismiss: () -> Unit) {
    val minutes = listOf(5, 10, 15, 20, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(minutes) { m ->
                    Text(
                        "$m мин",
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlaybackExtras.sleepAtTrackEnd.value = false
                                PlaybackExtras.sleepEndAt.value = SystemClock.elapsedRealtime() + m * 60_000L
                                onDismiss()
                            }
                            .padding(vertical = 12.dp)
                    )
                }
                item {
                    Text(
                        "До конца текущего трека",
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlaybackExtras.sleepEndAt.value = 0L
                                PlaybackExtras.sleepAtTrackEnd.value = true
                                onDismiss()
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = { PlaybackExtras.cancelSleep(); onDismiss() }) { Text("Выключить таймер") }
            } else {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

@Composable
fun BookmarksDialog(
    store: Store,
    mediaId: String,
    currentPosition: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var list by remember(mediaId) { mutableStateOf(store.loadBookmarks(mediaId)) }
    var label by remember { mutableStateOf("") }
    val positionAtOpen = remember(mediaId) { currentPosition }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Закладки") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val b = Bookmark(positionAtOpen, label.trim(), System.currentTimeMillis())
                        list = (list + b).sortedBy { it.positionMs }
                        store.saveBookmarks(mediaId, list)
                        label = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Добавить на ${formatTime(positionAtOpen)}")
                }
                Spacer(Modifier.size(12.dp))
                if (list.isEmpty()) {
                    Text("Закладок пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(list, key = { it.createdAt }) { b ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeek(b.positionMs); onDismiss() }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatTime(b.positionMs),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(72.dp)
                                )
                                Text(b.label.ifEmpty { "Закладка" }, Modifier.weight(1f))
                                IconButton(onClick = {
                                    list = list - b
                                    store.saveBookmarks(mediaId, list)
                                }) {
                                    Icon(Icons.Default.Delete, "Удалить закладку")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
