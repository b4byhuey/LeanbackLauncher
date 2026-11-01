package com.amazon.tv.leanbacklauncher.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.amazon.tv.firetv.leanbacklauncher.apps.RowPreferences
import com.amazon.tv.leanbacklauncher.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Jadwal sholat di header (jam + cuaca + sholat), meniru baris forecast pada contoh.
 *
 * Susunan: baris info (tanggal · kota | "Berikutnya: ...") lalu 5 kolom rata lebar:
 * nama sholat, ikon, jam. Kolom sholat berikutnya diberi highlight halus.
 */
class PrayerTimesOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private class Chip(val root: LinearLayout, val name: TextView, val icon: ImageView, val time: TextView)

    private val main = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var requestId = 0
    private var lastConfigKey: String? = null
    private var prayerTimeZone: TimeZone = TimeZone.getDefault()
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in relevantPreferenceKeys()) {
            main.post { loadTimes() }
        }
    }

    private val city = TextView(context)
    private val next = TextView(context)

    /** Nama lokasi yang sedang dipakai; null bila belum ada. */
    private var locationName: String? = null
    /** Pesan pengganti baris tanggal (mis. "Detecting location…"); null = tampilkan tanggal · kota. */
    private var infoText: String? = null
    private val chips = LinkedHashMap<String, Chip>()

    /** Waktu sholat hari ini ("HH:mm") per nama API; null = belum ada data. */
    private var timings: Map<String, String>? = null
    /** Pesan status (memuat / gagal); null = tampilkan "berikutnya". */
    private var statusText: String? = null

    private val refresh = Runnable { loadTimes(force = true) }
    private val tick = object : Runnable {
        override fun run() {
            render()
            main.postDelayed(this, TICK_INTERVAL)
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = false
        isClickable = false

        city.apply {
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        next.apply {
            textSize = 11f
            gravity = Gravity.END
            setTextColor(COLOR_ACCENT)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(city, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(next, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (name in NAMES) {
            val chip = buildChip()
            chips[name] = chip
            row.addView(chip.root, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 0
                marginEnd = 0
            })
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        main.removeCallbacks(tick)
        main.postDelayed(tick, TICK_INTERVAL)
        loadTimes()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        main.removeCallbacks(refresh)
        main.removeCallbacks(tick)
        requestId++ // abaikan hasil request yang masih berjalan
        executor?.shutdownNow()
        executor = null
        super.onDetachedFromWindow()
    }

    fun refreshForWeatherUpdate() {
        // Reload only when the detected city changed; ordinary weather refreshes
        // should not trigger an unnecessary AlAdhan request.
        loadTimes()
    }

    private fun buildChip(): Chip {
        val name = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            includeFontPadding = false
        }
        val icon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val time = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isSingleLine = true
            includeFontPadding = false
            setTextColor(Color.WHITE)
        }
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isFocusable = false
            isClickable = false
            setPadding(0, dp(2), 0, dp(1))
            addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(icon, LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(1)
                bottomMargin = dp(1)
            })
            addView(time, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        return Chip(root, name, icon, time)
    }

    private fun loadTimes(force: Boolean = false) {
        main.removeCallbacks(refresh)

        val configKey = currentConfigKey()
        val configurationChanged = configKey != lastConfigKey
        if (configurationChanged) {
            lastConfigKey = configKey
            timings = null
            prayerTimeZone = TimeZone.getDefault()
            statusText = null
        } else if (!force && timings != null) {
            render()
            return
        }

        val automatic = RowPreferences.isUseLocationEnabled(context)
        val location = if (automatic) {
            readWeather()?.optString("name")?.takeIf { it.isNotBlank() }
                ?: PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(KEY_DETECTED_CITY, null)?.takeIf { it.isNotBlank() }
        } else {
            RowPreferences.getUserLocation(context)?.takeIf { it.isNotBlank() }
        }

        if (location == null) {
            locationName = null
            infoText = context.getString(if (automatic) R.string.prayer_detecting else R.string.prayer_city_not_set)
            timings = null
            statusText = null
            render()
            main.postDelayed(refresh, RETRY_NO_LOCATION_INTERVAL)
            return
        }

        locationName = location
        infoText = null
        if (timings == null) statusText = context.getString(R.string.prayer_loading)
        render()

        val myRequest = ++requestId
        ensureExecutor().execute {
            val result = try { fetchTimings(location) } catch (error: Exception) {
                android.util.Log.w(TAG, "Unable to load prayer times", error)
                null
            }
            main.post {
                if (!isAttachedToWindow || myRequest != requestId) return@post
                if (result != null) {
                    timings = result.timings
                    prayerTimeZone = result.timeZoneId?.let { id ->
                        if (TimeZone.getAvailableIDs().contains(id)) TimeZone.getTimeZone(id)
                        else TimeZone.getDefault()
                    } ?: TimeZone.getDefault()
                    statusText = null
                    render()
                    main.postDelayed(refresh, REFRESH_INTERVAL)
                } else {
                    timings = null
                    statusText = context.getString(R.string.prayer_unavailable).lineSequence().first()
                    render()
                    main.postDelayed(refresh, RETRY_FAILURE_INTERVAL)
                }
            }
        }
    }

    private fun ensureExecutor(): ExecutorService =
        executor?.takeIf { !it.isShutdown } ?: Executors.newSingleThreadExecutor().also { executor = it }

    private data class PrayerResponse(
        val timings: Map<String, String>,
        val timeZoneId: String?
    )

    private fun fetchTimings(location: String): PrayerResponse {
        val encoded = URLEncoder.encode(location, "UTF-8")
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val method = preferences.getString(KEY_METHOD, DEFAULT_METHOD) ?: DEFAULT_METHOD
        val tune = listOf(
            0, prayerOffset(preferences, KEY_OFFSET_FAJR), 0,
            prayerOffset(preferences, KEY_OFFSET_DHUHR),
            prayerOffset(preferences, KEY_OFFSET_ASR),
            prayerOffset(preferences, KEY_OFFSET_MAGHRIB), 0,
            prayerOffset(preferences, KEY_OFFSET_ISHA), 0
        ).joinToString(",")

        val customSettings = if (method == CUSTOM_METHOD) {
            val fajr = preferences.getString(KEY_CUSTOM_FAJR, DEFAULT_CUSTOM_FAJR)
                ?.toDecimalOrNull() ?: DEFAULT_CUSTOM_FAJR
            val maghrib = preferences.getString(KEY_CUSTOM_MAGHRIB, "")
                ?.toDecimalOrNull() ?: "null"
            val isha = preferences.getString(KEY_CUSTOM_ISHA, DEFAULT_CUSTOM_ISHA)
                ?.toDecimalOrNull() ?: DEFAULT_CUSTOM_ISHA
            "&methodSettings=${URLEncoder.encode("$fajr,$maghrib,$isha", "UTF-8")}"
        } else ""

        val requestUrl = "https://api.aladhan.com/v1/timingsByAddress?address=$encoded" +
            "&method=$method&tune=$tune$customSettings"
        val connection = (URL(requestUrl)
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).optJSONObject("data") ?: error("Missing AlAdhan data")
            val timing = data.optJSONObject("timings") ?: error("Missing prayer timings")
            val parsed = NAMES.associateWith { name ->
                val raw = timing.optString(name, "").substringBefore(" ").trim()
                if (parseHm(raw) != null) raw else NO_TIME
            }
            if (parsed.values.none { it != NO_TIME }) error("No valid prayer times in response")
            val timeZoneId = data.optJSONObject("meta")?.optString("timezone")
                ?.takeIf { it.isNotBlank() && it != "null" }
            return PrayerResponse(parsed, timeZoneId)
        } finally {
            connection.disconnect()
        }
    }

    /** Gambar ulang kolom + teks "berikutnya" dari data yang sudah ada (murah, aman dipanggil tiap menit). */
    private fun render() {
        val data = timings
        val upcoming = data?.let { nextPrayer(it) }
        val dateText = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).apply {
            timeZone = prayerTimeZone
        }.format(Date())
        city.text = infoText ?: locationName?.let { "$dateText · $it" } ?: dateText
        for ((name, chip) in chips) {
            val active = upcoming?.first == name
            chip.name.text = labelFor(name)
            chip.time.text = data?.get(name) ?: NO_TIME
            chip.icon.setImageResource(iconFor(name))
            chip.name.setTextColor(if (active) Color.WHITE else COLOR_MUTED)
            chip.time.setTextColor(if (active) Color.WHITE else COLOR_TIME)
            chip.time.typeface = Typeface.create(
                "sans-serif-medium", if (active) Typeface.BOLD else Typeface.NORMAL
            )
            chip.root.setBackgroundResource(if (active) R.drawable.prayer_chip_active_bg else 0)
        }
        next.text = statusText ?: upcoming?.let { (name, millis) ->
            val minutes = ((millis - System.currentTimeMillis()) / 60000L).coerceAtLeast(0)
            context.getString(R.string.prayer_next, labelFor(name), minutes / 60, minutes % 60)
        }.orEmpty()
    }

    /** (nama API, waktu epoch) sholat terdekat berikutnya; yang sudah lewat dianggap besok. */
    private fun nextPrayer(data: Map<String, String>): Pair<String, Long>? {
        val now = System.currentTimeMillis()
        return NAMES.mapNotNull { name ->
            val (hour, minute) = parseHm(data[name]) ?: return@mapNotNull null
            val cal = Calendar.getInstance(prayerTimeZone).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }
            name to cal.timeInMillis
        }.minByOrNull { it.second }
    }

    private fun currentConfigKey(): String {
        val automatic = RowPreferences.isUseLocationEnabled(context)
        val location = if (automatic) {
            readWeather()?.optString("name")?.takeIf { it.isNotBlank() }
                ?: preferences.getString(KEY_DETECTED_CITY, "")
        } else {
            RowPreferences.getUserLocation(context)
        }
        val keys = listOf(
            KEY_METHOD, KEY_OFFSET_FAJR, KEY_OFFSET_DHUHR, KEY_OFFSET_ASR,
            KEY_OFFSET_MAGHRIB, KEY_OFFSET_ISHA, KEY_CUSTOM_FAJR,
            KEY_CUSTOM_MAGHRIB, KEY_CUSTOM_ISHA
        )
        return buildString {
            append(automatic).append('|').append(location.orEmpty())
            keys.forEach { append('|').append(preferences.getString(it, "")) }
        }
    }

    private fun relevantPreferenceKeys(): Set<String> = setOf(
        KEY_METHOD, KEY_OFFSET_FAJR, KEY_OFFSET_DHUHR, KEY_OFFSET_ASR,
        KEY_OFFSET_MAGHRIB, KEY_OFFSET_ISHA, KEY_CUSTOM_FAJR,
        KEY_CUSTOM_MAGHRIB, KEY_CUSTOM_ISHA,
        context.getString(R.string.pref_use_current_location),
        context.getString(R.string.pref_user_location)
    )

    private fun prayerOffset(preferences: android.content.SharedPreferences, key: String): Int =
        preferences.getString(key, "0")?.toIntOrNull()?.coerceIn(-180, 180) ?: 0

    private fun String.toDecimalOrNull(): String? =
        trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            ?.takeIf { it in 0.0..90.0 }?.toString()

    private fun parseHm(raw: String?): Pair<Int, Int>? {
        val parts = raw?.trim()?.split(":") ?: return null
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    private fun labelFor(name: String): String = context.getString(
        when (name) {
            "Fajr" -> R.string.prayer_fajr
            "Dhuhr" -> R.string.prayer_dhuhr
            "Asr" -> R.string.prayer_asr
            "Maghrib" -> R.string.prayer_maghrib
            else -> R.string.prayer_isha
        }
    )

    private fun iconFor(name: String): Int = when (name) {
        "Fajr" -> R.drawable.ic_prayer_fajr
        "Dhuhr" -> R.drawable.ic_prayer_dhuhr
        "Asr" -> R.drawable.ic_prayer_asr
        "Maghrib" -> R.drawable.ic_prayer_maghrib
        else -> R.drawable.ic_prayer_isha
    }

    private fun readWeather(): JSONObject? = try {
        val file = java.io.File(context.cacheDir, "weather.json")
        if (file.exists()) JSONObject(file.readText()) else null
    } catch (_: Exception) { null }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_DETECTED_CITY = "launcher_detected_city"
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
        private const val DEFAULT_CUSTOM_FAJR = "18"
        private const val DEFAULT_CUSTOM_ISHA = "18"
        private const val REFRESH_INTERVAL = 6 * 60 * 60 * 1000L
        private const val RETRY_NO_LOCATION_INTERVAL = 2 * 60 * 1000L
        private const val RETRY_FAILURE_INTERVAL = 5 * 60 * 1000L
        private const val TAG = "PrayerTimesOverlay"
        private const val TICK_INTERVAL = 60 * 1000L
        private const val NO_TIME = "--:--"
        private val COLOR_MUTED = Color.rgb(195, 205, 220)
        private val COLOR_TIME = Color.rgb(238, 238, 238)
        private val COLOR_ACCENT = Color.rgb(255, 214, 120)
        private val NAMES = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    }
}
