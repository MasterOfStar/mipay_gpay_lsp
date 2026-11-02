package com.mipay.gpay.lsp

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LSPosed 模块入口 — XposedInit 是 xposed_init 中声明的入口类
 */
class XposedInit : IXposedHookLoadPackage {

    companion object {
        const val TAG = "MiPayGPayLSP"
        const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"

        fun logToFile(msg: String) {
            try {
                val logFile = File(LOG_FILE)
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                logFile.appendText("$timestamp [XposedInit] $msg\n")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: logToFile failed: ${e.message}")
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        logToFile("=".repeat(50))
        logToFile("handleLoadPackage: ${lpparam.packageName}")
        XposedBridge.log("$TAG: XposedInit received package: ${lpparam.packageName}")

        // 委托给 MainHook 处理所有 Hook 逻辑
        try {
            MainHook().handleLoadPackage(lpparam)
            logToFile("MainHook.handleLoadPackage completed for ${lpparam.packageName}")
        } catch (e: Throwable) {
            logToFile("ERROR in MainHook: ${e.message}")
            logToFile(e.stackTraceToString())
        }
    }
}
