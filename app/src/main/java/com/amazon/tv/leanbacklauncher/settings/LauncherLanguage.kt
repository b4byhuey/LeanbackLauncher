package com.amazon.tv.leanbacklauncher.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.amazon.tv.leanbacklauncher.R

/**
 * Bahasa launcher: hanya Default (English), Indonesia, dan Malaysia.
 * Tidak ada "System default": launcher tidak mengikuti bahasa perangkat, supaya tidak error
 * pada bahasa yang tidak didukung.
 */
object LauncherLanguage {
    /** Tag BCP-47 untuk API locale (bukan nama folder resource seperti "ms-rMY"). */
    val tags = arrayOf("en", "in", "ms")

    fun labels(context: Context): Array<String> = arrayOf(
        context.getString(R.string.app_language_opt_default),
        context.getString(R.string.app_language_opt_indonesia),
        context.getString(R.string.app_language_opt_malaysia)
    )

    /** Indeks bahasa yang aktif; selain Indonesia/Malaysia dianggap Default (English). */
    fun currentIndex(): Int {
        val lang = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
        return when (lang) {
            "id", "in" -> 1
            "ms" -> 2
            else -> 0
        }
    }

    fun apply(index: Int) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(tags[index.coerceIn(tags.indices)])
        )
    }

    /**
     * Pastikan launcher memakai salah satu bahasa yang didukung. Bila belum ada pilihan
     * (mengikuti bahasa perangkat) atau bahasanya tidak didukung, pakai Default (English).
     * Panggil setelah super.onCreate() sebuah AppCompatActivity agar pilihan tersimpan sudah termuat.
     */
    fun ensureSupported() {
        val lang = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
        if (lang !in setOf("en", "id", "in", "ms")) apply(0)
    }
}
