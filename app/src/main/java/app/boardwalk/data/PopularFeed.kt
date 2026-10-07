package app.boardwalk.data

import kotlinx.coroutines.CancellationException

/** A transparent local approximation; 4chan does not expose its homepage ranking in the JSON API. */
object PopularFeed {
    private val representatives = mapOf(
        "Japanese Culture" to "a", "Video Games" to "v", "Interests" to "g",
        "Creative" to "mu", "Other" to "news", "Misc." to "b", "Adult" to "s",
    )

    fun sampleBoards(boards: List<Board>, showAll: Boolean, alreadyLoaded: Set<String>): List<Board> =
        BoardCategories.group(boards.filter { showAll || it.workSafe }).mapNotNull { group ->
            group.boards.find { it.id == representatives[group.name] } ?: group.boards.firstOrNull()
        }.filterNot { it.id in alreadyLoaded }

    suspend fun refresh(initial: List<FeedThread>, boards: List<Board>,
        fetch: suspend (Board) -> List<Post>, progress: (List<FeedThread>) -> Unit) {
        var entries = initial
        progress(entries)
        boards.forEach { board ->
            try { entries = HomeFeed.replaceBoard(entries, board, fetch(board)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the rest of Home usable when a board fails. */ }
            progress(entries)
        }
    }

    fun ranked(entries: List<FeedThread>, now: Long = System.currentTimeMillis() / 1000): List<FeedThread> {
        val perBoard = mutableMapOf<String, Int>()
        return HomeFeed.sorted(entries, FeedOrder.HOT, now).filter { entry ->
            val count = perBoard[entry.board.id] ?: 0
            if (count >= 2) false else { perBoard[entry.board.id] = count + 1; true }
        }.take(10)
    }
}
