package com.mipay.gpay.lsp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class NfcSuReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MiPayGPay"
        private const val NFC_KEY = "nfc_payment_default_component"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val component = intent.getStringExtra("component") ?: return
        val action = intent.getStringExtra("action") ?: "操作"

        android.util.Log.i(TAG, "[模块进程] 收到 NFC 设置请求: $component")

        Thread {
            try {
                // 在模块进程执行 su 命令
                val cmd = "settings put secure " + NFC_KEY + " " + component
                android.util.Log.d(TAG, "[模块进程] 执行命令: su -c '$cmd'")
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                p.waitFor()
                val exitCode = p.exitValue()

                android.util.Log.i(TAG, "[模块进程] su exit=$exitCode")

                if (exitCode == 0) {
                    // 验证
                    Thread.sleep(200)
                    val verifyCmd = "settings get secure " + NFC_KEY
                    android.util.Log.d(TAG, "[模块进程] 验证命令: su -c '$verifyCmd'")
                    val verifyP = Runtime.getRuntime().exec(arrayOf("su", "-c", verifyCmd))
                    verifyP.waitFor()
                    val verify = verifyP.inputStream.bufferedReader().readText().trim()

                    if (verify == component) {
                        android.util.Log.i(TAG, "[模块进程] $action NFC 成功")
                        showToast(context, "$action NFC 成功")
                    } else {
                        android.util.Log.w(TAG, "[模块进程] su exit=0 但验证失败: 期望=$component, 实际=$verify")
                        showToast(context, "$action NFC: 验证失败 (期望=$component, 实际=$verify)")
                    }
                } else {
                    val err = p.errorStream.bufferedReader().readText()
                    android.util.Log.e(TAG, "[模块进程] su exit=$exitCode, err=$err")
                    showToast(context, "$action NFC: su exit=$exitCode")
                }
            } catch (e: Throwable) {
                android.util.Log.e(TAG, "[模块进程] su 异常: ${e.message}")
                showToast(context, "$action NFC: ${e.message}")
            }
        }.start()
    }

    private fun showToast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
