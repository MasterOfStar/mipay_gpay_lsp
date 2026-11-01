package com.mipay.gpay.lsp

import android.content.ComponentName
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object SystemServerQuickDrawHooks {
    fun install(loader: ClassLoader) {
        DevLog.i("SYSTEM install QuickDraw hook mode=${ExperimentConfig.mode()}")
        val callbackClass = runCatching {
            XposedHelpers.findClass("com.android.internal.policy.IKeyguardDismissCallback", loader)
        }.getOrElse {
            DevLog.e("SYSTEM IKeyguardDismissCallback missing", it)
            return
        }

        val targets = listOf(
            "com.android.server.wm.KeyguardController",
            "com.android.server.wm.ActivityTaskManagerService"
        )
        var installed = 0
        for (target in targets) {
            try {
                val targetClass = XposedHelpers.findClass(target, loader)
                val method = XposedHelpers.findMethodExact(
                    targetClass,
                    "dismissKeyguard",
                    IBinder::class.java,
                    callbackClass,
                    CharSequence::class.java
                )
                DevLog.i("SYSTEM found $target#dismissKeyguard")
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mode = ExperimentConfig.mode()
                        if (!ExperimentConfig.system(mode)) {
                            DevLog.i("SYSTEM gate skip mode=$mode")
                            return
                        }

                        val callerUid = Binder.getCallingUid()
                        val token = param.args.getOrNull(0) as? IBinder
                        val callback = param.args.getOrNull(1)
                        DevLog.i(
                            "SYSTEM dismissKeyguard invoked mode=$mode callerUid=$callerUid token=$token " +
                                "callback=${callback?.javaClass?.name}"
                        )
                        if (token == null || callback == null) {
                            DevLog.i("SYSTEM missing token/callback; allow original")
                            return
                        }
                        if (!isWalletQuickDraw(token, loader)) {
                            DevLog.i("SYSTEM not Wallet QUICKDRAW; allow original")
                            return
                        }

                        DevLog.i("SYSTEM Wallet QUICKDRAW; suppress bouncer")
                        try {
                            XposedHelpers.callMethod(callback, "onDismissSucceeded")
                            DevLog.i("SYSTEM callback onDismissSucceeded sent")
                            param.result = null
                            DevLog.i("SYSTEM original dismiss skipped")
                        } catch (t: Throwable) {
                            DevLog.e("SYSTEM callback failed; allow original", t)
                        }
                    }
                })
                DevLog.i("SYSTEM hook installed on $target")
                installed++
            } catch (t: Throwable) {
                DevLog.e("SYSTEM hook attempt failed: $target", t)
            }
        }
        if (installed == 0) {
            DevLog.e("SYSTEM no dismissKeyguard hook target found")
        } else {
            DevLog.i("SYSTEM installed $installed dismissKeyguard hook(s)")
        }
    }

    private fun isWalletQuickDraw(token: IBinder, loader: ClassLoader): Boolean {
        val recordClass = runCatching {
            XposedHelpers.findClass("com.android.server.wm.ActivityRecord", loader)
        }.getOrNull()
        if (recordClass == null) {
            DevLog.e("SYSTEM ActivityRecord missing")
            return false
        }

        val record = runCatching {
            XposedHelpers.callStaticMethod(recordClass, "forTokenLocked", token)
        }.getOrNull() ?: runCatching {
            XposedHelpers.callStaticMethod(recordClass, "forToken", token)
        }.getOrNull()
        if (record == null) {
            DevLog.e("SYSTEM ActivityRecord token lookup failed")
            return false
        }

        val component = runCatching {
            XposedHelpers.getObjectField(record, "mActivityComponent") as? ComponentName
        }.getOrNull() ?: runCatching {
            XposedHelpers.getObjectField(record, "activityComponent") as? ComponentName
        }.getOrNull()
        val intent = runCatching {
            XposedHelpers.getObjectField(record, "intent") as? Intent
        }.getOrNull() ?: runCatching {
            XposedHelpers.getObjectField(record, "mIntent") as? Intent
        }.getOrNull()

        DevLog.i(
            "SYSTEM token record component=${component?.flattenToShortString()} " +
                "action=${intent?.action}"
        )
        return component?.packageName == MainHook.WALLET_PKG &&
            component.className == MainHook.WALLET_QUICKDRAW_ACTIVITY &&
            intent?.action == MainHook.WALLET_QUICKDRAW_ACTION
    }
}
