package mihon.core.metro

import android.content.Context

/**
 * Retrieves the Metro dependency injection graph [T] from the [Context]'s [android.app.Application]
 * by casting it to [GraphProvider].
 *
 * @param T The expected graph interface type.
 * @return The resolved graph instance.
 */
@Suppress("UNCHECKED_CAST")
fun <T> Context.metroGraph(): T = (applicationContext as GraphProvider<T>).graph

