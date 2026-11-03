package com.amazon.tv.leanbacklauncher.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.isDigitsOnly
import androidx.fragment.app.Fragment
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences
import com.amazon.tv.firetv.leanbacklauncher.util.FireTVUtils
import com.amazon.tv.firetv.leanbacklauncher.util.SharedPreferencesUtil
import com.amazon.tv.leanbacklauncher.BuildConfig
import com.amazon.tv.leanbacklauncher.LauncherApp
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.apps.AppsManager
import com.amazon.tv.leanbacklauncher.util.DefaultLauncher
import com.amazon.tv.leanbacklauncher.util.Permission
import com.amazon.tv.leanbacklauncher.util.Util
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class SettingsFragment : LeanbackSettingsFragmentCompat() {
    companion object {
        private val TAG by lazy {
            if (BuildConfig.DEBUG) ("[*]" + SettingsFragment::class.java.simpleName).take(
                21
            ) else SettingsFragment::class.java.simpleName
        }
        var needRestartHome = false

        private const val PKG = "com.amazon.tv.leanbacklauncher.settings."
        private val DEEP_LINK_FRAGMENTS = setOf(
            PKG + "LegacyAppsAndGamesPreferenceFragment",
            PKG + "LegacyHiddenPreferenceFragment",
            PKG + "HomePreferenceFragment",
            PKG + "HiddenPreferenceFragment",
            PKG + "LanguagePreferenceFragment",
            PKG + "VideoPlaylistPreferenceFragment",
            PKG + "DefaultLauncherPreferenceFragment",
            PKG + "RecommendationsPreferenceFragment",
            PKG + "ChannelsPreferenceFragment",
            PKG + "WeatherPreferenceFragment",
            PKG + "PrayerPreferenceFragment",
            PKG + "VersionPreferenceFragment",
        )
    }

    override fun onPreferenceStartInitialScreen() {
        needRestartHome = false // reset state
        // Dibuka dari halaman "Sesuaikan TV Anda": langsung ke sub-pengaturan yang dipilih.
        val target = requireActivity().intent?.getStringExtra(SettingsActivity.EXTRA_FRAGMENT)
        if (target != null && target in DEEP_LINK_FRAGMENTS) {
            val f = childFragmentManager.fragmentFactory.instantiate(requireActivity().classLoader, target)
            showInPanel(f)
            return
        }
        startPreferenceFragment(LauncherSettingsFragment())
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat?,
        pref: Preference
    ): Boolean {
        val args: Bundle = pref.extras
        val f: Fragment = childFragmentManager.fragmentFactory.instantiate(
            requireActivity().classLoader, pref.fragment
        )
        f.arguments = args
        @Suppress("DEPRECATION")
        f.setTargetFragment(caller, 0)
        showInPanel(f)
        return true
    }

    /**
     * Semua layar pengaturan tampil di panel samping yang sama. Hanya fragment GuidedStep
     * (layar Legacy layar penuh) yang tetap memakai container immersive.
     */
    fun showPreferencePanel(f: Fragment) {
        if (f is GuidedStepSupportFragment) startImmersiveFragment(f) else startPreferenceFragment(f)
    }

    private fun showInPanel(f: Fragment) = showPreferencePanel(f)

    override fun onPreferenceStartScreen(
        caller: PreferenceFragmentCompat?,
        pref: PreferenceScreen
    ): Boolean {
        val fragment: Fragment = LauncherSettingsFragment()
        val args = Bundle(1)
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
        fragment.arguments = args
        startPreferenceFragment(fragment)
        return true
    }

    override fun onStop() {
        super.onStop()
        if (needRestartHome) {
            if (BuildConfig.DEBUG) Log.d(TAG, "onStop() send refresh HOME broadcast")
            Util.refreshHome(LauncherApp.context)
            needRestartHome = false
        }
    }
}

/**
 * The fragment that is embedded in SettingsFragment
 */
class LauncherSettingsFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.prefs, rootKey)

        findPreference<ListPreference>("app_language")?.apply {
            entries = LauncherLanguage.labels(requireContext())
            entryValues = LauncherLanguage.tags
            value = LauncherLanguage.tags[LauncherLanguage.currentIndex()]
            setOnPreferenceChangeListener { _, newValue ->
                LauncherLanguage.apply(LauncherLanguage.tags.indexOf(newValue?.toString()).coerceAtLeast(0))
                SettingsFragment.needRestartHome = true
                true
            }
        }

        findPreference<Preference>("version")?.apply {
            summary = "${getString(R.string.app_name)} v" + BuildConfig.VERSION_NAME
            isSelectable = true
            setOnPreferenceClickListener {
                (parentFragment as? SettingsFragment)?.showPreferencePanel(VersionPreferenceFragment())
                    ?: return@setOnPreferenceClickListener false
                true
            }
        }

        findPreference<Preference>("set_default_launcher")?.apply {
            summary = getString(
                if (DefaultLauncher.isDefault(requireActivity())) R.string.default_launcher_already_desc
                else R.string.default_launcher_action_desc
            )
            setOnPreferenceClickListener {
                DefaultLauncher.request(requireActivity())
                true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ps = findPreference<PreferenceScreen>("root_prefs")
            ps?.removePreferenceRecursively("rec_sources")
        }
    }
}

/**
 * The fragment that is defined in home_prefs.xml
 */
class BannersPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.banners_prefs, rootKey)
        findPreference<EditTextPreference>(getString(R.string.pref_banner_focus_frame_color))?.apply {
            val color = hexStringColor(RowPreferences.getFrameColor(context))
            this.summary = "$color (#AARRGGBB)"
        }
    }

    private fun hexStringColor(color: Int): String {
        return String.format("#%08X", -0x1 and color)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return super.onPreferenceTreeClick(preference)
    }
}

/**
 * The fragment that is defined in home_prefs.xml
 */
class AppRowsPreferenceFragment : LeanbackPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.rows_prefs, rootKey)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ps = findPreference<PreferenceScreen>("rows_prefs")
            ps?.removePreferenceRecursively(getString(R.string.pref_enable_recommendations_row))
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        val ctx = requireContext()
        if (preference?.key == getString(R.string.pref_enable_recommendations_row)) {
            val enabled = (preference as SwitchPreference).isChecked
            RowPreferences.setRecommendationsEnabled(ctx, enabled)
            if (enabled && FireTVUtils.isAmazonNotificationsEnabled(ctx)) {
                LauncherApp.toast(getString(R.string.recs_warning_sale))
            }
            // refresh home broadcast
            SettingsFragment.needRestartHome = true
            return true
        }
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return super.onPreferenceTreeClick(preference)
    }
}

/**
 * The fragment that is defined in home_prefs.xml
 */
class WallpaperFragment : LeanbackPreferenceFragmentCompat() {

    override fun onResume() {
        super.onResume()
        // check permissions
        Permission.isReadStoragePermissionGranted(requireActivity())
        findPreference<Preference>("select_wallpaper")?.apply {
            this.summary = getWallpaperDesc(context)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Load the prefs from an XML resource
        setPreferencesFromResource(R.xml.wallpaper_prefs, rootKey)

        findPreference<Preference>("select_wallpaper")?.apply {
            this.summary = getWallpaperDesc(context)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        return when (preference?.key) {
            "reset_wallpaper" -> {
                resetWallpaper()
                parentFragmentManager?.popBackStack()
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    private fun resetWallpaper(): Boolean {
        val context = requireContext()
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        pref.edit().remove("wallpaper_image").apply()
        val file = File(context.getExternalFilesDir(null), "background.jpg")
        if (file.exists()) {
            try {
                file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return true
    }

    private fun getWallpaperDesc(context: Context): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val imagePath = pref.getString("wallpaper_image", "")
        return if (!imagePath.isNullOrEmpty()) {
            imagePath
        } else {
            val file = File(context.getExternalFilesDir(null), "background.jpg")
            if (file.canRead()) {
                file.toString()
            }
            getString(R.string.select_wallpaper_action_desc)
        }
    }
}

/**
 * The fragment that is defined in wallpaper_prefs.xml
 */
class FileListFragment : LeanbackPreferenceFragmentCompat() {

    private var screen: PreferenceScreen? = null

    companion object {
        private var rootPath: String? = null
        private var dirName: String? = null

        /* Action ID definition */
        private const val KEY_BACK = 1
        private const val KEY_DIR = 2
        private const val KEY_SELECT = 3
        private const val ARG_SUBDIR = "file_list_subdir"
        private var rootDepth = -1
    }

    override fun onResume() {
        super.onResume()
        // Kedalaman back stack folder awal bergantung pada cara pengaturan dibuka
        // (menu utama atau langsung dari "Home Screen View"), jadi dicatat saat pertama tampil.
        val depth = parentFragmentManager.backStackEntryCount
        if (arguments?.getBoolean(ARG_SUBDIR) != true && rootDepth < 0) rootDepth = depth
        if (depth == rootDepth) rootPath = Environment.getExternalStorageDirectory().absolutePath
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Folder awal (bukan subfolder) dibuat ulang: kedalaman akar dicatat lagi di onResume().
        if (arguments?.getBoolean(ARG_SUBDIR) != true) rootDepth = -1
        val context = preferenceManager.context
        screen = preferenceManager.createPreferenceScreen(context)
        screen?.title = getString(R.string.select_wallpaper_action_title)
        preferenceScreen = screen

        if (rootPath.isNullOrEmpty())
            rootPath = Environment.getExternalStorageDirectory().absolutePath

        loadFileList()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        val ctx = requireContext()
        val fm = parentFragmentManager

        when (preference?.key) {
            KEY_BACK.toString() -> {
                dirName?.let { rootPath = rootPath?.removeSuffix(it) }
                fm?.popBackStack()
                return true
            }
            KEY_DIR.toString() -> {
                rootPath = File(rootPath, preference.title.toString()).absolutePath
                dirName = preference.title.toString()
                val curFragContainerId = (requireView().parent as ViewGroup).id
                fm?.beginTransaction()?.replace(curFragContainerId, FileListFragment().apply {
                    arguments = Bundle().apply { putBoolean(ARG_SUBDIR, true) }
                })
                    ?.addToBackStack(null)?.commit()
                return true
            }
            KEY_SELECT.toString() -> {
                val name = preference.title
                val file = File(rootPath, name.toString())
                if (file.canRead())
                    setWallpaper(ctx, file.path.toString())
                else
                    LauncherApp.toast(getString(R.string.file_no_access))
                dirName?.let { rootPath = rootPath?.removeSuffix(it) }
                fm?.popBackStack()
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    private fun loadFileList() {
        var dir: File? = null
        var folders: ArrayList<File> = ArrayList()
        var images: ArrayList<File> = ArrayList()
        if (BuildConfig.DEBUG) Log.d("*****", "loadFileList($rootPath)")
        rootPath?.let { dir = File(it) }
        dir?.let {
            folders = dirReader(it)
            images = imageReader(it)
        }
        val prefs = ArrayList<Preference>()

        // back
        val backPref = Preference(context)
        backPref.key = KEY_BACK.toString()
        backPref.title = getString(R.string.goback)
        backPref.icon =
            ResourcesCompat.getDrawable(resources, R.drawable.ic_twotone_arrow_back_24, null)
        prefs.add(backPref)

        // directories
        if (folders.size > 0)
            folders.forEach {
                val dirPref = Preference(context)
                dirPref.key = KEY_DIR.toString()
                dirPref.title = it.name
                dirPref.icon =
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_twotone_folder_24,
                        null
                    )
                prefs.add(dirPref)
            }

        // images
        if (images.size > 0)
            images.forEach {
                val imagePref = Preference(context)
                imagePref.key = KEY_SELECT.toString()
                imagePref.title = it.name
                try {
                    val bannerWidth =
                        resources.getDimensionPixelSize(R.dimen.preference_item_banner_width)
                    val bannerHeight =
                        resources.getDimensionPixelSize(R.dimen.preference_item_banner_height)
                    Glide.with(requireContext())
                        .asDrawable()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .load(Uri.fromFile(it))
                        .into(object : CustomTarget<Drawable?>(bannerWidth, bannerHeight) {
                            override fun onResourceReady(
                                resource: Drawable,
                                transition: Transition<in Drawable?>?
                            ) {
                                imagePref.icon = resource
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }
                        })
                } catch (_: java.lang.Exception) {
                }
                prefs.add(imagePref)
            }

        // apply
        if (prefs.isNotEmpty()) {
            for (pref in prefs) {
                screen?.addPreference(pref)
            }
        }
    }

    private fun imageReader(root: File): ArrayList<File> {
        val imageList: ArrayList<File> = ArrayList()
        val images = root.listFiles { file ->
            (!file.isDirectory &&
                    file.name.endsWith(".jpeg") ||
                    file.name.endsWith(".jpg") ||
                    file.name.endsWith(".png"))
        }
        if (!images.isNullOrEmpty()) {
            for (image in images) {
                imageList.add(image.absoluteFile)
            }
        }
        return imageList
    }

    private fun dirReader(root: File): ArrayList<File> {
        val dirList: ArrayList<File> = ArrayList()
        val dirs = root.listFiles { file ->
            file.isDirectory && !file.name.startsWith(".")
        }
        if (!dirs.isNullOrEmpty())
            for (dir in dirs) {
                dirList.add(dir.absoluteFile)
            }
        return dirList
    }

    private fun setWallpaper(context: Context?, image: String): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        if (image.isNotEmpty()) pref.edit().putString("wallpaper_image", image).apply()
        // refresh home broadcast
        SettingsFragment.needRestartHome = true
        return true
    }
}

/**
 * The fragment that is defined in prefs.xml
 */
class WeatherPreferenceFragment : LeanbackPreferenceFragmentCompat() {

    /**
     * Hanya pengaturan cuaca yang membuat respons cuaca lama tidak berlaku. Pengaturan waktu
     * sholat diamati langsung oleh PrayerTimesOverlay dan tidak boleh menghapus cache cuaca.
     */
    private val weatherKeys by lazy {
        setOf(
            getString(R.string.pref_enable_local_weather),
            getString(R.string.pref_use_current_location),
            getString(R.string.pref_user_location),
            getString(R.string.pref_show_location),
            getString(R.string.pref_imp_weather),
        )
    }

    // Dipantau saat nilai benar-benar tersimpan (bukan saat diklik): untuk kolom teks, nilai baru
    // baru tersimpan setelah dialog ditutup, sehingga menghapus cache saat klik terlalu cepat.
    private val weatherChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in weatherKeys) {
            // Keep the last successful forecast; MainActivity restores it after recreation.
            SettingsFragment.needRestartHome = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(weatherChangeListener)
    }

    override fun onDestroy() {
        context?.let {
            PreferenceManager.getDefaultSharedPreferences(it)
                .unregisterOnSharedPreferenceChangeListener(weatherChangeListener)
        }
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.weather_prefs, rootKey)
        preferenceScreen?.let { addPrayerControls(this, it) }
    }
}

/**
 * Shared, persistent cache for AlAdhan calculation methods.
 * The list is prefetched at application startup and reused immediately by settings.
 */
internal object PrayerMethodRepository {
    private const val CACHE_KEY = "prayer_methods_cache"
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var loading = false
    private val callbacks = mutableListOf<(List<Pair<String, String>>) -> Unit>()

    fun cached(context: Context): List<Pair<String, String>> {
        return try {
            val raw = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString(CACHE_KEY, null) ?: return emptyList()
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) id to name else null
            }
        } catch (_: Exception) { emptyList() }
    }

    fun prefetch(context: Context, callback: ((List<Pair<String, String>>) -> Unit)? = null) {
        val appContext = context.applicationContext
        synchronized(this) {
            if (callback != null) callbacks += callback
            if (loading) return
            loading = true
        }
        executor.execute {
            val methods = try {
                val connection = (java.net.URL("https://api.aladhan.com/v1/methods")
                    .openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                }
                try {
                    if (connection.responseCode !in 200..299) error("AlAdhan methods unavailable")
                    val data = org.json.JSONObject(
                        connection.inputStream.bufferedReader().use { it.readText() }
                    ).getJSONObject("data")
                    val result = mutableListOf<Pair<String, String>>()
                    val keys = data.keys()
                    while (keys.hasNext()) {
                        val item = data.optJSONObject(keys.next()) ?: continue
                        val id = item.optInt("id", -1)
                        val name = item.optString("name")
                        if (id >= 0 && name.isNotBlank() && id != 99) result += id.toString() to name
                    }
                    result.sortedBy { it.second.lowercase(java.util.Locale.ROOT) }
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                android.util.Log.w("PrayerMethodRepository", "Unable to refresh calculation methods", error)
                emptyList()
            }

            val result = if (methods.isNotEmpty()) {
                val array = org.json.JSONArray()
                methods.forEach { (id, name) ->
                    array.put(org.json.JSONObject().put("id", id).put("name", name))
                }
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
                    .edit().putString(CACHE_KEY, array.toString()).apply()
                methods
            } else {
                cached(appContext)
            }

            val pending = synchronized(this) {
                loading = false
                callbacks.toList().also { callbacks.clear() }
            }
            main.post { pending.forEach { it(result) } }
        }
    }
}


/** Prayer calculation controls reuse the location configured in Weather Preferences. */
private fun addPrayerControls(fragment: PreferenceFragmentCompat, root: PreferenceScreen) {
    val ctx = fragment.requireContext()
    val prayerCategory = PreferenceCategory(ctx).apply { title = fragment.getString(R.string.prayer_settings_title) }
    root.addPreference(prayerCategory)
    val method = ListPreference(ctx).apply {
        key = "prayer_method"
        title = fragment.getString(R.string.prayer_method_title)
        summary = fragment.getString(R.string.prayer_method_desc)
        setDefaultValue("20")
        entries = arrayOf(fragment.getString(R.string.prayer_settings_loading))
        entryValues = arrayOf("20")
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            listOf("prayer_custom_fajr", "prayer_custom_maghrib", "prayer_custom_isha")
                .forEach { root.findPreference<Preference>(it)?.isVisible = value.toString() == "99" }
            true
        }
    }
    prayerCategory.addPreference(method)
    fun addDecimal(key: String, titleId: Int, default: String) {
        prayerCategory.addPreference(EditTextPreference(ctx).apply {
            this.key = key
            title = fragment.getString(titleId)
            setDefaultValue(default)
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            setOnBindEditTextListener { it.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                val text = value.toString().trim()
                text.isEmpty() || (text.toDoubleOrNull()?.let { it in 0.0..90.0 } == true)
            }
        })
    }
    addDecimal("prayer_custom_fajr", R.string.prayer_custom_fajr, "18")
    addDecimal("prayer_custom_maghrib", R.string.prayer_custom_maghrib, "")
    addDecimal("prayer_custom_isha", R.string.prayer_custom_isha, "18")
    val offsets = prayerCategory
    listOf(
        "prayer_offset_fajr" to R.string.prayer_offset_fajr,
        "prayer_offset_dhuhr" to R.string.prayer_offset_dhuhr,
        "prayer_offset_asr" to R.string.prayer_offset_asr,
        "prayer_offset_maghrib" to R.string.prayer_offset_maghrib,
        "prayer_offset_isha" to R.string.prayer_offset_isha
    ).forEach { (key, titleId) ->
        prayerCategory.addPreference(EditTextPreference(ctx).apply {
            this.key = key
            title = fragment.getString(titleId)
            summary = fragment.getString(R.string.prayer_offset_desc)
            setDefaultValue("0")
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            setOnBindEditTextListener { it.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                value.toString().toIntOrNull()?.let { it in -180..180 } == true
            }
        })
    }
    val selected = PreferenceManager.getDefaultSharedPreferences(ctx).getString("prayer_method", "20")
    listOf("prayer_custom_fajr", "prayer_custom_maghrib", "prayer_custom_isha")
        .forEach { root.findPreference<Preference>(it)?.isVisible = selected == "99" }
    method.value = selected ?: "20"
    PrayerMethodRepository.prefetch(ctx) { methods ->
        if (!fragment.isAdded) return@prefetch
        val choices = (if (methods.isEmpty()) listOf("20" to fragment.getString(R.string.prayer_method_kemenag_fallback))
            else methods) + ("99" to fragment.getString(R.string.prayer_method_custom))
        method.entries = choices.map { it.second }.toTypedArray()
        method.entryValues = choices.map { it.first }.toTypedArray()
        val saved = PreferenceManager.getDefaultSharedPreferences(ctx).getString("prayer_method", "20") ?: "20"
        method.value = if (choices.any { it.first == saved }) saved else "20"
    }
}

/** Prayer calculation settings backed by AlAdhan's live methods endpoint. */
class PrayerPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    private var root: PreferenceScreen? = null
    private var method: ListPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        root = preferenceManager.createPreferenceScreen(ctx).also {
            it.title = getString(R.string.prayer_settings_title)
        }
        preferenceScreen = root
        val methodCategory = PreferenceCategory(ctx).apply {
            title = getString(R.string.prayer_method_category)
        }
        root?.addPreference(methodCategory)
        method = ListPreference(ctx).apply {
            key = KEY_METHOD
            title = getString(R.string.prayer_method_title)
            summary = getString(R.string.prayer_method_desc)
            setDefaultValue(DEFAULT_METHOD)
            entries = arrayOf(getString(R.string.prayer_settings_loading))
            entryValues = arrayOf(DEFAULT_METHOD)
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                showCustom(value.toString() == CUSTOM_METHOD)
                true
            }
        }
        methodCategory.addPreference(method!!)
        addDecimal(methodCategory, KEY_CUSTOM_FAJR, R.string.prayer_custom_fajr, "18")
        addDecimal(methodCategory, KEY_CUSTOM_MAGHRIB, R.string.prayer_custom_maghrib, "")
        addDecimal(methodCategory, KEY_CUSTOM_ISHA, R.string.prayer_custom_isha, "18")

        val offsets = PreferenceCategory(ctx).apply {
            title = getString(R.string.prayer_offset_category)
        }
        root?.addPreference(offsets)
        addOffset(offsets, KEY_OFFSET_FAJR, R.string.prayer_offset_fajr)
        addOffset(offsets, KEY_OFFSET_DHUHR, R.string.prayer_offset_dhuhr)
        addOffset(offsets, KEY_OFFSET_ASR, R.string.prayer_offset_asr)
        addOffset(offsets, KEY_OFFSET_MAGHRIB, R.string.prayer_offset_maghrib)
        addOffset(offsets, KEY_OFFSET_ISHA, R.string.prayer_offset_isha)
        val selected = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
            .getString(KEY_METHOD, DEFAULT_METHOD)
        showCustom(selected == CUSTOM_METHOD)
        // Populate from disk immediately; refresh the shared cache in the background.
        // Even on a first/offline launch, show the built-in Kemenag option immediately.
        applyMethods(PrayerMethodRepository.cached(ctx))
        PrayerMethodRepository.prefetch(ctx) { applyMethods(it) }
    }

    private fun addDecimal(category: PreferenceCategory, key: String, titleId: Int, default: String) {
        category.addPreference(EditTextPreference(preferenceManager.context).apply {
            this.key = key
            title = getString(titleId)
            setDefaultValue(default)
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            setOnBindEditTextListener { it.inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                val text = value.toString().trim()
                text.isEmpty() || (text.toDoubleOrNull()?.let { it in 0.0..90.0 } == true)
            }
        })
    }

    private fun addOffset(category: PreferenceCategory, key: String, titleId: Int) {
        category.addPreference(EditTextPreference(preferenceManager.context).apply {
            this.key = key
            title = getString(titleId)
            summary = getString(R.string.prayer_offset_desc)
            setDefaultValue("0")
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            setOnBindEditTextListener { it.inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_SIGNED }
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                value.toString().toIntOrNull()?.let { it in -180..180 } == true
            }
        })
    }

    private fun showCustom(show: Boolean) {
        listOf(KEY_CUSTOM_FAJR, KEY_CUSTOM_MAGHRIB, KEY_CUSTOM_ISHA).forEach {
            root?.findPreference<Preference>(it)?.isVisible = show
        }
    }

    private fun applyMethods(methods: List<Pair<String, String>>) {
        if (!isAdded) return
        val choices = (if (methods.isEmpty()) listOf(
            DEFAULT_METHOD to getString(R.string.prayer_method_kemenag_fallback)
        ) else methods) + (CUSTOM_METHOD to getString(R.string.prayer_method_custom))
        method?.apply {
            entries = choices.map { it.second }.toTypedArray()
            entryValues = choices.map { it.first }.toTypedArray()
            val saved = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(preferenceManager.context)
                .getString(KEY_METHOD, DEFAULT_METHOD) ?: DEFAULT_METHOD
            value = if (choices.any { it.first == saved }) saved else DEFAULT_METHOD
        }
        showCustom(method?.value == CUSTOM_METHOD)
    }

    companion object {
        private const val KEY_METHOD = "prayer_method"
        private const val KEY_OFFSET_FAJR = "prayer_offset_fajr"
        private const val KEY_OFFSET_DHUHR = "prayer_offset_dhuhr"
        private const val KEY_OFFSET_ASR = "prayer_offset_asr"
        private const val KEY_OFFSET_MAGHRIB = "prayer_offset_maghrib"
        private const val KEY_OFFSET_ISHA = "prayer_offset_isha"
        private const val KEY_CUSTOM_FAJR = "prayer_custom_fajr"
        private const val KEY_CUSTOM_MAGHRIB = "prayer_custom_maghrib"
        private const val KEY_CUSTOM_ISHA = "prayer_custom_isha"
        private const val DEFAULT_METHOD = "20"
        private const val CUSTOM_METHOD = "99"
    }
}


/**
 * Pilihan bahasa langsung tampil di panel samping (tanpa tombol perantara).
 * Memilih salah satu langsung menerapkan bahasa lalu menutup panel.
 */
class LanguagePreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)
        screen.title = getString(R.string.app_language_title)
        preferenceScreen = screen

        val labels = LauncherLanguage.labels(ctx)
        val current = LauncherLanguage.currentIndex()
        val options = ArrayList<CheckBoxPreference>()
        labels.forEachIndexed { index, label ->
            val option = CheckBoxPreference(ctx).apply {
                key = "app_language_option_$index"
                title = label
                isPersistent = false
                isChecked = index == current
                setOnPreferenceChangeListener { _, _ ->
                    // Perubahan status dikendalikan manual: pilihan bersifat tunggal (radio).
                    options.forEachIndexed { i, o -> o.isChecked = i == index }
                    chooseLanguage(index)
                    false
                }
            }
            options.add(option)
            screen.addPreference(option)
        }
    }

    private fun chooseLanguage(index: Int) {
        if (index != LauncherLanguage.currentIndex()) {
            SettingsFragment.needRestartHome = true
            LauncherLanguage.apply(index)
        }
        activity?.finish()
    }
}

/**
 * Input URL video langsung tampil di panel samping: kolom teks, bukan tombol yang membuka dialog.
 */
class VideoPlaylistPreferenceFragment : Fragment() {
    private var input: EditText? = null

    companion object {
        private const val KEY = "launcher_video_wallpaper_playlist"
        private const val TEXT_PRIMARY = 0xffeeeeee.toInt()
        private const val TEXT_SECONDARY = 0xb3eeeeee.toInt()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val column = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        column.addView(android.widget.TextView(ctx).apply {
            text = getString(R.string.tv_video_urls)
            textSize = 20f
            setTextColor(TEXT_PRIMARY)
        })
        column.addView(android.widget.TextView(ctx).apply {
            text = getString(R.string.tv_video_urls_desc)
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(8), 0, dp(16))
        })

        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val field = EditText(ctx).apply {
            hint = getString(R.string.tv_video_urls_hint)
            setHintTextColor(TEXT_SECONDARY)
            setTextColor(TEXT_PRIMARY)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 6
            maxLines = 12
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setText(prefs.getString(KEY, "")?.let { raw ->
                try {
                    val a = org.json.JSONArray(raw)
                    (0 until a.length()).joinToString("\n") { a.getString(it) }
                } catch (_: Exception) {
                    raw
                }
            } ?: "")
        }
        input = field
        column.addView(field, android.widget.LinearLayout.LayoutParams(-1, -2))

        val buttons = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        buttons.addView(android.widget.Button(ctx).apply {
            text = getString(R.string.tv_save_video_playlist)
            setOnClickListener { save() }
        })
        buttons.addView(android.widget.Button(ctx).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { activity?.finish() }
        })
        column.addView(buttons)

        return android.widget.ScrollView(ctx).apply {
            isFillViewport = true
            // Latar buram (launcher tidak tembus) dengan warna yang sama dengan panel pengaturan lain.
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.lb_preference_decor_list_background))
            addView(column)
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        input?.requestFocus()
    }

    private fun save() {
        val ctx = requireContext()
        val urls = input?.text?.toString().orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (urls.isEmpty() || urls.any { !it.startsWith("https://", true) && !it.startsWith("http://", true) }) {
            android.widget.Toast.makeText(ctx, R.string.tv_video_urls_invalid, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val json = org.json.JSONArray().apply { urls.forEach { put(it) } }.toString()
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
            .putString(KEY, json)
            .remove(TvOsCustomizationActivity.KEY_WALLPAPER_URI)
            .remove("wallpaper_image")
            .putBoolean("launcher_live_background", false)
            .apply()
        SettingsFragment.needRestartHome = true
        android.widget.Toast.makeText(ctx, getString(R.string.tv_video_playlist_saved, urls.size), android.widget.Toast.LENGTH_SHORT).show()
        activity?.finish()
    }
}

/**
 * Pilihan default launcher langsung tampil di panel samping: status, aksi, dan "Nanti".
 */
class DefaultLauncherPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    private var status: Preference? = null
    private var action: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)
        screen.title = getString(R.string.default_launcher_title)
        preferenceScreen = screen

        status = Preference(ctx).apply {
            key = "default_launcher_status"
            isSelectable = false
            isPersistent = false
        }.also { screen.addPreference(it) }

        action = Preference(ctx).apply {
            key = "default_launcher_action"
            title = getString(R.string.default_launcher_action)
            isPersistent = false
            setOnPreferenceClickListener {
                DefaultLauncher.request(requireActivity())
                true
            }
        }.also { screen.addPreference(it) }

        screen.addPreference(Preference(ctx).apply {
            key = "default_launcher_later"
            title = getString(R.string.default_launcher_later)
            isPersistent = false
            setOnPreferenceClickListener {
                activity?.finish()
                true
            }
        })
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari dialog sistem: perbarui status.
        refreshStatus()
    }

    private fun refreshStatus() {
        val isDefault = DefaultLauncher.isDefault(requireActivity())
        status?.title = getString(
            if (isDefault) R.string.default_launcher_already else R.string.default_launcher_title
        )
        status?.summary = if (isDefault) null else getString(R.string.default_launcher_message)
        action?.summary = getString(
            if (isDefault) R.string.default_launcher_already_desc else R.string.default_launcher_action_desc
        )
    }
}

/**
 * "Home Screen View" sebagai panel samping (sama seperti pengaturan lain), bukan GuidedStep.
 * Isinya sama dengan versi Legacy: baris, urutan aplikasi, banner, wallpaper.
 */
class HomePreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.home_prefs, rootKey)
        val ctx = preferenceManager.context
        findPreference<ListPreference>("apps_order")?.apply {
            entries = arrayOf(
                getString(R.string.select_app_order_action_recency),
                getString(R.string.select_app_order_action_fixed)
            )
            entryValues = arrayOf("recency", "fixed")
            value = if (AppsManager.getSavedSortingMode(ctx) == AppsManager.SortingMode.FIXED) "fixed" else "recency"
            setOnPreferenceChangeListener { _, newValue ->
                AppsManager.saveSortingMode(
                    ctx,
                    if (newValue == "fixed") AppsManager.SortingMode.FIXED else AppsManager.SortingMode.RECENCY
                )
                true
            }
        }
    }
}

/**
 * "Hidden Applications" sebagai panel samping: sakelar "tampilkan semua aplikasi" dan daftar
 * aplikasi tersembunyi (hapus centang untuk menampilkannya kembali).
 */
class HiddenPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)
        screen.title = getString(R.string.hidden_applications_title)
        preferenceScreen = screen
        val prefUtil = SharedPreferencesUtil.instance(ctx) ?: return

        screen.addPreference(SwitchPreference(ctx).apply {
            key = "hidden_show_all_apps"
            title = getString(R.string.show_all_apps)
            isPersistent = false
            isChecked = prefUtil.isAllAppsShown()
            setOnPreferenceChangeListener { _, newValue ->
                prefUtil.showAllApps(newValue as Boolean)
                SettingsFragment.needRestartHome = true
                true
            }
        })

        val pm = ctx.packageManager
        for (pkg in ArrayList(prefUtil.hiddenApps())) {
            if (pkg.isEmpty() || !prefUtil.isHidden(pkg)) continue
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                screen.addPreference(CheckBoxPreference(ctx).apply {
                    key = "hidden_app_$pkg"
                    title = pm.getApplicationLabel(info)
                    icon = pm.getApplicationIcon(info)
                    isPersistent = false
                    isChecked = true
                    setOnPreferenceChangeListener { _, newValue ->
                        if (newValue == true) prefUtil.hide(pkg) else prefUtil.unhide(pkg)
                        SettingsFragment.needRestartHome = true
                        true
                    }
                })
            } catch (_: PackageManager.NameNotFoundException) {
                // Aplikasi sudah dihapus: lewati.
            }
        }
    }
}

/**
 * Sumber rekomendasi (Android di bawah 8) sebagai panel samping, sama seperti pengaturan lain.
 * Centang = rekomendasi dari aplikasi itu ditampilkan; hapus centang untuk memblokirnya.
 */
class RecommendationsPreferenceFragment : LeanbackPreferenceFragmentCompat(),
    RecommendationsPreferenceManager.LoadRecommendationPackagesCallback {
    private var manager: RecommendationsPreferenceManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = RecommendationsPreferenceManager(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(preferenceManager.context)
        screen.title = getString(R.string.recommendation_blacklist_content_title)
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        manager?.loadRecommendationsPackages(this)
    }

    override fun onRecommendationPackagesLoaded(info: List<RecommendationsPreferenceManager.PackageInfo>?) {
        if (!isAdded) return
        val screen = preferenceScreen ?: return
        val ctx = preferenceManager.context
        screen.removeAll()
        info?.forEach { pkg ->
            val packageName = pkg.packageName ?: return@forEach
            screen.addPreference(CheckBoxPreference(ctx).apply {
                key = "rec_source_$packageName"
                title = pkg.appTitle ?: packageName
                icon = pkg.icon
                isPersistent = false
                isChecked = !pkg.blacklisted
                setOnPreferenceChangeListener { _, newValue ->
                    manager?.savePackageBlacklisted(packageName, newValue != true)
                    true
                }
            })
        }
    }
}

/**
 * App version panel.
 */
class VersionPreferenceFragment : LeanbackPreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addPreference(Preference(requireContext()).apply {
                title = getString(R.string.version)
                summary = "${getString(R.string.app_name)} v${BuildConfig.VERSION_NAME}"
                isSelectable = false
            })
            addPreference(Preference(requireContext()).apply {
                title = "Logcat"
                summary = "View, clear, and save launcher logs"
                setOnPreferenceClickListener {
                    (parentFragment as? SettingsFragment)?.showPreferencePanel(LogcatViewerFragment())
                    true
                }
            })
        }
    }
}

/**
 * Log viewer with explicit Clear, Save and Close actions.
 * The capture is repeated every time this screen is opened; no one-shot cached output is reused.
 */
class LogcatViewerFragment : Fragment() {
    companion object {
        private const val MAX_LOG_LINES = 5_000
        private const val MAX_LOG_CHARS = 1_000_000
        private const val MAX_LOG_LINE_CHARS = 8_000
    }

    private var logText: android.widget.TextView? = null
    private var latestLog = ""

    private val saveLog = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(latestLog)
                } ?: error("Unable to open destination")
            }.onFailure {
                android.widget.Toast.makeText(requireContext(), "Save failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(android.graphics.Color.rgb(24, 24, 24))
            isFocusableInTouchMode = true
        }
        root.addView(android.widget.TextView(ctx).apply {
            text = "Logcat"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
        })
        val output = android.widget.TextView(ctx).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.rgb(220, 220, 220))
            setTextIsSelectable(true)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            text = "Loading logs..."
        }
        logText = output
        root.addView(android.widget.ScrollView(ctx).apply {
            isFillViewport = true
            addView(output)
        }, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        }
        actions.addView(android.widget.Button(ctx).apply {
            text = "Clear"
            setOnClickListener { clearLogs() }
        })
        actions.addView(android.widget.Button(ctx).apply {
            text = "Save"
            setOnClickListener {
                if (latestLog.isNotEmpty()) saveLog.launch("launcher-logcat.txt")
            }
        })
        actions.addView(android.widget.Button(ctx).apply {
            text = "Close"
            setOnClickListener { parentFragmentManager.popBackStack() }
        })
        root.addView(actions)
        return root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLogs()
    }

    override fun onDestroyView() {
        logText = null
        super.onDestroyView()
    }

    private fun loadLogs() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Follow Cloudxtreme's approach: read logcat output as it is produced,
                    // filtered to this launcher process. Keep a rolling window to bound memory usage.
                    val process = ProcessBuilder(
                        "logcat", "-d", "-v", "threadtime",
                        "--pid=${android.os.Process.myPid()}"
                    ).redirectErrorStream(true).start()
                    val lines = java.util.ArrayDeque<String>()
                    var totalChars = 0
                    process.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            val keptLine = line.takeLast(MAX_LOG_LINE_CHARS)
                            lines.addLast(keptLine)
                            totalChars += keptLine.length + 1
                            while (lines.size > MAX_LOG_LINES || totalChars > MAX_LOG_CHARS) {
                                totalChars -= lines.removeFirst().length + 1
                            }
                        }
                    }
                    val completed = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroyForcibly()
                        process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                        error("Logcat capture timed out after output was read")
                    }
                    if (process.exitValue() != 0) {
                        error("logcat exited with code ${process.exitValue()}")
                    }
                    lines.joinToString("\\n")
                } catch (error: Exception) {
                    "Unable to read launcher logs: ${error.javaClass.simpleName}: ${error.message}"
                }
            }
            if (!isAdded) return@launch
            latestLog = buildString {
                appendLine("App: ${context.packageName} v${BuildConfig.VERSION_NAME}")
                appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Scope: launcher process only (PID ${android.os.Process.myPid()})")
                appendLine()
                append(result.ifBlank { "No log entries found." })
            }
            logText?.text = latestLog
        }
    }

    private fun clearLogs() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val process = ProcessBuilder("logcat", "-c").redirectErrorStream(true).start()
                    if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        error("Logcat clear timed out")
                    }
                    process.exitValue() == 0
                } catch (_: Exception) {
                    false
                }
            }
            if (!isAdded) return@launch
            latestLog = ""
            logText?.text = if (result) "Logcat cleared." else "Unable to clear log buffer."
        }
    }
}
