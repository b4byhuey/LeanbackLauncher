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

/** Native, non-focusable information header that is part of the launcher layout. */
class PrayerTimesOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val clock = TextView(context)
    private val weather = TextView(context)
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
        gravity = Gravity.START
        setPadding(dp(48), dp(18), dp(48), dp(12))
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isClickable = false
        clock.textSize = 27f
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
        times.gravity = Gravity.START
        times.maxLines = 2
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clock.textSize = 34f
        top.addView(clock, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        val locationBlock = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }
        city.textSize = 15f
        city.typeface = Typeface.DEFAULT_BOLD
        locationBlock.addView(city)
        locationBlock.addView(date)
        top.addView(locationBlock, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        weather.textSize = 20f
        weather.typeface = Typeface.DEFAULT_BOLD
        weather.setTextColor(Color.WHITE)
        top.addView(weather, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(top)
        addView(times)
        updateWeatherSummary()
        clockTick.run()
        loadTimes()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, REFRESH_INTERVAL)
        main.removeCallbacks(clockTick)
        clockTick.run()
        updateWeatherSummary()
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(refresh)
        main.removeCallbacks(clockTick)
        super.onDetachedFromWindow()
    }

    private fun loadTimes() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val automatic = prefs.getBoolean(KEY_AUTO_LOCATION, true)
        val weatherData = readWeather()
        val location = if (automatic) {
            weatherData?.optString("name")?.takeIf { it.isNotBlank() }
                ?: prefs.getString(KEY_DETECTED_CITY, null)?.takeIf { it.isNotBlank() }
        } else {
            prefs.getString(KEY_PRAYER_CITY, null)?.takeIf { it.isNotBlank() }
        }
        city.text = location ?: if (automatic) "Mendeteksi lokasi..." else "Atur kota di pengaturan"
        updateWeatherSummary()
        date.text = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date())
        if (location == null) {
            times.text = if (automatic) "Menunggu lokasi perangkat..." else "Lokasi belum diatur"
            main.removeCallbacks(refresh)
            main.postDelayed(refresh, 2 * 60 * 1000L)
            return
        }
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

    fun refreshForWeatherUpdate() {
        updateWeatherSummary()
        loadTimes()
    }

    private fun readWeather(): JSONObject? = try {
        val file = java.io.File(context.cacheDir, "weather.json")
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: Exception) { null }

    private fun updateWeatherSummary() {
        val data = readWeather()
        val temperature = data?.optDouble("temperature", Double.NaN) ?: Double.NaN
        val description = data?.optJSONArray("descriptions")?.optString(0).orEmpty().lowercase(Locale.ROOT)
        val icon = when {
            "thunder" in description -> "⛈"
            "rain" in description || "drizzle" in description -> "🌧"
            "snow" in description -> "❄"
            "cloud" in description || "overcast" in description -> "☁"
            "clear" in description || "sun" in description -> "☀"
            else -> "🌤"
        }
        weather.text = if (!temperature.isNaN()) "$icon ${temperature.toInt()}°" else ""
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_AUTO_LOCATION = "launcher_prayer_use_weather_location"
        private const val KEY_PRAYER_CITY = "launcher_prayer_city"
        private const val KEY_DETECTED_CITY = "launcher_detected_city"
        private const val REFRESH_INTERVAL = 6 * 60 * 60 * 1000L
    }
}
