package com.amazon.tv.leanbacklauncher.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.amazon.tv.leanbacklauncher.R

/**
 * Helper untuk mengecek dan meminta agar launcher ini dijadikan default (HOME) launcher.
 */
object DefaultLauncher {
    private const val TAG = "DefaultLauncher"
    const val REQUEST_ROLE_HOME = 4108

    /** Dialog hanya ditampilkan sekali per proses agar tidak mengganggu setiap kali kembali ke home. */
    @Volatile
    var promptShown = false

    /** True bila launcher ini sudah menjadi default HOME. */
    fun isDefault(activity: Activity): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = activity.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == activity.packageName
    }

    /**
     * Minta sistem menjadikan launcher ini default. Urutan percobaan:
     * 1. RoleManager (Android 10+), 2. layar "Default home app" sistem, 3. petunjuk manual.
     */
    fun request(activity: Activity) {
        if (isDefault(activity)) {
            Toast.makeText(activity, R.string.default_launcher_already, Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val rm = activity.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_ROLE_HOME)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "RoleManager request failed", e)
            }
        }
        try {
            activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ACTION_HOME_SETTINGS not available", e)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to open home settings", e)
        }
        // Perangkat (mis. sebagian Android TV/Fire TV) tidak menyediakan layar pilihan default launcher.
        Toast.makeText(activity, R.string.default_launcher_manual, Toast.LENGTH_LONG).show()
    }
}
