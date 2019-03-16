package com.lantouzi.wheelview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
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
import java.util.ArrayList

/**
 * Created by kyle on 15/11/9.
 */
class WheelView : View, GestureDetector.OnGestureListener {

    private var mMarkPaint: Paint? = null
    private var mMarkTextPaint: TextPaint? = null
    var selectedPosition = -1
        private set

    private var mHighlightColor: Int = 0
    private var mMarkTextColor: Int = 0
    private var mMarkColor: Int = 0
    private var mFadeMarkColor: Int = 0

    private var mHeight: Int = 0
    private var mItems: MutableList<String>? = null
    private var mAdditionCenterMark: String? = null
    public var onWheelItemSelectedListener: OnWheelItemSelectedListener? = null
    private var mIntervalFactor = DEFAULT_INTERVAL_FACTOR
    private var mMarkRatio = DEFAULT_MARK_RATIO

    private var mMarkCount: Int = 0
    private var mAdditionCenterMarkWidth: Float = 0.0f
    private val mCenterIndicatorPath = Path()
    private var mCursorSize: Float = 0.0f
    private var mViewScopeSize: Int = 0

    // scroll control args ---- start
    private var mScroller: OverScroller? = null
    private var mMaxOverScrollDistance: Float = 0.0f
    private var mContentRectF: RectF? = null
    private var mFling = false
    private var mCenterTextSize: Float = 0.0f
    private var mNormalTextSize: Float = 0.0f
    private var mTopSpace: Float = 0.0f
    private var mBottomSpace: Float = 0.0f
    private var mIntervalDis: Float = 0.0f
    private var mCenterMarkWidth: Float = 0.0f
    private var mMarkWidth: Float = 0.0f
    private var mGestureDetectorCompat: GestureDetectorCompat? = null
    // scroll control args ---- end

    private var mLastSelectedIndex = -1
    private var _minSelectableIndex = Integer.MIN_VALUE
    private var _maxSelectableIndex = Integer.MAX_VALUE

    var minSelectableIndex: Int
        get() = _minSelectableIndex
        set(index) {
            _minSelectableIndex = if (index > _maxSelectableIndex) maxSelectableIndex else index
            val afterCenter = safeCenter(selectedPosition)
            if (afterCenter != selectedPosition) {
                selectIndex(afterCenter)
            }
        }

    var maxSelectableIndex: Int
        get() = _maxSelectableIndex
        set(index) {
            _maxSelectableIndex = if (maxSelectableIndex < _minSelectableIndex) _minSelectableIndex else index
            val afterCenter = safeCenter(selectedPosition)
            if (afterCenter != selectedPosition) {
                selectIndex(afterCenter)
            }
        }

    var items: List<String>?
        get() = mItems
        set(value) {
            if (mItems == null) {
                mItems = ArrayList()
            } else {
                mItems!!.clear()
            }
            value?.let {
                mItems!!.addAll(it)
            }
            mMarkCount = if (null == mItems) 0 else mItems!!.size
            if (mMarkCount > 0) {
                _minSelectableIndex = Math.max(_minSelectableIndex, 0)
                _maxSelectableIndex = Math.min(_maxSelectableIndex, mMarkCount - 1)
            }
            mContentRectF!!.set(0f, 0f, (mMarkCount - 1) * mIntervalDis, measuredHeight.toFloat())
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

    protected fun init(attrs: AttributeSet?) {
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

        val ta = if (attrs == null) null else context.obtainStyledAttributes(attrs, R.styleable.lwvWheelView)
        if (ta != null) {
            mHighlightColor = ta.getColor(R.styleable.lwvWheelView_lwvHighlightColor, mHighlightColor)
            mMarkTextColor = ta.getColor(R.styleable.lwvWheelView_lwvMarkTextColor, mMarkTextColor)
            mMarkColor = ta.getColor(R.styleable.lwvWheelView_lwvMarkColor, mMarkColor)
            mIntervalFactor = ta.getFloat(R.styleable.lwvWheelView_lwvIntervalFactor, mIntervalFactor)
            mMarkRatio = ta.getFloat(R.styleable.lwvWheelView_lwvMarkRatio, mMarkRatio)
            mAdditionCenterMark = ta.getString(R.styleable.lwvWheelView_lwvAdditionalCenterMark)
            mCenterTextSize = ta.getDimension(R.styleable.lwvWheelView_lwvCenterMarkTextSize, mCenterTextSize)
            mNormalTextSize = ta.getDimension(R.styleable.lwvWheelView_lwvMarkTextSize, mNormalTextSize)
            mCursorSize = ta.getDimension(R.styleable.lwvWheelView_lwvCursorSize, mCursorSize)
        }
        mFadeMarkColor = mHighlightColor and -0x55000001
        mIntervalFactor = Math.max(1f, mIntervalFactor)
        mMarkRatio = Math.min(1f, mMarkRatio)
        mTopSpace = mCursorSize + density * 2

        mMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mMarkColor
            strokeWidth = mCenterMarkWidth
        }

        mMarkTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            color = mHighlightColor
            textSize = mCenterTextSize
        }

        calcIntervalDis()

        mScroller = OverScroller(context)
        mContentRectF = RectF()

        mGestureDetectorCompat = GestureDetectorCompat(context, this)

        selectIndex(0)
    }

    /**
     * calculate interval distance between items
     */
    private fun calcIntervalDis() {
        if (mMarkTextPaint == null) {
            return
        }
        val defaultText = "888888"
        val temp = Rect()
        var max = 0
        if (mItems != null && mItems!!.size > 0) {
            for (i in mItems!!) {
                mMarkTextPaint!!.getTextBounds(i, 0, i.length, temp)
                if (temp.width() > max) {
                    max = temp.width()
                }
            }
        } else {
            mMarkTextPaint!!.getTextBounds(defaultText, 0, defaultText.length, temp)
            max = temp.width()
        }

        if (!TextUtils.isEmpty(mAdditionCenterMark)) {
            mMarkTextPaint!!.textSize = mNormalTextSize
            mMarkTextPaint!!.getTextBounds(mAdditionCenterMark, 0, mAdditionCenterMark!!.length, temp)
            mAdditionCenterMarkWidth = temp.width().toFloat()
            max += temp.width()
        }

        mIntervalDis = max * mIntervalFactor
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
        mScroller!!.fling(
            scrollX,
            scrollY,
            velocityX,
            velocityY,
            (-mMaxOverScrollDistance + _minSelectableIndex * mIntervalDis).toInt(),
            (mContentRectF!!.width() - mMaxOverScrollDistance - (mMarkCount - 1 - _maxSelectableIndex) * mIntervalDis).toInt(),
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
            mContentRectF!!.set(0f, 0f, (mMarkCount - 1) * mIntervalDis, h.toFloat())
            mViewScopeSize = Math.ceil((mMaxOverScrollDistance / mIntervalDis).toDouble()).toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        mCenterIndicatorPath.reset()
        val sizeDiv2 = mCursorSize / 2f
        val sizeDiv3 = mCursorSize / 3f
        mCenterIndicatorPath.moveTo(mMaxOverScrollDistance - sizeDiv2 + scrollX, 0f)
        mCenterIndicatorPath.rLineTo(0f, sizeDiv3)
        mCenterIndicatorPath.rLineTo(sizeDiv2, sizeDiv2)
        mCenterIndicatorPath.rLineTo(sizeDiv2, -sizeDiv2)
        mCenterIndicatorPath.rLineTo(0f, -sizeDiv3)
        mCenterIndicatorPath.close()

        mMarkPaint!!.color = mHighlightColor
        canvas.drawPath(mCenterIndicatorPath, mMarkPaint!!)

        var start = selectedPosition - mViewScopeSize
        var end = selectedPosition + mViewScopeSize + 1

        start = Math.max(start, -mViewScopeSize * 2)
        end = Math.min(end, mMarkCount + mViewScopeSize * 2)

        // extends both ends
        if (selectedPosition == _maxSelectableIndex) {
            end += mViewScopeSize
        } else if (selectedPosition == _minSelectableIndex) {
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

                if (i >= 0 && i <= mMarkCount && selectedPosition == i) {
                    val tempOffset = Math.abs(offset)
                    if (tempOffset == 0) {
                        mMarkPaint!!.color = mHighlightColor
                    } else if (tempOffset == 1) {
                        mMarkPaint!!.color = mFadeMarkColor
                    } else {
                        mMarkPaint!!.color = mMarkColor
                    }
                } else {
                    mMarkPaint!!.color = mMarkColor
                }

                if (offset == 0) {
                    // center mark
                    mMarkPaint!!.strokeWidth = mCenterMarkWidth
                    canvas.drawLine(ox, mTopSpace, ox, mTopSpace + markHeight, mMarkPaint!!)
                } else {
                    // other small mark
                    mMarkPaint!!.strokeWidth = mMarkWidth
                    canvas.drawLine(
                        ox,
                        mTopSpace + smallMarkShrinkY,
                        ox,
                        mTopSpace + markHeight - smallMarkShrinkY,
                        mMarkPaint!!
                    )
                }
            }

            // mark text
            if (mMarkCount > 0 && i >= 0 && i < mMarkCount) {
                val temp = mItems!![i]
                if (selectedPosition == i) {
                    mMarkTextPaint!!.color = mHighlightColor
                    mMarkTextPaint!!.textSize = mCenterTextSize
                    if (!TextUtils.isEmpty(mAdditionCenterMark)) {
                        val off = mAdditionCenterMarkWidth / 2f
                        val tsize = mMarkTextPaint!!.measureText(temp, 0, temp.length)
                        canvas.drawText(temp, 0, temp.length, x - off, mHeight - mBottomSpace, mMarkTextPaint!!)
                        mMarkTextPaint!!.textSize = mNormalTextSize
                        canvas.drawText(mAdditionCenterMark!!, x + tsize / 2f, mHeight - mBottomSpace, mMarkTextPaint!!)
                    } else {
                        canvas.drawText(temp, 0, temp.length, x, mHeight - mBottomSpace, mMarkTextPaint!!)
                    }
                } else {
                    mMarkTextPaint!!.color = mMarkTextColor
                    mMarkTextPaint!!.textSize = mNormalTextSize
                    canvas.drawText(temp, 0, temp.length, x, mHeight - mBottomSpace, mMarkTextPaint!!)
                }
            }

            x += mIntervalDis
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mItems == null || mItems!!.size == 0 || !isEnabled) {
            return false
        }
        var ret = mGestureDetectorCompat!!.onTouchEvent(event)
        if (!mFling && MotionEvent.ACTION_UP == event.action) {
            autoSettle()
            ret = true
        }
        return ret || super.onTouchEvent(event)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (mScroller!!.computeScrollOffset()) {
            scrollTo(mScroller!!.currX, mScroller!!.currY)
            refreshCenter()
            invalidate()
        } else {
            if (mFling) {
                mFling = false
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
        mScroller!!.startScroll(sx, 0, dx.toInt(), 0)
        postInvalidate()
        if (mLastSelectedIndex != selectedPosition) {
            mLastSelectedIndex = selectedPosition
            onWheelItemSelectedListener?.onWheelItemSelected(this, selectedPosition)
        }
    }

    /**
     * limit center index in bounds.
     *
     * @param center
     * @return
     */
    private fun safeCenter(center: Int): Int {
        var center = center
        if (center < _minSelectableIndex) {
            center = _minSelectableIndex
        } else if (center > _maxSelectableIndex) {
            center = _maxSelectableIndex
        }
        return center
    }

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
        if (!mScroller!!.isFinished) {
            mScroller!!.abortAnimation()
        }
        val deltaIndex = index - selectedPosition
        mScroller!!.startScroll(scrollX, 0, (deltaIndex * mIntervalDis).toInt(), 0)
        invalidate()
    }

    override fun onDown(e: MotionEvent): Boolean {
        if (!mScroller!!.isFinished) {
            mScroller!!.forceFinished(false)
        }
        mFling = false
        if (null != parent) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) {

    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        playSoundEffect(SoundEffectConstants.CLICK)
        refreshCenter((scrollX + e.x - mMaxOverScrollDistance).toInt())
        autoSettle()
        return true
    }

    override fun onLongPress(e: MotionEvent) {

    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        var dis = distanceX
        val scrollX = scrollX.toFloat()
        if (scrollX < _minSelectableIndex * mIntervalDis - 2 * mMaxOverScrollDistance) {
            dis = 0f
        } else if (scrollX < _minSelectableIndex * mIntervalDis - mMaxOverScrollDistance) {
            dis = distanceX / 4f
        } else if (scrollX > mContentRectF!!.width() - (mMarkCount - _maxSelectableIndex - 1) * mIntervalDis) {
            dis = 0f
        } else if (scrollX > mContentRectF!!.width() - (mMarkCount - _maxSelectableIndex - 1) * mIntervalDis - mMaxOverScrollDistance) {
            dis = distanceX / 4f
        }
        scrollBy(dis.toInt(), 0)
        refreshCenter()
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val scrollX = scrollX.toFloat()
        if (scrollX < -mMaxOverScrollDistance + _minSelectableIndex * mIntervalDis || scrollX > mContentRectF!!.width() - mMaxOverScrollDistance - (mMarkCount - 1 - _maxSelectableIndex) * mIntervalDis) {
            return false
        } else {
            mFling = true
            fling((-velocityX).toInt(), 0)
            return true
        }
    }

    public override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.index = selectedPosition
        ss.min = _minSelectableIndex
        ss.max = _maxSelectableIndex
        return ss
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        val ss = state as SavedState
        super.onRestoreInstanceState(ss.superState)
        _minSelectableIndex = ss.min
        _maxSelectableIndex = ss.max
        selectIndex(ss.index)
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

        constructor(superState: Parcelable) : super(superState) {}

        private constructor(`in`: Parcel) : super(`in`) {
            index = `in`.readInt()
            min = `in`.readInt()
            max = `in`.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(index)
            out.writeInt(min)
            out.writeInt(max)
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
        val DEFAULT_INTERVAL_FACTOR = 1.2f
        val DEFAULT_MARK_RATIO = 0.7f
    }
}
