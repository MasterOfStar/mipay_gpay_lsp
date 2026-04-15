package com.mipay.gpay.lsp

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.UserHandle

/**
 * NFC 切换广播接收器
 * 运行在 MiPay 进程，利用系统应用权限执行 NFC 切换
 */
class NfcSwitchReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NfcSwitchReceiver"
        private const val KEY_NFC_PAYMENT = "nfc_payment_default_component"
        
        // 默认支付方式组件名
        private const val WALLET_COMPONENT = "com.google.android.apps.walletnfcrel/com.google.android.apps.wallet.tapandpay.quickaccesswallet.WalletQuickAccessWalletService"
        private const val MIPAY_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.service.NfcService"
        
        // 保存/恢复文件
        private const val SAVED_NFC_FILE = "saved_nfc_component.txt"
    }

    override fun onReceive(context: Context, intent: Intent) {
        MainHook.log("NfcSwitchReceiver.onReceive: ${intent.action}")
        
        when (intent.action) {
            MainHook.ACTION_WALLET_FOREGROUND -> switchToWallet(context)
            MainHook.ACTION_WALLET_BACKGROUND -> restoreNfc(context)
        }
    }

    private fun switchToWallet(context: Context) {
        MainHook.log("switchToWallet: saving current NFC and switching to Wallet")
        
        try {
            // 保存当前 NFC 支付方式
            val current = getCurrentNfcComponent(context)
            if (current != null && current != WALLET_COMPONENT) {
                saveNfcComponent(context, current)
                MainHook.log("Saved current NFC: $current")
            }
            
            // 切换到 Wallet
            setNfcComponent(context, WALLET_COMPONENT)
            MainHook.log("Switched to Wallet NFC")
        } catch (e: Exception) {
            MainHook.log("switchToWallet failed: ${e.message}")
        }
    }

    private fun restoreNfc(context: Context) {
        MainHook.log("restoreNfc: restoring saved NFC")
        
        try {
            val saved = loadNfcComponent(context)
            val target = saved ?: MIPAY_COMPONENT
            
            setNfcComponent(context, target)
            MainHook.log("Restored NFC to: $target")
        } catch (e: Exception) {
            MainHook.log("restoreNfc failed: ${e.message}")
        }
    }

    /**
     * 反射调用 Settings.Secure.putStringForUser 设置 NFC 支付方式
     * MiPay 作为系统应用有 WRITE_SECURE_SETTINGS 权限
     */
    private fun setNfcComponent(context: Context, component: String) {
        try {
            val cr = context.contentResolver
            val cls = Class.forName("android.provider.Settings\$Secure")
            
            // 方法签名: putStringForUser(ContentResolver, String, String, int)
            val method = cls.getDeclaredMethod(
                "putStringForUser",
                ContentResolver::class.java,
                String::class.java,
                String::class.java,
                Int::class.java
            )
            
            // -2 = USER_CURRENT (UserHandle.USER_CURRENT)
            val result = method.invoke(null, cr, KEY_NFC_PAYMENT, component, -2)
            MainHook.log("setNfcComponent: result=$result, component=$component")
        } catch (e: Exception) {
            MainHook.log("setNfcComponent failed: ${e.message}")
            throw e
        }
    }

    /**
     * 获取当前 NFC 支付方式
     */
    private fun getCurrentNfcComponent(context: Context): String? {
        return try {
            val cr = context.contentResolver
            val cls = Class.forName("android.provider.Settings\$Secure")
            
            val method = cls.getDeclaredMethod(
                "getStringForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.java
            )
            
            method.invoke(null, cr, KEY_NFC_PAYMENT, -2) as? String
        } catch (e: Exception) {
            MainHook.log("getCurrentNfcComponent failed: ${e.message}")
            null
        }
    }

    /**
     * 保存 NFC 组件到私有文件
     */
    private fun saveNfcComponent(context: Context, component: String) {
        try {
            context.openFileOutput(SAVED_NFC_FILE, Context.MODE_PRIVATE).use {
                it.write(component.toByteArray())
            }
        } catch (e: Exception) {
            MainHook.log("saveNfcComponent failed: ${e.message}")
        }
    }

    /**
     * 从私有文件读取保存的 NFC 组件
     */
    private fun loadNfcComponent(context: Context): String? {
        return try {
            context.openFileInput(SAVED_NFC_FILE).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null // 文件不存在返回 null
        }
    }
}
