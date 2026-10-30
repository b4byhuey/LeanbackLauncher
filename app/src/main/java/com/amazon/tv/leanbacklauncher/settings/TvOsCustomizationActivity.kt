@file:Suppress("DEPRECATION")

package com.amazon.tv.leanbacklauncher.settings

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.amazon.tv.leanbacklauncher.MainActivity
import com.amazon.tv.leanbacklauncher.R

/**
 * TV-friendly customization hub for launcher wallpaper and prayer-time preferences.
 * All controls are focusable and usable with a D-pad/remote.
 */
class TvOsCustomizationActivity : AppCompatActivity() {
    private lateinit var cityInput: EditText
    private lateinit var autoPrayerLocation: Switch
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
            prefs.edit().remove(KEY_WALLPAPER_URI).remove("wallpaper_image").putBoolean("launcher_live_background", false).apply()
            refreshLauncher()
            Toast.makeText(this, getString(R.string.tv_default_wallpaper_saved), Toast.LENGTH_SHORT).show()
        }
        actionButton(root, getString(R.string.tv_animated_ambience), getString(R.string.tv_animated_ambience_desc)) {
            prefs.edit().putBoolean("launcher_live_background", true).remove(KEY_WALLPAPER_URI).remove("wallpaper_image").apply()
            refreshLauncher()
            Toast.makeText(this, getString(R.string.tv_animated_enabled), Toast.LENGTH_SHORT).show()
        }
        actionButton(root, getString(R.string.tv_photo_wallpaper), getString(R.string.tv_photo_wallpaper_desc)) {
            prefs.edit().putBoolean("launcher_live_background", false).apply()
            refreshLauncher()
            Toast.makeText(this, getString(R.string.tv_photo_mode_enabled), Toast.LENGTH_SHORT).show()
        }
        actionButton(root, getString(R.string.tv_live_wallpaper), getString(R.string.tv_live_wallpaper_desc)) {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            try {
                startActivity(intent)
            } catch (_: Exception) {
                try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                catch (_: Exception) { Toast.makeText(this, getString(R.string.tv_live_unsupported), Toast.LENGTH_LONG).show() }
            }
        }

        section(root, getString(R.string.tv_section_prayer))
        root.addView(TextView(this).apply {
            text = getString(R.string.tv_city_area)
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, dp(4), 0, dp(8))
        })
        autoPrayerLocation = Switch(this).apply {
            text = getString(R.string.use_current_location_title)
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(KEY_PRAYER_AUTO_LOCATION, true)
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(autoPrayerLocation)

        cityInput = EditText(this).apply {
            setSingleLine(true)
            textSize = 18f
            hint = getString(R.string.tv_city_hint)
            setHintTextColor(Color.rgb(132, 149, 178))
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.rgb(29, 46, 76))
                setStroke(dp(1), Color.rgb(66, 100, 148))
            }
            setText(prefs.getString(KEY_PRAYER_CITY, ""))
        }
        root.addView(cityInput, LinearLayout.LayoutParams(-1, dp(56)))
        actionButton(root, getString(R.string.tv_save_prayer_location), getString(R.string.tv_save_prayer_desc)) {
            val city = cityInput.text.toString().trim()
            if (!autoPrayerLocation.isChecked && city.isBlank()) {
                cityInput.error = getString(R.string.tv_enter_city)
            } else {
                prefs.edit()
                    .putBoolean(KEY_PRAYER_AUTO_LOCATION, autoPrayerLocation.isChecked)
                    .apply {
                        if (city.isNotBlank()) putString(KEY_PRAYER_CITY, city)
                    }
                    .apply()
                refreshLauncher()
                Toast.makeText(this, getString(R.string.tv_prayer_saved), Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.tv_prayer_note)
            textSize = 13f
            setTextColor(Color.rgb(153, 174, 205))
            setPadding(0, dp(16), 0, dp(16))
        })
        actionButton(root, getString(R.string.tv_back_launcher), getString(R.string.tv_back_launcher_desc)) { finish() }
        root.post { root.getChildAt(4)?.requestFocus() }
    }

    @Deprecated("Deprecated in Android API, retained for minSdk 22")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_WALLPAPER || resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Some document providers return a transient grant; we still save the URI.
        }
        prefs.edit().putString(KEY_WALLPAPER_URI, uri.toString()).remove("wallpaper_image").putBoolean("launcher_live_background", false).apply()
        refreshLauncher()
        Toast.makeText(this, getString(R.string.tv_wallpaper_saved), Toast.LENGTH_SHORT).show()
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

    private fun actionButton(root: LinearLayout, title: String, subtitle: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.rgb(29, 46, 76))
                setStroke(dp(1), Color.rgb(48, 73, 111))
            }
            setOnFocusChangeListener { v, focused ->
                v.background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (focused) Color.rgb(48, 79, 123) else Color.rgb(29, 46, 76))
                    setStroke(dp(if (focused) 2 else 1), if (focused) Color.rgb(115, 190, 255) else Color.rgb(48, 73, 111))
                }
                v.scaleX = if (focused) 1.015f else 1f
                v.scaleY = if (focused) 1.015f else 1f
            }
            setOnClickListener { action() }
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
        const val KEY_PRAYER_CITY = "launcher_prayer_city"
        private const val KEY_PRAYER_AUTO_LOCATION = "launcher_prayer_use_weather_location"
        private const val PICK_WALLPAPER = 4107
    }
}
