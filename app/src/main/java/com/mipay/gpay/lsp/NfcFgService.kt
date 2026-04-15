package com.mipay.gpay.lsp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 前台 Service 在 :nfc_switcher 进程执行 su 命令
 * 通过前台通知保活，绕过 stopped state 限制
 */
class NfcFgService : Service() {

    companion object {
        private const val TAG = "NfcFgService"
        private const val CHANNEL_ID = "nfc_switcher"
        private const val NOTIF_ID = 1
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"
        private const val SAVED_NFC_FILE = "/data/local/tmp/gpay_saved_nfc.txt"

        // Google Wallet HCE Service
        const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"
        // MiPay HCE Service
        const val MIPAY_NFC_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.hce.service.TsmClientHceService"

        // Actions
        const val ACTION_SWITCH_TO_WALLET = "com.mipay.gpay.lsp.ACTION_SWITCH_TO_WALLET"
        const val ACTION_RESTORE_NFC = "com.mipay.gpay.lsp.ACTION_RESTORE_NFC"
        const val ACTION_START_FOREGROUND = "com.mipay.gpay.lsp.ACTION_START_FOREGROUND"

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

    override fun onCreate() {
        super.onCreate()
        log("═══════════════════════════════════")
        log("onCreate: pid=${android.os.Process.myPid()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        log("onStartCommand: action=$action, pid=${android.os.Process.myPid()}")

        // 首次启动或需要保活时，启动前台通知
        if (action == ACTION_START_FOREGROUND || action == null) {
            startForegroundInternal()
        }

        when (action) {
            ACTION_SWITCH_TO_WALLET -> doSwitchToWallet()
            ACTION_RESTORE_NFC -> doRestoreNfc()
        }

        return START_STICKY
    }

    private fun startForegroundInternal() {
        log("startForegroundInternal")
        try {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NFC Switcher")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
            startForeground(NOTIF_ID, notification)
            log("startForeground: success")
        } catch (e: Exception) {
            log("startForeground: failed ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NFC Switcher",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
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

    override fun onBind(intent: Intent?): IBinder? = null
}
