@file:Suppress("DEPRECATION")

package com.amazon.tv.leanbacklauncher.settings

import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.amazon.tv.leanbacklauncher.MainActivity
import com.amazon.tv.leanbacklauncher.R
import com.amazon.tv.leanbacklauncher.util.DefaultLauncher

/**
 * TV-friendly customization hub for launcher wallpaper and prayer-time preferences.
 * All controls are focusable and usable with a D-pad/remote.
 */
class TvOsCustomizationActivity : AppCompatActivity() {
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 16, 34)
        window.navigationBarColor = Color.rgb(8, 16, 34)
        window.decorView.setBackgroundColor(Color.rgb(8, 16, 34))

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(36))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.rgb(18, 31, 56))
            }
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = getString(R.string.tv_customization_eyebrow)
            textSize = 13f
            letterSpacing = .22f
            setTextColor(Color.rgb(115, 190, 255))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.tv_customization_title)
            textSize = 34f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.tv_customization_subtitle)
            textSize = 17f
            setTextColor(Color.rgb(188, 204, 229))
            setPadding(0, 0, 0, dp(22))
        })

        section(root, getString(R.string.tv_section_wallpaper))
        actionButton(root, getString(R.string.tv_choose_gallery), getString(R.string.tv_choose_gallery_desc)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, PICK_WALLPAPER)
        }
        actionButton(root, getString(R.string.tv_default_wallpaper), getString(R.string.tv_default_wallpaper_desc)) {
            prefs.edit().remove(KEY_WALLPAPER_URI).remove(KEY_VIDEO_PLAYLIST).remove("wallpaper_image").putBoolean("launcher_live_background", false).apply()
            refreshLauncher()
            Toast.makeText(this, getString(R.string.tv_default_wallpaper_saved), Toast.LENGTH_SHORT).show()
        }
        actionButton(root, getString(R.string.tv_animated_ambience), getString(R.string.tv_animated_ambience_desc)) {
            prefs.edit().putBoolean("launcher_live_background", true).remove(KEY_WALLPAPER_URI).remove(KEY_VIDEO_PLAYLIST).remove("wallpaper_image").apply()
            refreshLauncher()
            Toast.makeText(this, getString(R.string.tv_animated_enabled), Toast.LENGTH_SHORT).show()
        }

        actionButton(root, getString(R.string.tv_choose_video), getString(R.string.tv_choose_video_desc)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, PICK_VIDEO_PLAYLIST)
        }
        actionButton(root, getString(R.string.tv_video_urls), getString(R.string.tv_video_urls_desc)) {
            showVideoUrlPlaylistDialog()
        }
        actionButton(root, getString(R.string.tv_clear_video), getString(R.string.tv_clear_video_desc)) {
        }

        section(root, getString(R.string.tv_section_prayer))
        actionButton(root, getString(R.string.prayer_settings_title), getString(R.string.prayer_settings_desc)) {
            openLauncherSetting("WeatherPreferenceFragment")
        }
        // Pengaturan launcher yang sebelumnya ada di ikon gerigi header (hanya yang belum ada di halaman ini).
        section(root, getString(R.string.tv_section_launcher))
        actionButton(root, getString(R.string.app_language_title), getString(R.string.app_language_desc)) { showLanguageDialog() }
        actionButton(root, getString(R.string.home_screen_order_action_title), getString(R.string.home_screen_order_desc)) {
            openLauncherSetting("HomePreferenceFragment")
        }
        actionButton(root, getString(R.string.hidden_applications_title), getString(R.string.hidden_applications_desc)) {
            openLauncherSetting("HiddenPreferenceFragment")
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            actionButton(
                root,
                getString(R.string.recommendation_blacklist_action_title),
                getString(R.string.recommendation_blacklist_content_description)
            ) { openLauncherSetting("RecommendationsPreferenceFragment") }
        }
        actionButton(root, getString(R.string.channels_sources_title), getString(R.string.channels_sources_desc)) {
            openLauncherSetting("ChannelsPreferenceFragment")
        }
        actionButton(
            root,
            getString(R.string.version),
            "${getString(R.string.app_name)} v${com.amazon.tv.leanbacklauncher.BuildConfig.VERSION_NAME}",
            enabled = false
        ) {}
        defaultLauncherButton(root)

        actionButton(root, getString(R.string.tv_back_launcher), getString(R.string.tv_back_launcher_desc)) { finish() }
        root.post { root.getChildAt(4)?.requestFocus() }
    }

    @Deprecated("Deprecated in Android API, retained for minSdk 22")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        if (requestCode == PICK_VIDEO_PLAYLIST) {
            val selected = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) selected += clip.getItemAt(i).uri
            } ?: data?.data?.let { selected += it }
            if (selected.isEmpty()) return
            val grantedFlags = data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: 0
            selected.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, grantedFlags) } catch (_: Exception) { }
            }
            saveVideoPlaylist(selected)
            return
        }
        if (requestCode != PICK_WALLPAPER) return
        val uri: Uri = data?.data ?: return
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some document providers return a transient grant; we still save the URI.
        }
        prefs.edit().putString(KEY_WALLPAPER_URI, uri.toString()).remove(KEY_VIDEO_PLAYLIST).remove("wallpaper_image").putBoolean("launcher_live_background", false).apply()
        refreshLauncher()
        Toast.makeText(this, getString(R.string.tv_wallpaper_saved), Toast.LENGTH_SHORT).show()
    }

    private fun showVideoUrlPlaylistDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.tv_video_urls_hint)
            minLines = 3
            maxLines = 8
            setText(prefs.getString(KEY_VIDEO_PLAYLIST, "")?.let { raw ->
                try { val a = org.json.JSONArray(raw); (0 until a.length()).joinToString("\n") { a.getString(it) } } catch (_: Exception) { "" }
            } ?: "")
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.tv_video_urls)
            .setMessage(R.string.tv_video_urls_desc)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.tv_save_video_playlist) { _, _ ->
                val urls = input.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (urls.isEmpty() || urls.any { !it.startsWith("https://", true) && !it.startsWith("http://", true) }) {
                    Toast.makeText(this, getString(R.string.tv_video_urls_invalid), Toast.LENGTH_LONG).show()
                } else {
                    saveVideoPlaylist(urls.map(Uri::parse))
                }
            }
            .show()
    }

    private fun saveVideoPlaylist(uris: List<Uri>) {
        val json = org.json.JSONArray().apply { uris.forEach { put(it.toString()) } }.toString()
        prefs.edit().putString(KEY_VIDEO_PLAYLIST, json).remove(KEY_WALLPAPER_URI)
            .remove("wallpaper_image").putBoolean("launcher_live_background", false).apply()
        refreshLauncher()
        Toast.makeText(this, getString(R.string.tv_video_playlist_saved, uris.size), Toast.LENGTH_SHORT).show()
    }

    /** Buka salah satu layar pengaturan launcher yang sudah ada, langsung ke sub-layarnya. */
    private fun openLauncherSetting(fragmentName: String) {
        val full = resources.getBoolean(R.bool.full_screen_settings_enabled)
        val side = resources.getBoolean(R.bool.side_panel_settings_enabled)
        val intent = when {
            side -> Intent(this, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_FRAGMENT, "com.amazon.tv.leanbacklauncher.settings.$fragmentName")
            full -> Intent(this, LegacyHomeScreenSettingsActivity::class.java)
            else -> return
        }
        startActivity(intent)
    }

    /** Pilih bahasa launcher: Default (English), Indonesia, Malaysia. */
    private fun showLanguageDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.app_language_title)
            .setSingleChoiceItems(LauncherLanguage.labels(this), LauncherLanguage.currentIndex()) { dialog, which ->
                dialog.dismiss()
                SettingsFragment.needRestartHome = true
                refreshLauncher()
                LauncherLanguage.apply(which)
            }
            .show()
    }

    private var defaultLauncherSubtitle: TextView? = null

    private fun defaultLauncherButton(root: LinearLayout) {
        actionButton(
            root,
            getString(R.string.default_launcher_action),
            getString(R.string.default_launcher_action_desc)
        ) { DefaultLauncher.request(this) }
        defaultLauncherSubtitle = ((root.getChildAt(root.childCount - 1) as LinearLayout).getChildAt(1) as TextView)
        updateDefaultLauncherSubtitle()
    }

    private fun updateDefaultLauncherSubtitle() {
        defaultLauncherSubtitle?.text = getString(
            if (DefaultLauncher.isDefault(this)) R.string.default_launcher_already_desc
            else R.string.default_launcher_action_desc
        )
    }

    override fun onResume() {
        super.onResume()
        updateDefaultLauncherSubtitle()
    }

    private fun refreshLauncher() {
        sendBroadcast(Intent(MainActivity::class.java.name).putExtra("RefreshHome", true))
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 13f
            letterSpacing = .18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(115, 190, 255))
            setPadding(0, dp(18), 0, dp(10))
        })
    }

    private fun actionButton(
        root: LinearLayout,
        title: String,
        subtitle: String,
        enabled: Boolean = true,
        action: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            // Tetap aktif untuk navigasi D-pad; hanya aksi klik yang boleh dinonaktifkan.
            isEnabled = true
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(29, 46, 76))
                setStroke(dp(2), Color.rgb(48, 73, 111))
            }
            // Fokus hanya mengganti warna; ukuran kartu dan tebal garis tetap, jadi border tidak melebar keluar layar.
            setOnFocusChangeListener { v, focused ->
                v.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (focused) Color.rgb(48, 79, 123) else Color.rgb(29, 46, 76))
                    setStroke(dp(2), if (focused) Color.rgb(115, 190, 255) else Color.rgb(48, 73, 111))
                }
            }
            setOnClickListener { if (enabled) action() }
            addView(TextView(this@TvOsCustomizationActivity).apply {
                text = title
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@TvOsCustomizationActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.rgb(180, 198, 224))
                setPadding(0, dp(3), 0, 0)
            })
        }
        root.addView(card, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(8) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val KEY_WALLPAPER_URI = "launcher_wallpaper_uri"
        private const val KEY_VIDEO_PLAYLIST = "launcher_video_wallpaper_playlist"
        private const val PICK_WALLPAPER = 4107
        private const val PICK_VIDEO_PLAYLIST = 4108
    }
}
