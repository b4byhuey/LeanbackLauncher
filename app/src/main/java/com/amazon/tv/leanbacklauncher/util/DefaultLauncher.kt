package com.amazon.tv.leanbacklauncher.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.util.concurrent.TimeUnit
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
    fun isDefault(activity: Activity): Boolean = isDefault(activity.applicationContext)


    data class RootResult(val success: Boolean, val message: String)

    /**
     * Uses an explicitly approved root shell to request this app as HOME.
     *
     * This deliberately does not disable the existing launcher: OEM TV firmware can
     * have additional HOME handlers or watchdogs, so disabling an unknown package
     * could leave the device without a usable home screen.
     */
    fun forceDefaultWithRoot(context: Context): RootResult {
        if (isDefault(context)) {
            return RootResult(true, context.getString(R.string.default_launcher_already))
        }

        val userId = Process.myUid() / 100000
        val component = ComponentName(
            context.packageName,
            "${context.packageName}.MainActivity"
        ).flattenToString()
        val command = "cmd package set-home-activity --user $userId $component"

        val shellOutput = try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                return RootResult(false, context.getString(R.string.default_launcher_root_timeout))
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.exitValue() != 0) {
                return RootResult(
                    false,
                    context.getString(
                        R.string.default_launcher_root_failed,
                        output.ifBlank { "exit code ${process.exitValue()}" }
                    )
                )
            }
            output
        } catch (e: Exception) {
            Log.w(TAG, "Root HOME request failed", e)
            return RootResult(
                false,
                context.getString(
                    R.string.default_launcher_root_failed,
                    e.message ?: e.javaClass.simpleName
                )
            )
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            homeIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return if (resolved?.activityInfo?.packageName == context.packageName) {
            RootResult(true, context.getString(R.string.default_launcher_root_success))
        } else {
            RootResult(
                false,
                context.getString(
                    R.string.default_launcher_root_not_verified,
                    shellOutput.ifBlank { "HOME resolver still points to another launcher" }
                )
            )
        }
    }

    private fun isDefault(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
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
