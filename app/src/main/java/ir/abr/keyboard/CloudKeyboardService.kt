package ir.abr.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CloudKeyboardService : InputMethodService() {
    private var english = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(22))
            background = CloudDrawable()
        }
        val rows = if (english) arrayOf(
            "qwertyuiop", "asdfghjkl", "zxcvbnm"
        ) else arrayOf(
            "ضصثقفغعهخحج", "شسیبلاتنمکگ", "ظطزرذدپو"
        )
        rows.forEachIndexed { index, row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { ch ->
                line.addView(key(ch.toString()) {
                    currentInputConnection?.commitText(ch.toString(), 1)
                }, LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                    setMargins(dp(2), dp(3), dp(2), dp(3))
                })
            }
            if (index == 2) {
                line.addView(key("⌫") { currentInputConnection?.deleteSurroundingText(1, 0) },
                    LinearLayout.LayoutParams(dp(48), dp(49)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
            }
            root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(key(if (english) "فا" else "EN") { english = !english; setInputView(onCreateInputView()) },
            LinearLayout.LayoutParams(dp(58), dp(46)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        controls.addView(key("،") { currentInputConnection?.commitText("،", 1) },
            LinearLayout.LayoutParams(dp(45), dp(46)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        controls.addView(key("فاصله") { currentInputConnection?.commitText(" ", 1) },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        controls.addView(key("↵") { currentInputConnection?.performEditorAction(currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_DONE) },
            LinearLayout.LayoutParams(dp(48), dp(46)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        root.addView(controls)
        return root
    }

    private fun key(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = if (label.length > 1) 13f else 20f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(55, 88, 119))
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(15).toFloat()
            setColor(Color.argb(220, 255, 255, 255))
            setStroke(dp(1), Color.rgb(205, 225, 242))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class CloudDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(242, 225, 242, 253)
            style = Paint.Style.FILL
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 178, 213, 239)
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val p = Path().apply {
                moveTo(w * .12f, h * .22f)
                cubicTo(w * .02f, h * .10f, w * .10f, h * .01f, w * .23f, h * .08f)
                cubicTo(w * .27f, h * -.02f, w * .43f, h * -.01f, w * .48f, h * .10f)
                cubicTo(w * .60f, h * -.01f, w * .76f, h * .04f, w * .77f, h * .17f)
                cubicTo(w * .95f, h * .12f, w * 1.02f, h * .30f, w * .91f, h * .40f)
                cubicTo(w * 1.02f, h * .56f, w * .94f, h * .76f, w * .80f, h * .76f)
                cubicTo(w * .75f, h * .95f, w * .57f, h * .94f, w * .49f, h * .84f)
                cubicTo(w * .35f, h * 1.00f, w * .18f, h * .91f, w * .19f, h * .78f)
                cubicTo(w * .02f, h * .81f, w * -.02f, h * .59f, w * .10f, h * .49f)
                cubicTo(w * .01f, h * .37f, w * .04f, h * .27f, w * .12f, h * .22f)
                close()
            }
            canvas.drawPath(p, paint)
            canvas.drawPath(p, stroke)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
