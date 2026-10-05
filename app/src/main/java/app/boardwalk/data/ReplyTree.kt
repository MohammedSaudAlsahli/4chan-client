package app.boardwalk.data

import java.net.URI

data class ReplyNode(val post: Post, val parent: Long?, val depth: Int, val descendants: Int, val references: List<Long>)

/** A post may quote several others. Nest once under its latest earlier quoted post. */
object ReplyTree {
    private val links = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    fun references(html: String, board: String, thread: Long): List<Long> = links.findAll(html).mapNotNull { match ->
        runCatching {
            val uri = URI(match.groupValues[1].replace("&amp;", "&"))
            if (uri.host != null && uri.host !in setOf("boards.4chan.org", "boards.4channel.org")) return@runCatching null
            if (uri.scheme != null && uri.scheme !in setOf("http", "https")) return@runCatching null
            val path = uri.path.orEmpty()
            if (path.isNotEmpty() && path !in setOf("/$board/thread/$thread", "/$board/res/$thread", "thread/$thread", "res/$thread")) return@runCatching null
            Regex("^p(\\d+)$").matchEntire(uri.fragment.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
        }.getOrNull()
    }.distinct().toList()

    fun build(posts: List<Post>, board: String, thread: Long): List<ReplyNode> {
        val unique = posts.distinctBy { it.id }
        val order = unique.mapIndexed { index, post -> post.id to index }.toMap()
        val refs = unique.associate { it.id to references(it.comment, board, thread) }
        val parents = unique.associate { post ->
            post.id to refs.getValue(post.id).filter { (order[it] ?: Int.MAX_VALUE) < order.getValue(post.id) }
                .maxByOrNull { order.getValue(it) }
        }
        val counts = mutableMapOf<Long, Int>()
        unique.asReversed().forEach { post -> parents[post.id]?.let { parent -> counts[parent] = (counts[parent] ?: 0) + 1 + (counts[post.id] ?: 0) } }
        val children = unique.groupBy { parents[it.id] }
        val stack = java.util.ArrayDeque<Pair<Post, Int>>()
        children[null].orEmpty().asReversed().forEach { stack.push(it to 0) }
        val result = mutableListOf<ReplyNode>()
        while (stack.isNotEmpty()) {
            val (post, depth) = stack.pop()
            result += ReplyNode(post, parents[post.id], depth, counts[post.id] ?: 0, refs.getValue(post.id))
            children[post.id].orEmpty().asReversed().forEach { stack.push(it to depth + 1) }
        }
        return result
    }
    fun visible(nodes: List<ReplyNode>, collapsed: Set<Long>): List<ReplyNode> {
        val hidden = mutableSetOf<Long>()
        return nodes.filter { node ->
            val hide = node.parent in collapsed || node.parent in hidden
            if (hide) hidden += node.post.id
            !hide
        }
    }
}
