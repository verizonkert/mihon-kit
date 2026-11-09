package mihon.core.common

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Global feature flags and system identifier utilities.
 */
object FeatureFlags {

    /**
     * Generates a unique random installation UUID formatted with hyphens.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newInstallationId(): String {
        return Uuid.random().toHexDashString()
    }
}
