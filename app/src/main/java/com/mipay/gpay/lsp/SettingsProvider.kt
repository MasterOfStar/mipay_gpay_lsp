package com.mipay.gpay.lsp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

class SettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val prefs = preferences() ?: return true
        if (!prefs.contains(SettingsStore.HIDE_LAUNCHER_ICON)) {
            val legacyShow = prefs.getBoolean(SettingsStore.LEGACY_SHOW_LAUNCHER_ICON, true)
            prefs.edit()
                .putBoolean(SettingsStore.HIDE_LAUNCHER_ICON, !legacyShow)
                .apply()
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isTrustedCaller()) return null
        val prefs = preferences() ?: return null

        return when (method) {
            METHOD_GET -> Bundle().apply {
                putBoolean(KEY_VALUE, prefs.getBoolean(arg, false))
            }
            METHOD_SET -> {
                if (arg !in validKeys) return null
                val value = extras?.getBoolean(KEY_VALUE, false) ?: false
                prefs.edit().putBoolean(arg, value).apply()
                Bundle().apply { putBoolean(KEY_SUCCESS, true) }
            }
            else -> null
        }
    }

    private fun preferences() = context?.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun isTrustedCaller(): Boolean {
        val callerUid = Binder.getCallingUid()
        if (callerUid == Process.myUid() || callerUid == Process.SYSTEM_UID) {
            return true
        }
        val packages = context?.packageManager?.getPackagesForUid(callerUid)
        return packages?.contains(MainHook.MIPAY_PKG) == true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private companion object {
        const val PREFS_NAME = "mipay_gpay_settings"
        const val METHOD_GET = "get_boolean"
        const val METHOD_SET = "set_boolean"
        const val KEY_VALUE = "value"
        const val KEY_SUCCESS = "success"
        val validKeys = setOf(
            SettingsStore.MIUIX_STYLE,
            SettingsStore.POWER_DOUBLE_CLICK,
            SettingsStore.HIDE_LAUNCHER_ICON
        )
    }
}
