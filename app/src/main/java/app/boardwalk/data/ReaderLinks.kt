package app.boardwalk.data

import java.net.URI

data class ReaderLink(val board: String, val thread: Long? = null, val post: Long? = null)

/** Only canonical 4chan board routes are handled inside the reader. */
object ReaderLinks {
    private val hosts = setOf("boards.4chan.org", "boards.4channel.org", "www.4chan.org", "www.4channel.org")
    private val boardId = Regex("[a-z0-9]+")
    private val threadPath = Regex("^/([a-z0-9]+)/(?:thread|res)/(\\d+)(?:/.*)?$")
    private val boardPath = Regex("^/([a-z0-9]+)/?$")
    private val hrefs = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, currentBoard: String? = null, currentThread: Long? = null): ReaderLink? = runCatching {
        val cleaned = raw.replace("&amp;", "&")
        val uri = URI(if (cleaned.startsWith("//")) "https:$cleaned" else cleaned)
        if (uri.scheme != null && uri.scheme !in setOf("http", "https")) return@runCatching null
        if (uri.host != null && uri.host.lowercase() !in hosts) return@runCatching null
        if (uri.host == null && uri.scheme != null) return@runCatching null
        val path = uri.path.orEmpty()
        val post = Regex("^p(\\d+)$").matchEntire(uri.fragment.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
        if (path.isBlank() && post != null && currentBoard != null && currentThread != null)
            return@runCatching ReaderLink(currentBoard, currentThread, post)
        val absolutePath = if (path.startsWith('/')) path else "/$path"
        threadPath.matchEntire(absolutePath)?.let { match ->
            return@runCatching ReaderLink(match.groupValues[1], match.groupValues[2].toLong(), post)
        }
        boardPath.matchEntire(absolutePath)?.let { match ->
            return@runCatching ReaderLink(match.groupValues[1])
        }
        null
    }.getOrNull()?.takeIf { boardId.matches(it.board) }

    fun links(html: String, currentBoard: String, currentThread: Long): List<ReaderLink> =
        hrefs.findAll(html).mapNotNull { parse(it.groupValues[1], currentBoard, currentThread) }.distinct().toList()
}
