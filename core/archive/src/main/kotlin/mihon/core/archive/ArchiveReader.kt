package mihon.core.archive

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.InputStream

/**
 * High-performance reader for archive files (CBZ, ZIP, TAR, 7Z, etc.) utilizing memory-mapped I/O (`mmap`).
 *
 * @param pfd The [ParcelFileDescriptor] providing access to the archive file.
 */
class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    private val size = pfd.statSize
    private val address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)

    /**
     * Executes the given [block] passing a sequence of [ArchiveEntry] items found in the archive.
     * The underlying archive input stream is automatically closed when [block] finishes.
     *
     * @param block Lambda receiving the sequence of archive entries.
     * @return The result produced by [block].
     */
    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = ArchiveInputStream(address, size).use {
        block(generateSequence { it.getNextEntry() })
    }

    /**
     * Opens an [InputStream] positioned at the content of the specified [entryName].
     *
     * @param entryName The relative path or name of the entry within the archive.
     * @return An [InputStream] for reading entry bytes, or `null` if the entry was not found.
     * @throws ArchiveException If reading the archive headers encounters an error.
     */
    fun getInputStream(entryName: String): InputStream? {
        val archive = ArchiveInputStream(address, size)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    return archive
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        return null
    }

    /**
     * Unmaps the memory-mapped buffer and releases underlying OS resources.
     */
    override fun close() {
        Os.munmap(address, size)
    }
}
