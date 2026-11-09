package mihon.core.archive

/**
 * Represents an entry within an archive (e.g. zip, cbz, epub).
 *
 * @property name The relative path or name of the file or directory in the archive.
 * @property isFile True if this entry represents a regular file, false if it is a directory.
 */
class ArchiveEntry(
    val name: String,
    val isFile: Boolean,
)

