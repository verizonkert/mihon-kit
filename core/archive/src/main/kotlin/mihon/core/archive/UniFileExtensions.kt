package mihon.core.archive

import android.content.Context
import android.os.ParcelFileDescriptor
import com.hippo.unifile.UniFile

internal fun UniFile.openFileDescriptor(context: Context, mode: String): ParcelFileDescriptor =
    context.contentResolver.openFileDescriptor(uri, mode) ?: error("Failed to open file descriptor: ${filePath ?: uri}")

/**
  * Opens an [ArchiveReader] on this [UniFile].
  *
  * @param context Android [Context] to resolve the file descriptor.
  * @return An [ArchiveReader] instance to read archive entries.
  */
fun UniFile.archiveReader(context: Context) = openFileDescriptor(context, "r").use { ArchiveReader(it) }

/**
  * Opens an [EpubReader] on this [UniFile].
  *
  * @param context Android [Context] to resolve the file descriptor.
  * @return An [EpubReader] instance to read chapters and pages from an EPUB file.
  */
fun UniFile.epubReader(context: Context) = EpubReader(archiveReader(context))

