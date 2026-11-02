package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

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

        val intent = Intent(WalletQuickDraw.ACTION).apply {
            component = ComponentName(WalletQuickDraw.PACKAGE, WalletQuickDraw.ACTIVITY)
            flags = WalletQuickDraw.FLAGS
            putExtra(WalletQuickDraw.EXTRA_SESSION, true)
        }

        try {
            startActivity(intent)
            overridePendingTransition(0, 0)
            Log.i("MiPayGPay", "LAUNCH activity started Wallet QuickDraw")
        } catch (t: Throwable) {
            Log.e("MiPayGPay", "LAUNCH activity failed", t)
        }

        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 800L)
    }
}
