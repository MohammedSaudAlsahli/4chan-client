package app.boardwalk.ui

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.boardwalk.ReaderModel
import app.boardwalk.data.ProxySettings

@Composable
fun SettingsScreen(model: ReaderModel) {
    val context = LocalContext.current
    var pickerError by remember { mutableStateOf<String?>(null) }
    val createBackup = rememberLauncherForActivityResult(LocalBackupFolder()) { uri -> uri?.let(model::chooseBackup) }
    val importBackup = rememberLauncherForActivityResult(LocalBackupImport()) { uri -> uri?.let(model::readBackup) }
    model.pendingRestore?.let { backup ->
        AlertDialog(onDismissRequest = model::cancelRestore, title = { Text("Restore local backup?") },
            text = { Text("This replaces your boards, settings, bookmarks, and offline discussions with this backup (${backup.threads.size} discussions). Proxy passwords and separately downloaded media are not included.") },
            confirmButton = { TextButton(onClick = model::restoreBackup) { Text("Restore") } },
            dismissButton = { TextButton(onClick = model::cancelRestore) { Text("Cancel") } })
    }
    var enabled by rememberSaveable(model.proxy) { mutableStateOf(model.proxy.enabled) }
    var host by rememberSaveable(model.proxy) { mutableStateOf(model.proxy.host) }
    var port by rememberSaveable(model.proxy) { mutableStateOf(model.proxy.port) }
    // Passwords deliberately stay out of saved-instance-state bundles.
    var username by remember(model.proxy) { mutableStateOf(model.proxy.username) }
    var password by remember(model.proxy) { mutableStateOf(model.proxy.password) }
    var visible by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 32.dp)) {
        SectionLabel("Appearance")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEach { value ->
                FilterChip(selected = model.theme == value, onClick = { model.changeTheme(value) }, label = { Text(value) })
            }
        }
        ListItem(headlineContent = { Text("Include NSFW boards") }, supportingContent = { Text("Show all boards in the board directory") },
            trailingContent = { Switch(checked = model.showAll, onCheckedChange = model::changeAllBoards,
                modifier = Modifier.semantics { contentDescription = "Include NSFW boards" }) })
        SectionLabel("Local backup")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose a local folder for backups. Boardwalk keeps the two latest completed backup files while you use the app. After reinstalling or changing phones, import the newest file.", style = MaterialTheme.typography.bodyMedium)
            Text("Includes favorite boards, bookmarks, offline discussions, reading positions, and appearance. Media files and proxy passwords are separate. No cloud backup or account is used.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = !model.backupBusy, onClick = { try { createBackup.launch(null) } catch (_: android.content.ActivityNotFoundException) { pickerError = "No local file picker is installed on this device." } }) {
                Text(if (model.backupConnected) "Choose another backup folder" else "Choose local backup folder")
            }
            OutlinedButton(enabled = !model.backupBusy, onClick = { try { importBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) } catch (_: android.content.ActivityNotFoundException) { pickerError = "No local file picker is installed on this device." } }) { Text("Import backup") }
            if (model.backupConnected) TextButton(enabled = !model.backupBusy, onClick = model::disconnectBackup) { Text("Stop updating backup") }
            if (model.backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            pickerError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            model.backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        SectionLabel("Connection")
        ListItem(headlineContent = { Text("Use a proxy") }, supportingContent = { Text("Applies to all content inside Boardwalk") },
            trailingContent = { Switch(checked = enabled, enabled = !model.connectionBusy,
                modifier = Modifier.semantics { contentDescription = "Use a proxy" },
                onCheckedChange = { enabled = it; model.clearConnectionResult() }) })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (enabled) {
                Text("HTTP proxy with HTTPS tunneling. Enter the details from your proxy provider.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(host, { host = it.trim(); model.clearConnectionResult() }, enabled = !model.connectionBusy, label = { Text("Server address") }, placeholder = { Text("proxy.example.com") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it; model.clearConnectionResult() }, enabled = !model.connectionBusy, label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(username, { username = it; model.clearConnectionResult() }, enabled = !model.connectionBusy, label = { Text("Username (optional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it; model.clearConnectionResult() }, enabled = !model.connectionBusy, label = { Text("Password (optional)") }, singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password")
                    } }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), enabled = !model.connectionBusy, onClick = { model.testProxy(ProxySettings(enabled, host.trim(), port, username, password)) }) {
                    Text(if (model.connectionBusy) "Testing…" else "Test connection")
                }
                Button(modifier = Modifier.weight(1f), enabled = !model.connectionBusy, onClick = { model.saveProxy(ProxySettings(enabled, host.trim(), port, username, password)) }) { Text("Save") }
            }
            model.connectionResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("External browser links use the browser’s own connection settings.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("About")
        ListItem(headlineContent = { Text("Boardwalk") }, supportingContent = { Text("Version 0.3 · Independent Android reader") })
        Text("Content is provided by 4chan. Boardwalk is not affiliated with 4chan. Data stays on this device unless you export a local backup file.",
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { context.openWebsite("https://www.4chan.org") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Visit 4chan") }
    }
}
