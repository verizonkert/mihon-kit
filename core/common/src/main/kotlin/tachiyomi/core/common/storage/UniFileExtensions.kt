package tachiyomi.core.common.storage

import com.hippo.unifile.UniFile

/** Returns the file extension of this [UniFile] (excluding dot), or null if none exists. */
val UniFile.extension: String?
    get() = name?.substringAfterLast('.')

/** Returns the filename of this [UniFile] without its trailing extension. */
val UniFile.nameWithoutExtension: String?
    get() = name?.substringBeforeLast('.')

/** Returns a human-readable display path, preferring [UniFile.getFilePath] over the raw URI string. */
val UniFile.displayablePath: String
    get() = filePath ?: uri.toString()

