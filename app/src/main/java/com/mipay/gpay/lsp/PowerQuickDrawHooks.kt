package com.mipay.gpay.lsp

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object PowerQuickDrawHooks {
    private const val DOUBLE_PRESS_WINDOW_MS = 600L
    private const val MIPAY_DOUBLE_CLICK_ACTIVITY =
        "com.miui.tsmclient.ui.quick.DoubleClickActivity"
    private const val MIPAY_LEGACY_DOUBLE_CLICK_ACTIVITY =
        "com.miui.tsmclient.ui.card.DoubleClickActivity"

    private var lastPowerDownAt = 0L

    fun install(loader: ClassLoader) {
        hookPowerKeys(loader)
        hookMiPayDoubleClickLaunch(loader)
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
                            val event = param.args.firstOrNull { it is KeyEvent } as? KeyEvent
                                ?: return
                            if (event.keyCode != KeyEvent.KEYCODE_POWER) return
                            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return

                            val context = contextOf(param.thisObject) ?: return
                            if (!SettingsStore.getBoolean(context, SettingsStore.POWER_DOUBLE_CLICK)) {
                                return
                            }

                            val eventTime = event.eventTime
                            val previous = lastPowerDownAt
                            lastPowerDownAt = eventTime
                            if (previous == 0L || eventTime - previous > DOUBLE_PRESS_WINDOW_MS) {
                                return
                            }

                            lastPowerDownAt = 0L
                            DevLog.i("POWER double press detected")
                            MainHook.launchWalletQuickDrawFromSystem(context)
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

    private fun hookMiPayDoubleClickLaunch(loader: ClassLoader) {
        val serviceClassNames = listOf(
            "com.android.server.am.ActivityManagerService",
            "com.android.server.wm.ActivityTaskManagerService"
        )
        var installed = false

        for (className in serviceClassNames) {
            val serviceClass = runCatching {
                XposedHelpers.findClass(className, loader)
            }.getOrNull() ?: continue

            val hooks = runCatching {
                XposedBridge.hookAllMethods(
                    serviceClass,
                    "startActivityAsUser",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                            if (!isMiPayDoubleClickIntent(intent)) return

                            val context = contextOf(param.thisObject) ?: return
                            if (!SettingsStore.getBoolean(context, SettingsStore.POWER_DOUBLE_CLICK)) {
                                return
                            }

                            DevLog.i("MIPAY double click activity blocked")
                            param.result = -1
                        }
                    }
                )
            }.getOrDefault(emptySet())

            if (hooks.isNotEmpty()) {
                DevLog.i("MIPAY double click launch blocker installed on $className")
                installed = true
            }
        }

        if (!installed) {
            DevLog.e("MIPAY double click launch blocker target not found")
        }
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
        }.getOrNull()
    }
}
