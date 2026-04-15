package com.mipay.gpay.lsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NfcSuService : Service() {

    companion object {
        private const val TAG = "MiPayGPay"
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"

        fun logToFile(msg: String) {
            try {
                val logFile = File(LOG_FILE)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                logFile.appendText("$timestamp [NfcSuService] $msg\n")
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "logToFile failed: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logToFile("=".repeat(50))
        logToFile("onStartCommand called")
        logToFile("intent=$intent")

        val component = intent?.getStringExtra("component")
        val action = intent?.getStringExtra("action") ?: "操作"

        logToFile("component=$component")
        logToFile("action=$action")

        if (component == null) {
            logToFile("ERROR: component is null, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Thread {
            try {
                logToFile("Starting su execution in Service")

                // 在模块进程执行 su 命令
                val cmd = "settings put secure " + NFC_KEY + " " + component
                logToFile("cmd=$cmd")

                logToFile("Executing: su -c '$cmd'")
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                logToFile("Process started, waiting...")

                p.waitFor()
                val exitCode = p.exitValue()
                logToFile("su exitCode=$exitCode")

                if (exitCode == 0) {
                    // 验证
                    Thread.sleep(200)
                    val verifyCmd = "settings get secure " + NFC_KEY
                    logToFile("verifyCmd=$verifyCmd")

                    val verifyP = Runtime.getRuntime().exec(arrayOf("su", "-c", verifyCmd))
                    verifyP.waitFor()
                    val verify = verifyP.inputStream.bufferedReader().readText().trim()
                    logToFile("verify result=$verify")

                    if (verify == component) {
                        logToFile("SUCCESS: $action NFC to $component")
                        showToast("$action NFC 成功")
                    } else {
                        logToFile("VERIFY FAILED: expected=$component, actual=$verify")
                        showToast("$action NFC: 验证失败")
                    }
                } else {
                    val err = p.errorStream.bufferedReader().readText()
                    logToFile("ERROR: su exit=$exitCode, err=$err")
                    showToast("$action NFC: su exit=$exitCode")
                }
            } catch (e: Throwable) {
                logToFile("EXCEPTION: ${e.message}")
                logToFile(e.stackTraceToString())
                showToast("$action NFC: ${e.message}")
            }
            logToFile("=".repeat(50))
            stopSelf(startId)
        }.start()

        return START_NOT_STICKY
    }

    private fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
