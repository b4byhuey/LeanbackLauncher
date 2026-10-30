package com.amazon.tv.leanbacklauncher.wallpaper

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.amazon.tv.leanbacklauncher.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/** Compact, non-focusable prayer-time card over the launcher home screen. */
class PrayerTimesOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val city = TextView(context)
    private val date = TextView(context)
    private val times = TextView(context)
    private val refresh = Runnable { loadTimes() }

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.argb(190, 12, 24, 44))
            setStroke(dp(1), Color.argb(100, 140, 190, 255))
        }
        isFocusable = false
        isClickable = false
        city.textSize = 13f
        city.setTextColor(Color.rgb(137, 195, 255))
        date.textSize = 11f
        date.setTextColor(Color.rgb(191, 207, 229))
        times.textSize = 13f
        times.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        times.setTextColor(Color.WHITE)
        times.setLineSpacing(dp(3).toFloat(), 1f)
        addView(city)
        addView(date)
        addView(times)
        loadTimes()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, REFRESH_INTERVAL)
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    private fun loadTimes() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val location = prefs.getString("launcher_prayer_city", "Sidoarjo, Indonesia") ?: "Sidoarjo, Indonesia"
        city.text = context.getString(R.string.prayer_heading, location)
        date.text = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date())
        times.text = context.getString(R.string.prayer_loading)
        executor.execute {
            val result = try {
                val encoded = URLEncoder.encode(location, "UTF-8")
                val connection = (URL("https://api.aladhan.com/v1/timingsByAddress?address=$encoded&method=20").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                }
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val data = json.getJSONObject("data")
                    val timing = data.getJSONObject("timings")
                    val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
                    val labels = mapOf("Fajr" to context.getString(R.string.prayer_fajr), "Dhuhr" to context.getString(R.string.prayer_dhuhr), "Asr" to context.getString(R.string.prayer_asr), "Maghrib" to context.getString(R.string.prayer_maghrib), "Isha" to context.getString(R.string.prayer_isha))
                    val dailyTimes = names.associateWith { name ->
                        timing.optString(name, "--:--").substringBefore(" ")
                    }
                    val now = Calendar.getInstance()
                    val next = names.mapNotNull { name ->
                        val raw = dailyTimes[name] ?: return@mapNotNull null
                        val parsed = try { SimpleDateFormat("HH:mm", Locale.US).parse(raw) } catch (_: Exception) { null }
                            ?: return@mapNotNull null
                        val prayerCalendar = Calendar.getInstance().apply {
                            time = parsed
                            set(Calendar.YEAR, now.get(Calendar.YEAR))
                            set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR))
                            val parsedTime = Calendar.getInstance().apply { time = parsed }
                            set(Calendar.HOUR_OF_DAY, parsedTime.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, parsedTime.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (prayerCalendar.timeInMillis <= now.timeInMillis) {
                            prayerCalendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
                        Triple(name, prayerCalendar.timeInMillis, raw)
                    }.minByOrNull { it.second }
                    val prayerLine = names.joinToString("     •     ") { name -> "${labels[name]}  ${dailyTimes[name]}" }
                    if (next == null) prayerLine else {
                        val remainingMinutes = ((next.second - now.timeInMillis) / 60000L).coerceAtLeast(0)
                        val hours = remainingMinutes / 60
                        val minutes = remainingMinutes % 60
                        "$prayerLine\nNext: ${next.first} in %02d:%02d".format(hours, minutes)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                context.getString(R.string.prayer_unavailable)
            }
            main.post {
                if (isAttachedToWindow) {
                    times.text = result
                    requestLayout()
                }
            }
        }
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, REFRESH_INTERVAL)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REFRESH_INTERVAL = 6 * 60 * 60 * 1000L
    }
}
