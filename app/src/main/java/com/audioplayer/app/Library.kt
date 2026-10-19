package com.audioplayer.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Медиатека: все аудиофайлы телефона, добавленные вручную файлы и плейлисты. */
class Library(private val context: Context, private val store: Store) {

    val scanned = mutableStateOf<List<Track>>(emptyList())
    val extras = mutableStateOf(store.loadExtraTracks())
    val playlists = mutableStateOf(store.loadPlaylists())
    val hasPermission = mutableStateOf(false)
    val scanning = mutableStateOf(false)
    val sortByDate = mutableStateOf(store.sortByDate)

    val audioPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun checkPermission() {
        hasPermission.value = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Все треки: найденные на телефоне + добавленные вручную, без повторов. */
    fun allTracks(): List<Track> {
        val found = scanned.value
        val foundUris = found.map { it.uri }.toHashSet()
        val combined = found + extras.value.filter { it.uri !in foundUris }
        return if (sortByDate.value) combined.sortedByDescending { it.dateAdded }
        else combined.sortedBy { it.title.lowercase() }
    }

    fun setSortByDate(v: Boolean) {
        sortByDate.value = v
        store.sortByDate = v
    }

    suspend fun scan() {
        checkPermission()
        if (!hasPermission.value) return
        scanning.value = true
        scanned.value = withContext(Dispatchers.IO) { queryMediaStore() }
        scanning.value = false
    }

    private fun queryMediaStore(): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        // Не показываем звуки уведомлений и будильников
        val selection = "(${MediaStore.Audio.Media.IS_NOTIFICATION} IS NULL OR " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0) AND " +
            "(${MediaStore.Audio.Media.IS_ALARM} IS NULL OR ${MediaStore.Audio.Media.IS_ALARM} = 0)"

        val result = ArrayList<Track>()
        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val artist = (c.getString(artistCol) ?: "").let { if (it == "<unknown>") "" else it }
                    result += Track(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        title = c.getString(titleCol) ?: "Аудио $id",
                        artist = artist,
                        durationMs = c.getLong(durCol),
                        dateAdded = c.getLong(dateCol),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /** Добавить файлы, выбранные вручную или открытые из другого приложения. */
    fun addExtras(uris: List<Uri>): List<Track> {
        val now = System.currentTimeMillis() / 1000
        val added = uris.map { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            val s = uri.toString()
            extras.value.firstOrNull { it.uri == s }
                ?: scanned.value.firstOrNull { it.uri == s }
                ?: Track(s, context.displayName(uri), dateAdded = now)
        }
        val newOnes = added.filter { t -> extras.value.none { it.uri == t.uri } && scanned.value.none { it.uri == t.uri } }
        if (newOnes.isNotEmpty()) {
            extras.value = (extras.value + newOnes).distinctBy { it.uri }
            store.saveExtraTracks(extras.value)
        }
        return added
    }

    fun isExtra(track: Track) = extras.value.any { it.uri == track.uri }

    fun removeExtra(track: Track) {
        extras.value = extras.value.filter { it.uri != track.uri }
        store.saveExtraTracks(extras.value)
    }

    // ---------- Плейлисты ----------

    private fun updatePlaylists(list: List<Playlist>) {
        playlists.value = list
        store.savePlaylists(list)
    }

    fun playlist(id: String?): Playlist? = playlists.value.firstOrNull { it.id == id }

    fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Playlist {
        val p = Playlist(store.newId(), name.trim().ifEmpty { "Плейлист" }, tracks.distinctBy { it.uri })
        updatePlaylists(playlists.value + p)
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        updatePlaylists(playlists.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it })
    }

    fun deletePlaylist(id: String) {
        updatePlaylists(playlists.value.filter { it.id != id })
    }

    /** Возвращает, сколько треков действительно добавлено (без повторов). */
    fun addToPlaylist(id: String, tracks: List<Track>): Int {
        var added = 0
        updatePlaylists(playlists.value.map { p ->
            if (p.id != id) p else {
                val have = p.tracks.map { it.uri }.toHashSet()
                val fresh = tracks.filter { it.uri !in have }.distinctBy { it.uri }
                added = fresh.size
                p.copy(tracks = p.tracks + fresh)
            }
        })
        return added
    }

    fun removeFromPlaylist(id: String, uri: String) {
        updatePlaylists(playlists.value.map { p ->
            if (p.id == id) p.copy(tracks = p.tracks.filter { it.uri != uri }) else p
        })
    }

    fun moveInPlaylist(id: String, from: Int, to: Int) {
        updatePlaylists(playlists.value.map { p ->
            if (p.id != id || from !in p.tracks.indices || to !in p.tracks.indices) p else {
                val list = p.tracks.toMutableList()
                list.add(to, list.removeAt(from))
                p.copy(tracks = list)
            }
        })
    }
}
