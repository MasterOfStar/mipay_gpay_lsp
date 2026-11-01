package com.mipay.gpay.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncLauncherIcon()

        val density = resources.displayMetrics.density
        val miuixEnabled = SettingsStore.getBoolean(this, SettingsStore.MIUIX_STYLE)
        val darkMode = isDarkMode()
        val backgroundColor = if (miuixEnabled) {
            if (darkMode) Color.parseColor("#000000") else Color.parseColor("#F2F2F7")
        } else {
            Color.WHITE
        }
        val cardColor = if (miuixEnabled) {
            if (darkMode) Color.parseColor("#1C1C1E") else Color.WHITE
        } else {
            Color.parseColor("#F6F6F8")
        }
        val titleColor = if (darkMode) Color.WHITE else Color.BLACK
        val subtitleColor = if (darkMode) Color.parseColor("#9A9AA0") else Color.parseColor("#6D6D72")

        contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (24 * density).toInt(),
                (20 * density).toInt(),
                (24 * density).toInt()
            )
        }

        contentLayout.addView(TextView(this).apply {
            text = "MiPay GPay"
            textSize = 28f
            setTextColor(titleColor)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })

        addSpacer((16 * density).toInt())
        addCard(
            title = "MIUIX 风格",
            subtitle = "设置页和注入按钮使用 MIUIX 样式",
            checked = miuixEnabled,
            cardColor = cardColor,
            titleColor = titleColor,
            subtitleColor = subtitleColor
        ) { enabled ->
            SettingsStore.setBoolean(this, SettingsStore.MIUIX_STYLE, enabled)
            recreate()
        }

        addSpacer((12 * density).toInt())
        addCard(
            title = "双击电源键唤起",
            subtitle = "替代 MiPay 双击电源键逻辑并打开 Google Pay",
            checked = SettingsStore.getBoolean(this, SettingsStore.POWER_DOUBLE_CLICK),
            cardColor = cardColor,
            titleColor = titleColor,
            subtitleColor = subtitleColor
        ) { enabled ->
            SettingsStore.setBoolean(this, SettingsStore.POWER_DOUBLE_CLICK, enabled)
        }

        addSpacer((12 * density).toInt())
        addCard(
            title = "显示桌面图标",
            subtitle = "默认隐藏，仅保留 LSPosed 隐式入口",
            checked = SettingsStore.getBoolean(this, SettingsStore.SHOW_LAUNCHER_ICON),
            cardColor = cardColor,
            titleColor = titleColor,
            subtitleColor = subtitleColor
        ) { enabled ->
            SettingsStore.setBoolean(this, SettingsStore.SHOW_LAUNCHER_ICON, enabled)
            syncLauncherIcon()
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(backgroundColor)
                addView(
                    contentLayout,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )
    }

    private fun syncLauncherIcon() {
        val enabled = SettingsStore.getBoolean(this, SettingsStore.SHOW_LAUNCHER_ICON)
        val component = ComponentName(packageName, "$packageName.LauncherActivity")
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun addCard(
        title: String,
        subtitle: String,
        checked: Boolean,
        cardColor: Int,
        titleColor: Int,
        subtitleColor: Int,
        onChanged: (Boolean) -> Unit
    ) {
        val density = resources.displayMetrics.density
        val miuixEnabled = SettingsStore.getBoolean(this, SettingsStore.MIUIX_STYLE)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                (20 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt()
            )
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textLayout.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(titleColor)
        })
        textLayout.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(subtitleColor)
            setPadding(0, (4 * density).toInt(), 0, 0)
        })

        row.addView(
            textLayout,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(MiuixSwitch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener(onChanged)
        })

        contentLayout.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(cardColor)
                    cornerRadius = (if (miuixEnabled) 16f else 8f) * density
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

    private fun isDarkMode(): Boolean {
        val uiMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
