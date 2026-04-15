package com.mipay.gpay.lsp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BroadcastReceiver 作为 NFC 切换的桥梁
 * 运行在 :nfc_switcher 独立进程，通过广播触发 su 命令
 */
class NfcReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NfcReceiver"
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"
        private const val SAVED_NFC_FILE = "/data/local/tmp/gpay_saved_nfc.txt"

        // Google Wallet HCE Service
        const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"
        // MiPay HCE Service
        const val MIPAY_NFC_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.hce.service.TsmClientHceService"

        // Action
        const val ACTION_SWITCH_TO_WALLET = "com.mipay.gpay.lsp.ACTION_SWITCH_TO_WALLET"
        const val ACTION_RESTORE_NFC = "com.mipay.gpay.lsp.ACTION_RESTORE_NFC"

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

    override fun onReceive(context: Context, intent: Intent) {
        log("═══════════════════════════════════")
        log("onReceive: action=${intent.action}, pid=${android.os.Process.myPid()}")

        when (intent.action) {
            ACTION_SWITCH_TO_WALLET -> doSwitchToWallet()
            ACTION_RESTORE_NFC -> doRestoreNfc()
            else -> log("Unknown action: ${intent.action}")
        }
    }

    private fun doSwitchToWallet() {
        log("doSwitchToWallet: starting")
        val saved = saveCurrentNfc()
        log("doSwitchToWallet: saved=$saved")

        if (setNfcDefault(WALLET_NFC_COMPONENT)) {
            Thread.sleep(300)
            val verify = getNfcDefault()
            val success = verify == WALLET_NFC_COMPONENT
            log("doSwitchToWallet: verify=$verify, success=$success")
        } else {
            log("doSwitchToWallet: setNfcDefault failed")
        }
    }

    private fun doRestoreNfc() {
        log("doRestoreNfc: starting")
        val saved = loadSavedNfc()
        log("doRestoreNfc: saved=$saved")

        if (saved != null) {
            val result = setNfcDefault(saved)
            if (result) {
                Thread.sleep(300)
                val verify = getNfcDefault()
                log("doRestoreNfc: verify=$verify")
            }
            clearSavedNfc()
        } else {
            log("doRestoreNfc: no saved NFC, fallback to MiPay")
            val result = setNfcDefault(MIPAY_NFC_COMPONENT)
            log("doRestoreNfc: fallback result=$result")
        }
    }
}
