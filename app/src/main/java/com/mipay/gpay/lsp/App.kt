package com.mipay.gpay.lsp

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LauncherIconSync.sync(this)
    }
}

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LauncherIconSync.sync(context)
        }
    }
}

object LauncherIconSync {
    private const val PREFS_NAME = "mipay_gpay_settings"

    fun sync(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.contains(SettingsStore.HIDE_LAUNCHER_ICON)) {
                val legacyShow = prefs.getBoolean(SettingsStore.LEGACY_SHOW_LAUNCHER_ICON, true)
                prefs.edit()
                    .putBoolean(SettingsStore.HIDE_LAUNCHER_ICON, !legacyShow)
                    .apply()
            }

            val hidden = prefs.getBoolean(SettingsStore.HIDE_LAUNCHER_ICON, false)
            val component = ComponentName(
                context.packageName,
                "${context.packageName}.LauncherActivity"
            )
            val state = if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
            context.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
