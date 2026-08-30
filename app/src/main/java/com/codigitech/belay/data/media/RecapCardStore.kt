package com.codigitech.belay.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.codigitech.belay.core.ErrorReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a rendered weekly-recap card goes (PRD §5.4): the gallery for "Save", and a private
 * cache file for "Share" — a share target receives a `content://` URI granted read permission for
 * the one intent, rather than the app needing to write to shared storage just to hand over a file.
 */
/**
 * A rendered recap card, deferred behind an interface so the save/share flow can be unit-tested:
 * `android.graphics.Bitmap` can't be constructed in a plain JVM test, and this way nothing off the
 * device ever needs to rasterise one.
 */
fun interface RecapCardImage {
  fun asBitmap(): Bitmap
}

interface RecapCardStore {
  /** Writes the card to the device gallery. Returns its URI, or null if it couldn't be saved. */
  suspend fun saveToGallery(image: RecapCardImage, fileName: String): String?

  /** Stashes the card where a share intent can read it. Returns its URI, or null if it couldn't be written. */
  suspend fun cacheForSharing(image: RecapCardImage, fileName: String): String?
}

private const val RELATIVE_PATH = "Pictures/Belay"
private const val MIME_TYPE = "image/png"
private const val SHARE_CACHE_DIR = "recap-cards"

class AndroidRecapCardStore
@Inject
constructor(@ApplicationContext private val context: Context, private val errorReporter: ErrorReporter) : RecapCardStore {

  override suspend fun saveToGallery(image: RecapCardImage, fileName: String): String? =
    withContext(Dispatchers.IO) {
      try {
        val values =
          ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            // RELATIVE_PATH only exists from Q; before that MediaStore still indexes a file
            // written into the public Pictures directory, which is what the else branch does.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
              put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
              val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Belay")
              directory.mkdirs()
              put(MediaStore.Images.Media.DATA, File(directory, fileName).absolutePath)
            }
          }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null

        resolver.openOutputStream(uri)?.use { image.asBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
          ?: return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        uri.toString()
      } catch (e: Exception) {
        // Saving a picture failing is a bad afternoon, not a crash — the recap is still on screen.
        errorReporter.recordException(e)
        null
      }
    }

  override suspend fun cacheForSharing(image: RecapCardImage, fileName: String): String? =
    withContext(Dispatchers.IO) {
      try {
        val directory = File(context.cacheDir, SHARE_CACHE_DIR).apply { mkdirs() }
        val file = File(directory, fileName)
        FileOutputStream(file).use { image.asBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
      } catch (e: Exception) {
        errorReporter.recordException(e)
        null
      }
    }
}
