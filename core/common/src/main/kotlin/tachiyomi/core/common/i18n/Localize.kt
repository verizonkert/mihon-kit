package tachiyomi.core.common.i18n

import android.content.Context
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Plural
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

/**
 * Resolves a MOKO [StringResource] to a localized [String] using this [Context].
 */
fun Context.stringResource(resource: StringResource): String {
    return StringDesc.Resource(resource).toString(this).fixed()
}

/**
 * Resolves a formatted MOKO [StringResource] with arguments to a localized [String].
 */
fun Context.stringResource(resource: StringResource, vararg args: Any): String {
    return StringDesc.ResourceFormatted(resource, *args).toString(this).fixed()
}

/**
 * Resolves a MOKO [PluralsResource] matching the given [count] to a localized [String].
 */
fun Context.pluralStringResource(resource: PluralsResource, count: Int): String {
    return StringDesc.Plural(resource, count).toString(this).fixed()
}

/**
 * Resolves a formatted MOKO [PluralsResource] matching [count] with arguments to a localized [String].
 */
fun Context.pluralStringResource(resource: PluralsResource, count: Int, vararg args: Any): String {
    return StringDesc.PluralFormatted(resource, count, *args).toString(this).fixed()
}

// TODO: janky workaround for https://github.com/icerockdev/moko-resources/issues/337
private fun String.fixed() =
    this.replace("""\""", """"""")
