package com.eventuath.gexrdp_kvm.ui.session

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.eventuath.gexrdp_kvm.core.protocol.FrameHeader
import com.eventuath.gexrdp_kvm.core.protocol.GexProtocol
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders decoded RGBA frames and turns touch/mouse input into the GEX input
 * model. Deliberately a plain View (not SurfaceView) so the first version can
 * post whole frames without an NDK/EGL pipeline; see docs/02 ADR-001.
 */
@SuppressLint("ClickableViewAccessibility")
class RemoteCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface InputSink {
        fun onRelative(dx: Int, dy: Int)
        fun onButton(button: Int, down: Boolean)
        fun onWheel(vertical: Int, horizontal: Int)
        fun onKey(keyCode: Int, down: Boolean)
        fun onReleasePointer()
    }

    var sink: InputSink? = null

    private var bitmap: Bitmap? = null
    private var buffer: ByteArray? = null
    private var contentWidth = 0
    private var contentHeight = 0

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val destination = RectF()

    private val touchSlop = 12f
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPressFired = false
    private var lastTapAt = 0L
    private var twoFingerY = 0f

    private var mouseX = 0f
    private var mouseY = 0f

    private val longPress = Runnable {
        if (!moved && sink != null) {
            longPressFired = true
            sink?.onButton(GexProtocol.BUTTON_RIGHT, true)
            sink?.onButton(GexProtocol.BUTTON_RIGHT, false)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    fun configure(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == contentWidth && height == contentHeight && bitmap != null) return
        contentWidth = width
        contentHeight = height
        buffer = ByteArray(width * height * 4)
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        invalidate()
    }

    fun applyFrame(header: FrameHeader, payload: ByteArray) {
        val target = buffer ?: return
        val image = bitmap ?: return
        if (header.width <= 0 || header.height <= 0 || header.payloadLen <= 0) return

        if (header.type == GexProtocol.FRAME_FULL) {
            if (header.stride == contentWidth * 4 &&
                payload.size >= contentWidth * contentHeight * 4
            ) {
                System.arraycopy(payload, 0, target, 0, target.size)
            } else {
                copyRows(header, payload, target, 0, 0)
            }
        } else {
            copyRows(header, payload, target, header.x, header.y)
        }

        image.copyPixelsFromBuffer(ByteBuffer.wrap(target))
        invalidate()
    }

    private fun copyRows(header: FrameHeader, payload: ByteArray, target: ByteArray, originX: Int, originY: Int) {
        val rowBytes = header.width * 4
        val sourceStride = if (header.stride > 0) header.stride else rowBytes
        val targetStride = contentWidth * 4
        for (row in 0 until header.height) {
            val source = row * sourceStride
            val destinationOffset = (originY + row) * targetStride + originX * 4
            val length = min(rowBytes, payload.size - source)
            val room = target.size - destinationOffset
            if (source < 0 || length <= 0 || room <= 0) continue
            System.arraycopy(payload, source, target, destinationOffset, min(min(length, room), rowBytes))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val image = bitmap ?: return
        if (contentWidth <= 0 || contentHeight <= 0) return
        val scale = min(width.toFloat() / contentWidth, height.toFloat() / contentHeight)
        val drawWidth = contentWidth * scale
        val drawHeight = contentHeight * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        destination.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(image, null, destination, paint)
    }

    // ------------------------------------------------------------------ //
    // Touch: single finger = relative mouse, two fingers = wheel,
    // three fingers = release pointer capture, tap/long press = buttons.
    // ------------------------------------------------------------------ //

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                downX = event.x
                downY = event.y
                lastX = downX
                lastY = downY
                moved = false
                longPressFired = false
                postDelayed(longPress, 500)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPress)
                if (event.pointerCount == 3) {
                    sink?.onReleasePointer()
                    return true
                }
                if (event.pointerCount == 2) {
                    twoFingerY = averageY(event)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    twoFingerY = averageY(event, event.actionIndex)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val y = averageY(event)
                    val dy = y - twoFingerY
                    if (abs(dy) >= 8f) {
                        sink?.onWheel(if (dy > 0) -1 else 1, 0)
                        twoFingerY = y
                    }
                    return true
                }
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    moved = true
                    removeCallbacks(longPress)
                }
                if (moved) {
                    sink?.onRelative(dx.roundToInt(), dy.roundToInt())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (!moved && !longPressFired) {
                    val now = SystemClock.uptimeMillis()
                    val doubleTap = now - lastTapAt < 300
                    lastTapAt = now
                    sink?.onButton(GexProtocol.BUTTON_LEFT, true)
                    sink?.onButton(GexProtocol.BUTTON_LEFT, false)
                    if (doubleTap) {
                        sink?.onButton(GexProtocol.BUTTON_LEFT, true)
                        sink?.onButton(GexProtocol.BUTTON_LEFT, false)
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun averageY(event: MotionEvent, skipIndex: Int = -1): Float {
        var sum = 0f
        var count = 0
        for (index in 0 until event.pointerCount) {
            if (index == skipIndex) continue
            sum += event.getY(index)
            count += 1
        }
        return if (count == 0) 0f else sum / count
    }

    // ------------------------------------------------------------------ //
    // Mouse / touchpad / pointer capture
    // ------------------------------------------------------------------ //

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val mouseSource = event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
        val touchpadSource = event.source and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
        if (!mouseSource && !touchpadSource) return super.onGenericMotionEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                if (relativeX != 0f || relativeY != 0f) {
                    sink?.onRelative(relativeX.roundToInt(), relativeY.roundToInt())
                } else {
                    if (mouseX != 0f || mouseY != 0f) {
                        sink?.onRelative((event.x - mouseX).roundToInt(), (event.y - mouseY).roundToInt())
                    }
                    mouseX = event.x
                    mouseY = event.y
                }
                return true
            }

            MotionEvent.ACTION_BUTTON_PRESS -> {
                sink?.onButton(buttonOf(event.actionButton), true)
                return true
            }

            MotionEvent.ACTION_BUTTON_RELEASE -> {
                sink?.onButton(buttonOf(event.actionButton), false)
                return true
            }

            MotionEvent.ACTION_SCROLL -> {
                val vertical = -event.getAxisValue(MotionEvent.AXIS_VSCROLL).roundToInt()
                val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL).roundToInt()
                if (vertical != 0 || horizontal != 0) sink?.onWheel(vertical, horizontal)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun buttonOf(button: Int): Int = when (button) {
        MotionEvent.BUTTON_PRIMARY -> GexProtocol.BUTTON_LEFT
        MotionEvent.BUTTON_TERTIARY -> GexProtocol.BUTTON_MIDDLE
        MotionEvent.BUTTON_SECONDARY -> GexProtocol.BUTTON_RIGHT
        MotionEvent.BUTTON_BACK -> GexProtocol.BUTTON_SIDE
        MotionEvent.BUTTON_FORWARD -> GexProtocol.BUTTON_EXTRA
        else -> GexProtocol.BUTTON_LEFT
    }

    fun startPointerCapture() {
        if (!hasPointerCapture()) requestPointerCapture()
    }

    fun stopPointerCapture() {
        if (hasPointerCapture()) releasePointerCapture()
    }

    // ------------------------------------------------------------------ //
    // Hardware keyboard
    // ------------------------------------------------------------------ //

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) sink?.onKey(keyCode, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyUp(keyCode, event)
        sink?.onKey(keyCode, false)
        return true
    }
}
