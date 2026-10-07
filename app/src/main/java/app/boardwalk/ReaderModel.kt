package app.boardwalk

import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.boardwalk.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri

data class GalleryData(val board: String, val threadId: Long, val posts: List<Post>, val startId: Long,
    val loading: Boolean = false, val error: String? = null)
private data class ReaderRoute(val section: String, val board: Board?, val threadId: Long?,
    val catalog: List<Post>, val posts: List<Post>, val offlineSavedAt: Long?)

class ReaderModel(application: android.app.Application) : AndroidViewModel(application) {
    private val app = application as BoardwalkApplication
    val store = app.store
    var network by mutableStateOf(NetworkBundle(app, store.proxy(), app.gate)); private set
    var section by mutableStateOf("Home"); private set
    var board by mutableStateOf<Board?>(null); private set
    var threadId by mutableStateOf<Long?>(null); private set
    var boards by mutableStateOf<List<Board>>(emptyList()); private set
    var catalog by mutableStateOf<List<Post>>(emptyList()); private set
    var home by mutableStateOf<List<FeedThread>>(emptyList()); private set
    var popular by mutableStateOf<List<FeedThread>>(emptyList()); private set
    var homeFailures by mutableStateOf<List<String>>(emptyList()); private set
    var homeProgress by mutableStateOf(0); private set
    val homeBoards get() = HomeFeed.boards(boards, favorites, showAll)
    val homeEntries get() = home.filter { entry -> homeBoards.any { it.id == entry.board.id } }
    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var targetPostId by mutableStateOf<Long?>(null); private set
    val quotePosts = mutableStateMapOf<String, Post?>()
    private val quoteLoading = mutableStateMapOf<String, Boolean>()
    private val routeStack = mutableListOf<ReaderRoute>()
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var gallery by mutableStateOf<GalleryData?>(null); private set
    var favorites by mutableStateOf(store.favorites); private set
    var saved by mutableStateOf(store.saved); private set
    var replyLayout by mutableStateOf(store.replyLayout); private set
    var theme by mutableStateOf(store.theme); private set
    var showAll by mutableStateOf(store.showAll); private set
    var proxy by mutableStateOf(store.proxy()); private set
    var connectionBusy by mutableStateOf(false); private set
    var connectionResult by mutableStateOf<String?>(null); private set
    var restoreGeneration by mutableStateOf(0); private set
    var offlineKeys by mutableStateOf(store.offlineKeys()); private set
    var savingThreads by mutableStateOf<Set<String>>(emptySet()); private set
    var offlineErrors by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var offlineSavedAt by mutableStateOf<Long?>(null); private set
    var backupBusy by mutableStateOf(false); private set
    var backupMessage by mutableStateOf<String?>(null); private set
    var backupConnected by mutableStateOf(store.backupUri != null); private set
    var pendingRestore by mutableStateOf<BackupData?>(null); private set
    var mediaMessage by mutableStateOf<String?>(null); private set
    private val archiveJobs = mutableMapOf<String, Job>()
    private val backupLock = Mutex()
    private var backupJob: Job? = null
    private var restoring = false
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!restoring && key !in setOf("backupUri", "backupLastFile", "backupPreviousFile") && store.backupUri != null) viewModelScope.launch { scheduleBackup() }
    }
    private var loadJob: Job? = null
    private var galleryJob: Job? = null
    private var testJob: Job? = null

    init { store.observe(preferenceListener); loadHome() }
    fun loadHome() = load {
        if (boards.isEmpty()) boards = network.api.boards()
        val selected = homeBoards
        // Shared ApiGate serializes these requests with reader/media requests.
        HomeFeed.refresh(home, selected, { network.api.catalog(it.id) }) { state ->
            home = state.entries; homeFailures = state.failures; homeProgress = state.completed
        }
        val sampled = PopularFeed.sampleBoards(boards, showAll, selected.map { it.id }.toSet())
        PopularFeed.refresh(home, sampled, { network.api.catalog(it.id) }) { entries ->
            popular = PopularFeed.ranked(entries)
        }
    }
    fun openHomeThread(entry: FeedThread) {
        pushRoute()
        board = entry.board; catalog = emptyList()
        showThread(entry.post.id)
    }
    private fun load(block: suspend () -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading = true; error = null
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = readableError(e) }
            finally { loading = false }
        }
    }
    fun loadBoards() = load { boards = network.api.boards() }
    fun openBoard(value: Board) {
        pushRoute()
        board = value; threadId = null; catalog = emptyList(); posts = emptyList()
        targetPostId = null
        load { catalog = network.api.catalog(value.id) }
    }
    fun openThread(id: Long) {
        if (board == null) return
        pushRoute()
        showThread(id)
    }
    private fun showThread(id: Long, target: Long? = null) {
        val b = board ?: return
        threadId = id; posts = emptyList(); offlineSavedAt = null
        targetPostId = target
        load { fetchThread(b.id, id) }
    }
    fun openSaved(value: SavedThread) {
        pushRoute()
        board = boards.find { it.id == value.board } ?: Board(value.board, value.board, false)
        threadId = value.id; posts = emptyList(); offlineSavedAt = null
        targetPostId = null
        load {
            val copy = withContext(Dispatchers.IO) { store.offline(value.board, value.id) }
            if (copy != null) { posts = copy.posts; offlineSavedAt = copy.savedAt }
            else fetchThread(value.board, value.id)
        }
    }
    private suspend fun fetchThread(boardId: String, id: Long) {
        if (posts.isEmpty()) withContext(Dispatchers.IO) { store.offline(boardId, id) }?.let {
            posts = it.posts; offlineSavedAt = it.savedAt
        }
        val fresh = network.api.thread(boardId, id)
        posts = fresh; offlineSavedAt = null
        if (isSaved(boardId, id)) persistThread(boardId, id, fresh)
    }
    private suspend fun persistThread(boardId: String, id: Long, content: List<Post>) {
        if (!isSaved(boardId, id)) return
        val title = saved.first { it.board == boardId && it.id == id }.title
        withContext(Dispatchers.IO) { store.saveOffline(OfflineThread(boardId, id, title, System.currentTimeMillis(), content)) }
        offlineKeys = store.offlineKeys()
    }
    fun switchSection(value: String) {
        closeGallery(); loadJob?.cancel(); loading = false; error = null
        routeStack.clear(); targetPostId = null
        section = value; board = null; threadId = null
        if (value == "Home") loadHome()
        else if (value == "Boards" && boards.isEmpty()) loadBoards()
    }
    fun back() {
        if (gallery != null) { closeGallery(); return }
        loadJob?.cancel(); loading = false; error = null; targetPostId = null
        val route = routeStack.removeLastOrNull()
        if (route != null) {
            section = route.section; board = route.board; threadId = route.threadId
            catalog = route.catalog; posts = route.posts; offlineSavedAt = route.offlineSavedAt
        } else { threadId = null; board = null }
    }
    private fun pushRoute() { routeStack += ReaderRoute(section, board, threadId, catalog, posts, offlineSavedAt) }
    fun consumeTargetPost() { targetPostId = null }
    fun openReaderLink(link: ReaderLink) {
        if (board?.id == link.board && threadId == link.thread) {
            targetPostId = link.post
            return
        }
        val nextBoard = boards.find { it.id == link.board } ?: Board(link.board, "/${link.board}/", false)
        if (link.thread == null) openBoard(nextBoard)
        else {
            pushRoute(); board = nextBoard; catalog = emptyList()
            showThread(link.thread, link.post)
        }
    }
    fun loadQuote(link: ReaderLink) {
        val thread = link.thread ?: return
        val post = link.post ?: return
        val key = "${link.board}:$thread:$post"
        if (key in quotePosts || quoteLoading[key] == true) return
        quoteLoading[key] = true
        viewModelScope.launch {
            try { quotePosts[key] = network.api.thread(link.board, thread).find { it.id == post } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { quotePosts[key] = null }
            finally { quoteLoading.remove(key) }
        }
    }
    fun refresh() {
        val b = board
        val id = threadId
        when {
            b != null && id != null -> load { fetchThread(b.id, id) }
            b != null -> load { catalog = network.api.catalog(b.id) }
            section == "Home" -> loadHome()
            else -> loadBoards()
        }
    }
    fun favorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        store.favorites = favorites
    }
    fun isSaved(boardId: String, id: Long) = saved.any { it.board == boardId && it.id == id }
    fun bookmark(boardId: String, id: Long, title: String) {
        val key = "$boardId:$id"
        if (isSaved(boardId, id)) {
            saved = saved.filterNot { it.board == boardId && it.id == id }; store.saved = saved
            val job = archiveJobs.remove(key); job?.cancel()
            viewModelScope.launch {
                job?.join()
                withContext(Dispatchers.IO) { store.removeOffline(boardId, id) }
                offlineKeys = store.offlineKeys()
            }
        } else {
            saved = listOf(SavedThread(boardId, id, title.take(500))) + saved; store.saved = saved
            downloadThread(boardId, id)
        }
    }
    fun downloadThread(boardId: String, id: Long) {
        val key = "$boardId:$id"
        if (key in savingThreads) return
        val current = if (board?.id == boardId && threadId == id && offlineSavedAt == null) posts else emptyList()
        archiveJobs[key] = viewModelScope.launch {
            savingThreads = savingThreads + key; offlineErrors = offlineErrors - key
            try {
                val content = current.ifEmpty { network.api.thread(boardId, id) }
                persistThread(boardId, id, content)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { offlineErrors = offlineErrors + (key to "Discussion couldn’t be saved. Retry when connected.") }
            finally { savingThreads = savingThreads - key }
        }
    }
    fun chooseBackup(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            store.backupUri = uri.toString(); backupConnected = true; scheduleBackup(immediate = true)
        } catch (e: Exception) { backupMessage = "This location doesn’t allow ongoing access. Choose a file in Documents or Downloads." }
    }
    fun disconnectBackup() {
        backupJob?.cancel(); store.backupUri = null; backupConnected = false
        backupMessage = "Automatic updates stopped. Your existing backup file is kept."
    }
    private fun scheduleBackup(immediate: Boolean = false) {
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            if (!immediate) delay(1500)
            val uri = store.backupUri?.let(Uri::parse) ?: return@launch
            backupBusy = true
            try {
                backupLock.withLock {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val bytes = BackupCodec.encode(store.backup()).toByteArray()
                        require(bytes.size <= BackupCodec.MAX_BYTES)
                        val resolver = app.contentResolver
                        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
                        val created = android.provider.DocumentsContract.createDocument(resolver, parent, "application/json",
                            "Boardwalk-$stamp-${java.util.UUID.randomUUID().toString().take(8)}.json") ?: error("Cannot create backup")
                        try {
                            val out = resolver.openOutputStream(created, "wt") ?: error("No output")
                            out.use { it.write(bytes); it.flush() }
                            // Only rotate completed files created by this app. Never truncate the previous backup.
                            val obsolete = store.rotateBackupFile(created.toString())
                            obsolete?.let { runCatching { android.provider.DocumentsContract.deleteDocument(resolver, Uri.parse(it)) } }
                        } catch (e: Exception) {
                            runCatching { android.provider.DocumentsContract.deleteDocument(resolver, created) }
                            throw e
                        }
                    }
                }
                backupMessage = "Local backup updated. Keep the newest file to restore after reinstall."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { backupMessage = "Backup couldn’t be updated. Choose a writable location or free up storage." }
            finally { backupBusy = false }
        }
    }
    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            backupBusy = true
            try {
                pendingRestore = withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri) ?: error("No input")
                    val bytes = input.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) break
                            require(output.size() + read <= BackupCodec.MAX_BYTES)
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= BackupCodec.MAX_BYTES)
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
            } catch (e: Exception) { backupMessage = "Couldn’t read this backup. Choose a valid Boardwalk backup under 64 MB." }
            finally { backupBusy = false }
        }
    }
    fun cancelRestore() { pendingRestore = null }
    fun restoreBackup() {
        val data = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch {
            restoring = true; backupBusy = true
            try {
                backupJob?.cancelAndJoin(); loadJob?.cancelAndJoin(); galleryJob?.cancelAndJoin()
                archiveJobs.values.toList().forEach { it.cancelAndJoin() }; archiveJobs.clear()
                withContext(NonCancellable + Dispatchers.IO) { store.restore(data) }
                favorites = store.favorites; saved = store.saved; theme = store.theme; showAll = store.showAll
                replyLayout = store.replyLayout; offlineKeys = store.offlineKeys(); offlineErrors = emptyMap()
                proxy = store.proxy(); network.close(); network = NetworkBundle(app, proxy, app.gate)
                boards = emptyList(); catalog = emptyList(); posts = emptyList(); home = emptyList(); popular = emptyList()
                homeFailures = emptyList()
                restoreGeneration++
                backupConnected = false; error = null; loading = false
                backupMessage = "Restored ${saved.size} bookmarks and ${offlineKeys.size} offline discussions. Re-enter proxy credentials if needed. Choose a backup folder to resume automatic local updates."
            } catch (e: Exception) { backupMessage = "Restore failed. Your previous backup file is unchanged." }
            finally { restoring = false; backupBusy = false }
        }
    }
    fun saveMedia(uri: Uri, post: Post, boardId: String) {
        viewModelScope.launch {
            mediaMessage = "Saving attachment…"
            try {
                withContext(Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(post.media(boardId) ?: error("No media")).build()
                    val call = network.client.newCall(request)
                    call.awaitResponse().use { response ->
                            check(response.isSuccessful)
                            val body = response.body ?: error("No media")
                            val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("No output")
                            output.use { out -> body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    out.write(buffer, 0, read)
                                }
                            } }
                        }
                }
                mediaMessage = "Attachment saved to your chosen location."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mediaMessage = "Attachment couldn’t be saved. Check your connection and retry." }
        }
    }
    fun changeReplyLayout(value: String) { replyLayout = value; store.replyLayout = value }
    fun changeTheme(value: String) { theme = value; store.theme = value }
    fun changeAllBoards(value: Boolean) { showAll = value; store.showAll = value }
    fun clearConnectionResult() { connectionResult = null }
    fun openGallery(post: Post, id: Long, source: Board? = board) {
        val b = source ?: return
        mediaMessage = null
        galleryJob?.cancel()
        val inThread = board?.id == b.id && threadId == id
        val available = if (inThread) posts.filter { it.hasMedia } else listOf(post)
        gallery = GalleryData(b.id, id, available, post.id, loading = !inThread)
        if (!inThread) galleryJob = viewModelScope.launch {
            try {
                val media = network.api.thread(b.id, id).filter { it.hasMedia }
                gallery = gallery?.copy(posts = if (media.any { it.id == post.id }) media else listOf(post) + media, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { gallery = gallery?.copy(loading = false, error = readableError(e)) }
        }
    }
    fun closeGallery() { galleryJob?.cancel(); gallery = null }
    fun testProxy(settings: ProxySettings) {
        connectionResult = settings.validationError()
        if (connectionResult != null) return
        testJob?.cancel()
        testJob = viewModelScope.launch {
            connectionBusy = true
            val client = NetworkFactory.create(settings)
            try {
                val count = BoardApi(client, app.gate).boards().size
                connectionResult = "Connected${if (settings.enabled) " through proxy" else " directly"}. $count boards available."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { connectionResult = readableError(e) }
            finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    client.dispatcher.cancelAll(); client.connectionPool.evictAll()
                }
                connectionBusy = false
            }
        }
    }
    fun saveProxy(settings: ProxySettings) {
        connectionResult = settings.validationError()
        if (connectionResult != null) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { store.saveProxy(settings) }
                loadJob?.cancelAndJoin(); galleryJob?.cancelAndJoin(); closeGallery()
                network.close()
                proxy = settings
                network = NetworkBundle(app, settings, app.gate)
                connectionResult = if (settings.enabled) "Proxy saved. All in-app requests use this connection." else "Direct connection saved."
                // Do not leave content from the old transport labeled as freshly loaded.
                boards = emptyList(); catalog = emptyList(); posts = emptyList(); home = emptyList(); homeFailures = emptyList()
            } catch (e: Exception) { connectionResult = "Could not save the connection settings. Try again." }
        }
    }
    private fun readableError(e: Exception) = if (e is ApiException) e.message ?: "Could not load content."
        else "Could not connect. Check your internet or proxy settings, then retry."
    override fun onCleared() { store.unobserve(preferenceListener); network.close() }
}
