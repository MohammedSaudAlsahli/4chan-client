package app.boardwalk.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class LocalBackupFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?) = super.createIntent(context, input)
        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
class LocalBackupImport : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>) = super.createIntent(context, input)
        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
class LocalMediaDocument : ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String) = super.createIntent(context, input)
        .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
