package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    // ════════════════════════ 常量定义 ════════════════════════

    private const val TAG = "MiPayGPay"
    private const val MIPAY_PKG = "com.miui.tsmclient"
    private const val WALLET_PKG = "com.google.android.apps.walletnfcrel"
    private const val NFC_KEY = "nfc_payment_default_component"
    private const val INJECT_TAG = "mipay_gpay_btn"
    private const val LOG_FILE = "/sdcard/gpay_lsp.log"

    // Google Wallet HCE Service
    private const val WALLET_NFC_COMPONENT = "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService"

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

    // ════════════════════════ NFC 切换状态 ════════════════════════

    private var savedNfcComponent: String? = null
    private var activeCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var restoreTask: Runnable? = null

    // ════════════════════════ 初始化 ════════════════════════

    override fun onInit() = configs {
        debugLog { tag = TAG }
        loggerD(msg = "onInit")
    }

    override fun onHook() = encase {
        // Hook 小米智能卡 - 注入按钮
        loadApp(MIPAY_PKG) {
            injectMember {
                method {
                    name = "onResume"
                    emptyParam()
                }
                afterHook {
                    val activity = thisObject as? Activity ?: return@afterHook
                    injectButton(activity)
                }
            }
        }

        // Hook Google Wallet - NFC 自动切换
        loadApp(WALLET_PKG) {
            // Hook Activity 基类，监听前后台切换
            "android.app.Activity".hook {
                injectMember {
                    method {
                        name = "onStart"
                        emptyParam()
                    }
                    afterHook {
                        onForeground(thisObject as? Activity ?: return@afterHook)
                    }
                }

                injectMember {
                    method {
                        name = "onStop"
                        emptyParam()
                    }
                    afterHook {
                        onBackground(thisObject as? Activity ?: return@afterHook)
                    }
                }
            }
        }
    }

    // ════════════════════════ MiPay 按钮注入 ════════════════════════

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
        logD(msg = "Button injected")
    }

    // ════════════════════════ Wallet NFC 切换 (纯反射实现) ════════════════════════

    private fun onForeground(activity: Activity) {
        restoreTask?.let { handler.removeCallbacks(it) }
        restoreTask = null

        activeCount++
        if (activeCount == 1) {
            try {
                val current = getNfcDefault(activity)
                if (current != WALLET_NFC_COMPONENT) {
                    savedNfcComponent = current
                    setNfcDefaultViaReflection(activity, WALLET_NFC_COMPONENT)
                    showToast(activity, "已切换 NFC 支付为 Google Wallet")
                    logD(msg = "Switched NFC to Wallet: saved=$current")
                } else {
                    logD(msg = "NFC already set to Wallet, skip")
                }
            } catch (e: Throwable) {
                logD(msg = "onForeground failed: ${e.message}")
            }
        }
    }

    private fun onBackground(activity: Activity) {
        activeCount--
        if (activeCount <= 0) {
            activeCount = 0
            if (savedNfcComponent != null) {
                restoreTask = Runnable {
                    try {
                        savedNfcComponent?.let {
                            setNfcDefaultViaReflection(activity, it)
                            showToast(activity, "已恢复 NFC 支付设置")
                            logD(msg = "Restored NFC to: $it")
                        }
                    } catch (e: Throwable) {
                        logD(msg = "onBackground restore failed: ${e.message}")
                    }
                    savedNfcComponent = null
                    restoreTask = null
                }
                handler.postDelayed(restoreTask!!, 800)
            }
        }
    }

    // ════════════════════════ Settings.Secure 反射工具 ════════════════════════

    /**
     * 纯反射方式设置 NFC 默认支付组件
     * 优先尝试 Settings.Secure.putStringForUser
     * 如果失败，尝试 su 命令
     */
    private fun setNfcDefaultViaReflection(context: Context, component: String): Boolean {
        logD(msg = "setNfcDefaultViaReflection: $component")

        // 方法1: 尝试 ContentResolver.update + Secure.CONTENT_URI
        try {
            val secureClass = Class.forName("android.provider.Settings\$Secure")
            val contentUri = secureClass.getField("CONTENT_URI").get(null) as Uri
            
            val values = ContentValues().apply {
                put(NFC_KEY, component)
            }

            context.contentResolver.update(contentUri, values, "$NFC_KEY = ?", arrayOf(NFC_KEY))
            logD(msg = "ContentResolver.update success")
            return true
        } catch (e: Throwable) {
            logD(msg = "ContentResolver.update failed: ${e.message}")
        }

        // 方法2: 尝试 Settings.Secure.putString 静态方法
        try {
            val secureClass = Class.forName("android.provider.Settings\$Secure")
            val putStringMethod = secureClass.getMethod(
                "putString",
                Class.forName("android.content.ContentResolver"),
                String::class.java,
                String::class.java
            )
            putStringMethod.invoke(null, context.contentResolver, NFC_KEY, component)
            logD(msg = "Settings.Secure.putString success")
            return true
        } catch (e: Throwable) {
            logD(msg = "putString failed: ${e.message}")
        }

        // 方法3: 尝试 putStringForUser (需要 root 权限)
        try {
            val secureClass = Class.forName("android.provider.Settings\$Secure")
            val userIdMethod = UserHandle::class.java.getMethod("myUserId")
            val userId = userIdMethod.invoke(null) as Int
            
            val putStringForUserMethod = secureClass.getMethod(
                "putStringForUser",
                Class.forName("android.content.ContentResolver"),
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            putStringForUserMethod.invoke(null, context.contentResolver, NFC_KEY, component, userId)
            logD(msg = "putStringForUser success")
            return true
        } catch (e: Throwable) {
            logD(msg = "putStringForUser failed: ${e.message}")
        }

        // 方法4: su 命令 (最后备选)
        logD(msg = "Trying su command fallback")
        return tryExecSu("settings put secure $NFC_KEY $component")
    }

    /**
     * 获取当前 NFC 默认支付组件
     */
    private fun getNfcDefault(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, NFC_KEY)
        } catch (e: Throwable) {
            logD(msg = "getNfcDefault failed: ${e.message}")
            null
        }
    }

    /**
     * 通过 su 命令执行 shell 命令
     */
    private fun tryExecSu(cmd: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = p.waitFor()
            if (exitCode == 0) {
                logD(msg = "su command success: $cmd")
                true
            } else {
                val err = p.errorStream.bufferedReader().readText()
                logD(msg = "su command failed: exit=$exitCode, err=$err")
                false
            }
        } catch (e: Throwable) {
            logD(msg = "tryExecSu failed: ${e.message}")
            false
        }
    }

    // ════════════════════════ 工具方法 ════════════════════════

    private fun showToast(context: Context, msg: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun logD(msg: String) {
        loggerD(msg = msg)
        try {
            val logFile = File(LOG_FILE)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logFile.appendText("$timestamp [HookEntry] $msg\n")
        } catch (e: Throwable) {
            // ignore
        }
    }
}

// ════════════════════════ Google Pay 按钮 View ════════════════════════

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
                context.startActivity(Intent().apply {
                    component = ComponentName(
                        "com.google.android.apps.walletnfcrel",
                        "com.google.android.apps.wallet.main.WalletActivity"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            } catch (e: Throwable) {
                Toast.makeText(context, "未安装 Google Wallet", Toast.LENGTH_SHORT).show()
            }
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
