package com.mipay.gpay.lsp

import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * Wallet's QUICKDRAW screen asks the system to dismiss the keyguard before the screen has
 * actually become visible. HyperOS fails that request, Wallet receives onDismissError and the
 * preview closes. Never answer the request ourselves: hold it until the QuickDraw activity is
 * visible, then invoke the original request so the real fingerprint/PIN prompt is shown over the
 * lock screen. Only a real dismissal may report onDismissSucceeded; otherwise the device stays
 * locked and tap-to-pay cannot work.
 */
object SystemServerQuickDrawHooks {
    private const val GMS_PKG = "com.google.android.gms"
    private const val RETRY_INTERVAL_MS = 50L
    private const val MAX_WAIT_MS = 5_000L
    private const val SESSION_WINDOW_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var sessionTaskId = -1

    @Volatile
    private var sessionUntil = 0L

    fun install(loader: ClassLoader) {
        DevLog.i("SYSTEM install QuickDraw hook")
        installDismissHook(loader)
        installShowWhenLockedHook(loader)
    }

    private fun installDismissHook(loader: ClassLoader) {
        val targets = listOf(
            "com.android.server.wm.ActivityClientController",
            "com.android.server.wm.KeyguardController"
        )
        var installed = 0

        for (className in targets) {
            val targetClass = runCatching {
                XposedHelpers.findClass(className, loader)
            }.getOrNull() ?: continue

            val hooks = runCatching {
                XposedBridge.hookAllMethods(
                    targetClass,
                    "dismissKeyguard",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            handleDismissKeyguard(param, loader)
                        }
                    }
                )
            }.getOrDefault(emptySet())

            if (hooks.isNotEmpty()) {
                DevLog.i("SYSTEM dismissKeyguard hook installed on $className")
                installed++
            }
        }

        if (installed == 0) {
            DevLog.e("SYSTEM no dismissKeyguard hook target found")
        }
    }

    private fun handleDismissKeyguard(param: XC_MethodHook.MethodHookParam, loader: ClassLoader) {
        val token = param.args.getOrNull(0) as? IBinder ?: return
        val callback = param.args.getOrNull(1)
        val record = findRecord(token, loader) ?: run {
            DevLog.i("SYSTEM dismiss token record not found token=$token")
            return
        }

        if (!isWalletSession(record, loader)) {
            return
        }

        val visibleIgnoringKeyguard = readBoolean(record, "visibleIgnoringKeyguard")
        val nowVisible = readBoolean(record, "nowVisible")
        val visible = visibleIgnoringKeyguard || nowVisible
        DevLog.i(
            "SYSTEM Wallet dismiss token=$token pkg=${packageOf(record)} " +
                "action=${intentOf(record)?.action} visibleIgnoringKeyguard=$visibleIgnoringKeyguard " +
                "nowVisible=$nowVisible callback=${callback != null}"
        )

        if (visible) {
            DevLog.i("SYSTEM Wallet QuickDraw visible; allow real keyguard authentication")
            return
        }

        DevLog.i("SYSTEM Wallet QuickDraw not visible yet; defer real dismiss")
        skipOriginal(param)
        deferUntilVisible(param, token, loader, SystemClock.uptimeMillis())
    }

    private fun skipOriginal(param: XC_MethodHook.MethodHookParam) {
        param.result = if ((param.method as? Method)?.returnType == Void.TYPE) null else false
    }

    private fun deferUntilVisible(
        param: XC_MethodHook.MethodHookParam,
        token: IBinder,
        loader: ClassLoader,
        startedAt: Long
    ) {
        val method = param.method
        val thisObject = param.thisObject
        val args = arrayOfNulls<Any?>(param.args.size)
        for (index in param.args.indices) {
            args[index] = param.args[index]
        }
        val globalLock = globalLockOf(thisObject)

        handler.postDelayed({
            val record = if (globalLock != null) {
                synchronized(globalLock) { findRecord(token, loader) }
            } else {
                findRecord(token, loader)
            }
            val visibleIgnoringKeyguard = record != null && readBoolean(record, "visibleIgnoringKeyguard")
            val nowVisible = record != null && readBoolean(record, "nowVisible")
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val ready = visibleIgnoringKeyguard && (nowVisible || elapsed >= 1_000L)

            when {
                record == null -> {
                    DevLog.i("SYSTEM deferred Wallet record disappeared; invoke original")
                    invokeOriginal(method, thisObject, args)
                }
                ready -> {
                    DevLog.i(
                        "SYSTEM deferred Wallet ready after ${elapsed}ms " +
                            "visibleIgnoringKeyguard=$visibleIgnoringKeyguard nowVisible=$nowVisible"
                    )
                    invokeOriginal(method, thisObject, args)
                }
                elapsed >= MAX_WAIT_MS -> {
                    DevLog.e("SYSTEM deferred Wallet timeout after ${elapsed}ms; invoke original")
                    invokeOriginal(method, thisObject, args)
                }
                else -> deferUntilVisible(param, token, loader, startedAt)
            }
        }, RETRY_INTERVAL_MS)
    }

    private fun invokeOriginal(method: Member, thisObject: Any?, args: Array<Any?>) {
        val globalLock = globalLockOf(thisObject)
        val invoke = {
            runCatching {
                XposedBridge.invokeOriginalMethod(method, thisObject, args)
            }.onFailure {
                DevLog.e("SYSTEM invoke original dismissKeyguard failed", it)
            }
        }
        if (globalLock != null) {
            synchronized(globalLock) { invoke() }
        } else {
            invoke()
        }
    }

    private fun globalLockOf(owner: Any?): Any? {
        return runCatching {
            val service = XposedHelpers.getObjectField(owner, "mService")
            XposedHelpers.getObjectField(service, "mGlobalLock")
        }.getOrNull()
    }

    private fun installShowWhenLockedHook(loader: ClassLoader) {
        val recordClass = runCatching {
            XposedHelpers.findClass("com.android.server.wm.ActivityRecord", loader)
        }.getOrElse {
            DevLog.e("SYSTEM ActivityRecord missing", it)
            return
        }

        val hooks = runCatching {
            XposedBridge.hookAllMethods(
                recordClass,
                "canShowWhenLocked",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val record = param.thisObject ?: return
                        if (!isQuickDrawRecord(record, loader)) return
                        markSession(record)
                        param.result = true
                    }
                }
            )
        }.getOrDefault(emptySet())

        if (hooks.isEmpty()) {
            DevLog.e("SYSTEM ActivityRecord#canShowWhenLocked hook not found")
        } else {
            DevLog.i("SYSTEM ActivityRecord#canShowWhenLocked hook installed")
        }
    }

    private fun findRecord(token: IBinder, loader: ClassLoader): Any? {
        val recordClass = runCatching {
            XposedHelpers.findClass("com.android.server.wm.ActivityRecord", loader)
        }.getOrNull() ?: return null

        return runCatching {
            XposedHelpers.callStaticMethod(recordClass, "forTokenLocked", token)
        }.getOrNull() ?: runCatching {
            XposedHelpers.callStaticMethod(recordClass, "forToken", token)
        }.getOrNull()
    }

    private fun isWalletSession(record: Any, loader: ClassLoader): Boolean {
        if (isQuickDrawRecord(record, loader)) {
            markSession(record)
            return true
        }

        val packageName = packageOf(record) ?: return false
        if (packageName != MainHook.WALLET_PKG && packageName != GMS_PKG) return false
        if (SystemClock.uptimeMillis() > sessionUntil) return false

        val taskId = taskIdOf(record)
        if (sessionTaskId != -1 && taskId != -1 && taskId != sessionTaskId) return false
        return true
    }

    private fun isQuickDrawRecord(record: Any, loader: ClassLoader): Boolean {
        if (packageOf(record) != MainHook.WALLET_PKG) return false
        val intent = intentOf(record)
        if (intent?.getBooleanExtra(MainHook.EXTRA_QUICKDRAW_SESSION, false) == true) return true
        if (intent?.action != MainHook.WALLET_QUICKDRAW_ACTION) return false

        val component = componentOf(record)
        return component?.className == MainHook.WALLET_QUICKDRAW_ACTIVITY
    }

    private fun markSession(record: Any) {
        sessionUntil = SystemClock.uptimeMillis() + SESSION_WINDOW_MS
        val taskId = taskIdOf(record)
        if (taskId != -1) sessionTaskId = taskId
        DevLog.i(
            "SYSTEM QuickDraw session marked pkg=${packageOf(record)} " +
                "task=$taskId action=${intentOf(record)?.action}"
        )
    }

    private fun packageOf(record: Any): String? {
        return runCatching {
            XposedHelpers.getObjectField(record, "packageName") as? String
        }.getOrNull() ?: runCatching {
            XposedHelpers.getObjectField(record, "mPackageName") as? String
        }.getOrNull() ?: componentOf(record)?.packageName
    }

    private fun intentOf(record: Any): Intent? {
        return runCatching {
            XposedHelpers.getObjectField(record, "intent") as? Intent
        }.getOrNull() ?: runCatching {
            XposedHelpers.getObjectField(record, "mIntent") as? Intent
        }.getOrNull()
    }

    private fun componentOf(record: Any): ComponentName? {
        return runCatching {
            XposedHelpers.getObjectField(record, "mActivityComponent") as? ComponentName
        }.getOrNull() ?: runCatching {
            XposedHelpers.getObjectField(record, "activityComponent") as? ComponentName
        }.getOrNull() ?: intentOf(record)?.component
    }

    private fun taskIdOf(record: Any): Int {
        return runCatching {
            val task = XposedHelpers.callMethod(record, "getTask")
            XposedHelpers.getIntField(task, "mTaskId")
        }.getOrDefault(-1)
    }

    private fun readBoolean(record: Any, field: String): Boolean {
        return runCatching {
            XposedHelpers.getBooleanField(record, field)
        }.getOrDefault(false)
    }
}
