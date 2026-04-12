package com.mipay.gpay.lsp

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块入口 — XposedInit 是 xposed_init 中声明的入口类
 */
class XposedInit : IXposedHookLoadPackage {

    companion object {
        const val TAG = "MiPayGPayLSP"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: XposedInit received package: ${lpparam.packageName}")

        // 委托给 MainHook 处理所有 Hook 逻辑
        MainHook().handleLoadPackage(lpparam)
    }
}
