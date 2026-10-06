package app.boardwalk.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.boardwalk.ReaderModel
import app.boardwalk.data.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardwalkApp(model: ReaderModel) {
    BoardwalkTheme(model.theme) {
        val context = LocalContext.current
        val isLight = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect {
            if (model.gallery == null) (context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                }
            }
        }
        val holder = rememberSaveableStateHolder()
        val board = model.board
        val id = model.threadId
        val route = when { id != null -> "thread:${board?.id}:$id"; board != null -> "board:${board.id}"; else -> model.section }
        BackHandler(enabled = model.gallery == null && (board != null || id != null)) { model.back() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Scaffold(
                modifier = if (model.gallery != null) Modifier.clearAndSetSemantics {} else Modifier,
                topBar = {
                    TopAppBar(title = {
                        Column {
                            Text(when { id != null -> "Thread"; board != null -> "/${board.id}/"; model.section == "Boards" -> "Boardwalk"; else -> model.section }, maxLines = 1)
                            if (board != null) Text(if (id != null) "/${board.id}/ · #$id" else plain(board.title),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }, navigationIcon = {
                        if (board != null) IconButton(onClick = model::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Go back") }
                    }, actions = {
                        if (id != null && board != null) {
                            val saved = model.isSaved(board.id, id)
                            IconButton(onClick = { model.bookmark(board.id, id, model.posts.firstOrNull()?.let { plain(it.subject).ifBlank { safePreview(it.comment).take(100) } } ?: "Thread #$id") }) {
                                Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, if (saved) "Unsave thread" else "Save thread")
                            }
                        }
                        if (board != null || model.section in setOf("Boards", "Home")) IconButton(enabled = !model.loading, onClick = model::refresh) {
                            Icon(Icons.Outlined.Refresh, "Refresh content")
                        }
                    })
                },
                bottomBar = { if (!wide && id == null) AppNavigation(model) },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (wide) NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        destinations.forEach { (name, icon) -> NavigationRailItem(selected = model.section == name,
                            onClick = { model.switchSection(name) }, icon = { Icon(icon, name) }, label = { Text(name) }) }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        holder.SaveableStateProvider("${model.restoreGeneration}:$route") {
                            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                                val hasContent = when { id != null -> model.posts.isNotEmpty(); board != null -> model.catalog.isNotEmpty(); model.section == "Home" -> model.homeEntries.isNotEmpty(); else -> model.boards.isNotEmpty() }
                                if (model.loading && hasContent) LinearProgressIndicator(Modifier.fillMaxWidth())
                                if (model.error != null && hasContent && model.section != "Settings") {
                                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(model.error!!, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer)
                                            TextButton(onClick = model::refresh) { Text("Retry") }
                                        }
                                    }
                                }
                                when {
                                    model.section == "Settings" && board == null -> SettingsScreen(model)
                                    model.error != null && !hasContent -> {
                                        EmptyMessage("Couldn’t load content", model.error!!, "Retry", model::refresh)
                                        TextButton(onClick = { model.switchSection("Settings") }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Connection settings") }
                                    }
                                    id != null && board != null -> ThreadScreen(model, board, id)
                                    board != null -> CatalogScreen(model, board)
                                    model.section == "Saved" -> SavedScreen(model)
                                    model.section == "Home" -> HomeScreen(model)
                                    else -> BoardsScreen(model)
                                }
                            }
                        }
                    }
                }
            }
            model.gallery?.let { gallery -> MediaViewer(gallery, model.network, model::closeGallery, model::saveMedia, model.mediaMessage) }
        }
    }
}

private val destinations: List<Pair<String, ImageVector>> = listOf("Home" to Icons.Outlined.Home, "Boards" to Icons.Outlined.Dashboard,
    "Saved" to Icons.Outlined.BookmarkBorder, "Settings" to Icons.Outlined.Tune)

@Composable
private fun AppNavigation(model: ReaderModel) {
    NavigationBar {
        destinations.forEach { (name, icon) -> NavigationBarItem(selected = model.section == name,
            onClick = { model.switchSection(name) }, icon = { Icon(icon, name) }, label = { Text(name) }) }
    }
}

@Composable
private fun BoardsScreen(model: ReaderModel) {
    var query by rememberSaveable { mutableStateOf("") }
    if (model.loading && model.boards.isEmpty()) { LoadingRows(); return }
    val boards = model.boards.filter { (model.showAll || it.workSafe) && (query.isBlank() || "${it.id} ${it.title}".contains(query, true)) }
    val favorites = boards.filter { it.id in model.favorites }
    val others = boards.filter { it.id !in model.favorites }
    LazyColumn {
        item {
            Text("Find your corner.", Modifier.padding(start = 20.dp, top = 8.dp), style = MaterialTheme.typography.headlineSmall)
            Text("Boards, conversations, and everything in between.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SearchField(query, { query = it }, "Find a board")
        }
        if (favorites.isNotEmpty()) {
            item { SectionLabel("Your boards") }
            items(favorites, key = { it.id }) { BoardRow(it, model) }
        }
        if (others.isNotEmpty()) {
            item { SectionLabel(if (model.showAll) "All boards" else "Work-safe boards") }
            items(others, key = { it.id }) { BoardRow(it, model) }
        }
        if (boards.isEmpty()) item { EmptyMessage("No boards found", "Try a different name, or change which boards appear in Settings.") }
        item {
            val context = LocalContext.current
            TextButton(onClick = { context.openWebsite("https://www.4chan.org") }, modifier = Modifier.padding(8.dp)) { Text("Content from 4chan · Unofficial reader") }
        }
    }
}

@Composable
private fun BoardRow(board: Board, model: ReaderModel) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ListItem(modifier = Modifier.weight(1f).clickable { model.openBoard(board) },
            headlineContent = { Text(plain(board.title), style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text("/${board.id}/${if (!board.workSafe) " · NSFW" else ""}") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
        IconButton(onClick = { model.favorite(board.id) }) {
            Icon(if (board.id in model.favorites) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                if (board.id in model.favorites) "Unfavorite ${board.id}" else "Favorite ${board.id}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CatalogScreen(model: ReaderModel, board: Board) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(FeedOrder.HOT) }
    val list = remember(model.catalog, query, sort) {
        val filtered = model.catalog.filter { query.isBlank() || "${plain(it.subject)} ${safePreview(it.comment)}".contains(query, true) }
        HomeFeed.sortedBoard(filtered, sort)
    }
    Column {
        SearchField(query, { query = it }, "Search this board")
        SortFilter(sort) { sort = it }
        if (model.loading && model.catalog.isEmpty()) { LoadingRows(); return }
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.testTag("catalog-list")) {
            if (list.isEmpty()) item { EmptyMessage("No threads found", "Try another search or refresh the board.") }
            items(list, key = { it.id }) { post ->
                ThreadRow(post, board.id, model.network.images, model.isSaved(board.id, post.id),
                    openThread = { model.openThread(post.id) }, openMedia = { model.openGallery(post, post.id) },
                    bookmark = { model.bookmark(board.id, post.id, plain(post.subject).ifBlank { safePreview(post.comment).take(100) }) },
                    openLatestReply = { model.openThread(post.id); model.openReaderLink(ReaderLink(board.id, post.id, post.lastReply?.id)) })
            }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
private fun ThreadScreen(model: ReaderModel, board: Board, id: Long) {
    if (model.loading && model.posts.isEmpty()) { LoadingRows(); return }
    val context = LocalContext.current
    val stored = remember(board.id, id) { model.store.position(board.id, id) }
    val state = rememberLazyListState(stored.index, stored.offset)
    var quoted by remember { mutableStateOf<Long?>(null) }
    var collapsed by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val nested = model.replyLayout == "Nested"
    val tree = remember(model.posts, board.id, id) { ReplyTree.build(model.posts, board.id, id) }
    val rows = remember(tree, nested, collapsed) {
        if (nested) ReplyTree.visible(tree, collapsed.toSet())
        else model.posts.map { ReplyNode(it, null, 0, 0, ReplyTree.references(it.comment, board.id, id)) }
    }
    LaunchedEffect(model.targetPostId, rows) {
        val target = model.targetPostId ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.post.id == target }
        if (index >= 0) {
            state.animateScrollToItem(index + 2 + if (model.offlineSavedAt != null) 1 else 0)
            model.consumeTargetPost()
        } else if (nested && collapsed.isNotEmpty()) collapsed = emptyList()
    }
    LaunchedEffect(state) {
        snapshotFlow { ReadPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) }
            .distinctUntilChanged().debounce(250).collect { model.store.rememberPosition(board.id, id, it) }
    }
    DisposableEffect(state) { onDispose {
        model.store.rememberPosition(board.id, id, ReadPosition(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset))
    } }
    LazyColumn(state = state) {
        item(key = "heading") {
            val first = model.posts.firstOrNull()
            if (first?.subject?.isNotBlank() == true) Text(plain(first.subject), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${(model.posts.size - 1).coerceAtLeast(0)} replies", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (first?.archived == true) Text("Archived", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (model.offlineSavedAt != null) item(key = "offline-status") {
            Text("Offline discussion · saved ${DateUtils.getRelativeTimeSpanString(model.offlineSavedAt!!)}. Refresh for the latest posts. Media is loaded separately.",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        item(key = "reply-layout") {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Nested", "Chronological").forEach { value ->
                    FilterChip(selected = model.replyLayout == value, onClick = { model.changeReplyLayout(value) }, label = { Text(value) })
                }
            }
        }
        items(rows, key = { it.post.id }) { node ->
            val post = node.post
            val indent = node.depth.coerceAtMost(4) * 16
            val lineColor = MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.fillMaxWidth()) {
                if (nested && node.parent != null) Canvas(Modifier.matchParentSize()) {
                    val x = (22 + indent - 9).dp.toPx()
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                    drawLine(lineColor, Offset(x, 39.dp.toPx()), Offset(x + 9.dp.toPx(), 39.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
            Column(Modifier.fillMaxWidth().padding(start = (20 + indent).dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(plain(post.name).ifBlank { "Anonymous" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("#${post.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (nested && node.parent != null) {
                    val parent = model.posts.find { it.id == node.parent }
                    TextButton(onClick = { quoted = node.parent }) {
                        Text("↳ To #${node.parent} · ${parent?.let { plain(it.name).ifBlank { "Anonymous" } } ?: "post"}: ${parent?.let { safePreview(it.comment).take(65) }.orEmpty()}",
                            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                    }
                } else if (node.references.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        node.references.forEach { ref -> TextButton(onClick = { quoted = ref }) { Text("Reply to #$ref", style = MaterialTheme.typography.labelSmall) } }
                    }
                }
                if (post.hasMedia) MediaThumbnail(post, board.id, model.network.images, { model.openGallery(post, id) })
                if (post.comment.isNotBlank()) CommentBody(post.comment, { quoted = it }, model::openReaderLink, board.id, id)
                ReaderLinks.links(post.comment, board.id, id)
                    .filter { it.thread != null && it.post != null && (it.board != board.id || it.thread != id) }
                    .take(3).forEach { link ->
                    QuotedPostCard(model, link)
                }
                Text(DateUtils.getRelativeTimeSpanString(post.time * 1000).toString(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (nested && node.descendants > 0) TextButton(onClick = {
                    collapsed = if (post.id in collapsed) collapsed - post.id else collapsed + post.id
                }) { Text("${if (post.id in collapsed) "Show" else "Hide"} ${node.descendants} replies") }
            }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
        item {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(onClick = { context.openWebsite("https://boards.4chan.org/${board.id}/thread/$id") }) { Text("Open thread on 4chan") }
                Text("Uses your browser’s connection settings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    quoted?.let { quoteId ->
        val post = model.posts.find { it.id == quoteId }
        AlertDialog(onDismissRequest = { quoted = null }, title = { Text("Post #$quoteId") }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (post == null) Text("This reply is no longer in the thread.")
                else {
                    if (post.hasMedia) MediaThumbnail(post, board.id, model.network.images, { quoted = null; model.openGallery(post, id) })
                    Spacer(Modifier.height(12.dp))
                    CommentBody(post.comment, { quoted = it }, { link -> quoted = null; model.openReaderLink(link) }, board.id, id)
                }
            }
        }, confirmButton = { TextButton(onClick = { quoted = null }) { Text("Close") } })
    }
}

@Composable
private fun QuotedPostCard(model: ReaderModel, link: ReaderLink) {
    val key = "${link.board}:${link.thread}:${link.post}"
    LaunchedEffect(key) { model.loadQuote(link) }
    val quoted = model.quotePosts[key]
    Surface(Modifier.fillMaxWidth().clickable { model.openReaderLink(link) },
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Quoted from /${link.board}/${link.thread?.let { " · #$it" }.orEmpty()}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (quoted != null) {
                if (quoted.subject.isNotBlank()) Text(plain(quoted.subject), style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (quoted.hasMedia && link.thread != null) MediaThumbnail(quoted, link.board, model.network.images,
                    { model.openGallery(quoted, link.thread, Board(link.board, "/${link.board}/", false)) })
                Text(safePreview(quoted.comment).ifBlank { "Open quoted post" }, style = MaterialTheme.typography.bodySmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else Text(if (model.quotePosts.containsKey(key)) "Quoted post unavailable · open source" else "Loading quoted post…",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SavedScreen(model: ReaderModel) {
    if (model.saved.isEmpty()) { EmptyMessage("Keep a good thread close", "Bookmark a thread to keep its discussion here for offline reading. Attachments can be downloaded separately."); return }
    LazyColumn {
        items(model.saved, key = { "${it.board}:${it.id}" }) { item ->
            ListItem(modifier = Modifier.clickable { model.openSaved(item) },
                headlineContent = { Text(item.title.ifBlank { "Thread #${item.id}" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val key = "${item.board}:${item.id}"
                    Column {
                        Text("/${item.board}/ · #${item.id}")
                        when {
                            key in model.savingThreads -> Text("Saving discussion…")
                            key in model.offlineKeys -> Text("Available offline · discussion only")
                            else -> Text("Bookmark only · discussion not downloaded")
                        }
                        model.offlineErrors[key]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (key !in model.offlineKeys && key !in model.savingThreads) TextButton(onClick = { model.downloadThread(item.board, item.id) }) { Text("Download discussion") }
                    }
                },
                trailingContent = { IconButton(onClick = { model.bookmark(item.board, item.id, item.title) }) { Icon(Icons.Outlined.BookmarkRemove, "Remove saved thread") } })
        }
    }
}

@Composable
internal fun SearchField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = change, placeholder = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Search, label) }, singleLine = true,
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { change("") }) { Icon(Icons.Outlined.Close, "Clear search") } },
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
internal fun SectionLabel(text: String) { Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
