package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * A transparent, showWhenLocked foreground activity used as the launch anchor for Wallet's
 * QUICKDRAW screen. HyperOS can reject a background/root `am start` while the device is locked;
 * launching from this activity also lets Wallet inherit showWhenLocked.
 */
class QuickDrawLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setInheritShowWhenLocked(true)

        val intent = Intent(MainHook.WALLET_QUICKDRAW_ACTION).apply {
            component = ComponentName(MainHook.WALLET_PKG, MainHook.WALLET_QUICKDRAW_ACTIVITY)
            flags = MainHook.WALLET_QUICKDRAW_FLAGS
            putExtra(MainHook.EXTRA_QUICKDRAW_SESSION, true)
        }

        try {
            startActivity(intent)
            overridePendingTransition(0, 0)
            MainHook.log("LAUNCH activity started Wallet QuickDraw")
        } catch (t: Throwable) {
            MainHook.log("LAUNCH activity failed: ${t.message}")
        }

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 800L)
    }
}
