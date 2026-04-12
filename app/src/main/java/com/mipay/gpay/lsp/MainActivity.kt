package com.mipay.gpay.lsp

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color

/**
 * LSPosed 模块的虚拟入口 Activity
 * 仅用于在 LSPosed 管理器中显示"打开"按钮，确认模块已安装
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        
        val title = TextView(this).apply {
            text = "MiPay GPay 快捷按钮"
            textSize = 24f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 20)
        }
        
        val desc = TextView(this).apply {
            text = "LSPosed 模块已安装\n\n请在 LSPosed 中启用此模块，并勾选 com.miui.tsmclient 作用域，然后重启手机。"
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }
        
        layout.addView(title)
        layout.addView(desc)
        setContentView(layout)
    }
}
