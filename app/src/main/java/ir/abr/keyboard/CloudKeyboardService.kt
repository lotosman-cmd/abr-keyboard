package ir.abr.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea

class CloudKeyboardService : InputMethodService() {
    private var english = false
    private var handwriting = false
    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null
    private var pad: InkPad? = null
    private var status: TextView? = null
    private var candidateRow: LinearLayout? = null

    override fun onCreateInputView(): View = buildKeyboard()

    private fun buildKeyboard(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = CloudDrawable()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("ابر  ☁", 17f), LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(key(if (handwriting) "صفحه‌کلید" else "دست‌خط") {
            handwriting = !handwriting
            setInputView(buildKeyboard())
        }, LinearLayout.LayoutParams(dp(112), dp(36)))
        root.addView(header)

        if (handwriting) {
            status = label("در حال آماده‌سازی تشخیص دست‌خط فارسی…", 12f)
            root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))
            pad = InkPad().also { root.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118))) }
            candidateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            root.addView(candidateRow)
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            actions.addView(key("پاک‌کردن") { pad?.clear(); candidateRow?.removeAllViews() },
                LinearLayout.LayoutParams(dp(90), dp(40)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            actions.addView(key("تبدیل به متن") { recognizeInk() },
                LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            actions.addView(key("فاصله") { currentInputConnection?.commitText(" ", 1) },
                LinearLayout.LayoutParams(dp(70), dp(40)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            root.addView(actions)
            preparePersianRecognizer()
            return root
        }

        val rows = if (english) arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
                   else arrayOf("ضصثقفغعهخحج", "شسیبلاتنمکگ", "ظطزرذدپو")
        rows.forEachIndexed { index, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            row.forEach { ch ->
                line.addView(key(ch.toString()) { currentInputConnection?.commitText(ch.toString(), 1) },
                    LinearLayout.LayoutParams(0, dp(47), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
            }
            if (index == 2) line.addView(key("⌫") { currentInputConnection?.deleteSurroundingText(1, 0) },
                LinearLayout.LayoutParams(dp(46), dp(47)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
            root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(key(if (english) "فا" else "EN") { english = !english; setInputView(buildKeyboard()) },
            LinearLayout.LayoutParams(dp(55), dp(43)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        controls.addView(key("،") { currentInputConnection?.commitText("،", 1) },
            LinearLayout.LayoutParams(dp(42), dp(43)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        controls.addView(key("فاصله") { currentInputConnection?.commitText(" ", 1) },
            LinearLayout.LayoutParams(0, dp(43), 1f).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        controls.addView(key("↵") { currentInputConnection?.performEditorAction(currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_DONE) },
            LinearLayout.LayoutParams(dp(45), dp(43)).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) })
        root.addView(controls)
        return root
    }

    private fun preparePersianRecognizer() {
        if (recognizer != null || model != null) return
        try {
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("fa")
            if (identifier == null) {
                status?.text = "مدل تشخیص فارسی در دسترس نیست"
                return
            }
            val m = DigitalInkRecognitionModel.builder(identifier).build()
            model = m
            val manager = RemoteModelManager.getInstance()
            manager.isModelDownloaded(m).addOnSuccessListener { downloaded ->
                if (downloaded) createRecognizer(m)
                else {
                    status?.text = "دریافت مدل فارسی (حدود ۲۰ مگابایت)…"
                    manager.download(m, DownloadConditions.Builder().build())
                        .addOnSuccessListener { createRecognizer(m) }
                        .addOnFailureListener { status?.text = "دریافت مدل ناموفق بود؛ اینترنت را بررسی کنید." }
                }
            }.addOnFailureListener { status?.text = "بررسی مدل ناموفق بود." }
        } catch (e: Exception) {
            status?.text = "راه‌اندازی تشخیص دست‌خط ناموفق بود."
        }
    }

    private fun createRecognizer(m: DigitalInkRecognitionModel) {
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
        status?.text = "آماده است؛ فارسی را در فضای ابری بنویسید."
    }

    private fun recognizeInk() {
        val ink = pad?.buildInk()
        if (ink == null) { status?.text = "ابتدا چیزی بنویسید."; return }
        val r = recognizer
        if (r == null) { status?.text = "مدل هنوز آماده نیست؛ کمی صبر کنید."; return }
        val writing = RecognitionContext.builder()
            .setWritingArea(WritingArea((pad?.width ?: 1).toFloat(), (pad?.height ?: 1).toFloat()))
            .build()
        status?.text = "در حال تشخیص…"
        r.recognize(ink, writing)
            .addOnSuccessListener { result ->
                candidateRow?.removeAllViews()
                val candidates = result.candidates.take(3)
                if (candidates.isEmpty()) status?.text = "متنی تشخیص داده نشد؛ دوباره واضح‌تر بنویسید."
                else {
                    status?.text = "برای درج متن، یکی از پیشنهادها را انتخاب کنید."
                    candidates.forEach { candidate ->
                        candidateRow?.addView(key(candidate.text) {
                            currentInputConnection?.commitText(candidate.text, 1)
                            pad?.clear()
                            candidateRow?.removeAllViews()
                        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                    }
                }
            }
            .addOnFailureListener { status?.text = "تشخیص انجام نشد؛ دوباره تلاش کنید." }
    }

    override fun onDestroy() {
        recognizer?.close()
        recognizer = null
        super.onDestroy()
    }

    private fun label(textValue: String, size: Float) = TextView(this).apply {
        text = textValue; textSize = size; gravity = Gravity.CENTER
        setTextColor(Color.rgb(55, 88, 119))
    }

    private fun key(textValue: String, action: () -> Unit): TextView = TextView(this).apply {
        text = textValue
        textSize = if (textValue.length > 2) 12f else 18f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(55, 88, 119))
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(Color.argb(228, 255, 255, 255))
            setStroke(dp(1), Color.rgb(205, 225, 242))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class InkPad : View(this@CloudKeyboardService) {
        private val inkBuilder = Ink.builder()
        private var strokeBuilder: Ink.Stroke.Builder? = null
        private val strokes = mutableListOf<Path>()
        private var activePath: Path? = null
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 100, 150); style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 180, 205); textSize = dp(13).toFloat()
        }
        init {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.argb(235, 255, 255, 255))
                setStroke(dp(1), Color.rgb(190, 218, 239))
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (strokes.isEmpty() && activePath == null) canvas.drawText("اینجا با انگشت بنویسید", dp(12).toFloat(), height / 2f, hintPaint)
            strokes.forEach { canvas.drawPath(it, linePaint) }
            activePath?.let { canvas.drawPath(it, linePaint) }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x; val y = event.y; val t = event.eventTime
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    strokeBuilder = Ink.Stroke.builder().also { it.addPoint(Ink.Point.create(x, y, t)) }
                    activePath = Path().apply { moveTo(x, y) }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                    activePath?.lineTo(x, y); invalidate(); return true
                }
                MotionEvent.ACTION_UP -> {
                    strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                    strokeBuilder?.let { inkBuilder.addStroke(it.build()) }
                    activePath?.let { strokes.add(it) }
                    activePath = null; strokeBuilder = null
                    invalidate(); return true
                }
            }
            return true
        }
        fun buildInk(): Ink? = if (strokes.isEmpty()) null else inkBuilder.build()
        fun clear() {
            strokes.clear()
            // A new pad is needed because Ink.Builder is immutable after build.
            pad = InkPad()
            (parent as? ViewGroup)?.let { group ->
                val index = group.indexOfChild(this)
                group.removeViewAt(index)
                group.addView(pad, index, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118)))
            }
        }
    }

    private inner class CloudDrawable : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(242, 225, 242, 253); style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 178, 213, 239); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
        }
        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
            val p = Path().apply {
                moveTo(w*.12f,h*.22f); cubicTo(w*.02f,h*.10f,w*.10f,h*.01f,w*.23f,h*.08f)
                cubicTo(w*.27f,h*-.02f,w*.43f,h*-.01f,w*.48f,h*.10f)
                cubicTo(w*.60f,h*-.01f,w*.76f,h*.04f,w*.77f,h*.17f)
                cubicTo(w*.95f,h*.12f,w*1.02f,h*.30f,w*.91f,h*.40f)
                cubicTo(w*1.02f,h*.56f,w*.94f,h*.76f,w*.80f,h*.76f)
                cubicTo(w*.75f,h*.95f,w*.57f,h*.94f,w*.49f,h*.84f)
                cubicTo(w*.35f,h*1.00f,w*.18f,h*.91f,w*.19f,h*.78f)
                cubicTo(w*.02f,h*.81f,w*-.02f,h*.59f,w*.10f,h*.49f)
                cubicTo(w*.01f,h*.37f,w*.04f,h*.27f,w*.12f,h*.22f); close()
            }
            canvas.drawPath(p, paint); canvas.drawPath(p, stroke)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
