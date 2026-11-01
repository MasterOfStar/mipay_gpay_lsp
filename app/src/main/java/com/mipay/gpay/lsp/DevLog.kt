package com.mipay.gpay.lsp

import de.robv.android.xposed.XposedBridge

object DevLog {
    const val TAG = "MPG-DEV"

    fun i(message: String) {
        XposedBridge.log("$TAG: $message")
    }

    fun e(message: String, throwable: Throwable? = null) {
        XposedBridge.log("$TAG ERROR: $message")
        throwable?.let {
            XposedBridge.log("$TAG ERROR: ${it.javaClass.name}: ${it.message}")
            XposedBridge.log("$TAG ERROR STACK:\n${it.stackTraceToString()}")
        }
    }
}
