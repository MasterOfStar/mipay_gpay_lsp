package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
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

/**
 * LSPosed 模块入口 — 在 MiPay 刷卡页面注入 Google Pay 快捷按钮
 *
 * 目标包：com.miui.tsmclient
 * 注入时机：Activity.onResume（视图已完全显示）
 * 注入方式：添加到 decorView 右下角
 *
 * 按钮样式：
 *  - 内容：Google Pay 完整 Logo（SVG 四色 G + Pay 灰色文字）
 *  - 背景：MD3 莫奈取色药丸
 *  - 跳转：尝试打开 Google Wallet，全部失败后弹 Toast
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TARGET_PKG = "com.miui.tsmclient"
        private const val TAG = "MiPayGPay"
        private const val INJECT_TAG = "mipay_gpay_btn"

        // Google Pay 官方 SVG（来自 Google_Pay_Logo.svg，保留原始 path 数据，内联 fill 颜色，无 CSS class）
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
        XposedBridge.log("$TAG: === Module loaded ===")
        XposedBridge.log("$TAG: Package: ${lpparam.packageName}")

        if (lpparam.packageName != TARGET_PKG) {
            return
        }

        XposedBridge.log("$TAG: ★ Target matched! Setting up hooks...")

        try {
            val activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(activityClass, "onResume", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (activity.packageName == TARGET_PKG) {
                        injectOnViewReady(activity)
                    }
                }
            })
            XposedBridge.log("$TAG: ✓ android.app.Activity.onResume hooked")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ✗ Activity hook FAILED: ${e.message}")
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

    /**
     * 执行按钮注入
     *  - 药丸尺寸：104×58dp
     *  - 位置：右上右下角，距右边缘 10dp，距底部 70dp（比之前往右上移了）
     *  - 绝对坐标计算，不依赖 gravity
     */
    private fun doInject(activity: Activity, decorView: View) {
        if (decorView.findViewWithTag<View>(INJECT_TAG) != null) return

        val frameLayout = decorView as? ViewGroup ?: return

        // 按钮尺寸（固定 dp）
        val btnW = dp(104)
        val btnH = dp(58)

        // 绝对位置：parent 右下角偏移
        // absX = parentW - btnW - marginRight → marginRight 越小按钮越靠右
        // absY = parentH - btnH - marginBottom → marginBottom 越大按钮越靠上
        val marginRight = dp(10)   // 距右边缘
        val marginBottom = dp(100)  // 距底部（往上抬30px）

        val gpayButton = GooglePayButtonView(context = frameLayout.context)
        gpayButton.tag = INJECT_TAG
        gpayButton.setSvgString(GOOGLE_PAY_SVG)

        // 等待父容器布局完成，再计算绝对坐标
        gpayButton.post {
            val pw = frameLayout.width
            val ph = frameLayout.height
            if (pw <= 0 || ph <= 0) {
                XposedBridge.log("$TAG: post layout but parent is 0! pw=$pw, ph=$ph")
                return@post
            }

            val absX = pw - btnW - marginRight
            val absY = ph - btnH - marginBottom
            XposedBridge.log("$TAG: pos → parent=${pw}x${ph}, btn=${btnW}x${btnH}, marginR=$marginRight marginB=$marginBottom, abs=($absX, $absY)")

            gpayButton.layoutParams = FrameLayout.LayoutParams(btnW, btnH).apply {
                leftMargin = absX
                topMargin = absY
            }
            // 触发重新绘制
            gpayButton.postInvalidate()
        }

        activity.runOnUiThread {
            try {
                if (frameLayout.findViewWithTag<View>(INJECT_TAG) != null) return@runOnUiThread
                frameLayout.addView(gpayButton)
                XposedBridge.log("$TAG: ✓ Button added ($btnW×$btnH, right=$marginRight, bottom=$marginBottom)")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: addView failed: ${e.message}")
            }
        }
    }

    private fun dp(v: Int): Int {
        return (v * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    }
}

/**
 * Google Pay 药丸按钮 — 自定义绘制
 */
class GooglePayButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var svg: SVG? = null
    // Bitmap 缓存：SVG → Bitmap（避免每次 onDraw 重新渲染）
    private var svgBitmap: Bitmap? = null

    // 背景画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // SVG Bitmap 绘制画笔
    private val svgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // 圆角矩形
    private val rect = RectF()

    // MD3 Surface Tint 色（兜底蓝色）
    private val monetColor: Int by lazy {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
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
            openWallet(it.context)
        }
    }

    fun setSvgString(svgString: String) {
        try {
            svg = SVG.getFromString(svgString)
            android.util.Log.d("GPB", "SVG parsed OK, doc=${svg?.documentWidth}x${svg?.documentHeight}")
            // 预渲染为 Bitmap，避免每次 onDraw 重新解析
            prepareBitmap()
            invalidate()
        } catch (e: SVGParseException) {
            android.util.Log.e("GPB", "SVG parse error: ${e.message}")
        }
    }

    private fun prepareBitmap() {
        val svgObj = this.svg ?: run {
            android.util.Log.e("GPB", "prepareBitmap: svg is null!")
            return
        }
        val svgW = svgObj.documentWidth
        val svgH = svgObj.documentHeight
        android.util.Log.d("GPB", "prepareBitmap: svgW=$svgW, svgH=$svgH")
        if (svgW <= 0 || svgH <= 0) {
            android.util.Log.e("GPB", "prepareBitmap: invalid SVG dimensions!")
            return
        }

        // 用 renderToPicture 获取 Picture（按 viewBox 尺寸），然后缩放绘制到 Bitmap
        val picture = svgObj.renderToPicture()
        android.util.Log.d("GPB", "prepareBitmap: picture ${picture.width}x${picture.height}")

        // 创建 2x 高清 Bitmap
        val bmpW = 160
        val bmpH = 76
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val bmpCanvas = Canvas(bitmap)

        // 计算缩放：将 picture 缩放到 bitmap 全区域
        val scaleX = bmpW.toFloat() / picture.width.toFloat()
        val scaleY = bmpH.toFloat() / picture.height.toFloat()
        bmpCanvas.save()
        bmpCanvas.scale(scaleX, scaleY)
        bmpCanvas.drawPicture(picture)
        bmpCanvas.restore()

        android.util.Log.d("GPB", "prepareBitmap: done, bmp=${bmpW}x${bmpH}, scale=${scaleX}x${scaleY}, midPixel=${bitmap.getPixel(bmpW/2, bmpH/2)}")

        svgBitmap?.recycle()
        svgBitmap = bitmap
        android.util.Log.d("GPB", "prepareBitmap: done, bitmap=${bitmap.width}x${bitmap.height}, pixels=${bitmap.getPixel(0,0)}")
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 固定尺寸，不受父容器影响
        val density = resources.displayMetrics.density
        val w = (104 * density).toInt()
        val h = (58 * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        rect.set(0f, 0f, w, h)

        // ── 药丸背景 ──────────────────────────────────────────
        bgPaint.color = monetColor
        bgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, h / 2f, h / 2f, bgPaint)

        // ── SVG Logo：绘制预渲染的 Bitmap ────────────────────
        val bmp = svgBitmap ?: return

        // Logo 高度 = 按钮高度的 60%
        val targetH = h * 0.60f
        val targetW = bmp.width.toFloat() / bmp.height.toFloat() * targetH

        // 居中绘制到 Canvas
        val left = (w - targetW) / 2f
        val top = (h - targetH) / 2f
        canvas.drawBitmap(bmp, null, RectF(left, top, left + targetW, top + targetH), svgPaint)
    }

    private fun openWallet(context: Context) {
        android.util.Log.d("GPB", "Google Pay button clicked")
        val walletActivities = listOf(
            Pair("com.google.android.apps.walletnfcrel",
                "com.google.android.apps.walletnfcrel.common.secureui.MainActivity"),
            Pair("com.google.android.apps.walletnfcrel",
                "com.google.android.apps.wallet.main.WalletActivity"),
            Pair("com.google.android.apps.walletnfcrel",
                "com.google.commerce.tapandpay.android.home.HomeActivity"),
            Pair("com.google.android.apps.walletnfcrel",
                "com.google.commerce.tapandpay.android.mainactivity.MainActivity"),
            Pair("com.google.android.apps.walletnfcrel",
                "com.google.commerce.tapandpay.android.payments.transition.ui.TapPayActivity"),
            Pair("com.android.chrome",
                "com.google.android.apps.chrome.Main"),
            Pair("com.android.chrome",
                "org.chromium.chrome.browser.ChromeTabbedActivity"),
        )

        for ((pkg, cls) in walletActivities) {
            try {
                val intent = android.content.Intent().apply {
                    component = ComponentName(pkg, cls)
                    flags = (android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(intent)
                return
            } catch (e: Throwable) {
                // 尝试下一个
            }
        }
        Toast.makeText(context, "未安装 Google Wallet / Chrome", Toast.LENGTH_LONG).show()
    }
}
