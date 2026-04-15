package com.mipay.gpay.lsp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 前台服务：感知前台 App 切换，调用 AppHook 回调
 * 使用 UsageStatsManager 监听哪个 App 在前台
 */
class NfcAppHookService : Service() {

    companion object {
        private const val TAG = "NfcAppHook"
        private const val CHANNEL_ID = "nfc_hook_channel"
        private const val NOTIFICATION_ID = 1

        private const val WALLET_PKG = "com.google.android.apps.walletnfcrel"
        private const val MIPAY_PKG = "com.miui.tsmclient"
        private const val NFC_KEY = "nfc_payment_default_component"
        private const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"
        private const val MIPAY_NFC_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.hce.service.TsmClientHceService"
        private const val SAVED_NFC_FILE = "/data/local/tmp/gpay_saved_nfc.txt"

        private var serviceInstance: NfcAppHookService? = null
        private var isNfcSwitchEnabled = true
        private var lastForegroundPkg: String? = null

        fun start(context: Context) {
            val intent = Intent(context, NfcAppHookService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop() {
            serviceInstance?.let {
                it.stopSelf()
                serviceInstance = null
            }
        }

        fun setEnabled(enabled: Boolean) {
            isNfcSwitchEnabled = enabled
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var monitorJob: Job? = null

    private lateinit var usageStatsManager: UsageStatsManager
    private var lastCheckTime = 0L

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        startMonitoring()
        log("NfcAppHookService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        serviceInstance = null
        log("NfcAppHookService destroyed")
        super.onDestroy()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                checkForegroundApp()
                delay(500)
            }
        }
    }

    private fun checkForegroundApp() {
        if (!isNfcSwitchEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastCheckTime < 500) return
        lastCheckTime = now

        try {
            val usageEvents = usageStatsManager.queryEvents(now - 10000, now)
            var foregroundPkg: String? = null
            var mostRecentTime = 0L

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.RESUME_ACTIVITY
                ) {
                    if (event.timeStamp > mostRecentTime) {
                        mostRecentTime = event.timeStamp
                        foregroundPkg = event.packageName
                    }
                }
            }

            if (foregroundPkg != null && foregroundPkg != lastForegroundPkg) {
                val prev = lastForegroundPkg
                lastForegroundPkg = foregroundPkg
                log("Foreground changed: $prev -> $foregroundPkg")
                onForegroundChanged(prev, foregroundPkg)
            }
        } catch (e: Exception) {
            log("checkForegroundApp error: ${e.message}")
        }
    }

    private fun onForegroundChanged(prev: String?, curr: String?) {
        if (curr == WALLET_PKG) {
            // 进入 Wallet
            scope.launch(Dispatchers.IO) {
                switchToWallet()
            }
        } else if (prev == WALLET_PKG && curr != null) {
            // 离开 Wallet
            scope.launch(Dispatchers.IO) {
                restoreNfc()
            }
        }
    }

    private fun switchToWallet() {
        try {
            // 保存当前 NFC 设置
            val current = getCurrentNfc()
            log("switchToWallet: current=$current")
            if (current != null && current != WALLET_NFC_COMPONENT) {
                saveNfc(current)
            }

            // 切换 NFC
            val result = putStringForUser(NFC_KEY, WALLET_NFC_COMPONENT)
            log("switchToWallet: putStringForUser=$result")

            if (result) {
                val verify = getCurrentNfc()
                if (verify == WALLET_NFC_COMPONENT) {
                    log("switchToWallet: SUCCESS")
                    showToast("已切换 NFC 到 Google Wallet")
                } else {
                    log("switchToWallet: verify failed, actual=$verify")
                }
            }
        } catch (e: Exception) {
            log("switchToWallet error: ${e.message}")
        }
    }

    private fun restoreNfc() {
        try {
            val saved = loadSavedNfc()
            log("restoreNfc: saved=$saved")
            if (saved != null) {
                val result = putStringForUser(NFC_KEY, saved)
                if (result) {
                    val verify = getCurrentNfc()
                    if (verify == saved) {
                        log("restoreNfc: SUCCESS, restored=$saved")
                        showToast("已还原 NFC 到小米 Pay")
                        deleteSavedNfc()
                    } else {
                        log("restoreNfc: verify failed, actual=$verify")
                    }
                }
            } else {
                // 无保存记录，切回 MiPay
                val result = putStringForUser(NFC_KEY, MIPAY_NFC_COMPONENT)
                log("restoreNfc: fallback to MiPay, result=$result")
            }
        } catch (e: Exception) {
            log("restoreNfc error: ${e.message}")
        }
    }

    // 直接用 ContentResolver，不需要 root
    private fun putStringForUser(key: String, value: String): Boolean {
        return try {
            val userId = UserHandle::class.java.getDeclaredMethod("myUserId").invoke(null) as Int
            val method = Settings.Secure::class.java.getDeclaredMethod(
                "putStringForUser",
                android.content.ContentResolver::class.java,
                String::class.java,
                String::class.java,
                Int::class.java
            )
            method.invoke(null, contentResolver, key, value, userId) as Boolean
        } catch (e: Exception) {
            log("putStringForUser failed: ${e.message}")
            false
        }
    }

    private fun getCurrentNfc(): String? {
        return try {
            Settings.Secure.getString(contentResolver, NFC_KEY)
        } catch (e: Exception) {
            log("getCurrentNfc: ${e.message}")
            null
        }
    }

    private fun saveNfc(value: String) {
        try {
            File(SAVED_NFC_FILE).writeText(value)
        } catch (e: Exception) {
            log("saveNfc failed: ${e.message}")
        }
    }

    private fun loadSavedNfc(): String? {
        return try {
            val f = File(SAVED_NFC_FILE)
            if (f.exists()) f.readText().trim().takeIf { it.isNotEmpty() } else null
        } catch (e: Exception) {
            log("loadSavedNfc failed: ${e.message}")
            null
        }
    }

    private fun deleteSavedNfc() {
        try {
            File(SAVED_NFC_FILE).delete()
        } catch (e: Exception) {}
    }

    private fun showToast(msg: String) {
        try {
            handler.post {
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {}
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        try {
            File("/sdcard/Documents/gpay_lsp_yukihook.log").appendText(
                "${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())} $msg\n"
            )
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NFC 切换服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 NFC 自动切换服务在后台运行"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MiPay GPay NFC")
            .setContentText("NFC 自动切换运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
