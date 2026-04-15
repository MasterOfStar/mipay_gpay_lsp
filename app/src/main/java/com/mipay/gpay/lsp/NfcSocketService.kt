package com.mipay.gpay.lsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 本地 Socket Server，在模块进程运行（有 root 权限）
 * Wallet 进程通过 localhost socket 连接并发送 NFC 切换指令
 */
class NfcSocketService : Service() {

    companion object {
        private const val TAG = "NfcSocketService"
        private const val SOCKET_PORT = 9876
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"
        private const val SAVED_NFC_FILE = "/data/local/tmp/gpay_saved_nfc.txt"

        const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"
        const val MIPAY_NFC_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.hce.service.TsmClientHceService"

        fun log(msg: String) {
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                File(LOG_FILE).appendText("$ts [$TAG] $msg\n")
            } catch (e: Throwable) {}
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

    private val executor = Executors.newSingleThreadExecutor()
    private var serverSocket: ServerSocket? = null
    private var running = false

    override fun onCreate() {
        super.onCreate()
        log("═══════════════════════════════════")
        log("onCreate: pid=${android.os.Process.myPid()}")
        startServer()
    }

    private fun startServer() {
        running = true
        executor.execute {
            try {
                serverSocket = ServerSocket(SOCKET_PORT)
                log("Server started on port $SOCKET_PORT")

                while (running) {
                    try {
                        val client = serverSocket!!.accept()
                        log("Client connected: ${client.inetAddress}")

                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val writer = PrintWriter(client.getOutputStream(), true)

                        val cmd = reader.readLine()
                        log("Received command: $cmd")

                        val response = when (cmd) {
                            "SWITCH_TO_WALLET" -> {
                                doSwitchToWallet()
                                "OK"
                            }
                            "RESTORE_NFC" -> {
                                doRestoreNfc()
                                "OK"
                            }
                            "PING" -> "PONG"
                            else -> "UNKNOWN"
                        }

                        writer.println(response)
                        client.close()
                    } catch (e: Exception) {
                        log("Client handling error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                log("Server error: ${e.message}")
            }
        }
    }

    private fun doSwitchToWallet() {
        log("doSwitchToWallet")
        saveCurrentNfc()
        setNfcDefault(WALLET_NFC_COMPONENT)
    }

    private fun doRestoreNfc() {
        log("doRestoreNfc")
        val saved = loadSavedNfc()
        if (saved != null) {
            setNfcDefault(saved)
            clearSavedNfc()
        } else {
            setNfcDefault(MIPAY_NFC_COMPONENT)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
