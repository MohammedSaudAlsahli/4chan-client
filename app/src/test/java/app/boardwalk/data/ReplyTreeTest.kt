package app.boardwalk.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplyTreeTest {
    private fun posts(vararg comments: String) = ApiParser.thread(JSONObject().put("posts", JSONArray().apply {
        comments.forEachIndexed { index, comment -> put(JSONObject().put("no", index + 1).put("com", comment)) }
    }).toString())
    private fun quote(id: Int) = "<a href=\"#p$id\">&gt;&gt;$id</a>"
    @Test fun nestsRepliesAndCollapsesWholeBranch() {
        val tree = ReplyTree.build(posts("OP", quote(1), quote(2), "Another root", quote(4)), "news", 1)
        assertEquals(listOf(0, 1, 2, 0, 1), tree.map { it.depth })
        assertEquals(2, tree.first().descendants)
        assertEquals(listOf(1L, 4L, 5L), ReplyTree.visible(tree, setOf(1)).map { it.post.id })
    }
    @Test fun multipleReferencesNestOnceUnderLatestEarlierPost() {
        val tree = ReplyTree.build(posts("OP", "Second", quote(1) + quote(2)), "biz", 1)
        assertEquals(2L, tree.last().parent)
        assertEquals(listOf(1L, 2L), tree.last().references)
        assertEquals(3, tree.map { it.post.id }.distinct().size)
    }
    @Test fun missingFutureAndSelfQuotesDoNotCreateCycles() {
        val tree = ReplyTree.build(posts(quote(3), quote(2), quote(1) + quote(99)), "biz", 1)
        assertEquals(3, tree.size)
        assertNull(tree.first().parent)
        assertEquals(listOf(1L, 3L, 2L), tree.map { it.post.id })
    }
    @Test fun excludesOtherBoardsThreadsAndExternalLinks() {
        val html = "<a href=\"/news/thread/1#p2\">x</a><a href=\"https://boards.4chan.org/news/thread/1#p3\">x</a>" +
            "<a href=\"/biz/thread/1#p4\">x</a><a href=\"/news/thread/8#p5\">x</a><a href=\"https://evil.example/#p6\">x</a>"
        assertEquals(listOf(2L, 3L), ReplyTree.references(html, "news", 1))
    }
    @Test fun deepThreadsDoNotOverflowTheCallStack() {
        val comments = (0 until 5000).map { if (it == 0) "OP" else quote(it) }
        val tree = ReplyTree.build(posts(*comments.toTypedArray()), "news", 1)
        assertEquals(4999, tree.last().depth)
        assertEquals(1, ReplyTree.visible(tree, setOf(1)).size)
    }
}
