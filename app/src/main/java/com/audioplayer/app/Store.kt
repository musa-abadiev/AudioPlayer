package com.audioplayer.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Track(
    val uri: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L,
)

data class Playlist(val id: String, val name: String, val tracks: List<Track>)

data class Bookmark(val positionMs: Long, val label: String, val createdAt: Long)

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uri)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setArtist(artist.ifEmpty { null })
            .build()
    )
    .build()

fun Track.matches(query: String): Boolean =
    query.isBlank() || title.contains(query.trim(), ignoreCase = true) ||
        artist.contains(query.trim(), ignoreCase = true)

private fun Track.toJson(): JSONObject = JSONObject()
    .put("uri", uri).put("title", title).put("artist", artist)
    .put("dur", durationMs).put("added", dateAdded)

private fun JSONObject.toTrack() = Track(
    uri = getString("uri"),
    title = getString("title"),
    artist = optString("artist", ""),
    durationMs = optLong("dur", 0L),
    dateAdded = optLong("added", 0L),
)

private fun tracksToJson(tracks: List<Track>): JSONArray =
    JSONArray().also { arr -> tracks.forEach { arr.put(it.toJson()) } }

private fun jsonToTracks(arr: JSONArray): List<Track> =
    (0 until arr.length()).map { arr.getJSONObject(it).toTrack() }

/** Очередь, плейлисты, позиции остановки, закладки и настройки — в SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("audio_player", Context.MODE_PRIVATE)

    init {
        migrateOldList()
    }

    /** Список из первой версии приложения переносим в плейлист «Мой список». */
    private fun migrateOldList() {
        val old = prefs.getString("tracks", null) ?: return
        val tracks = jsonToTracks(JSONArray(old))
        if (tracks.isNotEmpty()) {
            val list = loadPlaylists() + Playlist(newId(), "Мой список", tracks)
            savePlaylists(list)
            if (loadQueue().isEmpty()) saveQueue(tracks)
            saveExtraTracks((loadExtraTracks() + tracks).distinctBy { it.uri })
        }
        prefs.edit().remove("tracks").apply()
    }

    fun newId(): String = System.currentTimeMillis().toString(36) + (0..999).random()

    // Текущая очередь воспроизведения
    fun loadQueue(): List<Track> = jsonToTracks(JSONArray(prefs.getString("queue", null) ?: "[]"))
    fun saveQueue(tracks: List<Track>) =
        prefs.edit().putString("queue", tracksToJson(tracks).toString()).apply()

    // Файлы, добавленные вручную (не найденные автоматически)
    fun loadExtraTracks(): List<Track> = jsonToTracks(JSONArray(prefs.getString("extra", null) ?: "[]"))
    fun saveExtraTracks(tracks: List<Track>) =
        prefs.edit().putString("extra", tracksToJson(tracks).toString()).apply()

    fun loadPlaylists(): List<Playlist> {
        val arr = JSONArray(prefs.getString("playlists", null) ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Playlist(o.getString("id"), o.getString("name"), jsonToTracks(o.getJSONArray("tracks")))
        }
    }

    fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("tracks", tracksToJson(it.tracks)))
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    fun getPosition(uri: String): Long = prefs.getLong("pos_$uri", 0L)
    fun setPosition(uri: String, ms: Long) = prefs.edit().putLong("pos_$uri", ms).apply()
    fun clearPosition(uri: String) = prefs.edit().remove("pos_$uri").apply()

    fun loadBookmarks(uri: String): List<Bookmark> {
        val arr = JSONArray(prefs.getString("bm_$uri", null) ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Bookmark(o.getLong("pos"), o.optString("label", ""), o.getLong("at"))
        }.sortedBy { it.positionMs }
    }

    fun saveBookmarks(uri: String, list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("pos", it.positionMs).put("label", it.label).put("at", it.createdAt))
        }
        prefs.edit().putString("bm_$uri", arr.toString()).apply()
    }

    var lastIndex: Int
        get() = prefs.getInt("last_index", 0)
        set(v) = prefs.edit().putInt("last_index", v).apply()

    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(v) = prefs.edit().putFloat("speed", v).apply()

    var sortByDate: Boolean
        get() = prefs.getBoolean("sort_by_date", false)
        set(v) = prefs.edit().putBoolean("sort_by_date", v).apply()
}

/** Общие для экрана и сервиса настройки: таймер сна и повтор A–B. */
object PlaybackExtras {
    /** Момент срабатывания таймера (SystemClock.elapsedRealtime), 0 — выключен. */
    val sleepEndAt = MutableStateFlow(0L)
    val sleepAtTrackEnd = MutableStateFlow(false)
    val abStart = MutableStateFlow(-1L)
    val abEnd = MutableStateFlow(-1L)

    fun clearAb() {
        abStart.value = -1L
        abEnd.value = -1L
    }

    fun cancelSleep() {
        sleepEndAt.value = 0L
        sleepAtTrackEnd.value = false
    }
}

fun Context.displayName(uri: Uri): String {
    var name: String? = null
    try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
    } catch (_: Exception) {
    }
    val n = name ?: uri.lastPathSegment ?: "Аудио"
    return n.substringBeforeLast('.', n)
}

fun formatTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun speedLabel(speed: Float): String {
    val whole = speed.toInt()
    return if (speed == whole.toFloat()) "$whole×" else "$speed×"
}
