package com.amazon.tv.leanbacklauncher

import android.content.Intent
import android.provider.Settings
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class AppDrawerActivity : AppCompatActivity() {

    private data class AppEntry(val label: String, val icon: android.graphics.drawable.Drawable, val intent: Intent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 17, 27)
        window.navigationBarColor = Color.rgb(12, 17, 27)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 17, 27))
            setPadding(dp(36), dp(24), dp(36), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "All Apps"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(18))
        })

        val grid = GridLayout(this).apply {
            columnCount = 5
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(grid)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val apps = loadApps()
        apps.forEach { app ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                background = GradientDrawable().apply {
                    setColor(Color.rgb(29, 39, 55))
                    cornerRadius = dp(12).toFloat()
                }
                setPadding(dp(8), dp(12), dp(8), dp(10))
                setOnClickListener {
                    try {
                        startActivity(app.intent)
                    } catch (_: Exception) {
                    }
                }
                setOnFocusChangeListener { view, focused ->
                    view.scaleX = if (focused) 1.06f else 1f
                    view.scaleY = if (focused) 1.06f else 1f
                    view.elevation = if (focused) dp(12).toFloat() else 0f
                    view.background = GradientDrawable().apply {
                        setColor(if (focused) Color.rgb(54, 78, 108) else Color.rgb(29, 39, 55))
                        cornerRadius = dp(12).toFloat()
                        if (focused) setStroke(dp(2), Color.WHITE)
                    }
                }
            }
            tile.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(64), dp(64)))
            tile.addView(TextView(this).apply {
                text = app.label
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, 0)
            }, LinearLayout.LayoutParams(-1, dp(42)))
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(132)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            grid.addView(tile, params)
        }
        if (apps.isNotEmpty()) grid.getChildAt(0)?.requestFocus()
    }

    private fun loadApps(): List<AppEntry> {
        val packageManager = packageManager
        val entries = LinkedHashMap<String, AppEntry>()
        val categories = listOf(
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_LEANBACK_LAUNCHER
        )
        categories.forEach { category ->
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(query, 0).forEach { resolve ->
                addResolveInfo(entries, resolve)
            }
        }

        // System Settings is not consistently exposed as a launcher activity on TV devices.
        listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_SETTINGS)
        ).forEach { intent ->
            val resolved = packageManager.resolveActivity(intent, 0)
            if (resolved != null && resolved.activityInfo != null) {
                addResolveInfo(entries, ResolveInfo().apply {
                    activityInfo = resolved.activityInfo
                    loadLabel(packageManager)
                })
            }
        }
        return entries.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun addResolveInfo(entries: MutableMap<String, AppEntry>, info: ResolveInfo) {
        val activityInfo = info.activityInfo ?: return
        if (!activityInfo.enabled) return
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = android.content.ComponentName(activityInfo.packageName, activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val label = info.loadLabel(packageManager)?.toString()
            ?.takeIf { it.isNotBlank() } ?: activityInfo.packageName
        val key = "${activityInfo.packageName}/${activityInfo.name}"
        if (!entries.containsKey(key)) {
            entries[key] = AppEntry(label, info.loadIcon(packageManager), intent)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
