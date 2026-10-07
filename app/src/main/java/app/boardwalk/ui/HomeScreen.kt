package app.boardwalk.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.boardwalk.ReaderModel
import app.boardwalk.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(model: ReaderModel) {
    var order by rememberSaveable { mutableStateOf(FeedOrder.HOT) }
    val entries = remember(model.home, model.favorites, model.showAll, order) {
        HomeFeed.sorted(model.homeEntries, order)
    }
    val popular = model.popular.filter { model.showAll || it.board.workSafe }
    PullToRefreshBox(isRefreshing = model.loading, onRefresh = model::loadHome) {
        LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize().testTag("home-list")) {
            item(key = "popular-heading") {
                Column {
                    Text("Popular Threads", Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                        style = MaterialTheme.typography.headlineSmall)
                    Text("Across a selection of boards · ranked by replies and age",
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (popular.isEmpty()) item(key = "popular-empty") {
                if (model.loading) LoadingRows()
                else EmptyMessage("No popular threads yet", "Pull down to try again. Your board feed remains below.")
            }
            items(popular, key = { "popular:${it.key}" }) { entry -> FeedEntryRow(entry, model) }
            item(key = "favorites-heading") {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    Text("Your boards", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.headlineSmall)
                    Text(if (model.loading) "Loading boards · ${model.homeProgress} of ${model.homeBoards.size}"
                        else "Threads from ${model.homeBoards.size} favorite boards",
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SortFilter(order) { order = it }
                }
            }
            if (model.homeFailures.isNotEmpty()) item(key = "favorite-failures") {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text("Couldn’t refresh ${model.homeFailures.joinToString { "/$it/" }}. Previously loaded threads are kept when available.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(enabled = !model.loading, onClick = model::loadHome) { Text("Retry") }
                    }
                }
            }
            if (entries.isEmpty()) item(key = "favorite-empty") {
                if (model.homeBoards.isEmpty()) EmptyMessage("Choose your boards", "Star a board to build your personal feed.",
                    "Browse boards", { model.switchSection("Boards") })
                else if (!model.loading) EmptyMessage("No threads yet", "Pull down to refresh your boards.")
            }
            items(entries, key = { "favorite:${it.key}" }) { entry -> FeedEntryRow(entry, model, order) }
        }
    }
}

@Composable
private fun FeedEntryRow(entry: FeedThread, model: ReaderModel, order: FeedOrder = FeedOrder.HOT) {
    val post = entry.post
    val time = if (order == FeedOrder.NEWEST) post.time else post.lastReplyTime
    val age = DateUtils.getRelativeTimeSpanString(time * 1000).toString()
    ThreadRow(post, entry.board.id, model.network.images, model.isSaved(entry.board.id, post.id),
        openThread = { model.openHomeThread(entry) },
        openMedia = { model.openGallery(post, post.id, entry.board) },
        bookmark = { model.bookmark(entry.board.id, post.id, plain(post.subject).ifBlank { safePreview(post.comment).take(100) }) },
        origin = "/${entry.board.id}/ · $age",
        openLatestReply = {
            model.openHomeThread(entry)
            model.openReaderLink(ReaderLink(entry.board.id, post.id, post.lastReply?.id))
        })
}
