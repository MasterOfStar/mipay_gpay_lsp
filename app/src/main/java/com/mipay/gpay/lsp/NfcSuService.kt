package com.mipay.gpay.lsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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
                Log.d(TAG, "[NfcSuService] $msg")
            } catch (e: Throwable) {
                Log.e(TAG, "logToFile failed: ${e.message}")
            }
        }

        // 执行 su 命令的健壮方法
        fun executeSuCommand(cmd: String): Pair<Int, String> {
            logToFile("executeSuCommand: $cmd")
            try {
                // 方法1: 直接 exec
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                reader.close()
                process.waitFor()
                val exitCode = process.exitValue()
                logToFile("su result: exitCode=$exitCode, output=$output")
                return Pair(exitCode, output)
            } catch (e: Exception) {
                logToFile("su exec failed: ${e.message}")
                
                // 方法2: 使用 shell
                try {
                    val process = Runtime.getRuntime().exec("su")
                    val writer = OutputStreamWriter(process.outputStream)
                    writer.write(cmd)
                    writer.flush()
                    writer.close()
                    
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = reader.readText()
                    reader.close()
                    
                    process.waitFor()
                    val exitCode = process.exitValue()
                    logToFile("su shell result: exitCode=$exitCode, output=$output")
                    return Pair(exitCode, output)
                } catch (e2: Exception) {
                    logToFile("su shell also failed: ${e2.message}")
                    return Pair(-1, e2.message ?: "Unknown error")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        logToFile("=".repeat(50))
        logToFile("onCreate: Service created in process ${android.os.Process.myPid()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logToFile("=".repeat(50))
        logToFile("onStartCommand: intent=$intent, flags=$flags, startId=$startId")

        val component = intent?.getStringExtra("component")
        val action = intent?.getStringExtra("action") ?: "操作"

        logToFile("component=$component")
        logToFile("action=$action")

        if (component == null) {
            logToFile("ERROR: component is null, stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // 在后台线程执行
        Thread {
            try {
                val cmd = "settings put secure $NFC_KEY $component"
                logToFile("Executing: $cmd")

                val (exitCode, output) = executeSuCommand(cmd)

                if (exitCode == 0) {
                    // 验证
                    Thread.sleep(200)
                    val verifyCmd = "settings get secure $NFC_KEY"
                    val (verifyCode, verifyOutput) = executeSuCommand(verifyCmd)
                    val result = verifyOutput.trim()
                    logToFile("Verify: result=$result")

                    if (result == component) {
                        logToFile("SUCCESS: $action NFC -> $component")
                        showToast("$action 成功")
                    } else {
                        logToFile("VERIFY FAILED: expected=$component, actual=$result")
                        showToast("$action 验证失败")
                    }
                } else {
                    logToFile("FAILED: exitCode=$exitCode, output=$output")
                    showToast("$action 失败: $output")
                }
            } catch (e: Throwable) {
                logToFile("EXCEPTION: ${e.message}")
                logToFile(e.stackTraceToString())
                showToast("$action 异常: ${e.message}")
            }
            logToFile("=".repeat(50))
            stopSelf(startId)
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logToFile("onDestroy")
        super.onDestroy()
    }

    private fun showToast(msg: String) {
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            logToFile("showToast failed: ${e.message}")
        }
    }
}
