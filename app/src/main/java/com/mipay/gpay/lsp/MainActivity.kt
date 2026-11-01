package com.mipay.gpay.lsp

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
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
            text = "DEV 锁屏唤起\n\n请在 LSPosed 中启用：com.miui.tsmclient、android。\n不要启用 Google Wallet 作用域。\n用 persist.debug.mipay_gpay.experiment 切换 system/off。"
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }
        
        layout.addView(title)
        layout.addView(desc)
        setContentView(layout)

        Thread {
            val result = RootCommandProvider.runRootCommand("id")
            runOnUiThread {
                val message = if (result.success) {
                    "Root 权限已授权"
                } else {
                    "未获得 Root 权限，锁屏跳转将不可用"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
