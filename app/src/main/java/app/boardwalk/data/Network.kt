package app.boardwalk.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Deliberately not a data class: never print credentials through generated toString().
class ProxySettings(
    val enabled: Boolean = false, val host: String = "", val port: String = "8080",
    val username: String = "", val password: String = "",
) {
    fun validationError(): String? {
        if (!enabled) return null
        if (host.isBlank() || host.any { it.isWhitespace() || it in "/@?#" } || host.contains("://"))
            return "Enter a server hostname or IP address, without http:// or a path."
        val parsedPort = port.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1..65535) return "Enter a port between 1 and 65535."
        if (username.contains(':')) return "The username cannot contain a colon."
        return null
    }
}

object NetworkFactory {
    fun create(settings: ProxySettings): OkHttpClient {
        require(settings.validationError() == null) { "Invalid proxy settings" }
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        if (settings.enabled) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(settings.host.trim(), settings.port.toInt())))
            if (settings.username.isNotEmpty()) {
                builder.proxyAuthenticator { _, response ->
                    if (response.request.header("Proxy-Authorization") != null) null
                    else response.request.newBuilder().header("Proxy-Authorization",
                        Credentials.basic(settings.username, settings.password)).build()
                }
            }
        } else builder.proxy(Proxy.NO_PROXY)
        return builder.build()
    }
}

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

class ApiException(message: String) : IOException(message)

/** One gate survives proxy changes and covers connection tests as well as browsing. */
class ApiGate(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val lock = Mutex()
    private var next = 0L
    suspend fun <T> request(block: suspend () -> T): T = lock.withLock {
        delay((next - clock()).coerceAtLeast(0))
        try { block() } finally { next = maxOf(next, clock() + 1100) }
    }
    fun backoff(seconds: Long) { next = maxOf(next, clock() + seconds.coerceIn(1, 3600) * 1000) }
}

class BoardApi(
    private val client: OkHttpClient,
    private val gate: ApiGate,
    private val baseUrl: String = "https://a.4cdn.org/",
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Cached(val body: String, val modified: String?, val at: Long)
    private val cache = object : LinkedHashMap<String, Cached>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 32
    }
    suspend fun boards() = withContext(Dispatchers.IO) { ApiParser.boards(get("boards.json", 60_000)) }
    suspend fun catalog(board: String): List<Post> = withContext(Dispatchers.IO) {
        validate(board)
        ApiParser.catalog(get("$board/catalog.json", 15_000))
    }
    suspend fun thread(board: String, id: Long): List<Post> = withContext(Dispatchers.IO) {
        validate(board); require(id > 0)
        ApiParser.thread(get("$board/thread/$id.json", 15_000))
    }
    private fun validate(board: String) = require(board.matches(Regex("[a-z0-9]+")))
    private suspend fun get(path: String, minimumAge: Long): String = gate.request {
        val cached = cache[path]
        if (cached != null && clock() - cached.at < minimumAge) return@request cached.body
        val request = Request.Builder().url(baseUrl + path).header("Accept", "application/json")
            .header("User-Agent", "Boardwalk/0.1 (Android reader)")
        cached?.modified?.let { request.header("If-Modified-Since", it) }
        client.newCall(request.build()).awaitResponse().use { response ->
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: throw ApiException("The server returned no content. Try again.")
                    cache[path] = Cached(body, response.header("Last-Modified"), clock())
                    body
                }
                304 -> {
                    val previous = cached ?: throw ApiException("Content could not be refreshed. Try again.")
                    cache[path] = previous.copy(at = clock())
                    previous.body
                }
                404 -> throw ApiException("This thread or board is no longer available.")
                403 -> throw ApiException("Access was refused. Check your connection or proxy settings.")
                407 -> throw ApiException("The proxy rejected your username or password.")
                429 -> {
                    gate.backoff(response.header("Retry-After")?.toLongOrNull() ?: 60)
                    throw ApiException("Too many requests. Wait a minute, then try again.")
                }
                else -> throw ApiException("The server could not load this content (${response.code}). Try again.")
            }
        }
    }
}
