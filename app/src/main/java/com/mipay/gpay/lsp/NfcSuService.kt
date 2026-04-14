package com.mipay.gpay.lsp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast

class NfcSuService : Service() {

    companion object {
        private const val TAG = "MiPayGPay"
        private const val NFC_KEY = "nfc_payment_default_component"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val component = intent?.getStringExtra("component") ?: return START_NOT_STICKY
        val action = intent?.getStringExtra("action") ?: "操作"

        Thread {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure $NFC_KEY $component"))
                p.waitFor()
                val exitCode = p.exitValue()
                
                if (exitCode == 0) {
                    // 验证
                    Thread.sleep(200)
                    val verify = runCatching {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "settings get secure $NFC_KEY")).inputStream.bufferedReader().readText().trim()
                    }.getOrNull()
                    
                    if (verify == component) {
                        android.util.Log.i(TAG, "$action NFC 成功 (su)")
                    } else {
                        android.util.Log.w(TAG, "$action NFC su exit=0 但验证失败: $verify")
                        showToast("$action NFC: su 写入验证失败")
                    }
                } else {
                    android.util.Log.e(TAG, "$action NFC su exit=$exitCode")
                    showToast("$action NFC: su exit=$exitCode")
                }
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "$action NFC su 异常: ${e.message}")
                showToast("$action NFC: ${e.message}")
            }
            
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
