package com.mipay.gpay.lsp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ContentProvider 作为 NFC 切换的桥梁
 * 运行在模块默认进程（非 Wallet/MiPay 进程），在此执行 su 命令
 */
class NfcProvider : ContentProvider() {

    companion object {
        private const val TAG = "NfcProvider"
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"
        private const val SAVED_NFC_FILE = "/data/local/tmp/gpay_saved_nfc.txt"

        // Google Wallet HCE Service
        const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"
        // MiPay HCE Service
        const val MIPAY_NFC_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.hce.service.TsmClientHceService"

        // Method names for call()
        const val METHOD_SWITCH_TO_WALLET = "switch_to_wallet"
        const val METHOD_RESTORE_NFC = "restore_nfc"

        // Authority
        const val AUTHORITY = "com.mipay.gpay.lsp.provider"

        fun log(msg: String) {
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                File(LOG_FILE).appendText("$ts [$TAG] $msg\n")
            } catch (e: Throwable) {
                // ignore
            }
        }

        fun execSu(cmd: String): Pair<Int, String> {
            log("execSu: $cmd")
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
                val err = BufferedReader(InputStreamReader(process.errorStream)).readText()
                process.waitFor()
                val code = process.exitValue()
                val result = if (err.isNotEmpty()) "$output\n[STDERR]$err" else output
                log("execSu result: exitCode=$code")
                Pair(code, result)
            } catch (e: Exception) {
                log("execSu failed: ${e.message}")
                Pair(-1, e.message ?: "Unknown")
            }
        }

        fun getNfcDefault(): String? {
            val (code, out) = execSu("settings get secure $NFC_KEY")
            val result = out.trim()
            return if (code == 0 && result.isNotEmpty() && result != "null") result else null
        }

        fun setNfcDefault(component: String): Boolean {
            val (code, _) = execSu("settings put secure $NFC_KEY $component")
            return code == 0
        }

        fun saveCurrentNfc(): String? {
            val current = getNfcDefault()
            if (current != null && current != WALLET_NFC_COMPONENT) {
                try {
                    File(SAVED_NFC_FILE).writeText(current)
                    log("savedNfc=$current")
                    return current
                } catch (e: Exception) {
                    log("saveCurrentNfc failed: ${e.message}")
                }
            }
            return null
        }

        fun loadSavedNfc(): String? {
            return try {
                val f = File(SAVED_NFC_FILE)
                if (f.exists()) {
                    val s = f.readText().trim()
                    log("loadSavedNfc=$s")
                    s.takeIf { it.isNotEmpty() }
                } else null
            } catch (e: Exception) {
                log("loadSavedNfc failed: ${e.message}")
                null
            }
        }

        fun clearSavedNfc() {
            try {
                File(SAVED_NFC_FILE).delete()
                log("clearSavedNfc")
            } catch (e: Exception) {
                log("clearSavedNfc failed: ${e.message}")
            }
        }
    }

    override fun onCreate(): Boolean {
        log("═══════════════════════════════════")
        log("NfcProvider onCreate: pid=${android.os.Process.myPid()}")
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        log("call: method=$method, arg=$arg")

        return when (method) {
            METHOD_SWITCH_TO_WALLET -> {
                val result = doSwitchToWallet()
                Bundle().apply { putBoolean("result", result) }
            }
            METHOD_RESTORE_NFC -> {
                val result = doRestoreNfc()
                Bundle().apply { putBoolean("result", result) }
            }
            else -> {
                log("Unknown method: $method")
                Bundle().apply { putBoolean("result", false) }
            }
        }
    }

    private fun doSwitchToWallet(): Boolean {
        log("doSwitchToWallet: starting")
        val saved = saveCurrentNfc()
        log("doSwitchToWallet: saved=$saved")

        return if (setNfcDefault(WALLET_NFC_COMPONENT)) {
            Thread.sleep(300)
            val verify = getNfcDefault()
            val success = verify == WALLET_NFC_COMPONENT
            log("doSwitchToWallet: verify=$verify, success=$success")
            success
        } else {
            log("doSwitchToWallet: setNfcDefault failed")
            false
        }
    }

    private fun doRestoreNfc(): Boolean {
        log("doRestoreNfc: starting")
        val saved = loadSavedNfc()
        log("doRestoreNfc: saved=$saved")

        return if (saved != null) {
            val result = setNfcDefault(saved)
            if (result) {
                Thread.sleep(300)
                val verify = getNfcDefault()
                log("doRestoreNfc: verify=$verify")
            }
            clearSavedNfc()
            result
        } else {
            log("doRestoreNfc: no saved NFC, fallback to MiPay")
            val result = setNfcDefault(MIPAY_NFC_COMPONENT)
            log("doRestoreNfc: fallback result=$result")
            result
        }
    }

    // Required but unused methods
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
