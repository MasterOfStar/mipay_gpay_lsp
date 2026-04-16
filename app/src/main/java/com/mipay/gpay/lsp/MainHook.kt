package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainHook : IXposedHookLoadPackage {

    companion object {
        const val MIPAY_PKG = "com.miui.tsmclient"
        const val WALLET_PKG = "com.google.android.apps.walletnfcrel"
        private const val TAG = "MiPayGPay"
        private const val INJECT_TAG = "mipay_gpay_btn"
        private const val LOG_FILE = "/sdcard/Documents/gpay_lsp.log"
        
        // NFC 设置 Key
        private const val KEY_NFC_PAYMENT = "nfc_payment_default_component"
        // Google Wallet 组件
        const val WALLET_COMPONENT = "com.google.android.apps.walletnfcrel/com.google.android.apps.wallet.tapandpay.quickaccesswallet.WalletQuickAccessWalletService"
        // MiPay 组件（默认）
        private const val MIPAY_COMPONENT = "com.miui.tsmclient/com.miui.tsmclient.service.NfcService"

        private const val GOOGLE_PAY_SVG = """
<svg xmlns="http://www.w3.org/2000/svg" width="80" height="38.1" viewBox="0 0 80 38.1">
  <path fill="#5F6368" d="M37.8,19.7V29h-3V6h7.8c1.9,0,3.7,0.7,5.1,2c1.4,1.2,2.1,3,2.1,4.9c0,1.9-0.7,3.6-2.1,4.9c-1.4,1.3-3.1,2-5.1,2L37.8,19.7L37.8,19.7z M37.8,8.8v8h5c1.1,0,2.2-0.4,2.9-1.2c1.6-1.5,1.6-4,0.1-5.5c0,0-0.1-0.1-0.1-0.1c-0.8-0.8-1.8-1.3-2.9-1.2L37.8,8.8L37.8,8.8z"/>
  <path fill="#5F6368" d="M56.7,12.8c2.2,0,3.9,0.6,5.2,1.8s1.9,2.8,1.9,4.8V29H61v-2.2h-0.1c-1.2,1.8-2.9,2.7-4.9,2.7c-1.7,0-3.2-0.5-4.4-1.5c-1.1-1-1.8-2.4-1.8-3.9c0-1.6,0.6-2.9,1.8-3.9c1.2-1,2.9-1.4,4.9-1.4c1.8,0,3.2,0.3,4.3,1v-0.7c0-1-0.4-2-1.2-2.6c-0.8-0.7-1.8-1.1-2.9-1.1c-1.7,0-3,0.7-3.9,2.1l-2.6-1.6C51.8,13.8,53.9,12.8,56.7,12.8z M52.9,24.2c0,0.8,0.4,1.5,1,1.9c0.7,0.5,1.5,0.8,2.3,0.8c1.2,0,2.4-0.5,3.3-1.4c1-0.9,1.5-2,1.5-3.2c-0.9-0.7-2.2-1.1-3.9-1.1c-1.2,0-2.2,0.3-3,0.9C53.3,22.6,52.9,23.3,52.9,24.2z"/>
  <path fill="#5F6368" d="M80,13.3l-9.9,22.7h-3l3.7-7.9l-6.5-14.7h3.2l4.7,11.3h0.1l4.6-11.3H80z"/>
  <path fill="#4285F4" d="M25.9,17.7c0-0.9-0.1-1.8-0.2-2.7H13.2v5.1h7.1c-0.3,1.6-1.2,3.1-2.6,4v3.3H22C24.5,25.1,25.9,21.7,25.9,17.7z"/>
  <path fill="#34A853" d="M13.2,30.6c3.6,0,6.6-1.2,8.8-3.2l-4.3-3.3c-1.2,0.8-2.7,1.3-4.5,1.3c-3.4,0-6.4-2.3-7.4-5.5H1.4v3.4C3.7,27.8,8.2,30.6,13.2,30.6z"/>
  <path fill="#FBBC04" d="M5.8,19.9c-0.6-1.6-0.6-3.4,0-5.1v-3.4H1.4c-1.9,3.7-1.9,8.1,0,11.9L5.8,19.9z"/>
  <path fill="#EA4335" d="M13.2,9.4c1.9,0,3.7,0.7,5.1,2l0,0l3.8-3.8c-2.4-2.2-5.6-3.5-8.8-3.4c-5,0-9.6,2.8-11.8,7.3l4.4,3.4C6.8,11.7,9.8,9.4,13.2,9.4z"/>
</svg>"""

        fun log(msg: String) {
            try {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                File(LOG_FILE).appendText("$ts [MainHook] $msg\n")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: log failed: ${e.message}")
            }
        }

        // 保存原始 NFC 组件（静态变量，进程内共享）
        @JvmStatic
        var savedNfcComponent: String? = null
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("handleLoadPackage: ${lpparam.packageName}")
        // 极简方案：只 hook MiPay，Wallet 完全不需要
        if (lpparam.packageName == MIPAY_PKG) {
            setupMiPayHooks(lpparam)
        }
    }

    // ════════════════════════ MiPay: 注入 GPay 按钮 + NFC 切换（极简方案）════════════════════════

    private fun setupMiPayHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook DoubleClickActivity 的 onResume 和 onPause
            val targetClass = XposedHelpers.findClass(
                "com.miui.tsmclient.ui.quick.DoubleClickActivity", lpparam.classLoader
            )
            
            // onResume: MiPay 显示时，确保 NFC = MiPay
            XposedHelpers.findAndHookMethod(targetClass, "onResume", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    log("DoubleClickActivity.onResume")
                    // 如果之前切换到 Wallet 时保存了原始值，现在恢复
                    if (savedNfcComponent != null) {
                        log("恢复原始 NFC: $savedNfcComponent")
                        setNfcComponent(activity, savedNfcComponent!!)
                        savedNfcComponent = null
                    } else {
                        // 首次打开 MiPay，设置 NFC = MiPay
                        log("设置 NFC = MiPay")
                        setNfcComponent(activity, MIPAY_COMPONENT)
                    }
                    injectButton(activity)
                }
            })
            
            // onPause: MiPay 离开时，注入的按钮点击事件会切换 NFC 并启动 Wallet
            // 所以这里不需要额外处理 NFC
            XposedHelpers.findAndHookMethod(targetClass, "onPause", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    log("DoubleClickActivity.onPause")
                }
            })
            
        } catch (e: Throwable) {
            log("MiPay hook failed: ${e.message}")
            XposedBridge.log("$TAG: MiPay hook failed: ${e.message}")
        }
    }

    /**
     * 设置 NFC 默认支付方式
     * MiPay 有系统权限，直接反射调用 Settings.Secure.putStringForUser
     */
    private fun setNfcComponent(context: Context, component: String) {
        setNfcComponentStatic(context, component)
    }

    // 静态方法，供 GooglePayButtonView 调用
    fun setNfcComponentStatic(context: Context, component: String) {
        try {
            val cr = context.contentResolver
            val cls = Class.forName("android.provider.Settings\$Secure")
            val method = cls.getDeclaredMethod(
                "putStringForUser",
                ContentResolver::class.java,
                String::class.java,
                String::class.java,
                Int::class.java
            )
            val result = method.invoke(null, cr, KEY_NFC_PAYMENT, component, -2) as Boolean
            log("setNfcComponent: $component, result=$result")
        } catch (e: Exception) {
            log("setNfcComponent failed: ${e.message}")
        }
    }

    private fun injectButton(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(INJECT_TAG) != null) return

        val btn = GooglePayButtonView(decor.context).apply {
            tag = INJECT_TAG
            setSvgString(GOOGLE_PAY_SVG)
        }

        val density = decor.context.resources.displayMetrics.density
        val w = (104 * density).toInt()
        val h = (58 * density).toInt()

        btn.post {
            val pw = decor.width
            val ph = decor.height
            if (pw > 0 && ph > 0) {
                btn.layoutParams = FrameLayout.LayoutParams(w, h).apply {
                    leftMargin = pw - w - (10 * density).toInt()
                    topMargin = ph - h - (100 * density).toInt()
                }
            }
        }

        decor.post { decor.addView(btn) }
    }


}

// ════════════════════════ Google Pay 按钮视图 ════════════════════════

class GooglePayButtonView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var svg: SVG? = null
    private var bmp: Bitmap? = null
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    private val bgColor: Int by lazy {
        val isDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        when {
            isDark -> android.graphics.Color.parseColor("#121212")
            Build.VERSION.SDK_INT >= 31 -> context.theme.resources.getColor(
                android.R.color.system_accent1_100, context.theme
            )
            else -> android.graphics.Color.rgb(33, 150, 243)
        }
    }

    init {
        isClickable = true
        setOnClickListener {
            try {
                // 保存当前 NFC 组件（点击按钮时，NFC 还是 MiPay）
                val currentNfc = getCurrentNfcComponent(context)
                if (currentNfc != null && currentNfc != MainHook.WALLET_COMPONENT) {
                    MainHook.savedNfcComponent = currentNfc
                    MainHook.log("按钮点击: 保存当前 NFC=$currentNfc")
                }
                
                // 切换 NFC 到 Wallet
                MainHook.log("按钮点击: 设置 NFC = WALLET")
                MainHook.setNfcComponentStatic(context, "com.google.android.apps.walletnfcrel/com.google.android.apps.wallet.tapandpay.quickaccesswallet.WalletQuickAccessWalletService")
                
                // 启动 Google Wallet
                context.startActivity(Intent().apply {
                    component = ComponentName(MainHook.WALLET_PKG,
                        "com.google.android.apps.wallet.main.WalletActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            } catch (e: Throwable) {
                Toast.makeText(context, "未安装 Google Wallet", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
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
            method.invoke(null, cr, "nfc_payment_default_component", -2) as? String
        } catch (e: Exception) {
            MainHook.log("getCurrentNfcComponent failed: ${e.message}")
            null
        }
    }

    fun setSvgString(s: String) {
        try {
            svg = SVG.getFromString(s)
            prepareBmp()
            invalidate()
        } catch (e: SVGParseException) {
            android.util.Log.e("GPB", "SVG error: ${e.message}")
        }
    }

    private fun prepareBmp() {
        val svgObj = svg ?: return
        val pic = svgObj.renderToPicture()
        bmp = Bitmap.createBitmap(160, 76, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                save()
                scale(160f / pic.width, 76f / pic.height)
                drawPicture(pic)
                restore()
            }
        }
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension((104 * d).toInt(), (58 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        bgPaint.color = bgColor
        c.drawRoundRect(rect, h / 2, h / 2, bgPaint)

        bmp?.let {
            val th = h * 0.6f
            val tw = it.width.toFloat() / it.height * th
            c.drawBitmap(it, null, RectF((w - tw) / 2, (h - th) / 2, (w + tw) / 2, (h + th) / 2), svgPaint)
        }
    }
}
