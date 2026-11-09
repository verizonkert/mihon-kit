package mihon.core.archive

import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import me.zhanghai.android.libarchive.ArchiveException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import mihon.core.archive.ArchiveEntry as MihonArchiveEntry

/**
 * An [InputStream] implementation that reads entries from a memory-mapped archive buffer using `libarchive`.
 *
 * It manages an unmanaged native `archive` pointer, decoding archive formats (CBZ, ZIP, 7z, TAR)
 * directly from memory.
 *
 * @param buffer Native memory address of the mapped archive data.
 * @param size Total byte size of the mapped archive buffer.
 */
internal class ArchiveInputStream(buffer: Long, size: Long) : InputStream() {
    private val lock = Any()

    @Volatile
    private var isClosed = false

    private val archive = Archive.readNew()

    init {
        try {
            Archive.setCharset(archive, Charsets.UTF_8.name().toByteArray())
            Archive.readSupportFilterAll(archive)
            Archive.readSupportFormatAll(archive)
            Archive.readOpenMemoryUnsafe(archive, buffer, size)
        } catch (e: ArchiveException) {
            close()
            throw e
        }
    }

    private val oneByteBuffer = ByteBuffer.allocateDirect(1)

    /**
     * Reads a single byte from the currently active archive entry.
     *
     * @return The next byte of data (0-255), or -1 if the end of the entry stream is reached.
     */
    override fun read(): Int {
        read(oneByteBuffer)
        return if (oneByteBuffer.hasRemaining()) oneByteBuffer.get().toUByte().toInt() else -1
    }

    /**
     * Reads up to [len] bytes into array [b] starting at offset [off].
     *
     * @return Total number of bytes read into the buffer, or -1 if end of entry stream.
     */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val buffer = ByteBuffer.wrap(b, off, len)
        read(buffer)
        return if (buffer.hasRemaining()) buffer.remaining() else -1
    }

    private fun read(buffer: ByteBuffer) {
        buffer.clear()
        Archive.readData(archive, buffer)
        buffer.flip()
    }

    /**
     * Closes the stream and frees the underlying native libarchive resources.
     */
    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
        }

        Archive.readFree(archive)
    }

    /**
     * Advances to the next entry in the archive.
     *
     * @return The next [MihonArchiveEntry], or `null` if all entries have been traversed.
     */
    fun getNextEntry(): MihonArchiveEntry? {
        return Archive.readNextHeader(archive).takeUnless { it == 0L }?.let { entry ->
            val name = ArchiveEntry.pathnameUtf8(entry) ?: ArchiveEntry.pathname(entry)?.decodeToString() ?: return null
            val isFile = ArchiveEntry.filetype(entry) == ArchiveEntry.AE_IFREG
            MihonArchiveEntry(name, isFile)
        }
    }
}
