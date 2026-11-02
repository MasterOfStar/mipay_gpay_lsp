package com.mipay.gpay.lsp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit

class RootCommandProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        checkCaller()
        return when (method) {
            METHOD_LAUNCH_WALLET -> launchWalletQuickDraw()
            else -> null
        }
    }

    private fun checkCaller() {
        val callerUid = Binder.getCallingUid()
        if (callerUid == Process.myUid() || callerUid == Process.SYSTEM_UID) return

        val packages = context?.packageManager?.getPackagesForUid(callerUid)
        if (packages?.contains(MainHook.MIPAY_PKG) != true) {
            throw SecurityException("Untrusted root command caller uid=$callerUid")
        }
    }

    private fun launchWalletQuickDraw(): Bundle {
        val flags = MainHook.WALLET_QUICKDRAW_FLAGS.toString(16)
        val command = "/system/bin/am start -f 0x$flags " +
                "-n com.mipay.gpay.lsp/.QuickDrawLaunchActivity"
        val result = runRootCommand(command)

        return Bundle().apply {
            putBoolean(KEY_SUCCESS, result.success)
            putString(KEY_OUTPUT, result.output)
        }
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

    companion object {
        const val AUTHORITY = "com.mipay.gpay.lsp.root"
        const val METHOD_LAUNCH_WALLET = "launch_wallet_quickdraw"
        const val KEY_SUCCESS = "success"
        const val KEY_OUTPUT = "output"

        private const val TAG = "MiPayGPayRoot"
        private const val ROOT_TIMEOUT_SECONDS = 60L

        data class RootResult(val success: Boolean, val output: String)

        @JvmStatic
        fun runRootCommand(command: String): RootResult {
            return try {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()

                if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return RootResult(false, "root command timed out")
                }

                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                val success = process.exitValue() == 0
                Log.i(TAG, "exit=${process.exitValue()} output=$output")
                RootResult(success, output)
            } catch (e: IOException) {
                Log.e(TAG, "root command failed", e)
                RootResult(false, e.message ?: "root unavailable")
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                RootResult(false, "root command interrupted")
            }
        }
    }
}
