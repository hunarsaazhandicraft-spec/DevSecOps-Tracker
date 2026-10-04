package com.devsecops.tracker.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Writes the JSON backup into internal storage, then hands it to Android's
// share sheet so it can go to Drive / email / "Save to Files" — whatever
// you pick. Internal storage + FileProvider means no storage permission is
// needed and no other app can read the folder directly.
object BackupExporter {

    // Suspend: the (potentially large) file write + pruning run on Dispatchers.IO.
    suspend fun writeAndShare(context: Context, json: String): File {
        val file = withContext(Dispatchers.IO) { write(context, json) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(sendIntent, "Save backup")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        return file
    }

    private fun write(context: Context, json: String): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        val file = File(dir, "devsecops-tracker-$stamp.json")
        file.writeText(json)
        prune(dir, keep = 3)
        return file
    }

    // Keep only the newest `keep` backups (the folder is exposed through the
    // FileProvider as files/backups, so it stays where it is). Names sort by
    // timestamp; lastModified is the tie-breaker.
    private fun prune(dir: File, keep: Int) {
        val backups = dir.listFiles { f -> f.isFile && f.name.startsWith("devsecops-tracker-") && f.name.endsWith(".json") }
            ?: return
        backups.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .drop(keep)
            .forEach { runCatching { it.delete() } }
    }
}
