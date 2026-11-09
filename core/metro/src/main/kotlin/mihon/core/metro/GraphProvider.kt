package mihon.core.metro

/**
 * Interface for application components or contexts that expose a Metro dependency injection graph.
 *
 * @param T The type of the dependency graph interface.
 */
interface GraphProvider<T> {
    val graph: T
}

