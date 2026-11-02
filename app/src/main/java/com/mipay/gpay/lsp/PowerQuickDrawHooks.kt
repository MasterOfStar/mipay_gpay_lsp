package com.mipay.gpay.lsp

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object PowerQuickDrawHooks {
    private const val DOUBLE_PRESS_WINDOW_MS = 600L
    private const val SETTINGS_CACHE_MS = 2_000L
    private const val MIPAY_DOUBLE_CLICK_ACTIVITY =
        "com.miui.tsmclient.ui.quick.DoubleClickActivity"
    private const val MIPAY_LEGACY_DOUBLE_CLICK_ACTIVITY =
        "com.miui.tsmclient.ui.card.DoubleClickActivity"

    private var firstDownAt = 0L
    private var pendingSecond = false
    private var secondDownAt = 0L
    private var lastLaunchedDownTime = 0L

    private var lastEventSignature = ""
    private var lastEventAt = 0L

    private var cachedPowerDoubleClick = false
    private var settingsCachedAt = 0L

    private val startCanceled: Int by lazy {
        runCatching {
            XposedHelpers.getStaticIntField(
                Class.forName("android.app.ActivityManager"),
                "START_CANCELED"
            )
        }.getOrDefault(-96)
    }

    fun install(loader: ClassLoader) {
        hookPowerKeys(loader)
        hookMiPayLaunch(loader)
    }

    private fun hookPowerKeys(loader: ClassLoader) {
        val targetClasses = listOf(
            "com.android.server.policy.PhoneWindowManager",
            "com.android.internal.policy.PhoneWindowManager",
            "com.android.server.wm.PhoneWindowManager"
        )
        var installed = false

        for (className in targetClasses) {
            val targetClass = runCatching {
                XposedHelpers.findClass(className, loader)
            }.getOrNull() ?: continue

            val hooks = runCatching {
                XposedBridge.hookAllMethods(
                    targetClass,
                    "interceptKeyBeforeQueueing",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            handlePowerEvent(param)
                        }
                    }
                )
            }.getOrDefault(emptySet())

            if (hooks.isNotEmpty()) {
                DevLog.i("POWER key hook installed on $className")
                installed = true
            }
        }

        if (!installed) {
            DevLog.e("POWER key hook target not found")
        }
    }

    private fun handlePowerEvent(param: XC_MethodHook.MethodHookParam) {
        val event = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
        if (event.keyCode != KeyEvent.KEYCODE_POWER) return

        val signature = "${event.action}:${event.downTime}:${event.repeatCount}"
        val now = SystemClock.uptimeMillis()
        if (signature == lastEventSignature && now - lastEventAt < 100L) {
            return
        }
        lastEventSignature = signature
        lastEventAt = now

        when (event.action) {
            KeyEvent.ACTION_DOWN -> handlePowerDown(param, event)
            KeyEvent.ACTION_UP -> handlePowerUp(param, event)
            else -> resetGesture()
        }
    }

    private fun handlePowerDown(param: XC_MethodHook.MethodHookParam, event: KeyEvent) {
        if (event.repeatCount > 0) {
            if (!pendingSecond) {
                firstDownAt = 0L
            }
            return
        }

        if (pendingSecond) {
            return
        }

        val previous = firstDownAt
        if (previous != 0L && event.downTime - previous in 1L..DOUBLE_PRESS_WINDOW_MS) {
            val context = contextOf(param.thisObject) ?: return
            if (!isPowerDoubleClickEnabled(context)) {
                DevLog.i("POWER double press ignored: setting disabled")
                firstDownAt = 0L
                return
            }

            firstDownAt = 0L
            pendingSecond = true
            secondDownAt = event.downTime
            DevLog.i(
                "POWER double press detected owner=${param.thisObject?.javaClass?.name} " +
                    "downTime=${event.downTime} repeat=${event.repeatCount}"
            )
            return
        }

        firstDownAt = event.downTime
    }

    private fun handlePowerUp(param: XC_MethodHook.MethodHookParam, event: KeyEvent) {
        if (!pendingSecond) return

        val duration = event.eventTime - secondDownAt
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        pendingSecond = false

        if (duration in 0L until longPressTimeout && lastLaunchedDownTime != event.downTime) {
            lastLaunchedDownTime = event.downTime
            val context = contextOf(param.thisObject) ?: return
            if (SystemServerQuickDrawHooks.isQuickDrawActive()) {
                DevLog.i("POWER QuickDraw already active; skip duplicate launch")
                return
            }
            DevLog.i("POWER launch Wallet QuickDraw duration=${duration}ms")
            MainHook.launchWalletQuickDrawFromSystem(context)
        } else {
            DevLog.i("POWER second press not launched duration=${duration}ms")
        }
    }

    private fun resetGesture() {
        firstDownAt = 0L
        pendingSecond = false
        secondDownAt = 0L
    }

    private fun isPowerDoubleClickEnabled(context: Context): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - settingsCachedAt > SETTINGS_CACHE_MS) {
            cachedPowerDoubleClick =
                SettingsStore.getBoolean(context, SettingsStore.POWER_DOUBLE_CLICK)
            settingsCachedAt = now
        }
        return cachedPowerDoubleClick
    }

    private fun hookMiPayLaunch(loader: ClassLoader) {
        val starterClass = runCatching {
            XposedHelpers.findClass("com.android.server.wm.ActivityStarter", loader)
        }.getOrNull()

        if (starterClass != null) {
            val requestHooks = runCatching {
                XposedBridge.hookAllMethods(
                    starterClass,
                    "executeRequest",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val request = param.args.getOrNull(0) ?: return
                            blockMiPayRequest(param, request)
                        }
                    }
                )
            }.getOrDefault(emptySet())

            if (requestHooks.isNotEmpty()) {
                DevLog.i("MIPAY blocker installed on ActivityStarter#executeRequest")
                return
            }

            val executeHooks = runCatching {
                XposedBridge.hookAllMethods(
                    starterClass,
                    "execute",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val request = runCatching {
                                XposedHelpers.getObjectField(param.thisObject, "mRequest")
                            }.getOrNull() ?: return
                            blockMiPayRequest(param, request)
                        }
                    }
                )
            }.getOrDefault(emptySet())

            if (executeHooks.isNotEmpty()) {
                DevLog.i("MIPAY blocker installed on ActivityStarter#execute")
                return
            }
        }

        val serviceClass = runCatching {
            XposedHelpers.findClass("com.android.server.wm.ActivityTaskManagerService", loader)
        }.getOrNull()
        if (serviceClass != null) {
            val hooks = runCatching {
                XposedBridge.hookAllMethods(
                    serviceClass,
                    "startActivityAsUser",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                            if (!isMiPayDoubleClickIntent(intent)) return
                            val context = contextOf(param.thisObject) ?: return
                            if (!isPowerDoubleClickEnabled(context)) return

                            DevLog.i("MIPAY double click blocked at startActivityAsUser")
                            param.result = startCanceled
                        }
                    }
                )
            }.getOrDefault(emptySet())

            if (hooks.isNotEmpty()) {
                DevLog.i("MIPAY blocker installed on ActivityTaskManagerService#startActivityAsUser")
                return
            }
        }

        DevLog.e("MIPAY blocker target not found")
    }

    private fun blockMiPayRequest(
        param: XC_MethodHook.MethodHookParam,
        request: Any
    ) {
        val intent = runCatching {
            XposedHelpers.getObjectField(request, "intent") as? Intent
        }.getOrNull()
        val activityInfo = runCatching {
            XposedHelpers.getObjectField(request, "activityInfo")
        }.getOrNull()

        val packageName = activityInfo?.let {
            runCatching {
                XposedHelpers.getObjectField(it, "packageName") as? String
            }.getOrNull()
        }
        val className = activityInfo?.let {
            runCatching {
                XposedHelpers.getObjectField(it, "name") as? String
            }.getOrNull()
        }

        val isMiPayDoubleClick = packageName == MainHook.MIPAY_PKG &&
            (className == MIPAY_DOUBLE_CLICK_ACTIVITY ||
                className == MIPAY_LEGACY_DOUBLE_CLICK_ACTIVITY)
        val hasMiPayIntent = intent?.let { isMiPayDoubleClickIntent(it) } == true
        if (!isMiPayDoubleClick && !hasMiPayIntent) return

        val context = contextOf(param.thisObject) ?: return
        if (!isPowerDoubleClickEnabled(context)) return

        val callingPackage = runCatching {
            XposedHelpers.getObjectField(request, "callingPackage") as? String
        }.getOrNull()
        val callingUid = runCatching {
            XposedHelpers.getIntField(request, "callingUid")
        }.getOrDefault(-1)
        DevLog.i(
            "MIPAY double click blocked at ActivityStarter pkg=$packageName " +
                "class=$className callingPackage=$callingPackage callingUid=$callingUid " +
                "result=$startCanceled"
        )
        param.result = startCanceled
    }

    private fun isMiPayDoubleClickIntent(intent: Intent): Boolean {
        val component = intent.component ?: return false
        if (component.packageName != MainHook.MIPAY_PKG) return false
        return component.className == MIPAY_DOUBLE_CLICK_ACTIVITY ||
            component.className == MIPAY_LEGACY_DOUBLE_CLICK_ACTIVITY
    }

    private fun contextOf(owner: Any?): Context? {
        return runCatching {
            XposedHelpers.getObjectField(owner, "mContext") as? Context
        }.getOrNull() ?: runCatching {
            val service = XposedHelpers.getObjectField(owner, "mService")
            XposedHelpers.getObjectField(service, "mContext") as? Context
        }.getOrNull()
    }
}
