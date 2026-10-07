package app.boardwalk.data

import org.junit.Assert.*
import org.junit.Test

class PopularFeedTest {
    private fun board(id: String, safe: Boolean = true) = Board(id, id, safe)
    private fun post(id: Long, replies: Int) = Post(id, "", "", "Anonymous", 1000, replies, 0, null,
        "", "", false, 0, 0, false)

    @Test fun samplesAcrossCategoriesAndRespectsVisibility() {
        val boards = listOf(board("a"), board("v"), board("g"), board("mu"), board("news"), board("b", false))
        assertEquals(listOf("a", "v", "g", "mu"), PopularFeed.sampleBoards(boards, false, setOf("news")).map { it.id })
        assertEquals(listOf("a", "v", "g", "mu", "b"), PopularFeed.sampleBoards(boards, true, setOf("news")).map { it.id })
    }

    @Test fun rankingPreventsOneBoardFromFillingTheSection() {
        val entries = (1L..5L).map { FeedThread(board("a"), post(it, 100)) } +
            FeedThread(board("v"), post(6, 50))
        assertEquals(2, PopularFeed.ranked(entries, now = 2000).count { it.board.id == "a" })
        assertTrue(PopularFeed.ranked(entries, now = 2000).any { it.board.id == "v" })
    }
}
