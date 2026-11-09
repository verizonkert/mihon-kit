package tachiyomi.core.common.storage

import java.io.File

/**
 * Interface that provides root directories for internal storage, backup, or downloads.
 */
interface FolderProvider {

    /** Returns the resolved [File] handle of the directory. */
    fun directory(): File

    /** Returns the absolute filesystem path string. */
    fun path(): String
}

