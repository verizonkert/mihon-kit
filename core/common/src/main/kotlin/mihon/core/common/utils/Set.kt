package mihon.core.common.utils

/**
 * Returns a new [Set] containing the results of applying [action] to a mutable copy of this set.
 *
 * @param action Lambda modifying the mutable copy.
 * @return An immutable [Set] with the modifications applied.
 */
fun <T> Set<T>.mutate(action: (MutableSet<T>) -> Unit): Set<T> {
    return toMutableSet().apply(action)
}

