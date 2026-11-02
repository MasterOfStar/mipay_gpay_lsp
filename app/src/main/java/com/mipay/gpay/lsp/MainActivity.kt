package com.mipay.gpay.lsp

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var contentLayout: LinearLayout

    private data class Tokens(
        val pageBackground: Int,
        val cardBackground: Int,
        val titleColor: Int,
        val subtitleColor: Int,
        val cardRadiusDp: Float,
        val rowMinHeightDp: Int,
        val rowHorizontalPaddingDp: Int,
        val rowVerticalPaddingDp: Int,
        val titleSp: Float,
        val subtitleSp: Float,
        val headerSp: Float,
        val headerTypeface: Typeface,
        val headerGapDp: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncLauncherIcon()

        val density = resources.displayMetrics.density
        val dark = isDarkMode()
        val miuix = SettingsStore.getBoolean(this, SettingsStore.MIUIX_STYLE)
        val tokens = buildTokens(miuix, dark)

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).toInt(),
                (24 * density).toInt(),
                (16 * density).toInt(),
                (24 * density).toInt()
            )
        }

        contentLayout.addView(TextView(this).apply {
            text = "MiPay GPay"
            textSize = tokens.headerSp
            setTextColor(tokens.titleColor)
            typeface = tokens.headerTypeface
        })

        addSpacer((tokens.headerGapDp * density).toInt())
        addCard(
            title = "MIUIX 风格",
            subtitle = "设置页和注入按钮统一使用 MIUIX 样式",
            checked = miuix,
            tokens = tokens,
            miuix = miuix
        ) { enabled ->
            SettingsStore.setBoolean(this, SettingsStore.MIUIX_STYLE, enabled)
            recreate()
        }

        addSpacer((12 * density).toInt())
        addCard(
            title = "双击电源键唤起",
            subtitle = "替代 MiPay 双击电源键逻辑并打开 Google Pay",
            checked = SettingsStore.getBoolean(this, SettingsStore.POWER_DOUBLE_CLICK),
            tokens = tokens,
            miuix = miuix
        ) { enabled ->
            SettingsStore.setBoolean(this, SettingsStore.POWER_DOUBLE_CLICK, enabled)
        }

        addSpacer((12 * density).toInt())
        addCard(
            title = "Wallet 内鉴权",
            subtitle = "锁屏下先尝试 Wallet 自己的鉴权；失败可关闭改用系统解锁",
            checked = SettingsStore.getBoolean(this, SettingsStore.WALLET_NATIVE_AUTH, true),
            tokens = tokens,
            miuix = miuix
        ) { enabled ->
            SettingsStore.setBoolean(this, SettingsStore.WALLET_NATIVE_AUTH, enabled)
        }

        addSpacer((12 * density).toInt())
        addCard(
            title = "隐藏桌面图标",
            subtitle = "默认显示桌面图标，打开后仅保留 LSPosed 隐式入口",
            checked = SettingsStore.getBoolean(this, SettingsStore.HIDE_LAUNCHER_ICON),
            tokens = tokens,
            miuix = miuix
        ) { hidden ->
            SettingsStore.setBoolean(this, SettingsStore.HIDE_LAUNCHER_ICON, hidden)
            syncLauncherIcon()
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(tokens.pageBackground)
                addView(
                    contentLayout,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )
        applySystemBars(tokens.pageBackground, dark)
    }

    private fun buildTokens(miuix: Boolean, dark: Boolean): Tokens {
        return if (miuix) {
            Tokens(
                pageBackground = Color.parseColor(if (dark) "#000000" else "#F7F7F7"),
                cardBackground = Color.parseColor(if (dark) "#242424" else "#FFFFFF"),
                titleColor = if (dark) Color.argb(0xE6, 255, 255, 255) else Color.BLACK,
                subtitleColor = if (dark) Color.argb(0x80, 255, 255, 255)
                else Color.argb(0x99, 0, 0, 0),
                cardRadiusDp = 16f,
                rowMinHeightDp = 56,
                rowHorizontalPaddingDp = 16,
                rowVerticalPaddingDp = 10,
                titleSp = 17f,
                subtitleSp = 14f,
                headerSp = 32f,
                headerTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL),
                headerGapDp = 20
            )
        } else {
            Tokens(
                pageBackground = Color.parseColor(if (dark) "#141218" else "#FFFBFE"),
                cardBackground = Color.parseColor(if (dark) "#211F26" else "#F3EDF7"),
                titleColor = Color.parseColor(if (dark) "#E6E0E9" else "#1C1B1F"),
                subtitleColor = Color.parseColor(if (dark) "#CAC4D0" else "#49454F"),
                cardRadiusDp = 12f,
                rowMinHeightDp = 72,
                rowHorizontalPaddingDp = 16,
                rowVerticalPaddingDp = 12,
                titleSp = 16f,
                subtitleSp = 14f,
                headerSp = 28f,
                headerTypeface = Typeface.create("sans-serif", Typeface.NORMAL),
                headerGapDp = 16
            )
        }
    }

    private fun syncLauncherIcon() {
        LauncherIconSync.sync(this)
    }

    private fun addCard(
        title: String,
        subtitle: String,
        checked: Boolean,
        tokens: Tokens,
        miuix: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = (tokens.rowMinHeightDp * density).toInt()
            setPadding(
                (tokens.rowHorizontalPaddingDp * density).toInt(),
                (tokens.rowVerticalPaddingDp * density).toInt(),
                (tokens.rowHorizontalPaddingDp * density).toInt(),
                (tokens.rowVerticalPaddingDp * density).toInt()
            )
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textLayout.addView(TextView(this).apply {
            text = title
            textSize = tokens.titleSp
            setTextColor(tokens.titleColor)
        })
        textLayout.addView(TextView(this).apply {
            text = subtitle
            textSize = tokens.subtitleSp
            setTextColor(tokens.subtitleColor)
            setPadding(0, (4 * density).toInt(), 0, 0)
        })

        val toggle = MiuixSwitch(this, miuix).apply {
            isChecked = checked
            setOnCheckedChangeListener(onChanged)
        }
        row.addView(
            textLayout,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(toggle)
        row.setOnClickListener { toggle.toggle() }

        contentLayout.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(tokens.cardBackground)
                    cornerRadius = tokens.cardRadiusDp * density
                }
                addView(row)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addSpacer(heightPx: Int) {
        contentLayout.addView(
            View(this),
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
        )
    }

    private fun applySystemBars(backgroundColor: Int, dark: Boolean) {
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        var flags = window.decorView.systemUiVisibility
        flags = if (dark) {
            flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } else {
            flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            flags = if (dark) {
                flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
