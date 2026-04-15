package com.mipay.gpay.lsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 独立进程 NFC 切换服务（:nfc_switcher 进程）
 * 该进程通过 su 命令修改 NFC 设置
 */
class NfcSuService : Service() {

    companion object {
        private const val TAG = "NfcSuService"
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"
        private const val SAVED_NFC_FILE = "/data/local/tmp/gpay_saved_nfc.txt"

        // Google Wallet HCE Service
        const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"
        // MiPay HCE Service
        const val MIPAY_NFC_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.hce.service.TsmClientHceService"

        // Intent Action: 切换到 Wallet
        const val ACTION_SWITCH_TO_WALLET = "com.mipay.gpay.lsp.SWITCH_TO_WALLET"
        // Intent Action: 还原 NFC
        const val ACTION_RESTORE = "com.mipay.gpay.lsp.RESTORE_NFC"

        fun log(msg: String) {
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                File(LOG_FILE).appendText("$ts [NfcSu] $msg\n")
                Log.d(TAG, msg)
            } catch (e: Throwable) {
                Log.e(TAG, "log failed: ${e.message}")
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
                log("execSu result: exitCode=$code, output=$result")
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

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        log("═══════════════════════════════════")
        log("onCreate: pid=${android.os.Process.myPid()}, process=${android.os.Process.myPid()}")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand: action=${intent?.action}, startId=$startId")

        val action = intent?.action ?: return START_NOT_STICKY

        Thread {
            try {
                when (action) {
                    ACTION_SWITCH_TO_WALLET -> switchToWallet()
                    ACTION_RESTORE -> restoreNfc()
                    else -> log("Unknown action: $action")
                }
            } catch (e: Throwable) {
                log("EXCEPTION: ${e.message}")
            } finally {
                stopSelf(startId)
            }
        }.start()

        // START_STICKY 让进程被杀死后能重新拉起
        return START_STICKY
    }

    private fun switchToWallet() {
        log("switchToWallet: starting")
        val saved = saveCurrentNfc()
        log("switchToWallet: saved=$saved")

        if (setNfcDefault(WALLET_NFC_COMPONENT)) {
            Thread.sleep(300)
            val verify = getNfcDefault()
            if (verify == WALLET_NFC_COMPONENT) {
                log("switchToWallet: SUCCESS")
                showToast("已切换 NFC 到 Wallet")
            } else {
                log("switchToWallet: VERIFY FAILED actual=$verify")
                showToast("切换验证失败")
            }
        } else {
            log("switchToWallet: setNfcDefault failed")
            showToast("切换 NFC 失败")
        }
    }

    private fun restoreNfc() {
        log("restoreNfc: starting")
        val saved = loadSavedNfc()
        log("restoreNfc: saved=$saved")

        if (saved != null) {
            if (setNfcDefault(saved)) {
                Thread.sleep(300)
                val verify = getNfcDefault()
                if (verify == saved) {
                    log("restoreNfc: SUCCESS restored=$saved")
                    showToast("已还原 NFC")
                } else {
                    log("restoreNfc: VERIFY FAILED actual=$verify")
                }
            }
            clearSavedNfc()
        } else {
            log("restoreNfc: no saved NFC, fallback to MiPay")
            if (setNfcDefault(MIPAY_NFC_COMPONENT)) {
                log("restoreNfc: switched to MiPay SUCCESS")
                showToast("已切换到小米 Pay")
            }
        }
    }

    private fun showToast(msg: String) {
        try {
            handler.post {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            log("showToast failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        log("onDestroy")
        super.onDestroy()
    }
}
