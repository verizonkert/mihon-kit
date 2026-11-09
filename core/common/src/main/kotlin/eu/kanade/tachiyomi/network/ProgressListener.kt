package eu.kanade.tachiyomi.network

/**
 * Callback listener interface for observing file or image download progress.
 */
interface ProgressListener {
    /**
     * Called whenever a chunk of data is transferred.
     *
     * @param bytesRead Total number of bytes downloaded so far (including resume offsets).
     * @param contentLength Total size in bytes of the resource, or -1 if unknown / chunked transfer.
     * @param done True when the end of stream has been reached.
     */
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}

