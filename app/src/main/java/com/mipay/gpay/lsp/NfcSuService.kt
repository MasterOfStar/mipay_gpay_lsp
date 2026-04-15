package com.mipay.gpay.lsp

import android.app.Service
import android.content.Context
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
        private const val SAVED_NFC_FILE = "saved_nfc.txt"

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

        // 执行 su 命令
        fun executeSuCommand(cmd: String): Pair<Int, String> {
            logToFile("executeSuCommand: $cmd")
            try {
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
                return Pair(-1, e.message ?: "Unknown error")
            }
        }

        // 获取当前 NFC 默认支付应用
        fun getNfcDefault(): String? {
            val (exitCode, output) = executeSuCommand("settings get secure $NFC_KEY")
            val result = output.trim()
            logToFile("getNfcDefault: exitCode=$exitCode, result=$result")
            return if (exitCode == 0 && result.isNotEmpty() && result != "null") result else null
        }

        // 设置 NFC 默认支付应用
        fun setNfcDefault(component: String): Boolean {
            val (exitCode, output) = executeSuCommand("settings put secure $NFC_KEY $component")
            logToFile("setNfcDefault: exitCode=$exitCode")
            return exitCode == 0
        }

        // 保存当前 NFC 设置
        fun saveCurrentNfc(context: Context): String? {
            val current = getNfcDefault()
            if (current != null && current != MainHook.WALLET_NFC_COMPONENT) {
                try {
                    context.filesDir.resolve(SAVED_NFC_FILE).writeText(current)
                    logToFile("savedNfc: $current")
                    return current
                } catch (e: Exception) {
                    logToFile("saveCurrentNfc failed: ${e.message}")
                }
            }
            return null
        }

        // 读取保存的 NFC 设置
        fun loadSavedNfc(context: Context): String? {
            return try {
                val file = context.filesDir.resolve(SAVED_NFC_FILE)
                if (file.exists()) {
                    val saved = file.readText().trim()
                    logToFile("loadSavedNfc: $saved")
                    saved.takeIf { it.isNotEmpty() }
                } else null
            } catch (e: Exception) {
                logToFile("loadSavedNfc failed: ${e.message}")
                null
            }
        }

        // 清除保存的 NFC 设置
        fun clearSavedNfc(context: Context) {
            try {
                context.filesDir.resolve(SAVED_NFC_FILE).delete()
                logToFile("clearSavedNfc")
            } catch (e: Exception) {
                logToFile("clearSavedNfc failed: ${e.message}")
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
        logToFile("onStartCommand: intent=$intent")

        val action = intent?.getStringExtra("action") ?: "操作"
        val component = intent?.getStringExtra("component")

        logToFile("action=$action")
        logToFile("component=$component")

        Thread {
            try {
                when (action) {
                    "切换" -> {
                        // 切换到 Wallet，先保存当前设置
                        val currentNfc = saveCurrentNfc(applicationContext)
                        logToFile("currentNfc before switch: $currentNfc")
                        
                        if (setNfcDefault(MainHook.WALLET_NFC_COMPONENT)) {
                            // 验证
                            Thread.sleep(200)
                            val verify = getNfcDefault()
                            if (verify == MainHook.WALLET_NFC_COMPONENT) {
                                logToFile("SUCCESS: 切换到 Wallet")
                                showToast("切换成功")
                            } else {
                                logToFile("VERIFY FAILED: expected=${MainHook.WALLET_NFC_COMPONENT}, actual=$verify")
                                showToast("切换验证失败")
                            }
                        } else {
                            logToFile("FAILED: setNfcDefault failed")
                            showToast("切换失败")
                        }
                    }
                    "还原" -> {
                        // 还原到保存的设置
                        val savedNfc = loadSavedNfc(applicationContext)
                        logToFile("savedNfc to restore: $savedNfc")
                        
                        if (savedNfc != null) {
                            if (setNfcDefault(savedNfc)) {
                                Thread.sleep(200)
                                val verify = getNfcDefault()
                                if (verify == savedNfc) {
                                    logToFile("SUCCESS: 还原到 $savedNfc")
                                    showToast("还原成功")
                                } else {
                                    logToFile("VERIFY FAILED: expected=$savedNfc, actual=$verify")
                                    showToast("还原验证失败")
                                }
                            } else {
                                logToFile("FAILED: setNfcDefault failed")
                                showToast("还原失败")
                            }
                            clearSavedNfc(applicationContext)
                        } else {
                            // 没有保存的设置，切换到 MiPay
                            logToFile("No saved NFC, fallback to MiPay")
                            if (setNfcDefault(MainHook.MIPAY_NFC_COMPONENT)) {
                                logToFile("SUCCESS: 切换到 MiPay")
                                showToast("已切换到 MiPay")
                            }
                        }
                    }
                    else -> {
                        logToFile("Unknown action: $action")
                    }
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
