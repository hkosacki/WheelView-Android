package com.lantouzi.wheelview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.math.MathUtils
import android.support.v4.view.GestureDetectorCompat
import android.support.v4.view.ViewCompat
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.widget.OverScroller
import com.lantouzi.wheelview.library.R

/**
 * Created by kyle on 15/11/9.
 */
class WheelView : View, GestureDetector.OnGestureListener {

    private lateinit var markPaint: Paint
    private lateinit var markTextPaint: TextPaint
    var selectedPosition = -1
        private set

    private var highlightColor: Int = 0
    private var markTextColor: Int = 0
    private var markColor: Int = 0
    private var fadeMarkColor: Int = 0

    private var internalHeight: Int = 0
    private var additionCenterMark: String? = null
    var onWheelItemSelectedListener: OnWheelItemSelectedListener? = null
    private var intervalFactor = DEFAULT_INTERVAL_FACTOR
    private var markRatio = DEFAULT_MARK_RATIO
    private var useDefaultSpacing = false

    private var markCount: Int = 0
    private var additionCenterMarkWidth: Float = 0.0f
    private val centerIndicatorPath = Path()
    private var cursorSize: Float = 0.0f
    private var viewScopeSize: Int = 0

    // scroll control args ---- start
    private lateinit var scroller: OverScroller
    private var maxOverScrollDistance: Float = 0.0f
    private lateinit var contentRectF: RectF
    private var fling = false
    private var centerTextSize: Float = 0.0f
    private var normalTextSize: Float = 0.0f
    private var topSpace: Float = 0.0f
    private var bottomSpace: Float = 0.0f
    private var intervalDis: Float = 0.0f
    private var centerMarkWidth: Float = 0.0f
    private var markWidth: Float = 0.0f
    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    // scroll control args ---- end

    private var lastSelectedIndex = -1

    var minSelectableIndex: Int = Int.MIN_VALUE
        set(index) {
            field = if (index > maxSelectableIndex) maxSelectableIndex else index
            val afterCenter = safeCenter(selectedPosition)
            if (afterCenter != selectedPosition) {
                selectIndex(afterCenter)
            }
        }

    var maxSelectableIndex: Int = Int.MAX_VALUE
        set(index) {
            field = if (maxSelectableIndex < minSelectableIndex) minSelectableIndex else index
            val afterCenter = safeCenter(selectedPosition)
            if (afterCenter != selectedPosition) {
                selectIndex(afterCenter)
            }
        }

    var items: MutableList<String> = mutableListOf()
        set(value) {
            field.clear()
            field.addAll(value)

            markCount = items.size
            if (markCount > 0) {
                minSelectableIndex = Math.max(minSelectableIndex, 0)
                maxSelectableIndex = Math.min(maxSelectableIndex, markCount - 1)
            }
            contentRectF.set(0f, 0f, (markCount - 1) * intervalDis, measuredHeight.toFloat())
            selectedPosition = Math.min(selectedPosition, markCount)
            calcIntervalDis()
            invalidate()
        }

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    @SuppressLint("CustomViewStyleable")
    private fun init(attrs: AttributeSet?) {
        val density = resources.displayMetrics.density
        centerMarkWidth = (density * 1.5f + 0.5f).toInt().toFloat()
        markWidth = density

        highlightColor = 0xFFF74C39.toInt()
        markTextColor = 0xFF666666.toInt()
        markColor = 0xFFEEEEEE.toInt()
        cursorSize = density * 18
        centerTextSize = density * 22
        normalTextSize = density * 18
        bottomSpace = density * 6

        attrs?.let{
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.WheelView)
            highlightColor = typedArray.getColor(R.styleable.WheelView_highlightColor, highlightColor)
            markTextColor = typedArray.getColor(R.styleable.WheelView_markTextColor, markTextColor)
            markColor = typedArray.getColor(R.styleable.WheelView_markColor, markColor)
            intervalFactor = typedArray.getFloat(R.styleable.WheelView_intervalFactor, intervalFactor)
            markRatio = typedArray.getFloat(R.styleable.WheelView_markRatio, markRatio)
            additionCenterMark = typedArray.getString(R.styleable.WheelView_additionalCenterMark)
            centerTextSize = typedArray.getDimension(R.styleable.WheelView_centerMarkTextSize, centerTextSize)
            normalTextSize = typedArray.getDimension(R.styleable.WheelView_markTextSize, normalTextSize)
            cursorSize = typedArray.getDimension(R.styleable.WheelView_cursorSize, cursorSize)
            useDefaultSpacing = typedArray.getBoolean(R.styleable.WheelView_useDefaultSpacing, useDefaultSpacing)
            typedArray.recycle()
        }

        fadeMarkColor = highlightColor and 0xAAFFFFFF.toInt()
        intervalFactor = Math.max(1f, intervalFactor)
        markRatio = Math.min(1f, markRatio)
        topSpace = cursorSize + density * 2

        markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = markColor
            strokeWidth = centerMarkWidth
        }

        markTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = highlightColor
            textSize = centerTextSize
        }

        calcIntervalDis()

        scroller = OverScroller(context)
        contentRectF = RectF()

        gestureDetectorCompat = GestureDetectorCompat(context, this)

        selectIndex(0)
    }

    /**
     * calculate interval distance between items
     */
    private fun calcIntervalDis() {
        markTextPaint.let {
            val defaultText = "888888"
            val temp = Rect()
            var max = 0
            if (!useDefaultSpacing && items.isNotEmpty()) {
                for (i in items) {
                    it.getTextBounds(i, 0, i.length, temp)
                    if (temp.width() > max) {
                        max = temp.width()
                    }
                }
            } else {
                it.getTextBounds(defaultText, 0, defaultText.length, temp)
                max = temp.width()
            }

            if (!TextUtils.isEmpty(additionCenterMark)) {
                it.textSize = normalTextSize
                it.getTextBounds(additionCenterMark, 0, additionCenterMark!!.length, temp)
                additionCenterMarkWidth = temp.width().toFloat()
                max += temp.width()
            }

            intervalDis = max * intervalFactor
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec))
    }

    private fun measureWidth(widthMeasureSpec: Int): Int {
        val measureMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val measureSize = View.MeasureSpec.getSize(widthMeasureSpec)
        var result = suggestedMinimumWidth
        when (measureMode) {
            View.MeasureSpec.AT_MOST, View.MeasureSpec.EXACTLY -> result = measureSize
            View.MeasureSpec.UNSPECIFIED -> Unit
            else -> Unit
        }
        return result
    }

    private fun measureHeight(heightMeasure: Int): Int {
        val measureMode = View.MeasureSpec.getMode(heightMeasure)
        val measureSize = View.MeasureSpec.getSize(heightMeasure)
        var result = (bottomSpace + topSpace * 2 + centerTextSize).toInt()
        when (measureMode) {
            View.MeasureSpec.EXACTLY -> result = Math.max(result, measureSize)
            View.MeasureSpec.AT_MOST -> result = Math.min(result, measureSize)
            View.MeasureSpec.UNSPECIFIED -> Unit
            else -> Unit
        }
        return result
    }

    fun fling(velocityX: Int, velocityY: Int) {
        scroller.fling(
            scrollX,
            scrollY,
            velocityX,
            velocityY,
            (-maxOverScrollDistance + minSelectableIndex * intervalDis).toInt(),
            (contentRectF.width() - maxOverScrollDistance - (markCount - 1 - maxSelectableIndex) * intervalDis).toInt(),
            0,
            0,
            maxOverScrollDistance.toInt(),
            0
        )
        ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            internalHeight = h
            maxOverScrollDistance = w / 2f
            contentRectF.set(0f, 0f, (markCount - 1) * intervalDis, h.toFloat())
            viewScopeSize = Math.ceil((maxOverScrollDistance / intervalDis).toDouble()).toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        centerIndicatorPath.reset()
        val sizeDiv2 = cursorSize / 2f
        val sizeDiv3 = cursorSize / 3f
        with(centerIndicatorPath) {
            moveTo(maxOverScrollDistance - sizeDiv2 + scrollX, 0f)
            rLineTo(0f, sizeDiv3)
            rLineTo(sizeDiv2, sizeDiv2)
            rLineTo(sizeDiv2, -sizeDiv2)
            rLineTo(0f, -sizeDiv3)
            close()
        }

        markPaint.color = highlightColor
        canvas.drawPath(centerIndicatorPath, markPaint)

        var start = selectedPosition - viewScopeSize
        var end = selectedPosition + viewScopeSize + 1

        start = Math.max(start, -viewScopeSize * 2)
        end = Math.min(end, markCount + viewScopeSize * 2)

        // extends both ends
        if (selectedPosition == maxSelectableIndex) {
            end += viewScopeSize
        } else if (selectedPosition == minSelectableIndex) {
            start -= viewScopeSize
        }

        var x = start * intervalDis

        val markHeight = internalHeight.toFloat() - bottomSpace - centerTextSize - topSpace
        // small scale Y offset
        var smallMarkShrinkY = markHeight * (1 - markRatio) / 2f
        smallMarkShrinkY = Math.min((markHeight - markWidth) / 2f, smallMarkShrinkY)

        for (i in start until end) {
            val tempDis = intervalDis / 5f
            // offset: Small mark offset Big mark
            for (offset in -2..2) {
                val ox = x + offset * tempDis

                if (i in 0..markCount && selectedPosition == i) {
                    val tempOffset = Math.abs(offset)
                    when (tempOffset) {
                        0 -> markPaint.color = highlightColor
                        1 -> markPaint.color = fadeMarkColor
                        else -> markPaint.color = markColor
                    }
                } else {
                    markPaint.color = markColor
                }

                if (offset == 0) {
                    // center mark
                    markPaint.strokeWidth = centerMarkWidth
                    canvas.drawLine(ox, topSpace, ox, topSpace + markHeight, markPaint)
                } else {
                    // other small mark
                    markPaint.strokeWidth = markWidth
                    canvas.drawLine(
                        ox,
                        topSpace + smallMarkShrinkY,
                        ox,
                        topSpace + markHeight - smallMarkShrinkY,
                        markPaint
                    )
                }
            }

            // mark text
            if (markCount > 0 && i >= 0 && i < markCount) {
                val temp = items[i]
                if (selectedPosition == i) {
                    markTextPaint.color = highlightColor
                    markTextPaint.textSize = centerTextSize
                    if (!TextUtils.isEmpty(additionCenterMark)) {
                        val off = additionCenterMarkWidth / 2f
                        val tsize = markTextPaint.measureText(temp, 0, temp.length)
                        canvas.drawText(temp, 0, temp.length, x - off, internalHeight - bottomSpace, markTextPaint)
                        markTextPaint.textSize = normalTextSize
                        canvas.drawText(additionCenterMark!!, x + tsize / 2f, internalHeight - bottomSpace, markTextPaint)
                    } else {
                        canvas.drawText(temp, 0, temp.length, x, internalHeight - bottomSpace, markTextPaint)
                    }
                } else {
                    markTextPaint.color = markTextColor
                    markTextPaint.textSize = normalTextSize
                    canvas.drawText(temp, 0, temp.length, x, internalHeight - bottomSpace, markTextPaint)
                }
            }

            x += intervalDis
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty() || !isEnabled) {
            return false
        }
        var ret = gestureDetectorCompat.onTouchEvent(event)
        if (!fling && MotionEvent.ACTION_UP == event.action) {
            autoSettle()
            ret = true
        }
        return ret || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            refreshCenter()
            invalidate()
        } else {
            if (fling) {
                fling = false
                autoSettle()
            }
        }
    }

    fun setAdditionCenterMark(additionCenterMark: String) {
        this.additionCenterMark = additionCenterMark
        calcIntervalDis()
        invalidate()
    }

    private fun autoSettle() {
        val sx = scrollX
        val dx = selectedPosition * intervalDis - sx.toFloat() - maxOverScrollDistance
        scroller.startScroll(sx, 0, dx.toInt(), 0)
        postInvalidate()
        if (lastSelectedIndex != selectedPosition) {
            lastSelectedIndex = selectedPosition
            onWheelItemSelectedListener?.onWheelItemSelected(this, selectedPosition)
        }
    }

    /**
     * limit center index in bounds.
     *
     * @param position
     * @return
     */
    private fun safeCenter(position: Int): Int = MathUtils.clamp(position, minSelectableIndex, maxSelectableIndex)

    private fun refreshCenter(offsetX: Int = scrollX) {
        val offset = (offsetX + maxOverScrollDistance).toInt()
        var tempIndex = Math.round(offset / intervalDis)
        tempIndex = safeCenter(tempIndex)
        if (selectedPosition == tempIndex) {
            return
        }
        selectedPosition = tempIndex
        onWheelItemSelectedListener?.onWheelItemChanged(this, selectedPosition)
    }

    fun selectIndex(index: Int) {
        selectedPosition = index
        post {
            scrollTo((selectedPosition * intervalDis - maxOverScrollDistance).toInt(), 0)
            invalidate()
            refreshCenter()
        }
    }

    fun smoothSelectIndex(index: Int) {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        val deltaIndex = index - selectedPosition
        scroller.startScroll(scrollX, 0, (deltaIndex * intervalDis).toInt(), 0)
        invalidate()
    }

    override fun onDown(e: MotionEvent): Boolean {
        if (!scroller.isFinished) {
            scroller.forceFinished(false)
        }
        fling = false
        if (null != parent) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        playSoundEffect(SoundEffectConstants.CLICK)
        refreshCenter((scrollX + e.x - maxOverScrollDistance).toInt())
        autoSettle()
        return true
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        var dis = distanceX
        val scrollX = scrollX.toFloat()
        when {
            scrollX < minSelectableIndex * intervalDis - 2 * maxOverScrollDistance -> dis = 0f
            scrollX < minSelectableIndex * intervalDis - maxOverScrollDistance -> dis = distanceX / 4f
            scrollX > contentRectF.width() - (markCount - maxSelectableIndex - 1) * intervalDis -> dis = 0f
            scrollX > contentRectF.width() - (markCount - maxSelectableIndex - 1) * intervalDis - maxOverScrollDistance -> dis =
                distanceX / 4f
        }
        scrollBy(dis.toInt(), 0)
        refreshCenter()
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val scrollX = scrollX.toFloat()
        return if (
            scrollX < -maxOverScrollDistance + minSelectableIndex * intervalDis ||
            scrollX > contentRectF.width() - maxOverScrollDistance - (markCount - 1 - maxSelectableIndex) * intervalDis
        ) {
            false
        } else {
            fling = true
            fling((-velocityX).toInt(), 0)
            true
        }
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            index = selectedPosition
            min = minSelectableIndex
            max = maxSelectableIndex
        }
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        (state as SavedState).let {
            super.onRestoreInstanceState(it.superState)
            minSelectableIndex = it.min
            maxSelectableIndex = it.max
            selectIndex(it.index)
        }
        requestLayout()
    }

    interface OnWheelItemSelectedListener {
        fun onWheelItemChanged(wheelView: WheelView, position: Int)

        fun onWheelItemSelected(wheelView: WheelView, position: Int)
    }

    internal class SavedState : View.BaseSavedState {
        var index: Int = 0
        var min: Int = 0
        var max: Int = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(incoming: Parcel) : super(incoming) {
            with(incoming) {
                index = readInt()
                min = readInt()
                max = readInt()
            }
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            with(out) {
                writeInt(index)
                writeInt(min)
                writeInt(max)
            }
        }

        override fun toString(): String {
            return ("WheelView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " index=" + index + " min=" + min + " max=" + max + "}")
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(incoming: Parcel): SavedState {
                    return SavedState(incoming)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_FACTOR = 1.2f
        const val DEFAULT_MARK_RATIO = 0.7f
    }
}
