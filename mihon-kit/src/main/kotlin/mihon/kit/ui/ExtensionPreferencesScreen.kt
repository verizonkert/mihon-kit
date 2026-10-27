package mihon.kit.ui

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.DialogPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.kit.MihonKit

/**
 * A Jetpack Compose screen that renders the native AndroidX PreferenceScreen of a [ConfigurableSource].
 *
 * This allows user customization for:
 * - Domain / mirror switches
 * - Image quality / format options
 * - Login credentials & tokens
 * - Custom User-Agents and HTTP headers
 *
 * @param sourceId The ID of the extension source.
 * @param modifier Composable modifier.
 */
@Composable
fun ExtensionPreferencesScreen(
    sourceId: Long,
    modifier: Modifier = Modifier,
) {
    val configurable = MihonKit.getConfigurableSource(sourceId)
    if (configurable == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "This extension does not provide custom preferences.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    if (fragmentActivity == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Preferences screen requires a FragmentActivity context.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        return
    }

    FragmentContainer(
        fragmentManager = fragmentActivity.supportFragmentManager,
        modifier = modifier.fillMaxSize(),
    ) { containerId ->
        replace(containerId, ExtensionPreferenceFragment.newInstance(sourceId))
    }
}

/**
 * Fragment that populates and manages an extension's [PreferenceScreen].
 */
class ExtensionPreferenceFragment : PreferenceFragmentCompat() {

    override fun getContext(): Context? {
        val superCtx = super.getContext() ?: return null
        val tv = TypedValue()
        val hasTheme = superCtx.theme.resolveAttribute(androidx.preference.R.attr.preferenceTheme, tv, true)
        return if (hasTheme) {
            ContextThemeWrapper(superCtx, tv.resourceId)
        } else {
            superCtx
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val sourceId = arguments?.getLong(ARG_SOURCE_ID) ?: return
        val configurable = MihonKit.getConfigurableSource(sourceId) ?: return

        // Scoped preferences storage
        val prefs = configurable.getSourcePreferences()
        preferenceManager.sharedPreferencesName = "source_$sourceId"

        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Preload preferences
                prefs.all
            }
            configurable.setupPreferenceScreen(screen)
            cleanPreferenceDisplay(screen)
        }
    }

    private fun cleanPreferenceDisplay(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            pref.isIconSpaceReserved = false
            pref.isSingleLineTitle = false
            if (pref is DialogPreference && pref.dialogTitle.isNullOrEmpty()) {
                pref.dialogTitle = pref.title
            }
            if (pref is PreferenceGroup) {
                cleanPreferenceDisplay(pref)
            }
        }
    }

    companion object {
        private const val ARG_SOURCE_ID = "arg_source_id"

        fun newInstance(sourceId: Long): ExtensionPreferenceFragment {
            return ExtensionPreferenceFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SOURCE_ID, sourceId)
                }
            }
        }
    }
}

/**
 * Renders an Android Fragment inside a Jetpack Compose hierarchy.
 */
@Composable
private fun FragmentContainer(
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier,
    commit: FragmentTransaction.(containerId: Int) -> Unit,
) {
    val containerId by rememberSaveable { mutableIntStateOf(View.generateViewId()) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = containerId }
        },
        update = { view ->
            if (!initialized) {
                fragmentManager.commit { commit(view.id) }
                initialized = true
            }
        },
    )
}
