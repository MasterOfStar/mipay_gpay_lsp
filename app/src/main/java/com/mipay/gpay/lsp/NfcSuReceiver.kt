package com.mipay.gpay.lsp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NfcSuReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MiPayGPay"
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val LOG_FILE = "/sdcard/gpay_lsp.log"

        fun logToFile(msg: String) {
            try {
                val logFile = File(LOG_FILE)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                logFile.appendText("$timestamp [NfcSuReceiver] $msg\n")
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "logToFile failed: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        logToFile("=".repeat(50))
        logToFile("onReceive called")
        logToFile("intent=$intent")
        logToFile("intent.action=${intent.action}")
        logToFile("intent.extras=${intent.extras}")

        val component = intent.getStringExtra("component")
        val action = intent.getStringExtra("action") ?: "操作"

        logToFile("component=$component")
        logToFile("action=$action")

        if (component == null) {
            logToFile("ERROR: component is null, returning")
            return
        }

        Thread {
            try {
                logToFile("Starting su execution thread")

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
                        showToast(context, "$action NFC 成功")
                    } else {
                        logToFile("VERIFY FAILED: expected=$component, actual=$verify")
                        showToast(context, "$action NFC: 验证失败")
                    }
                } else {
                    val err = p.errorStream.bufferedReader().readText()
                    logToFile("ERROR: su exit=$exitCode, err=$err")
                    showToast(context, "$action NFC: su exit=$exitCode")
                }
            } catch (e: Throwable) {
                logToFile("EXCEPTION: ${e.message}")
                logToFile(e.stackTraceToString())
                showToast(context, "$action NFC: ${e.message}")
            }
            logToFile("=".repeat(50))
        }.start()
    }

    private fun showToast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
