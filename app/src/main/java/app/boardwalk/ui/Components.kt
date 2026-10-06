package app.boardwalk.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.boardwalk.data.Post
import app.boardwalk.data.FeedOrder
import app.boardwalk.data.ReaderLink
import app.boardwalk.data.ReaderLinks
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage

fun plain(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
fun safePreview(html: String): String = plain(html.replace(Regex("<s(?:\\s[^>]*)?>.*?</s>", RegexOption.DOT_MATCHES_ALL), "[spoiler]"))
fun Context.openWebsite(url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme !in setOf("http", "https")) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

@Composable
fun MediaThumbnail(post: Post, board: String, images: ImageLoader, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(88.dp).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .clickable(role = Role.Button, onClickLabel = "Open full-screen media", onClick = onClick)
        .semantics { contentDescription = "Open image for post ${post.id}" }, contentAlignment = Alignment.Center) {
        if (post.spoiler) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(22.dp))
            Text("Spoiler", style = MaterialTheme.typography.labelSmall)
        } else SubcomposeAsyncImage(
            model = post.thumbnail(board), imageLoader = images, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, null) } },
            error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.BrokenImage, null) } },
        )
        if (post.extension in setOf(".webm", ".gif", ".pdf")) {
            Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp), shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                Text(post.extension.drop(1).uppercase(), Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ThreadRow(post: Post, board: String, images: ImageLoader, saved: Boolean,
    openThread: () -> Unit, openMedia: () -> Unit, bookmark: () -> Unit, origin: String? = null,
    openLatestReply: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        if (post.hasMedia) MediaThumbnail(post, board, images, openMedia)
        Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Read thread", onClick = openThread)) {
            val title = remember(post.subject, post.comment) { plain(post.subject).ifBlank { safePreview(post.comment).lineSequence().firstOrNull().orEmpty().ifBlank { "Thread #${post.id}" } } }
            if (origin != null) Text(origin, Modifier.padding(bottom = 5.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (post.comment.isNotBlank()) Text(remember(post.comment) { safePreview(post.comment) },
                Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${post.replies} replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (post.images > 0) Text("· ${post.images} images", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            post.lastReply?.let { reply ->
                Surface(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { (openLatestReply ?: openThread)() },
                    shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Latest reply · #${reply.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(safePreview(reply.comment).ifBlank { if (reply.hasMedia) "Attachment" else "Open reply" },
                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        IconButton(onClick = bookmark, modifier = Modifier.size(48.dp)) {
            Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                if (saved) "Remove saved thread" else "Save thread", tint = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
}

@Composable
fun CommentBody(html: String, onQuote: (Long) -> Unit, onReaderLink: ((ReaderLink) -> Unit)? = null,
    currentBoard: String? = null, currentThread: Long? = null) {
    var reveal by remember(html) { mutableStateOf(false) }
    val spoiler = remember(html) { Regex("<s(?:\\s[^>]*)?>.*?</s>", RegexOption.DOT_MATCHES_ALL) }
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val text = remember(html, reveal, primary) {
        val clean = if (reveal) html else html.replace(spoiler, "[spoiler hidden]")
        val parsed = Html.fromHtml(clean, Html.FROM_HTML_MODE_LEGACY)
        buildAnnotatedString {
            append(parsed.toString().trimEnd())
            parsed.getSpans(0, parsed.length, URLSpan::class.java).forEach { span ->
                val start = parsed.getSpanStart(span).coerceAtMost(length)
                val end = parsed.getSpanEnd(span).coerceAtMost(length)
                if (start < end) {
                    addStyle(SpanStyle(color = primary), start, end)
                    addStringAnnotation("link", span.url, start, end)
                }
            }
            Regex("(?m)^>[^>].*$").findAll(toString()).forEach { match ->
                addStyle(SpanStyle(color = primary), match.range.first, match.range.last + 1)
            }
        }
    }
    ClickableText(text, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface), onClick = { offset ->
        text.getStringAnnotations("link", offset, offset).firstOrNull()?.item?.let { link ->
            val id = Regex("^#p(\\d+)$").find(link)?.groupValues?.get(1)?.toLongOrNull()
            val reader = ReaderLinks.parse(link, currentBoard, currentThread)
            if (reader != null && onReaderLink != null) onReaderLink(reader)
            else if (id != null) onQuote(id)
            else context.openWebsite(when { link.startsWith("//") -> "https:$link"; link.startsWith("/") -> "https://boards.4chan.org$link"; else -> link })
        }
    })
    if (spoiler.containsMatchIn(html)) TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide spoilers" else "Reveal spoilers") }
}

@Composable
fun SortFilter(selected: FeedOrder, onSelect: (FeedOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sort: ${selected.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FeedOrder.entries.forEach { order ->
                DropdownMenuItem(text = { Text(order.label) }, onClick = { onSelect(order); expanded = false },
                    leadingIcon = { if (order == selected) Icon(Icons.Outlined.Check, null) })
            }
        }
    }
}

@Composable
fun EmptyMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun LoadingRows() {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading content" }) {
        repeat(5) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(.85f).height(16.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                    Box(Modifier.fillMaxWidth(.6f).height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
        }
    }
}
