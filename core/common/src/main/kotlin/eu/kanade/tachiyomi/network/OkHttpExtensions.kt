package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import java.io.IOException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resumeWithException

val jsonMime = "application/json; charset=utf-8".toMediaType()

@OptIn(ExperimentalAtomicApi::class)
@Deprecated("Use suspend APIs instead")
fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Since Call is a one-shot type, clone it for each new subscriber.
        val call = clone()

        // Wrap the call in a helper which handles both unsubscription and backpressure.
        val requestArbiter = object : Producer, Subscription {
            val boolean = AtomicBoolean(false)
            override fun request(n: Long) {
                if (n == 0L || !boolean.compareAndSet(expectedValue = false, newValue = true)) return

                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(e)
                    }
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean {
                return call.isCanceled()
            }
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> {
    @Suppress("DEPRECATION")
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }
}

// Based on https://github.com/square/okhttp/blob/master/okhttp-coroutines/src/main/kotlin/okhttp3/coroutines/ExecuteAsync.kt
// and https://github.com/gildor/kotlin-coroutines-okhttp
private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            try {
                this.cancel()
            } catch (_: Throwable) {
                // ignore
            }
        }

        this.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                val exception = IOException(e.message, e).apply { stackTrace = callStack }
                continuation.resumeWithException(exception)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ ->
                    value.close()
                }
            }
        })
    }
}

/**
 * Asynchronously executes this [Call] without blocking a thread using Kotlin coroutines.
 * Automatically handles coroutine cancellation by canceling the underlying HTTP call.
 *
 * @return The [Response] from the server.
 * @throws IOException If the request could not be executed due to cancellation or network connectivity issues.
 */
suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * Asynchronously executes this [Call] and validates that the response returned 2xx status.
 *
 * @return The successful [Response].
 * @throws HttpException If the HTTP status code is not 2xx.
 * @throws IOException If a network error occurs.
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code).apply { stackTrace = callStack }
    }
    return response
}

/**
 * Creates an [OkHttpClient] call that bypasses local HTTP cache and streams download progress
 * through the supplied [listener].
 *
 * @param request The HTTP request to perform.
 * @param listener Receives progress events (bytes read, total bytes, completion).
 * @param existingSize Resumes partial downloads via `Range` header if greater than 0.
 */
fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
    existingSize: Long = 0L,
): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .apply {
                    if (existingSize > 0 && request.header("Range") == null) {
                        header("Range", "bytes=$existingSize-")
                    }
                }
                .build()

            val originalResponse = chain.proceed(request)
            val actualExistingSize = if (originalResponse.code == 206) existingSize else 0L
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, listener, actualExistingSize))
                .build()
        }
        .build()

    return progressClient.newCall(request)
}

context(_: Json)
inline fun <reified T> Response.parseAs(): T {
    return decodeFromJsonResponse(serializer(), this)
}

context(json: Json)
fun <T> decodeFromJsonResponse(
    deserializer: DeserializationStrategy<T>,
    response: Response,
): T {
    return response.body.source().use {
        json.decodeFromBufferedSource(deserializer, it)
    }
}
