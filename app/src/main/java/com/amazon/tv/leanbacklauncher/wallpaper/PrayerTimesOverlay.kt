package com.amazon.tv.leanbacklauncher.wallpaper

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
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

/** Compact prayer-time row embedded in the launcher's clock/weather area. */
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
        setPadding(0, dp(4), 0, dp(2))
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isClickable = false

        city.textSize = 13f
        city.typeface = Typeface.DEFAULT_BOLD
        city.setTextColor(Color.WHITE)
        date.textSize = 10f
        date.setTextColor(Color.rgb(195, 205, 220))
        times.textSize = 11f
        times.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        times.setTextColor(Color.WHITE)
        times.maxLines = 2

        addView(city, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(date, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(times, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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

    fun refreshForWeatherUpdate() {
        loadTimes()
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
                        name to prayerCalendar.timeInMillis
                    }.minByOrNull { it.second }
                    val upcoming = if (next == null) "" else {
                        val remaining = ((next.second - now.timeInMillis) / 60000L).coerceAtLeast(0)
                        val label = labels[next.first] ?: next.first
                        val timeLeft = if (remaining >= 60) "${remaining / 60}h ${remaining % 60}m" else "${remaining}m"
                        "Berikutnya: $label · $timeLeft"
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

    private fun readWeather(): JSONObject? = try {
        val file = java.io.File(context.cacheDir, "weather.json")
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: Exception) { null }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_AUTO_LOCATION = "launcher_prayer_use_weather_location"
        private const val KEY_PRAYER_CITY = "launcher_prayer_city"
        private const val KEY_DETECTED_CITY = "launcher_detected_city"
        private const val REFRESH_INTERVAL = 6 * 60 * 60 * 1000L
    }
}