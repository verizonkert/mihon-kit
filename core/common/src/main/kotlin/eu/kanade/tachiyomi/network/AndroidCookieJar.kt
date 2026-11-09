package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * An OkHttp [CookieJar] implementation synchronized with Android's system [CookieManager].
 *
 * This allows sharing authentication, sessions, and Cloudflare clearance cookies between
 * OkHttp background requests and embedded WebViews.
 */
class AndroidCookieJar : CookieJar {

    private val manager by lazy { CookieManager.getInstance() }

    /**
     * Saves cookies received from an HTTP response directly into Android's [CookieManager].
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()

        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    /**
     * Loads cookies for an outgoing HTTP request from [CookieManager].
     */
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return get(url)
    }

    /**
     * Retrieves parsed OkHttp [Cookie] objects for the specified [url].
     */
    fun get(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())

        return if (cookies != null && cookies.isNotEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it) }
        } else {
            emptyList()
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) {
                this.filter { it in cookieNames }
            } else {
                this
            }
        }

        return cookies.split(";")
            .map { it.substringBefore("=") }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
            .count()
    }

    fun removeAll() {
        manager.removeAllCookies {}
    }
}
