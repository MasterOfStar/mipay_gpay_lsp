package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * LSPosed 模块入口
 *
 * 目标包：
 *  - com.miui.tsmclient：注入 Google Pay 快捷按钮
 *  - com.google.android.apps.walletnfcrel：前台时自动将 NFC 默认应用切为 Google Wallet，后台还原
 *
 * 注入时机：Activity.onResume（MiPay）/ onStart（Wallet）
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        const val MIPAY_PKG   = "com.miui.tsmclient"
        const val WALLET_PKG  = "com.google.android.apps.walletnfcrel"
        private const val TAG          = "MiPayGPay"
        private const val INJECT_TAG   = "mipay_gpay_btn"

        private const val NFC_SETTING_KEY = "nfc_payment_default_component"

        // ── NFC 状态（模块级别单例） ────────────────────────
        @Volatile private var walletActiveCount = 0
        @Volatile private var savedNfcDefault: String? = null
        private var walletNfcComponent: String? = null
        private var nfcComponentDiscovered = false
        private val mainHandler = Handler(Looper.getMainLooper())
        private var restoreRunnable: Runnable? = null

        // Google Pay 官方 SVG Logo
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
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: === Module loaded: ${lpparam.packageName} ===")

        when (lpparam.packageName) {
            MIPAY_PKG  -> setupMiPayHooks(lpparam)
            WALLET_PKG -> setupWalletNfcHooks(lpparam)
        }
    }

    // ════════════════════════ MiPay 注入 ════════════════════════

    private fun setupMiPayHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: ★ MiPay target matched!")

        try {
            val targetClass = XposedHelpers.findClass(
                "com.miui.tsmclient.ui.quick.DoubleClickActivity",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(targetClass, "onResume", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    injectOnViewReady(param.thisObject as Activity)
                }
            })
            XposedBridge.log("$TAG: ✓ DoubleClickActivity.onResume hooked")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ✗ MiPay hook FAILED: ${e.message}")
        }
    }

    private fun injectOnViewReady(activity: Activity) {
        try {
            val decorView = activity.window.decorView ?: return
            if (decorView.findViewWithTag<View>(INJECT_TAG) != null) return

            if (decorView.width == 0 || decorView.height == 0) {
                decorView.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            decorView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            doInject(activity, decorView)
                        }
                    }
                )
            } else {
                doInject(activity, decorView)
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: injectOnViewReady error: ${e.message}")
        }
    }

    private fun doInject(activity: Activity, decorView: View) {
        if (decorView.findViewWithTag<View>(INJECT_TAG) != null) return

        val frameLayout = decorView as? ViewGroup ?: return

        val btnW = dp(104)
        val btnH = dp(58)
        val marginRight  = dp(10)
        val marginBottom = dp(100)

        val gpayButton = GooglePayButtonView(context = frameLayout.context)
        gpayButton.tag = INJECT_TAG
        gpayButton.setSvgString(GOOGLE_PAY_SVG)

        gpayButton.post {
            val pw = frameLayout.width
            val ph = frameLayout.height
            if (pw <= 0 || ph <= 0) {
                XposedBridge.log("$TAG: post layout but parent is 0! pw=$pw, ph=$ph")
                return@post
            }
            val absX = pw - btnW - marginRight
            val absY = ph - btnH - marginBottom
            XposedBridge.log("$TAG: pos → parent=${pw}x${ph}, btn=${btnW}x${btnH}, abs=($absX,$absY)")
            gpayButton.layoutParams = FrameLayout.LayoutParams(btnW, btnH).apply {
                leftMargin = absX
                topMargin  = absY
            }
            gpayButton.postInvalidate()
        }

        activity.runOnUiThread {
            try {
                if (frameLayout.findViewWithTag<View>(INJECT_TAG) != null) return@runOnUiThread
                frameLayout.addView(gpayButton)
                XposedBridge.log("$TAG: ✓ Button added (${btnW}×${btnH})")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: addView failed: ${e.message}")
            }
        }
    }

    private fun dp(v: Int): Int {
        return (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    }

    // ════════════════════════ Google Wallet NFC 自动管理 ════════════════════════

    /**
     * 在 Google Wallet 进程内安装 Activity 生命周期钩子
     * onStart 计数+1，onStop 计数-1，计数从 0→1 时设 NFC，1→0 时还原
     */
    private fun setupWalletNfcHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("$TAG: ★ Wallet package matched — setting up NFC hooks")

        try {
            val activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)

            XposedHelpers.findAndHookMethod(activityClass, "onStart", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    onWalletForeground(param.thisObject as Activity)
                }
            })

            XposedHelpers.findAndHookMethod(activityClass, "onStop", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    onWalletBackground(param.thisObject as Activity)
                }
            })

            XposedBridge.log("$TAG: ✓ Wallet onStart/onStop hooked")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ✗ Wallet NFC hook FAILED: ${e.message}")
        }
    }

    /**
     * Wallet Activity 进入前台
     * - 取消待执行的还原任务
     * - 计数从 0 变为 1（首次进入前台）时：保存当前 NFC 默认应用，切换为 Google Wallet
     */
    private fun onWalletForeground(activity: Activity) {
        // 取消待执行的还原
        restoreRunnable?.let { mainHandler.removeCallbacks(it) }
        restoreRunnable = null

        walletActiveCount++
        XposedBridge.log("$TAG: Wallet onStart → count=$walletActiveCount")

        if (walletActiveCount == 1) {
            XposedBridge.log("$TAG: Wallet foreground — taking NFC control")
            takeNfcControl(activity)
        }
    }

    /**
     * Wallet Activity 进入后台
     * - 计数从 1 变为 0 时（所有 Wallet Activity 都已后台）：延迟 800ms 后还原 NFC 默认应用
     *   延迟是为了避免同一 app 内 Activity 切换导致的误触发
     */
    private fun onWalletBackground(activity: Activity) {
        walletActiveCount--
        if (walletActiveCount <= 0) {
            walletActiveCount = 0
            XposedBridge.log("$TAG: Wallet onStop → count=0, scheduling NFC restore")

            restoreRunnable = Runnable {
                XposedBridge.log("$TAG: Wallet background — restoring NFC default")
                restoreNfcControl(activity)
                restoreRunnable = null
            }
            mainHandler.postDelayed(restoreRunnable!!, 800)
        } else {
            XposedBridge.log("$TAG: Wallet onStop → count=$walletActiveCount (still active)")
        }
    }

    // ── NFC 控制 ─────────────────────────────────────────────

    /**
     * 获取当前 NFC 默认支付应用
     * 优先尝试 Settings.Secure（部分设备可直接读取），失败则通过 su 读取
     */
    private fun readNfcDefault(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, NFC_SETTING_KEY)
                ?.takeIf { it.isNotEmpty() && it != "null" }
        } catch (e: Throwable) {
            execSu("settings get secure $NFC_SETTING_KEY")?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    /**
     * 设置 NFC 默认支付应用
     * 优先尝试 Settings.Secure（部分设备可直接写入），失败则通过 su 写入
     */
    private fun writeNfcDefault(context: Context, component: String): Boolean {
        return try {
            Settings.Secure.putString(context.contentResolver, NFC_SETTING_KEY, component)
            true
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Settings.Secure.putString failed → trying su: ${e.message}")
            execSu("settings put secure $NFC_SETTING_KEY $component") != null
        }
    }

    /**
     * 通过 su 执行 shell 命令，返回 stdout 内容（失败返回 null）
     */
    private fun execSu(cmd: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output  = process.inputStream.bufferedReader().use { it.readText() }
            val err     = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            XposedBridge.log("$TAG: su [$cmd] → exit=${process.exitValue()}, out=$output, err=$err")
            if (process.exitValue() == 0) output else null
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: execSu [$cmd] FAILED: ${e.message}")
            null
        }
    }

    /**
     * 接管 NFC：将当前默认应用保存后，切换为 Google Wallet
     */
    private fun takeNfcControl(activity: Activity) {
        // 发现一次 Wallet NFC 服务组件名，之后复用
        if (!nfcComponentDiscovered) {
            walletNfcComponent = discoverWalletNfcComponent(activity)
            nfcComponentDiscovered = true
        }

        val walletComponent = walletNfcComponent
        if (walletComponent == null) {
            XposedBridge.log("$TAG: Cannot take NFC control — Wallet component unknown")
            return
        }

        val current = readNfcDefault(activity)
        XposedBridge.log("$TAG: Current NFC default: $current")

        // 如果当前已经是 Wallet，不需要重复切换
        if (walletComponent == current) {
            XposedBridge.log("$TAG: NFC already set to Wallet — no action needed")
            return
        }

        // 保存当前值（仅当之前未保存过时才保存）
        if (savedNfcDefault == null && current != null) {
            savedNfcDefault = current
            XposedBridge.log("$TAG: Saved original NFC default: $current")
        }

        val ok = writeNfcDefault(activity, walletComponent)
        XposedBridge.log("$TAG: Set NFC default to Wallet ($walletComponent): $ok")
    }

    /**
     * 还原 NFC：恢复之前保存的默认应用
     */
    private fun restoreNfcControl(activity: Activity) {
        val original = savedNfcDefault
        if (original == null) {
            XposedBridge.log("$TAG: No saved NFC default — nothing to restore")
            return
        }

        val ok = writeNfcDefault(activity, original)
        XposedBridge.log("$TAG: Restored NFC default ($original): $ok")
        savedNfcDefault = null
    }

    /**
     * 从 Google Wallet 包中枚举所有 Service，找出持有 BIND_NFC_SERVICE 权限的服务
     * （HCE Host APDU Service 的标识），作为 NFC 支付组件
     * 兜底：直接返回 Wallet Activity 组件（部分 ROM 可能走此路径）
     */
    private fun discoverWalletNfcComponent(context: Context): String? {
        try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(WALLET_PKG, PackageManager.GET_SERVICES)
            val nfcService = pkgInfo.services?.firstOrNull {
                it.permission == android.Manifest.permission.BIND_NFC_SERVICE
            }
            if (nfcService != null) {
                val component = ComponentName(nfcService.packageName, nfcService.name).flattenToString()
                XposedBridge.log("$TAG: Discovered Wallet NFC component: $component")
                return component
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: discoverWalletNfcComponent failed: ${e.message}")
        }
        // 兜底：直接返回 Wallet 主 Activity（部分系统 NFC 入口）
        return "$WALLET_PKG/com.google.android.apps.wallet.main.WalletActivity"
    }
}

/**
 * 在 Wallet 进程内持有 Application Context
 * 用于后台还原 NFC 时（此时 Activity 可能已被销毁）仍有 Context 可用
 */
/**
 * Google Pay 药丸按钮 — 自定义绘制
 */
class GooglePayButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var svg: SVG? = null
    private var svgBitmap: Bitmap? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    // 深色模式 → #121212，浅色 → system_accent1_100，兜底蓝色
    private val monetColor: Int by lazy {
        try {
            val uiMode = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

            if (isDark) {
                android.graphics.Color.parseColor("#121212")
            } else if (Build.VERSION.SDK_INT >= 31) {
                val resId = android.R.color.system_accent1_100
                context.theme.resources.getColor(resId, context.theme)
            } else {
                android.graphics.Color.rgb(33, 150, 243)
            }
        } catch (e: Throwable) {
            android.graphics.Color.rgb(33, 150, 243)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            try {
                val intent = android.content.Intent().apply {
                    component = ComponentName(MainHook.WALLET_PKG, "com.google.android.apps.wallet.main.WalletActivity")
                    flags = (android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                it.context.startActivity(intent)
            } catch (e: Throwable) {
                Toast.makeText(it.context, "未安装 Google Wallet", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun setSvgString(svgString: String) {
        try {
            svg = SVG.getFromString(svgString)
            prepareBitmap()
            invalidate()
        } catch (e: SVGParseException) {
            android.util.Log.e("GPB", "SVG parse error: ${e.message}")
        }
    }

    private fun prepareBitmap() {
        val svgObj = this.svg ?: return
        val svgW = svgObj.documentWidth
        val svgH = svgObj.documentHeight
        if (svgW <= 0 || svgH <= 0) return

        val picture = svgObj.renderToPicture()
        val bmpW = 160
        val bmpH = 76
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bitmap)

        val scaleX = bmpW.toFloat() / picture.width.toFloat()
        val scaleY = bmpH.toFloat() / picture.height.toFloat()
        bmpCanvas.save()
        bmpCanvas.scale(scaleX, scaleY)
        bmpCanvas.drawPicture(picture)
        bmpCanvas.restore()

        svgBitmap?.recycle()
        svgBitmap = bitmap
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        setMeasuredDimension((104 * density).toInt(), (58 * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        rect.set(0f, 0f, w, h)
        bgPaint.color = monetColor
        bgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, h / 2f, h / 2f, bgPaint)

        val bmp = svgBitmap ?: return
        val targetH = h * 0.60f
        val targetW = bmp.width.toFloat() / bmp.height.toFloat() * targetH
        val left = (w - targetW) / 2f
        val top  = (h - targetH) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + targetW, top + targetH), svgPaint)
    }
}
