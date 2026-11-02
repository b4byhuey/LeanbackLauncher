package com.amazon.tv.leanbacklauncher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal object OpenMeteoWeather {
    data class Location(val latitude: Double, val longitude: Double, val name: String)

    fun forecast(latitude: Double, longitude: Double): JSONObject {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,is_day,weather_code,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m" +
            "&daily=temperature_2m_max,temperature_2m_min&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days=1"
        return request(URL(endpoint)).also {
            if (!it.has("current") || !it.has("daily")) error("Incomplete Open-Meteo response")
        }
    }

    fun resolve(query: String): Location? {
        val parts = query.split(Regex("\\s*,\\s*"))
        if (parts.size == 2) {
            val lat = parts[0].toDoubleOrNull()
            val lon = parts[1].toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0)
                return Location(lat, lon, query)
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = request(URL("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"))
        val item = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        val lat = item.optDouble("latitude", Double.NaN)
        val lon = item.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val name = listOf(item.optString("name"), item.optString("admin1"), item.optString("country"))
            .filter { it.isNotBlank() && it != "null" }.distinct().joinToString(", ")
        return Location(lat, lon, name.ifBlank { query })
    }

    fun ipLocation(): Location? {
        val json = request(URL("https://ipwho.is/"))
        if (!json.optBoolean("success", false)) return null
        val lat = json.optDouble("latitude", Double.NaN)
        val lon = json.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val name = listOf(json.optString("city"), json.optString("region"), json.optString("country"))
            .filter { it.isNotBlank() && it != "null" }.distinct().joinToString(", ")
        return Location(lat, lon, name)
    }

    private fun request(url: URL): JSONObject {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("HTTP $status: $body")
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
