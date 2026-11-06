package com.amazon.tv.leanbacklauncher.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.channels.TvChannelsRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Daftar aplikasi yang membagikan Channels / Watch Next, masing-masing dengan switch on/off.
 * Aplikasi yang diaktifkan akan memunculkan program-programnya di baris "Rekomendasi" layar utama.
 * Didefinisikan di prefs.xml.
 */
class ChannelsPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    private var screen: PreferenceScreen? = null
    private var executor: ExecutorService? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        screen = preferenceManager.createPreferenceScreen(preferenceManager.context).also {
            it.title = getString(R.string.channels_sources_title)
        }
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        val appContext = requireContext().applicationContext
        val exec = Executors.newSingleThreadExecutor().also { executor = it }
        exec.execute {
            val sources = try {
                TvChannelsRepository.loadSources(appContext)
            } catch (_: Exception) {
                emptyList()
            }
            val granted = TvChannelsRepository.hasPermission(appContext)
            main.post { if (isAdded) showSources(sources, granted) }
        }
    }

    override fun onPause() {
        executor?.shutdownNow()
        executor = null
        super.onPause()
    }

    private fun showSources(sources: List<TvChannelsRepository.AppSource>, permissionGranted: Boolean) {
        val root = screen ?: return
        root.removeAll()
        val ctx = preferenceManager.context
        if (!permissionGranted) {
            root.addPreference(infoPreference("no_permission", getString(R.string.channels_sources_no_permission)))
        }
        if (sources.isEmpty()) {
            root.addPreference(infoPreference("empty", getString(R.string.channels_sources_empty)))
            return
        }
        val enabled = TvChannelsRepository.enabledPackages(ctx)
        val pm = ctx.packageManager
        for (source in sources) {
            root.addPreference(SwitchPreference(ctx).apply {
                key = KEY_PREFIX + source.packageName
                title = source.label
                summary = ctx.resources.getQuantityString(
                    R.plurals.channels_sources_programs, source.programCount, source.programCount
                )
                isPersistent = false // disimpan sendiri lewat TvChannelsRepository
                isChecked = source.packageName in enabled
                try {
                    icon = pm.getApplicationIcon(source.packageName)
                } catch (_: Exception) {
                }
            })
        }
    }

    private fun infoPreference(prefKey: String, text: String) = Preference(preferenceManager.context).apply {
        key = prefKey
        title = text
        isEnabled = false
        isSelectable = false
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key
        if (preference is SwitchPreference && key.startsWith(KEY_PREFIX)) {
            TvChannelsRepository.setEnabled(
                requireContext(), key.removePrefix(KEY_PREFIX), preference.isChecked
            )
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    companion object {
        private const val KEY_PREFIX = "tvch_"
    }
}
