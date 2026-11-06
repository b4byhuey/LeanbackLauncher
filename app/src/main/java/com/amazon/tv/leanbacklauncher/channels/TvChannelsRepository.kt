package com.amazon.tv.leanbacklauncher.channels

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

/**
 * Membaca baris "Channels" dan "Watch Next" Android TV dari TvProvider.
 *
 * Catatan: pada banyak perangkat, aplikasi pihak ketiga hanya bisa membaca data miliknya sendiri
 * kecuali memegang izin sistem. Semua query dibungkus try/catch dan dicatat di log (tag "TvChannels")
 * supaya mudah dicek lewat `adb logcat -s TvChannels` apakah data milik aplikasi lain terbaca.
 * Kolom/URI memakai string literal agar tidak bergantung pada API level (minSdk 23).
 */
object TvChannelsRepository {
    private const val TAG = "TvChannels"
    const val PERMISSION_READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"
    private const val KEY_ENABLED = "tv_channels_enabled_packages"
    const val MAX_PER_APP = 15

    private val PREVIEW_URI: Uri = Uri.parse("content://android.media.tv/preview_program")
    private val WATCH_NEXT_URI: Uri = Uri.parse("content://android.media.tv/watch_next_program")

    val OBSERVED_URIS: List<Uri> = listOf(PREVIEW_URI, WATCH_NEXT_URI)

    data class Program(
        val id: Long,
        val packageName: String,
        val title: String,
        val posterUri: String?,
        val intentUri: String,
        val watchNext: Boolean
    ) {
        /** id unik antar tabel (watch next dan preview punya _id sendiri-sendiri). */
        val stableId: Long get() = if (watchNext) -id - 1 else id
    }

    data class AppSource(val packageName: String, val label: CharSequence, val programCount: Int)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION_READ_EPG) == PackageManager.PERMISSION_GRANTED

    // ---- pilihan pengguna (switch per aplikasi); default: semua mati ----

    fun enabledPackages(context: Context): Set<String> =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(KEY_ENABLED, emptySet())?.toSet() ?: emptySet()

    fun setEnabled(context: Context, packageName: String, enabled: Boolean) {
        val updated = enabledPackages(context).toMutableSet()
        if (enabled) updated.add(packageName) else updated.remove(packageName)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putStringSet(KEY_ENABLED, updated).apply()
    }

    // ---- query (blocking: panggil dari thread background) ----

    /** Program dari aplikasi yang diaktifkan pengguna, dikelompokkan per aplikasi. */
    fun loadEnabledPrograms(context: Context): List<Program> {
        val enabled = enabledPackages(context)
        if (enabled.isEmpty()) return emptyList()
        return loadAllPrograms(context)
            .filter { it.packageName in enabled }
            .groupBy { it.packageName }
            .values
            .flatMap { it.take(MAX_PER_APP) }
    }

    /** Aplikasi yang membagikan program (untuk layar pengaturan). */
    fun loadSources(context: Context): List<AppSource> {
        val pm = context.packageManager
        return loadAllPrograms(context)
            .groupBy { it.packageName }
            .mapNotNull { (pkg, list) ->
                try {
                    AppSource(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)), list.size)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label.toString().lowercase() }
    }

    private fun loadAllPrograms(context: Context): List<Program> {
        val resolver = context.contentResolver
        val result = ArrayList<Program>()
        result += queryWatchNext(resolver)
        result += queryPreview(resolver)
        Log.i(
            TAG,
            "programs read: ${result.size}, per package: ${result.groupingBy { it.packageName }.eachCount()}"
        )
        return result
    }

    /**
     * Sengaja TIDAK memfilter channel "browsable": aplikasi pihak ketiga (mis. Cloudxtream) membuat
     * channel dengan browsable=0 dan launcher ini tidak bisa mengubahnya (butuh izin sistem) maupun
     * menampilkan dialog persetujuan sistem. Pengguna sudah memilih aplikasi mana yang tampil lewat
     * switch di "Sumber channel", jadi pilihan itu yang dipakai.
     */
    private fun queryPreview(resolver: ContentResolver): List<Program> = try {
        val out = ArrayList<Program>()
        val skipped = HashMap<String, Int>()
        resolver.query(
            PREVIEW_URI,
            arrayOf("_id", "package_name", "channel_id", "title", "poster_art_uri", "intent_uri"),
            null, null, "weight DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val program = toProgram(c, false)
                if (program != null) out.add(program)
                else skipped.merge(c.str("package_name") ?: "?", 1, Int::plus)
            }
        }
        if (skipped.isNotEmpty()) Log.i(TAG, "preview programs skipped (no intent_uri / title / poster): $skipped")
        out
    } catch (e: Exception) {
        Log.w(TAG, "preview_program query failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

    private fun queryWatchNext(resolver: ContentResolver): List<Program> = try {
        val out = ArrayList<Program>()
        resolver.query(
            WATCH_NEXT_URI,
            arrayOf("_id", "package_name", "title", "poster_art_uri", "intent_uri"),
            null, null, "last_engagement_time_utc_millis DESC"
        )?.use { c ->
            var total = 0
            while (c.moveToNext()) {
                total++
                toProgram(c, true)?.let { out.add(it) }
            }
            Log.i(TAG, "watch_next rows: $total, usable: ${out.size}")
        }
        out
    } catch (e: Exception) {
        Log.w(TAG, "watch_next query failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

    private fun toProgram(c: Cursor, watchNext: Boolean): Program? {
        val pkg = c.str("package_name") ?: return null
        val intent = c.str("intent_uri")?.takeIf { it.isNotBlank() } ?: return null
        val title = c.str("title").orEmpty()
        val poster = c.str("poster_art_uri")?.takeIf { it.isNotBlank() }
        if (title.isBlank() && poster == null) return null
        return Program(c.lng("_id"), pkg, title, poster, intent, watchNext)
    }

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i >= 0 && !isNull(i)) getString(i) else null
    }

    private fun Cursor.lng(col: String): Long {
        val i = getColumnIndex(col)
        return if (i >= 0 && !isNull(i)) getLong(i) else 0L
    }
}
