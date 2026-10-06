package app.boardwalk.data

import kotlinx.coroutines.CancellationException
import kotlin.math.pow

/** Post numbers are board-local; every Home action retains its board identity. */
data class FeedThread(val board: Board, val post: Post) {
    val key get() = "${board.id}:${post.id}"
}

enum class FeedOrder(val label: String) { HOT("Hot"), NEWEST("New"), LATEST_REPLY("Latest reply") }

data class FeedProgress(val entries: List<FeedThread>, val failures: List<String> = emptyList(), val completed: Int = 0)

object HomeFeed {
    suspend fun refresh(initial: List<FeedThread>, selected: List<Board>,
        fetch: suspend (Board) -> List<Post>, progress: (FeedProgress) -> Unit) {
        val ids = selected.map { it.id }.toSet()
        var state = FeedProgress(initial.filter { it.board.id in ids })
        progress(state)
        selected.forEach { board ->
            state = try { state.copy(entries = replaceBoard(state.entries, board, fetch(board))) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.copy(failures = state.failures + board.id) }
            state = state.copy(completed = state.completed + 1)
            progress(state)
        }
    }

    fun boards(boards: List<Board>, favorites: Set<String>, showAll: Boolean) =
        boards.filter { it.id in favorites && (showAll || it.workSafe) }

    fun sorted(entries: List<FeedThread>, order: FeedOrder, now: Long = System.currentTimeMillis() / 1000): List<FeedThread> =
        entries.distinctBy { it.key }.sortedWith(
            compareByDescending<FeedThread> { score(it.post, order, now) }
                .thenBy { it.board.id }.thenByDescending { it.post.id }
        )

    fun sortedBoard(posts: List<Post>, order: FeedOrder, now: Long = System.currentTimeMillis() / 1000): List<Post> =
        posts.sortedWith(compareByDescending<Post> { score(it, order, now) }.thenByDescending { it.id })

    private fun score(post: Post, order: FeedOrder, now: Long): Double = when (order) {
        FeedOrder.NEWEST -> post.time.toDouble()
        FeedOrder.LATEST_REPLY -> post.lastReplyTime.toDouble()
        FeedOrder.HOT -> {
            val ageHours = ((now - post.time).coerceAtLeast(0L) / 3600.0)
            kotlin.math.ln(1.0 + post.replies.coerceAtLeast(0)) / (ageHours + 2.0).pow(0.7)
        }
    }

    fun replaceBoard(entries: List<FeedThread>, board: Board, posts: List<Post>) =
        entries.filterNot { it.board.id == board.id } + posts.map { FeedThread(board, it) }
}
