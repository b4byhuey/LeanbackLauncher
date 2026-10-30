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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Small, non-focusable time and prayer summary for the top-right corner. */
class PrayerTimesOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val clock = TextView(context)
    private val city = TextView(context)
    private val date = TextView(context)
    private val times = TextView(context)
    private val refresh = Runnable { loadTimes() }
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            main.postDelayed(this, 30_000L)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.END
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.argb(175, 12, 20, 32))
            setStroke(dp(1), Color.argb(65, 180, 210, 255))
        }
        isFocusable = false
        isClickable = false
        clock.textSize = 30f
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        clock.setTextColor(Color.WHITE)
        city.textSize = 12f
        city.setTextColor(Color.rgb(155, 202, 255))
        date.textSize = 10f
        date.setTextColor(Color.rgb(195, 205, 220))
        times.textSize = 11f
        times.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        times.setTextColor(Color.WHITE)
        times.maxLines = 2
        times.gravity = Gravity.END
        addView(clock)
        addView(city)
        addView(date)
        addView(times)
        clockTick.run()
        loadTimes()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, REFRESH_INTERVAL)
        main.removeCallbacks(clockTick)
        clockTick.run()
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(refresh)
        main.removeCallbacks(clockTick)
        super.onDetachedFromWindow()
    }

    private fun loadTimes() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val location = prefs.getString("launcher_prayer_city", "Sidoarjo, Indonesia")
            ?: "Sidoarjo, Indonesia"
        city.text = location
        date.text = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date())
        times.text = context.getString(R.string.prayer_loading)
        executor.execute {
            val result = try {
                val encoded = URLEncoder.encode(location, "UTF-8")
                val connection = (URL("https://api.aladhan.com/v1/timingsByAddress?address=$encoded&method=20")
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                }
                try {
                    if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                    val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        .getJSONObject("data")
                    val timing = data.getJSONObject("timings")
                    val names = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
                    val labels = mapOf(
                        "Fajr" to context.getString(R.string.prayer_fajr),
                        "Dhuhr" to context.getString(R.string.prayer_dhuhr),
                        "Asr" to context.getString(R.string.prayer_asr),
                        "Maghrib" to context.getString(R.string.prayer_maghrib),
                        "Isha" to context.getString(R.string.prayer_isha)
                    )
                    val dailyTimes = names.associateWith { timing.optString(it, "--:--").substringBefore(" ") }
                    val now = Calendar.getInstance()
                    val next = names.mapNotNull { name ->
                        val raw = dailyTimes[name] ?: return@mapNotNull null
                        val parsed = try { SimpleDateFormat("HH:mm", Locale.US).parse(raw) }
                            catch (_: Exception) { null } ?: return@mapNotNull null
                        val parsedTime = Calendar.getInstance().apply { time = parsed }
                        val prayerCalendar = Calendar.getInstance().apply {
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
                    val upcoming = if (next == null) "" else {
                        val remaining = ((next.second - now.timeInMillis) / 60000L).coerceAtLeast(0)
                        val label = labels[next.first] ?: next.first
                        val timeLeft = if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m" else "${remaining}m"
                        "Next: $label · $timeLeft"
                    }
                    names.joinToString("   •   ") { name -> "${labels[name]} ${dailyTimes[name]}" } +
                        if (upcoming.isNotEmpty()) "\n$upcoming" else ""
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
