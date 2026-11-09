package eu.kanade.tachiyomi.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

/**
 * An OkHttp [ResponseBody] decorator that monitors download progress using a [ForwardingSource].
 *
 * @param responseBody The original HTTP response body to wrap.
 * @param progressListener Listener notified as byte chunks are read.
 * @param existingSize Bytes already downloaded on disk (used for resumed downloads).
 */
class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener,
    private val existingSize: Long, // bytes already downloaded
) : ResponseBody() {

    private val bufferedSource: BufferedSource by lazy {
        source(responseBody.source()).buffer()
    }

    override fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override fun source(): BufferedSource {
        return bufferedSource
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = existingSize

            @Throws(IOException::class)
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0

                // contentLength() returns -1L if Content-Length is missing
                val totalLength = responseBody.contentLength().let {
                    if (it != -1L) it + existingSize else -1L
                }

                progressListener.update(
                    totalBytesRead,
                    totalLength,
                    bytesRead == -1L,
                )
                return bytesRead
            }
        }
    }
}
