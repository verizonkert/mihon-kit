package mihon.kit.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.kit.backup.model.Backup
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * High-level manager for exporting and restoring full Mihon / Tachiyomi `.tachibk` backups.
 * Supports GZIP compressed Protobuf encoding and decoding with automatic format detection.
 *
 * @param context Android [Context] used to resolve Storage Access Framework (SAF) content URIs.
 */
@OptIn(ExperimentalSerializationApi::class)
class BackupManager(private val context: Context) {

    private val proto = ProtoBuf

    /**
     * Serializes [backup] into a GZIP-compressed Protobuf stream, written to [outputStream].
     */
    suspend fun exportToStream(backup: Backup, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val protoBytes = proto.encodeToByteArray(backup)
        GZIPOutputStream(outputStream).use { gzip ->
            gzip.write(protoBytes)
            gzip.flush()
        }
    }

    /**
     * Serializes [backup] to a local [targetFile] (.tachibk).
     */
    suspend fun exportToFile(backup: Backup, targetFile: File) = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { fos ->
            exportToStream(backup, fos)
        }
    }

    /**
     * Serializes [backup] to an Android [Uri] (e.g. from SAF createDocument).
     */
    suspend fun exportToUri(backup: Backup, uri: Uri) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Cannot open OutputStream for URI: $uri")
        stream.use { fos ->
            exportToStream(backup, fos)
        }
    }

    /**
     * Restores a [Backup] from an [inputStream], auto-detecting GZIP compression or plain Protobuf.
     */
    suspend fun importFromStream(inputStream: InputStream): Backup = withContext(Dispatchers.IO) {
        val rawBytes = inputStream.readBytes()
        val decompressedBytes = try {
            // Check GZIP magic bytes: 0x1F, 0x8B
            if (rawBytes.size >= 2 && rawBytes[0] == 0x1F.toByte() && rawBytes[1] == 0x8B.toByte()) {
                GZIPInputStream(rawBytes.inputStream()).use { it.readBytes() }
            } else {
                rawBytes
            }
        } catch (_: Exception) {
            rawBytes
        }

        proto.decodeFromByteArray<Backup>(decompressedBytes)
    }

    /**
     * Restores a [Backup] from a local file.
     */
    suspend fun importFromFile(file: File): Backup = withContext(Dispatchers.IO) {
        file.inputStream().use { importFromStream(it) }
    }

    /**
     * Restores a [Backup] from an Android [Uri].
     */
    suspend fun importFromUri(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open InputStream for URI: $uri")
        stream.use { importFromStream(it) }
    }

    /**
     * Validates whether the given [inputStream] represents a valid `.tachibk` Protobuf file.
     */
    suspend fun validate(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            importFromStream(inputStream)
        }.isSuccess
    }

    /**
     * Validates whether the given [file] is a valid `.tachibk` backup.
     */
    suspend fun validate(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            importFromFile(file)
        }.isSuccess
    }
}
