package com.audioplayer.app

import android.Manifest
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audioplayer.app.ui.AppActions
import com.audioplayer.app.ui.AppTheme
import com.audioplayer.app.ui.MainScreen
import kotlinx.coroutines.launch
import java.util.concurrent.Future

class MainActivity : ComponentActivity() {

    private lateinit var store: Store
    private lateinit var library: Library
    private val controller = mutableStateOf<MediaController?>(null)
    private var controllerFuture: Future<MediaController>? = null
    private var pendingUris: List<Uri> = emptyList()

    private val pickAudio =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                library.addExtras(uris)
                Toast.makeText(this, "Добавлено файлов: ${uris.size}", Toast.LENGTH_SHORT).show()
            }
        }

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            rescan()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        library = Library(this, store)
        library.checkPermission()

        val needed = buildList {
            if (!library.hasPermission.value) add(library.audioPermission)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())

        handleIntent(intent)

        val actions = AppActions(
            onAddFiles = { pickAudio.launch(arrayOf("audio/*")) },
            onRequestPermission = { permissions.launch(arrayOf(library.audioPermission)) },
            onRefresh = ::rescan,
            onPlay = ::playList,
            onShare = ::shareTrack,
        )

        setContent {
            AppTheme {
                MainScreen(
                    player = controller.value,
                    library = library,
                    store = store,
                    actions = actions,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        rescan()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = try {
                future.get()
            } catch (e: Exception) {
                null
            } ?: return@addListener
            onControllerReady(c)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller.value = null
        super.onStop()
    }

    private fun rescan() {
        lifecycleScope.launch { library.scan() }
    }

    private fun onControllerReady(c: MediaController) {
        // Восстанавливаем прошлую очередь, если сервис запущен заново
        if (c.mediaItemCount == 0) {
            val queue = store.loadQueue()
            if (queue.isNotEmpty()) {
                val index = store.lastIndex.coerceIn(0, queue.size - 1)
                c.setMediaItems(queue.map { it.toMediaItem() }, index, store.getPosition(queue[index].uri))
                c.prepare()
            }
        }
        controller.value = c
        if (pendingUris.isNotEmpty()) {
            val uris = pendingUris
            pendingUris = emptyList()
            playUris(uris)
        }
    }

    /** Играть список, начиная с трека [index]. Повторное нажатие на играющий трек — пауза. */
    private fun playList(list: List<Track>, index: Int) {
        val c = controller.value ?: return
        val track = list.getOrNull(index) ?: return
        if (c.currentMediaItem?.mediaId == track.uri) {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.isPlaying) c.pause() else c.play()
            return
        }
        store.saveQueue(list)
        c.setMediaItems(list.map { it.toMediaItem() }, index, store.getPosition(track.uri))
        c.prepare()
        c.play()
    }

    private fun playUris(uris: List<Uri>) {
        val tracks = library.addExtras(uris)
        if (tracks.isNotEmpty()) playList(tracks, 0)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        intent.action = null // не обрабатывать повторно при повороте экрана
        if (controller.value != null) playUris(uris) else pendingUris = uris
    }

    private fun shareTrack(track: Track) {
        val uri = Uri.parse(track.uri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, track.title)
            clipData = ClipData.newRawUri(track.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, "Отправить «${track.title}»"))
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось отправить файл", Toast.LENGTH_SHORT).show()
        }
    }
}
