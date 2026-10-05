package app.boardwalk.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.boardwalk.ReaderModel
import app.boardwalk.data.*

@Composable
internal fun HomeScreen(model: ReaderModel) {
    var order by rememberSaveable { mutableStateOf(FeedOrder.ACTIVE) }
    val entries = remember(model.home, model.favorites, model.showAll, order) {
        HomeFeed.sorted(model.homeEntries, order)
    }
    Column {
        Text("Your boards, together.", Modifier.padding(start = 20.dp, top = 8.dp), style = MaterialTheme.typography.headlineSmall)
        Text(if (model.loading) "Loading boards · ${model.homeProgress} of ${model.homeBoards.size}"
            else "Threads from ${model.homeBoards.size} favorite boards",
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedOrder.entries.forEach { value ->
                FilterChip(selected = order == value, onClick = { order = value }, label = { Text(value.label) })
            }
        }
        if (model.homeFailures.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Couldn’t refresh ${model.homeFailures.joinToString { "/$it/" }}. Previously loaded threads are kept when available.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(enabled = !model.loading, onClick = { model.loadHome() }) { Text("Retry") }
                }
            }
        }
        if (model.loading && entries.isEmpty()) { LoadingRows(); return }
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.testTag("home-list")) {
            if (entries.isEmpty()) item {
                if (model.homeBoards.isEmpty()) EmptyMessage("Make yourself at home", "Star a board to add its threads here. Home follows your board visibility setting.",
                    "Choose boards", { model.switchSection("Boards") })
                else EmptyMessage("No threads yet", "Refresh to try your favorite boards again.", "Refresh", { model.loadHome() })
            }
            items(entries, key = { it.key }) { entry ->
                val post = entry.post
                val time = if (order == FeedOrder.ACTIVE) post.lastModified else post.time
                val age = DateUtils.getRelativeTimeSpanString(time * 1000).toString()
                ThreadRow(post, entry.board.id, model.network.images, model.isSaved(entry.board.id, post.id),
                    openThread = { model.openHomeThread(entry) },
                    openMedia = { model.openGallery(post, post.id, entry.board) },
                    bookmark = { model.bookmark(entry.board.id, post.id, plain(post.subject).ifBlank { safePreview(post.comment).take(100) }) },
                    origin = "/${entry.board.id}/ · $age")
            }
        }
    }
}
