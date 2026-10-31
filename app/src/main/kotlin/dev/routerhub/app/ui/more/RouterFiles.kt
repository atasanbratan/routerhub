package dev.routerhub.app.ui.more

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.routerhub.app.R
import dev.routerhub.app.data.InvalidRoutersFileException
import dev.routerhub.app.data.ProfileTransfer
import dev.routerhub.app.data.RouterProfile
import dev.routerhub.app.data.RouterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

internal const val EXPORT_FILE_NAME = "routerhub-routers.json"

private const val TAG = "RouterFiles"

/** Far beyond any real export; a bigger file isn't one. */
private const val MAX_FILE_BYTES = 1024 * 1024

/** Writes [profiles] to the file the user picked and returns what to tell them. */
internal suspend fun exportRouters(context: Context, uri: Uri, profiles: List<RouterProfile>): String =
    withContext(Dispatchers.IO) {
        try {
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream")
            output.use { it.write(ProfileTransfer.encode(profiles).toByteArray()) }
            context.resources.getQuantityString(R.plurals.routers_exported, profiles.size, profiles.size)
        } catch (e: IOException) {
            Log.w(TAG, "Export failed", e)
            context.getString(R.string.export_failed)
        } catch (e: SecurityException) {
            Log.w(TAG, "Export failed", e)
            context.getString(R.string.export_failed)
        }
    }

/** Adds the routers in the file the user picked and returns what to tell them. */
internal suspend fun importRouters(context: Context, uri: Uri, routers: RouterRepository): String {
    val imported = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream")
            ProfileTransfer.decode(input.use { it.readLimited() })
        } catch (e: InvalidRoutersFileException) {
            Log.w(TAG, "Not a routers file: ${e.message}")
            return@withContext null
        } catch (e: IOException) {
            Log.w(TAG, "Import failed", e)
            return@withContext emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "Import failed", e)
            return@withContext emptyList()
        }
    }
    return when {
        imported == null -> context.getString(R.string.import_invalid)
        imported.isEmpty() -> context.getString(R.string.import_failed)
        else -> {
            val added = routers.addImported(imported)
            if (added == 0) {
                context.getString(R.string.import_none_new)
            } else {
                context.resources.getQuantityString(R.plurals.routers_imported, added, added)
            }
        }
    }
}

private fun InputStream.readLimited(): String {
    val bytes = ByteArray(MAX_FILE_BYTES + 1)
    var size = 0
    while (size < bytes.size) {
        val count = read(bytes, size, bytes.size - size)
        if (count < 0) break
        size += count
    }
    if (size > MAX_FILE_BYTES) throw InvalidRoutersFileException("Too big")
    return String(bytes, 0, size)
}
