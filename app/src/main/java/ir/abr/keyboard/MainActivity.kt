package ir.abr.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(239, 247, 255))
        }
        val title = TextView(this).apply {
            text = "ابر ☁"
            textSize = 34f
            setTextColor(Color.rgb(55, 105, 150))
            gravity = Gravity.CENTER
        }
        val info = TextView(this).apply {
            text = "برای استفاده از صفحه‌کلید ابر، ابتدا آن را فعال و سپس انتخاب کنید."
            textSize = 17f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 24, 8, 24)
        }
        val enable = Button(this).apply {
            text = "۱. فعال‌سازی صفحه‌کلید"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        val choose = Button(this).apply {
            text = "۲. انتخاب صفحه‌کلید ابر"
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        root.addView(title)
        root.addView(info)
        root.addView(enable)
        root.addView(choose)
        setContentView(root)
    }
}
