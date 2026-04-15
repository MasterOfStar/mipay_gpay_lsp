package com.mipay.gpay.lsp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机广播接收器：启动 NfcSocketService
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            NfcSocketService.log("BootReceiver: BOOT_COMPLETED, starting NfcSocketService")
            try {
                val serviceIntent = Intent(context, NfcSocketService::class.java)
                context.startService(serviceIntent)
                NfcSocketService.log("BootReceiver: startService called")
            } catch (e: Exception) {
                NfcSocketService.log("BootReceiver: startService failed: ${e.message}")
            }
        }
    }
}
