package ir.abr.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
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

/**
 * ABR is a handwriting-first input method. It intentionally has no QWERTY/key grid.
 * Gestures: double tap = space; two-finger tap = enter; long-press + drag right = repeat delete.
 * Tap the dark cloud tab to hide the IME and return to scrolling.
 */
class CloudKeyboardService : InputMethodService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null
    private var pad: InkPad? = null
    private var status: TextView? = null
    private var candidates: LinearLayout? = null
    private var modelAttempted = false

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(7))
            background = CloudBackground()
        }
        status = TextView(this).apply {
            text = "روی ابر بنویسید؛ برای تبدیل، یک‌بار روی فضای خالی بزنید"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(55, 88, 119))
            setPadding(dp(4), dp(2), dp(4), dp(5))
        }
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(30)
        ))
        candidates = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(candidates, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(38)
        ))
        pad = InkPad()
        root.addView(pad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val spacer = View(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        root.addView(spacer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(5)
        ))
        val hideTab = TextView(this).apply {
            text = "ابر آبی  •  بستن صفحه‌کلید و ادامهٔ پیمایش"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.rgb(92, 157, 207))
            }
            setOnClickListener { requestHideSelf(0) }
        }
        root.addView(hideTab, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(34)
        ))
        prepareRecognizer()
        return root
    }

    private fun prepareRecognizer() {
        if (modelAttempted) return
        modelAttempted = true
        // ML Kit does not publish a dedicated Persian digital-ink model in every release.
        // Try Persian first; Arabic-script fallback is used only if the Persian tag is absent.
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("fa")
            ?: DigitalInkRecognitionModelIdentifier.fromLanguageTag("ar")
        if (identifier == null) {
            status?.text = "مدل دست‌خط فارسی در این نسخه پشتیبانی نمی‌شود."
            return
        }
        val m = DigitalInkRecognitionModel.builder(identifier).build()
        model = m
        val manager = RemoteModelManager.getInstance()
        manager.isModelDownloaded(m).addOnSuccessListener { downloaded ->
            if (downloaded) createRecognizer(m)
            else {
                status?.text = "در حال دریافت مدل تشخیص دست‌خط؛ اتصال اینترنت لازم است…"
                manager.download(m, DownloadConditions.Builder().build())
                    .addOnSuccessListener { createRecognizer(m) }
                    .addOnFailureListener {
                        status?.text = "دریافت مدل ناموفق بود؛ اتصال اینترنت را بررسی کنید."
                    }
            }
        }.addOnFailureListener {
            status?.text = "بررسی مدل تشخیص دست‌خط ناموفق بود."
        }
    }

    private fun createRecognizer(m: DigitalInkRecognitionModel) {
        recognizer?.close()
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(m).build()
        )
        status?.text = "مدل آماده است؛ بنویسید و برای تبدیل یک‌بار روی فضای خالی بزنید."
    }

    private fun recognize() {
        val ink = pad?.buildInk()
        if (ink == null) {
            status?.text = "ابتدا روی فضای ابر چیزی بنویسید."
            return
        }
        val r = recognizer
        if (r == null) {
            status?.text = "مدل هنوز آماده نیست؛ کمی صبر کنید."
            return
        }
        val currentPad = pad
        val context = RecognitionContext.builder()
            .setWritingArea(WritingArea(
                (currentPad?.width ?: 1).toFloat(),
                (currentPad?.height ?: 1).toFloat()
            ))
            .build()
        status?.text = "در حال تشخیص دست‌خط…"
        r.recognize(ink, context)
            .addOnSuccessListener { result ->
                candidates?.removeAllViews()
                val found = result.candidates.take(3)
                if (found.isEmpty()) {
                    status?.text = "متنی تشخیص داده نشد؛ واضح‌تر بنویسید."
                } else {
                    status?.text = "برای درج، روی یکی از متن‌های پیشنهادی بزنید."
                    found.forEach { item ->
                        val choice = TextView(this).apply {
                            text = item.text
                            textSize = 15f
                            gravity = Gravity.CENTER
                            setTextColor(Color.rgb(40, 76, 108))
                            background = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = dp(12).toFloat()
                                setColor(Color.argb(225, 255, 255, 255))
                                setStroke(dp(1), Color.rgb(174, 207, 232))
                            }
                            setPadding(dp(8), 0, dp(8), 0)
                            setOnClickListener {
                                currentInputConnection?.commitText(item.text, 1)
                                pad?.clearInk()
                                candidates?.removeAllViews()
                                status?.text = "ادامه دهید؛ برای فاصله دو بار ضربه بزنید."
                            }
                        }
                        candidates?.addView(choice, LinearLayout.LayoutParams(
                            0, dp(34), 1f
                        ).apply {
                            setMargins(dp(2), dp(1), dp(2), dp(1))
                        })
                    }
                }
            }
            .addOnFailureListener {
                status?.text = "تشخیص انجام نشد؛ دوباره تلاش کنید."
            }
    }

    private fun deleteOne() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun enter() {
        val connection = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action)
        } else {
            connection.commitText("\n", 1)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        recognizer?.close()
        recognizer = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class InkPad : View(this@CloudKeyboardService) {
        private var builder = Ink.builder()
        private var stroke: Ink.Stroke.Builder? = null
        private val paths = mutableListOf<Path>()
        private var activePath: Path? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(47, 104, 151)
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(137, 171, 198)
            textSize = dp(14).toFloat()
        }
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L
        private var lastTapAt = 0L
        private var lastTapX = 0f
        private var moved = false
        private var longDragging = false
        private var multiTouch = false
        private var nextDeleteAt = 0L
        private val longPress = Runnable {
            if (stroke != null && !moved && !multiTouch) {
                longDragging = true
                stroke = null
                activePath = null
                invalidate()
            }
        }

        init {
            background = CloudSurface()
            isClickable = true
            isFocusable = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (paths.isEmpty() && activePath == null) {
                canvas.drawText("با انگشت اینجا بنویسید", dp(18).toFloat(), height / 2f, hint)
            }
            paths.forEach { canvas.drawPath(it, paint) }
            activePath?.let { canvas.drawPath(it, paint) }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val now = event.eventTime
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downAt = now
                    moved = false
                    longDragging = false
                    multiTouch = false
                    nextDeleteAt = now + 550L
                    if (paths.isNotEmpty()) {
                        mainHandler.postDelayed(longPress, 550L)
                    }
                    stroke = Ink.Stroke.builder().also {
                        it.addPoint(Ink.Point.create(event.x, event.y, now))
                    }
                    activePath = Path().apply { moveTo(event.x, event.y) }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    multiTouch = true
                    mainHandler.removeCallbacks(longPress)
                    stroke = null
                    activePath = null
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (multiTouch) return true
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8)) moved = true
                    if (longDragging && dx > dp(18) && now >= nextDeleteAt) {
                        deleteOne()
                        nextDeleteAt = now + 200L
                    } else if (!longDragging) {
                        mainHandler.removeCallbacks(longPress)
                        stroke?.addPoint(Ink.Point.create(event.x, event.y, now))
                        activePath?.lineTo(event.x, event.y)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (multiTouch) {
                        enter()
                        multiTouch = false
                        stroke = null
                        activePath = null
                        mainHandler.removeCallbacks(longPress)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPress)
                    if (multiTouch) return true
                    if (longDragging) {
                        longDragging = false
                        return true
                    }
                    val duration = now - downAt
                    val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                    if (distance < dp(10) && duration < 260L) {
                        stroke = null
                        activePath = null
                        if (now - lastTapAt < 340L &&
                            kotlin.math.hypot(event.x - lastTapX, event.y - lastTapY) < dp(42)
                        ) {
                            currentInputConnection?.commitText(" ", 1)
                            lastTapAt = 0L
                            status?.text = "فاصله درج شد."
                        } else if (paths.isNotEmpty()) {
                            recognize()
                            lastTapAt = now
                            lastTapX = event.x
                        } else {
                            lastTapAt = now
                            lastTapX = event.x
                        }
                    } else {
                        stroke?.addPoint(Ink.Point.create(event.x, event.y, now))
                        stroke?.let { builder.addStroke(it.build()) }
                        activePath?.let { paths.add(it) }
                    }
                    stroke = null
                    activePath = null
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPress)
                    stroke = null
                    activePath = null
                    multiTouch = false
                    longDragging = false
                    invalidate()
                    return true
                }
            }
            return true
        }

        fun buildInk(): Ink? = if (paths.isEmpty()) null else builder.build()

        fun clearInk() {
            paths.clear()
            activePath = null
            stroke = null
            builder = Ink.builder()
            invalidate()
        }
    }

    private inner class CloudSurface : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 250, 253, 255)
            style = Paint.Style.FILL
        }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 168, 207, 235)
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val p = Path().apply {
                moveTo(w*.10f,h*.30f)
                cubicTo(w*.01f,h*.16f,w*.10f,h*.04f,w*.24f,h*.10f)
                cubicTo(w*.30f,h*.00f,w*.44f,h*.02f,w*.49f,h*.13f)
                cubicTo(w*.61f,h*.02f,w*.76f,h*.07f,w*.78f,h*.20f)
                cubicTo(w*.94f,h*.12f,w*1.01f,h*.27f,w*.91f,h*.40f)
                cubicTo(w*1.02f,h*.57f,w*.94f,h*.79f,w*.79f,h*.77f)
                cubicTo(w*.72f,h*.97f,w*.55f,h*.94f,w*.48f,h*.85f)
                cubicTo(w*.34f,h*.99f,w*.17f,h*.91f,w*.18f,h*.79f)
                cubicTo(w*.02f,h*.82f,w*-.01f,h*.60f,w*.10f,h*.50f)
                cubicTo(w*.02f,h*.42f,w*.03f,h*.34f,w*.10f,h*.30f)
                close()
            }
            canvas.drawPath(p, fill)
            canvas.drawPath(p, outline)
        }
        override fun setAlpha(alpha: Int) { fill.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { fill.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private inner class CloudBackground : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(239, 248, 255)
        }
        override fun draw(canvas: Canvas) {
            canvas.drawColor(paint.color)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
