package com.mipay.gpay.lsp

import android.content.Context
import android.net.Uri

object SettingsStore {
    const val MIUIX_STYLE = "miuix_style"
    const val POWER_DOUBLE_CLICK = "power_double_click"
    const val SHOW_LAUNCHER_ICON = "show_launcher_icon"

    private const val AUTHORITY = "com.mipay.gpay.lsp.settings"
    private const val METHOD_GET = "get_boolean"
    private const val METHOD_SET = "set_boolean"
    private const val KEY_VALUE = "value"
    private const val KEY_SUCCESS = "success"

    private fun uri(): Uri = Uri.parse("content://$AUTHORITY")

    fun getBoolean(context: Context, key: String): Boolean {
        return runCatching {
            context.contentResolver
                .call(uri(), METHOD_GET, key, null)
                ?.getBoolean(KEY_VALUE, false)
                ?: false
        }.getOrDefault(false)
    }

    fun setBoolean(context: Context, key: String, value: Boolean): Boolean {
        val extras = android.os.Bundle().apply { putBoolean(KEY_VALUE, value) }
        return runCatching {
            context.contentResolver
                .call(uri(), METHOD_SET, key, extras)
                ?.getBoolean(KEY_SUCCESS, false)
                ?: false
        }.getOrDefault(false)
    }
}
