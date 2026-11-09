package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reactive wrapper around a single key-value setting.
 * Supports synchronous retrieval, reactive flows, and coroutine [StateFlow] binding.
 *
 * @param T The type of value stored in this preference.
 */
interface Preference<T> {

    /** Returns the unique storage key for this preference. */
    fun key(): String

    /** Returns the current value synchronously, or [defaultValue] if unset. */
    fun get(): T

    /** Persists a new [value] to storage and notifies change observers. */
    fun set(value: T)

    /** Returns true if this key currently exists in storage. */
    fun isSet(): Boolean

    /** Deletes this key from storage, reverting it to [defaultValue]. */
    fun delete()

    /** Returns the default fallback value. */
    fun defaultValue(): T

    /** Emits updates as a cold [Flow] whenever this preference changes. */
    fun changes(): Flow<T>

    /** Converts this preference into a hot [StateFlow] within the provided [scope]. */
    fun stateIn(scope: CoroutineScope): StateFlow<T>

    companion object {
        /**
         * A preference that should not be exposed in places like backups without user consent.
         */
        fun isPrivate(key: String): Boolean {
            return key.startsWith(PRIVATE_PREFIX)
        }
        fun privateKey(key: String): String {
            return "$PRIVATE_PREFIX$key"
        }

        /**
         * A preference used for internal app state that isn't really a user preference
         * and therefore should not be in places like backups.
         */
        fun isAppState(key: String): Boolean {
            return key.startsWith(APP_STATE_PREFIX)
        }
        fun appStateKey(key: String): String {
            return "$APP_STATE_PREFIX$key"
        }

        private const val APP_STATE_PREFIX = "__APP_STATE_"
        private const val PRIVATE_PREFIX = "__PRIVATE_"
    }
}

inline fun <reified T, R : T> Preference<T>.getAndSet(crossinline block: (T) -> R) = set(
    block(get()),
)

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.plusAssign(items: Iterable<T>) {
    set(get() + items)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}
