package com.amazon.tv.leanbacklauncher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import com.amazon.tv.leanbacklauncher.capabilities.HighEndLauncherConfiguration
import com.amazon.tv.leanbacklauncher.capabilities.LauncherConfiguration
import com.amazon.tv.leanbacklauncher.util.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LauncherApp : Application() {
    companion object {
        private lateinit var appContext: Context

        val context: Context
            get() = appContext

        fun toast(txt: String?, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                if (!txt.isNullOrEmpty()) Toast.makeText(appContext, txt, duration).show()
            }
        }

        fun toast(resId: Int?, long: Boolean = false) {
            Handler(Looper.getMainLooper()).post {
                val duration = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                if (resId != null) Toast.makeText(appContext, resId, duration).show()
            }
        }
    }

    override fun onCreate() {
        appContext = applicationContext
        super.onCreate()
        initDeviceCapabilities()

        // Warm the prayer calculation-method cache before the user opens its settings.
        com.amazon.tv.leanbacklauncher.settings.PrayerMethodRepository.prefetch(appContext)

        val autoupdate = PreferenceManager.getDefaultSharedPreferences(appContext)
            .getBoolean("update_check", true)
        if (autoupdate) {
            CoroutineScope(Dispatchers.IO).launch {
                var count = 60
                try {
                    while (!isConnected(appContext) && count > 0) {
                        delay(1000)
                        count--
                    }
                    if (Updater.check()) {
                        val intent = Intent(appContext, UpdateActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isConnectedOld(context: Context): Boolean {
        val connManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connManager.activeNetworkInfo?.isConnected == true
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isConnectedNewApi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun isConnected(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isConnectedNewApi(context)
        } else {
            isConnectedOld(context)
        }

    private fun initDeviceCapabilities() {
        LauncherConfiguration.setInstance(HighEndLauncherConfiguration())
    }
}
