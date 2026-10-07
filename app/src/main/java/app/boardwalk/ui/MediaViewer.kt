package app.boardwalk.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.boardwalk.GalleryData
import app.boardwalk.NetworkBundle
import app.boardwalk.data.Post
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun MediaViewer(gallery: GalleryData, network: NetworkBundle, close: () -> Unit, save: (Uri, Post, String) -> Unit, saveMessage: String?) {
    BackHandler(onBack = close)
    val context = LocalContext.current
    var pickerError by remember { mutableStateOf<String?>(null) }
    var saveTarget by remember { mutableStateOf<Post?>(null) }
    val saveDocument = rememberLauncherForActivityResult(LocalMediaDocument()) { uri ->
        val post = saveTarget
        if (uri != null && post != null) save(uri, post, gallery.board)
        saveTarget = null
    }
    val window = (context as? Activity)?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val statusWasLight = controller?.isAppearanceLightStatusBars ?: false
        val navWasLight = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = statusWasLight
            controller?.isAppearanceLightNavigationBars = navWasLight
        }
    }
    val pager = rememberPagerState(initialPage = gallery.posts.indexOfFirst { it.id == gallery.startId }.coerceAtLeast(0)) { gallery.posts.size }
    var zoomed by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var viewerHeight by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { zoomed = false; dragY = 0f }
    val dismissDrag = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) }
    Box(Modifier.fillMaxSize().onSizeChanged { viewerHeight = it.height }
        .background(Color.Black.copy(alpha = (1f - dragY / viewerHeight.coerceAtLeast(1) * .8f).coerceIn(.2f, 1f)))
        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
        .semantics { contentDescription = "Full-screen media viewer" }) {
        Column(Modifier.fillMaxSize().graphicsLayer {
            translationY = dragY
            val shrink = (1f - dragY / viewerHeight.coerceAtLeast(1) * .15f).coerceIn(.85f, 1f)
            scaleX = shrink; scaleY = shrink
        }.draggable(dismissDrag, orientation = Orientation.Vertical, enabled = !zoomed,
            onDragStopped = { velocity ->
                if (dragY > viewerHeight * .16f || velocity > 1400f) close()
                else animate(dragY, 0f) { value, _ -> dragY = value }
            }).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Close image viewer", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("/${gallery.board}/", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("${pager.currentPage + 1} of ${gallery.posts.size}${if (gallery.loading) " · loading thread images" else ""}",
                        color = Color(0xFFC9CFC9), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = {
                    gallery.posts.getOrNull(pager.currentPage)?.let { post ->
                        saveTarget = post
                        try { saveDocument.launch(post.filename.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(100).ifBlank { post.id.toString() } + post.extension) }
                        catch (_: android.content.ActivityNotFoundException) { pickerError = "No local file picker is installed on this device." }
                    }
                }) { Icon(Icons.Outlined.Download, "Save attachment to device", tint = Color.White) }
                if (zoomed) Text("Zoomed", color = Color(0xFFC9CFC9), modifier = Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelMedium)
            }
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, key = { gallery.posts[it].id },
                modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val post = gallery.posts[page]
                when (post.extension) {
                    ".webm" -> if (page == pager.currentPage) VideoPage(post.media(gallery.board)!!, network) else Box(Modifier.fillMaxSize())
                    ".pdf" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PDF attachment", color = Color.White)
                        Text("Opens using your browser’s connection", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { context.openWebsite(post.media(gallery.board)!!) }) { Text("Open PDF", color = Color.White) }
                    }
                    else -> ZoomImage(post, gallery.board, network, active = page == pager.currentPage, onZoom = { if (page == pager.currentPage) zoomed = it })
                }
            }
            if (pickerError != null) Text(pickerError!!, Modifier.padding(horizontal = 20.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            if (saveMessage != null) Text(saveMessage, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            if (gallery.error != null) Text("Couldn’t load the rest of this thread’s images. Reopen the viewer to retry.",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = Color(0xFFFFDAD6), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Icon(Icons.Outlined.ChevronLeft, "Previous image", tint = if (pager.currentPage > 0) Color.White else Color.DarkGray)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(gallery.posts.getOrNull(pager.currentPage)?.filename.orEmpty(), color = Color.White,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(when (gallery.posts.getOrNull(pager.currentPage)?.extension) {
                        ".webm" -> "Swipe down to close"; ".pdf" -> "Open the attachment in your browser"; else -> "Pinch to zoom · swipe down to close"
                    }, color = Color(0xFFC9CFC9), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(enabled = pager.currentPage < gallery.posts.lastIndex, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Icon(Icons.Outlined.ChevronRight, "Next image", tint = if (pager.currentPage < gallery.posts.lastIndex) Color.White else Color.DarkGray)
                }
            }
        }
    }
}

@Composable
private fun ZoomImage(post: Post, board: String, network: NetworkBundle, active: Boolean, onZoom: (Boolean) -> Unit) {
    var scale by remember(post.id) { mutableFloatStateOf(1f) }
    var offset by remember(post.id) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero } }
    val context = LocalContext.current
    fun clamp(value: Offset, zoom: Float): Offset {
        if (size.width == 0 || size.height == 0) return Offset.Zero
        val imageWidth = post.width.coerceAtLeast(1).toFloat()
        val imageHeight = post.height.coerceAtLeast(1).toFloat()
        val fit = min(size.width / imageWidth, size.height / imageHeight)
        val x = ((imageWidth * fit * zoom - size.width) / 2).coerceAtLeast(0f)
        val y = ((imageHeight * fit * zoom - size.height) / 2).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = clamp(offset + pan, scale)
        onZoom(scale > 1.01f)
    }
    Box(Modifier.fillMaxSize().then(if (active) Modifier.testTag("zoomable-image") else Modifier)
        .clipToBounds().onSizeChanged { size = it }
        .pointerInput(post.id) {
            detectTapGestures(onDoubleTap = { tap ->
                scale = if (scale > 1f) 1f else 3f
                offset = if (scale == 1f) Offset.Zero else clamp(
                    Offset(size.width / 2f - tap.x, size.height / 2f - tap.y) * (scale - 1), scale)
                onZoom(scale > 1f)
            })
        }.transformable(state = transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = remember(post.id, retry, active) {
                ImageRequest.Builder(context).data(if (active) post.media(board) else post.thumbnail(board))
                    .size(if (active) 4096 else 256).setParameter("retry", retry).build()
            },
            imageLoader = network.images, contentDescription = "${post.filename}, full-size image",
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
            error = {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Image couldn’t load", color = Color.White)
                    Text("It may have expired, or your connection is unavailable.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { retry++ }) { Text("Retry", color = Color.White) }
                }
            },
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(url: String, network: NetworkBundle) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(url) { mutableStateOf(false) }
    val player = remember(url, network) {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(network.client))).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = false
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        val listener = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failed = true } }
        player.addListener(listener); lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } },
            update = { it.player = player }, modifier = Modifier.fillMaxSize())
        if (failed) Column(Modifier.align(Alignment.Center).background(Color.Black).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Video couldn’t load", color = Color.White)
            TextButton(onClick = { failed = false; player.prepare() }) { Text("Retry", color = Color.White) }
        }
    }
}
