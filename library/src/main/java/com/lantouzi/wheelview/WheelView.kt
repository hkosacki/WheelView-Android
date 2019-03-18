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

    private var mHighlightColor: Int = 0
    private var mMarkTextColor: Int = 0
    private var mMarkColor: Int = 0
    private var mFadeMarkColor: Int = 0

    private var mHeight: Int = 0
    private var mAdditionCenterMark: String? = null
    var onWheelItemSelectedListener: OnWheelItemSelectedListener? = null
    private var mIntervalFactor = DEFAULT_INTERVAL_FACTOR
    private var mMarkRatio = DEFAULT_MARK_RATIO

    private var mMarkCount: Int = 0
    private var mAdditionCenterMarkWidth: Float = 0.0f
    private val mCenterIndicatorPath = Path()
    private var mCursorSize: Float = 0.0f
    private var mViewScopeSize: Int = 0

    // scroll control args ---- start
    private lateinit var scroller: OverScroller
    private var mMaxOverScrollDistance: Float = 0.0f
    private lateinit var contentRectF: RectF
    private var fling = false
    private var mCenterTextSize: Float = 0.0f
    private var mNormalTextSize: Float = 0.0f
    private var mTopSpace: Float = 0.0f
    private var mBottomSpace: Float = 0.0f
    private var mIntervalDis: Float = 0.0f
    private var mCenterMarkWidth: Float = 0.0f
    private var mMarkWidth: Float = 0.0f
    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    // scroll control args ---- end

    private var mLastSelectedIndex = -1

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

            mMarkCount = items.size
            if (mMarkCount > 0) {
                minSelectableIndex = Math.max(minSelectableIndex, 0)
                maxSelectableIndex = Math.min(maxSelectableIndex, mMarkCount - 1)
            }
            contentRectF.set(0f, 0f, (mMarkCount - 1) * mIntervalDis, measuredHeight.toFloat())
            selectedPosition = Math.min(selectedPosition, mMarkCount)
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
        mCenterMarkWidth = (density * 1.5f + 0.5f).toInt().toFloat()
        mMarkWidth = density

        mHighlightColor = -0x8b3c7
        mMarkTextColor = -0x99999a
        mMarkColor = -0x111112
        mCursorSize = density * 18
        mCenterTextSize = density * 22
        mNormalTextSize = density * 18
        mBottomSpace = density * 6

        val typedArray = if (attrs == null) null else context.obtainStyledAttributes(attrs, R.styleable.WheelView)
        typedArray?.let {
            mHighlightColor = typedArray.getColor(R.styleable.lwvWheelView_lwvHighlightColor, mHighlightColor)
            mMarkTextColor = typedArray.getColor(R.styleable.lwvWheelView_lwvMarkTextColor, mMarkTextColor)
            mMarkColor = typedArray.getColor(R.styleable.lwvWheelView_lwvMarkColor, mMarkColor)
            mIntervalFactor = typedArray.getFloat(R.styleable.lwvWheelView_lwvIntervalFactor, mIntervalFactor)
            mMarkRatio = typedArray.getFloat(R.styleable.lwvWheelView_lwvMarkRatio, mMarkRatio)
            mAdditionCenterMark = typedArray.getString(R.styleable.lwvWheelView_lwvAdditionalCenterMark)
            mCenterTextSize = typedArray.getDimension(R.styleable.lwvWheelView_lwvCenterMarkTextSize, mCenterTextSize)
            mNormalTextSize = typedArray.getDimension(R.styleable.lwvWheelView_lwvMarkTextSize, mNormalTextSize)
            mCursorSize = typedArray.getDimension(R.styleable.lwvWheelView_lwvCursorSize, mCursorSize)
            typedArray.recycle()
        }
        mFadeMarkColor = mHighlightColor and -0x55000001
        mIntervalFactor = Math.max(1f, mIntervalFactor)
        mMarkRatio = Math.min(1f, mMarkRatio)
        mTopSpace = mCursorSize + density * 2

        markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mMarkColor
            strokeWidth = mCenterMarkWidth
        }

        markTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = mHighlightColor
            textSize = mCenterTextSize
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
            if (items.isNotEmpty()) {
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

            if (!TextUtils.isEmpty(mAdditionCenterMark)) {
                it.textSize = mNormalTextSize
                it.getTextBounds(mAdditionCenterMark, 0, mAdditionCenterMark!!.length, temp)
                mAdditionCenterMarkWidth = temp.width().toFloat()
                max += temp.width()
            }

            mIntervalDis = max * mIntervalFactor
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
            else -> Unit
        }
        return result
    }

    private fun measureHeight(heightMeasure: Int): Int {
        val measureMode = View.MeasureSpec.getMode(heightMeasure)
        val measureSize = View.MeasureSpec.getSize(heightMeasure)
        var result = (mBottomSpace + mTopSpace * 2 + mCenterTextSize).toInt()
        when (measureMode) {
            View.MeasureSpec.EXACTLY -> result = Math.max(result, measureSize)
            View.MeasureSpec.AT_MOST -> result = Math.min(result, measureSize)
            else -> {
            }
        }
        return result
    }

    fun fling(velocityX: Int, velocityY: Int) {
        scroller.fling(
            scrollX,
            scrollY,
            velocityX,
            velocityY,
            (-mMaxOverScrollDistance + minSelectableIndex * mIntervalDis).toInt(),
            (contentRectF.width() - mMaxOverScrollDistance - (mMarkCount - 1 - maxSelectableIndex) * mIntervalDis).toInt(),
            0,
            0,
            mMaxOverScrollDistance.toInt(),
            0
        )
        ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            mHeight = h
            mMaxOverScrollDistance = w / 2f
            contentRectF.set(0f, 0f, (mMarkCount - 1) * mIntervalDis, h.toFloat())
            mViewScopeSize = Math.ceil((mMaxOverScrollDistance / mIntervalDis).toDouble()).toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        mCenterIndicatorPath.reset()
        val sizeDiv2 = mCursorSize / 2f
        val sizeDiv3 = mCursorSize / 3f
        with(mCenterIndicatorPath) {
            moveTo(mMaxOverScrollDistance - sizeDiv2 + scrollX, 0f)
            rLineTo(0f, sizeDiv3)
            rLineTo(sizeDiv2, sizeDiv2)
            rLineTo(sizeDiv2, -sizeDiv2)
            rLineTo(0f, -sizeDiv3)
            close()
        }

        markPaint.color = mHighlightColor
        canvas.drawPath(mCenterIndicatorPath, markPaint)

        var start = selectedPosition - mViewScopeSize
        var end = selectedPosition + mViewScopeSize + 1

        start = Math.max(start, -mViewScopeSize * 2)
        end = Math.min(end, mMarkCount + mViewScopeSize * 2)

        // extends both ends
        if (selectedPosition == maxSelectableIndex) {
            end += mViewScopeSize
        } else if (selectedPosition == minSelectableIndex) {
            start -= mViewScopeSize
        }

        var x = start * mIntervalDis

        val markHeight = mHeight.toFloat() - mBottomSpace - mCenterTextSize - mTopSpace
        // small scale Y offset
        var smallMarkShrinkY = markHeight * (1 - mMarkRatio) / 2f
        smallMarkShrinkY = Math.min((markHeight - mMarkWidth) / 2f, smallMarkShrinkY)

        for (i in start until end) {
            val tempDis = mIntervalDis / 5f
            // offset: Small mark offset Big mark
            for (offset in -2..2) {
                val ox = x + offset * tempDis

                if (i in 0..mMarkCount && selectedPosition == i) {
                    val tempOffset = Math.abs(offset)
                    if (tempOffset == 0) {
                        markPaint.color = mHighlightColor
                    } else if (tempOffset == 1) {
                        markPaint.color = mFadeMarkColor
                    } else {
                        markPaint.color = mMarkColor
                    }
                } else {
                    markPaint.color = mMarkColor
                }

                if (offset == 0) {
                    // center mark
                    markPaint.strokeWidth = mCenterMarkWidth
                    canvas.drawLine(ox, mTopSpace, ox, mTopSpace + markHeight, markPaint)
                } else {
                    // other small mark
                    markPaint.strokeWidth = mMarkWidth
                    canvas.drawLine(
                        ox,
                        mTopSpace + smallMarkShrinkY,
                        ox,
                        mTopSpace + markHeight - smallMarkShrinkY,
                        markPaint
                    )
                }
            }

            // mark text
            if (mMarkCount > 0 && i >= 0 && i < mMarkCount) {
                val temp = items[i]
                if (selectedPosition == i) {
                    markTextPaint.color = mHighlightColor
                    markTextPaint.textSize = mCenterTextSize
                    if (!TextUtils.isEmpty(mAdditionCenterMark)) {
                        val off = mAdditionCenterMarkWidth / 2f
                        val tsize = markTextPaint.measureText(temp, 0, temp.length)
                        canvas.drawText(temp, 0, temp.length, x - off, mHeight - mBottomSpace, markTextPaint)
                        markTextPaint.textSize = mNormalTextSize
                        canvas.drawText(mAdditionCenterMark!!, x + tsize / 2f, mHeight - mBottomSpace, markTextPaint)
                    } else {
                        canvas.drawText(temp, 0, temp.length, x, mHeight - mBottomSpace, markTextPaint)
                    }
                } else {
                    markTextPaint.color = mMarkTextColor
                    markTextPaint.textSize = mNormalTextSize
                    canvas.drawText(temp, 0, temp.length, x, mHeight - mBottomSpace, markTextPaint)
                }
            }

            x += mIntervalDis
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
        mAdditionCenterMark = additionCenterMark
        calcIntervalDis()
        invalidate()
    }

    private fun autoSettle() {
        val sx = scrollX
        val dx = selectedPosition * mIntervalDis - sx.toFloat() - mMaxOverScrollDistance
        scroller.startScroll(sx, 0, dx.toInt(), 0)
        postInvalidate()
        if (mLastSelectedIndex != selectedPosition) {
            mLastSelectedIndex = selectedPosition
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
        val offset = (offsetX + mMaxOverScrollDistance).toInt()
        var tempIndex = Math.round(offset / mIntervalDis)
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
            scrollTo((selectedPosition * mIntervalDis - mMaxOverScrollDistance).toInt(), 0)
            invalidate()
            refreshCenter()
        }
    }

    fun smoothSelectIndex(index: Int) {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        val deltaIndex = index - selectedPosition
        scroller.startScroll(scrollX, 0, (deltaIndex * mIntervalDis).toInt(), 0)
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
        refreshCenter((scrollX + e.x - mMaxOverScrollDistance).toInt())
        autoSettle()
        return true
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        var dis = distanceX
        val scrollX = scrollX.toFloat()
        when {
            scrollX < minSelectableIndex * mIntervalDis - 2 * mMaxOverScrollDistance -> dis = 0f
            scrollX < minSelectableIndex * mIntervalDis - mMaxOverScrollDistance -> dis = distanceX / 4f
            scrollX > contentRectF.width() - (mMarkCount - maxSelectableIndex - 1) * mIntervalDis -> dis = 0f
            scrollX > contentRectF.width() - (mMarkCount - maxSelectableIndex - 1) * mIntervalDis - mMaxOverScrollDistance -> dis =
                distanceX / 4f
        }
        scrollBy(dis.toInt(), 0)
        refreshCenter()
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val scrollX = scrollX.toFloat()
        return if (
            scrollX < -mMaxOverScrollDistance + minSelectableIndex * mIntervalDis ||
            scrollX > contentRectF.width() - mMaxOverScrollDistance - (mMarkCount - 1 - maxSelectableIndex) * mIntervalDis
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
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
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
