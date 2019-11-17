package com.sanjay.scalableedittext

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.abs

/**
 * Custom EditText which is scalable in any direction
 */
class ScalableEditText : AppCompatEditText {

    private var touchRadius: Float = 0.toFloat()
    private var rect: RectF? = null

    /** The Paint used to draw the corners of the Border  */
    private var mBorderCornerPaint: Paint? = null
    /** Minimum width in pixels that the crop window can get.  */
    private val mMinCropWidth = 100f

    /** Minimum width in pixels that the crop window can get.  */
    private val mMinCropHeight = 30f

    /** Maximum height in pixels that the crop window can get.  */
    private val mMaxCropWidth = 1080f

    /** Maximum height in pixels that the crop window can get.  */
    private val mMaxCropHeight = 1920f

    /** The type of crop window move that is handled.  */
    private var mType: Type? = null

    /**
     * Holds the x and y offset between the exact touch location and the exact handle location that is
     * activated. There may be an offset because we allow for some leeway (specified by mHandleRadius)
     * in activating a handle. However, we want to maintain these offset values while the handle is
     * being dragged so that the handle doesn't jump.
     */
    private val mTouchOffset = PointF()
    private val mAvailableSpaceRect = RectF()
    private val mTextCachedSizes = SparseIntArray()
    private var mSizeTester: SizeTester? = null
    private var mMaxTextSize: Float = 0.toFloat()
    private var mSpacingMulti = 1.0f
    private var mSpacingAdd = 0.0f
    private var mMinTextSize: Float = 0.toFloat()
    private var mWidthLimit: Int = 0
    private var mMaxLines: Int = 0
    private var mEnableSizeCache = true
    private var mInitialized = false
    private var mPaint: TextPaint? = null


    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context)


    }

    private fun init(context: Context) {
        val dm = Resources.getSystem().displayMetrics
        //mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        touchRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, dm)
        mBorderCornerPaint = Paint()
        mBorderCornerPaint!!.color = Color.RED
        mBorderCornerPaint!!.strokeWidth = 3f
        mBorderCornerPaint!!.style = Paint.Style.STROKE
        mBorderCornerPaint!!.isAntiAlias = true


        // using the minimal recommended font size
        mMinTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f, resources.displayMetrics
        )
        mMaxTextSize = textSize
        if (mMaxLines == 0)
        // no value was assigned during construction
            mMaxLines = NO_LINE_LIMIT
        // prepare size tester:
        mSizeTester = object : SizeTester {
            internal val textRect = RectF()

            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            override fun onTestSize(
                suggestedSize: Int,
                availableSPace: RectF
            ): Int {
                paint!!.textSize = suggestedSize.toFloat()
                val text = text.toString()
                val isSingleLine = maxLines == 1
                if (isSingleLine) {
                    textRect.bottom = paint!!.fontSpacing
                    textRect.right = paint!!.measureText(text)
                } else {
                    val layout = StaticLayout(
                        text, paint,
                        mWidthLimit, Layout.Alignment.ALIGN_NORMAL, mSpacingMulti,
                        mSpacingAdd, true
                    )
                    // return early if we have more lines
                    Log.d("NLN", "Current Lines = " + Integer.toString(layout.lineCount))
                    Log.d("NLN", "Max Lines = " + Integer.toString(maxLines))
                    if (maxLines != NO_LINE_LIMIT && layout.lineCount > maxLines)
                        return 1
                    textRect.bottom = layout.height.toFloat()
                    var maxWidth = -1
                    for (i in 0 until layout.lineCount)
                        if (maxWidth < layout.getLineWidth(i))
                            maxWidth = layout.getLineWidth(i).toInt()
                    textRect.right = maxWidth.toFloat()
                }
                textRect.offsetTo(0f, 0f)
                return if (availableSPace.contains(textRect)) -1 else 1
                // else, too big
            }
        }
        mInitialized = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touchType = getRectanglePressedMoveType(event.rawX, event.rawY, touchRadius)
        if (touchType == Type.CENTER) {
            showSoftKeyboard()
        }
        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                onActionDown(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onActionUp()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onActionMove(event.rawX, event.rawY)
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            else -> return false
        }
    }

    /**
     * On press down start crop window movment depending on the location of the press.<br></br>
     * if press is far from crop window then no move handler is returned (null).
     */
    private fun onActionDown(x: Float, y: Float) {
        mType = getRectanglePressedMoveType(x, y, touchRadius)
        calculateTouchOffset(rect, x, y)
        requestLayout()
    }

    /** Clear move handler starting in [.onActionDown] if exists.  */
    private fun onActionUp() {
        /* if (mMoveHandler != null) {
            mMoveHandler = null;
            callOnCropWindowChanged(false);
            invalidate();
        }*/
    }

    /**
     * Handle move of crop window using the move handler created in [.onActionDown].<br></br>
     * The move handler will do the proper move/resize of the crop window.
     */
    private fun onActionMove(x: Float, y: Float) {
        move(
            rect,
            x,
            y,
            1080,
            1920
        )
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (rect == null) {
            val width = View.MeasureSpec.getSize(widthMeasureSpec)
            val height = View.MeasureSpec.getSize(heightMeasureSpec)
            setMeasuredDimension(width, height)

            return
        }


        x = rect!!.left
        y = rect!!.top
        setMeasuredDimension(rect!!.width().toInt(), rect!!.height().toInt())

    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (rect == null) {
            rect = RectF()
            rect!!.left = left.toFloat()
            rect!!.top = top.toFloat()
            rect!!.right = right.toFloat()
            rect!!.bottom = bottom.toFloat()
        }

    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mTextCachedSizes.clear()
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh)
            reAdjust()
    }

    private fun move(
        rect: RectF?,
        x: Float,
        y: Float,
        viewWidth: Int,
        viewHeight: Int
    ) {

        // Adjust the coordinates for the finger position's offset (i.e. the
        // distance from the initial touch to the precise handle location).
        // We want to maintain the initial touch's distance to the pressed
        // handle so that the crop window size does not "jump".
        val adjX = x + mTouchOffset.x
        val adjY = y + mTouchOffset.y

        moveSizeWithFreeAspectRatio(rect, adjX, adjY, viewWidth, viewHeight)
    }


    /**
     * Change the size of the crop window on the required edge (or edges for corner size move) without
     * affecting "secondary" edges.<br></br>
     * Only the primary edge(s) are fixed to stay within limits.
     */
    private fun moveSizeWithFreeAspectRatio(
        rect: RectF?, x: Float, y: Float, viewWidth: Int, viewHeight: Int
    ) {
        when (mType) {
            Type.TOP_LEFT -> {
                adjustTop(rect, y)
                adjustLeft(rect, x)
            }
            Type.TOP_RIGHT -> {
                adjustTop(rect, y)
                adjustRight(rect, x, viewWidth)
            }
            Type.BOTTOM_LEFT -> {
                adjustBottom(rect, y, viewHeight)
                adjustLeft(rect, x)
            }
            Type.BOTTOM_RIGHT -> {
                adjustBottom(rect, y, viewHeight)
                adjustRight(rect, x, viewWidth)
            }
            Type.LEFT -> adjustLeft(rect, x)
            Type.TOP -> adjustTop(rect, y)
            Type.RIGHT -> adjustRight(rect, x, viewWidth)
            Type.BOTTOM -> adjustBottom(rect, y, viewHeight)
            else -> {
            }
        }
    }


    /**
     * Get the resulting x-position of the left edge of the crop window given the handle's position
     * and the image's bounding box and snap radius.
     *
     * @param left the position that the left edge is dragged to
     */
    private fun adjustLeft(
        rect: RectF?,
        left: Float
    ) {

        var newLeft = left

        if (newLeft < 0) {
            newLeft /= 1.05f
            mTouchOffset.x -= newLeft / 1.1f
        }


        // Checks if the window is too small horizontally
        if (rect!!.right - newLeft < mMinCropWidth) {
            newLeft = rect.right - mMinCropWidth
        }

        // Checks if the window is too large horizontally
        if (rect.right - newLeft > mMaxCropWidth) {
            newLeft = rect.right - mMaxCropWidth
        }

        rect.left = newLeft
    }

    /**
     * Get the resulting x-position of the right edge of the crop window given the handle's position
     * and the image's bounding box and snap radius.
     *
     * @param right the position that the right edge is dragged to
     * @param viewWidth)
     */
    private fun adjustRight(
        rect: RectF?,
        right: Float,
        viewWidth: Int
    ) {

        var newRight = right

        if (newRight > viewWidth) {
            newRight = viewWidth + (newRight - viewWidth) / 1.05f
            mTouchOffset.x -= (newRight - viewWidth) / 1.1f
        }


        // Checks if the window is too small horizontally
        if (newRight - rect!!.left < mMinCropWidth) {
            newRight = rect.left + mMinCropWidth
        }

        // Checks if the window is too large horizontally
        if (newRight - rect.left > mMaxCropWidth) {
            newRight = rect.left + mMaxCropWidth
        }


        rect.right = newRight
    }

    /**
     * Get the resulting y-position of the top edge of the crop window given the handle's position and
     * the image's bounding box and snap radius.
     *
     * @param top the x-position that the top edge is dragged to
     */
    private fun adjustTop(
        rect: RectF?,
        top: Float
    ) {

        var newTop = top

        if (newTop < 0) {
            newTop /= 1.05f
            mTouchOffset.y -= newTop / 1.1f
        }

        // Checks if the window is too small vertically
        if (rect!!.bottom - newTop < mMinCropHeight) {
            newTop = rect.bottom - mMinCropHeight
        }

        // Checks if the window is too large vertically
        if (rect.bottom - newTop > mMaxCropHeight) {
            newTop = rect.bottom - mMaxCropHeight
        }

        rect.top = newTop
    }

    /**
     * Get the resulting y-position of the bottom edge of the crop window given the handle's position
     * and the image's bounding box and snap radius.
     *
     * @param bottom the position that the bottom edge is dragged to
     * @param viewHeight
     *
     */
    private fun adjustBottom(
        rect: RectF?,
        bottom: Float,
        viewHeight: Int
    ) {

        var newBottom = bottom

        if (newBottom > viewHeight) {
            newBottom = viewHeight + (newBottom - viewHeight) / 1.05f
            mTouchOffset.y -= (newBottom - viewHeight) / 1.1f
        }


        // Checks if the window is too small vertically
        if (newBottom - rect!!.top < mMinCropHeight) {
            newBottom = rect.top + mMinCropHeight
        }

        // Checks if the window is too small vertically
        if (newBottom - rect.top > mMaxCropHeight) {
            newBottom = rect.top + mMaxCropHeight
        }

        rect.bottom = newBottom
    }


    enum class Type {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
        CENTER
    }

    /**
     * Determines which, if any, of the handles are pressed given the touch coordinates, the bounding
     * box, and the touch radius.
     *
     * @param x the x-coordinate of the touch point
     * @param y the y-coordinate of the touch point
     * @param targetRadius the target radius in pixels
     * @return the Handle that was pressed; null if no Handle was pressed
     */
    private fun getRectanglePressedMoveType(
        x: Float, y: Float, targetRadius: Float
    ): Type? {
        var moveType: Type? = null

        // Note: corner-handles take precedence, then side-handles, then center.
        if (isInCornerTargetZone(x, y, rect!!.left, rect!!.top, targetRadius)) {
            moveType = Type.TOP_LEFT
        } else if (isInCornerTargetZone(
                x, y, rect!!.right, rect!!.top, targetRadius
            )
        ) {
            moveType = Type.TOP_RIGHT
        } else if (isInCornerTargetZone(
                x, y, rect!!.left, rect!!.bottom, targetRadius
            )
        ) {
            moveType = Type.BOTTOM_LEFT
        } else if (isInCornerTargetZone(
                x, y, rect!!.right, rect!!.bottom, targetRadius
            )
        ) {
            moveType = Type.BOTTOM_RIGHT
        } else if (isInCenterTargetZone(
                x, y, rect!!.left, rect!!.top, rect!!.right, rect!!.bottom
            ) && focusCenter()
        ) {
            moveType = Type.CENTER
        } else if (isInHorizontalTargetZone(
                x, y, rect!!.left, rect!!.right, rect!!.top, targetRadius
            )
        ) {
            moveType = Type.TOP
        } else if (isInHorizontalTargetZone(
                x, y, rect!!.left, rect!!.right, rect!!.bottom, targetRadius
            )
        ) {
            moveType = Type.BOTTOM
        } else if (isInVerticalTargetZone(
                x, y, rect!!.left, rect!!.top, rect!!.bottom, targetRadius
            )
        ) {
            moveType = Type.LEFT
        } else if (isInVerticalTargetZone(
                x, y, rect!!.right, rect!!.top, rect!!.bottom, targetRadius
            )
        ) {
            moveType = Type.RIGHT
        } else if (isInCenterTargetZone(
                x, y, rect!!.left, rect!!.top, rect!!.right, rect!!.bottom
            ) && !focusCenter()
        ) {
            moveType = Type.CENTER
        }

        return moveType
    }

    /**
     * Determines if the cropper should focus on the center handle or the side handles. If it is a
     * small image, focus on the center handle so the user can move it. If it is a large image, focus
     * on the side handles so user can grab them. Corresponds to the appearance of the
     * RuleOfThirdsGuidelines.
     *
     * @return true if it is small enough such that it should focus on the center; less than
     * show_guidelines limit
     */
    private fun focusCenter(): Boolean {
        return !showGuidelines()
    }

    /**
     * Indicates whether the crop window is small enough that the guidelines should be shown. Public
     * because this function is also used to determine if the center handle should be focused.
     *
     * @return boolean Whether the guidelines should be shown or not
     */
    private fun showGuidelines(): Boolean {
        return !(rect!!.width() < 100 || rect!!.height() < 100)
    }

    /**
     * Calculates the offset of the touch point from the precise location of the specified handle.<br></br>
     * Save these values in a member variable since we want to maintain this offset as we drag the
     * handle.
     */
    private fun calculateTouchOffset(rect: RectF?, touchX: Float, touchY: Float) {

        var touchOffsetX = 0f
        var touchOffsetY = 0f

        // Calculate the offset from the appropriate handle.
        when (mType) {
            Type.TOP_LEFT -> {
                touchOffsetX = rect!!.left - touchX
                touchOffsetY = rect.top - touchY
            }
            Type.TOP_RIGHT -> {
                touchOffsetX = rect!!.right - touchX
                touchOffsetY = rect.top - touchY
            }
            Type.BOTTOM_LEFT -> {
                touchOffsetX = rect!!.left - touchX
                touchOffsetY = rect.bottom - touchY
            }
            Type.BOTTOM_RIGHT -> {
                touchOffsetX = rect!!.right - touchX
                touchOffsetY = rect.bottom - touchY
            }
            Type.LEFT -> {
                touchOffsetX = rect!!.left - touchX
                touchOffsetY = 0f
            }
            Type.TOP -> {
                touchOffsetX = 0f
                touchOffsetY = rect!!.top - touchY
            }
            Type.RIGHT -> {
                touchOffsetX = rect!!.right - touchX
                touchOffsetY = 0f
            }
            Type.BOTTOM -> {
                touchOffsetX = 0f
                touchOffsetY = rect!!.bottom - touchY
            }
            Type.CENTER -> {
                touchOffsetX = rect!!.centerX() - touchX
                touchOffsetY = rect.centerY() - touchY
            }
            else -> {
            }
        }

        mTouchOffset.x = touchOffsetX
        mTouchOffset.y = touchOffsetY
    }

    private fun showSoftKeyboard() {
        if (requestFocus()) {
            val imm =
                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    //Text autosize logic

    private interface SizeTester {
        /**
         * AutoResizeEditText
         *
         * @param suggestedSize
         * Size of text to be tested
         * @param availableSpace
         * available space in which text must fit
         * @return an integer < 0 if after applying `suggestedSize` to
         * text, it takes less space than `availableSpace`, > 0
         * otherwise
         */
        fun onTestSize(suggestedSize: Int, availableSpace: RectF): Int
    }

    override fun setTypeface(tf: Typeface) {
        if (mPaint == null)
            mPaint = TextPaint(paint)
        mPaint!!.typeface = tf
        super.setTypeface(tf)
    }

    override fun setTextSize(size: Float) {
        mMaxTextSize = size
        mTextCachedSizes.clear()
        adjustTextSize()
    }

    override fun setMaxLines(maxlines: Int) {
        super.setMaxLines(maxlines)
        mMaxLines = maxlines
        reAdjust()
    }

    override fun getMaxLines(): Int {
        return mMaxLines
    }

    override fun setSingleLine() {
        super.setSingleLine()
        mMaxLines = 1
        reAdjust()
    }

    override fun setSingleLine(singleLine: Boolean) {
        super.setSingleLine(singleLine)
        if (singleLine)
            mMaxLines = 1
        else
            mMaxLines = NO_LINE_LIMIT
        reAdjust()
    }

    override fun setLines(lines: Int) {
        super.setLines(lines)
        mMaxLines = lines
        reAdjust()
    }

    override fun setTextSize(unit: Int, size: Float) {
        val c = context
        val r: Resources
        r = if (c == null)
            Resources.getSystem()
        else
            c.resources
        mMaxTextSize = TypedValue.applyDimension(
            unit, size,
            r.displayMetrics
        )
        mTextCachedSizes.clear()
        adjustTextSize()
    }

    override fun setLineSpacing(add: Float, mult: Float) {
        super.setLineSpacing(add, mult)
        mSpacingMulti = mult
        mSpacingAdd = add
    }

    /**
     * Set the lower text size limit and invalidate the view
     *
     * @param
     */
    fun setMinTextSize(minTextSize: Float) {
        mMinTextSize = minTextSize
        reAdjust()
    }

    private fun reAdjust() {
        adjustTextSize()
    }

    private fun adjustTextSize() {
        if (!mInitialized)
            return
        val startSize = mMinTextSize.toInt()
        val heightLimit = (measuredHeight
                - compoundPaddingBottom - compoundPaddingTop)
        mWidthLimit = (measuredWidth - compoundPaddingLeft
                - compoundPaddingRight)
        if (mWidthLimit <= 0)
            return
        mAvailableSpaceRect.right = mWidthLimit.toFloat()
        mAvailableSpaceRect.bottom = heightLimit.toFloat()
        super.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            efficientTextSizeSearch(
                startSize, mMaxTextSize.toInt(),
                mSizeTester, mAvailableSpaceRect
            ).toFloat()
        )
    }

    /**
     * Enables or disables size caching, enabling it will improve performance
     * where you are animating a value inside TextView. This stores the font
     * size against getText().length() Be careful though while enabling it as 0
     * takes more space than 1 on some fonts and so on.
     *
     * @param enable
     * enable font size caching
     */
    fun setEnableSizeCache(enable: Boolean) {
        mEnableSizeCache = enable
        mTextCachedSizes.clear()
        adjustTextSize()
    }

    private fun efficientTextSizeSearch(
        start: Int, end: Int,
        sizeTester: SizeTester?, availableSpace: RectF
    ): Int {
        if (!mEnableSizeCache)
            return binarySearch(start, end, sizeTester, availableSpace)
        val text = text.toString()
        val key = text.length
        var size = mTextCachedSizes.get(key)
        if (size != 0)
            return size
        size = binarySearch(start, end, sizeTester, availableSpace)
        mTextCachedSizes.put(key, size)
        return size
    }

    private fun binarySearch(
        start: Int, end: Int,
        sizeTester: SizeTester?, availableSpace: RectF
    ): Int {
        var lastBest = start
        var lo = start
        var hi = end - 1
        var mid = 0
        while (lo <= hi) {
            mid = (lo + hi).ushr(1)
            val midValCmp = sizeTester!!.onTestSize(mid, availableSpace)
            when {
                midValCmp < 0 -> {
                    lastBest = lo
                    lo = mid + 1
                }
                midValCmp > 0 -> {
                    hi = mid - 1
                    lastBest = hi
                }
                else -> return mid
            }
        }
        // make sure to return last best
        // this is what should always be returned
        return lastBest
    }

    protected override fun onTextChanged(
        text: CharSequence, start: Int,
        before: Int, after: Int
    ) {
        super.onTextChanged(text, start, before, after)
        reAdjust()
    }

    companion object {

        //Auto-resize Code
        private const val NO_LINE_LIMIT = -1

        /**
         * Determines if the specified coordinate is in the target touch zone for a corner handle.
         *
         * @param x the x-coordinate of the touch point
         * @param y the y-coordinate of the touch point
         * @param handleX the x-coordinate of the corner handle
         * @param handleY the y-coordinate of the corner handle
         * @param targetRadius the target radius in pixels
         * @return true if the touch point is in the target touch zone; false otherwise
         */
        private fun isInCornerTargetZone(
            x: Float, y: Float, handleX: Float, handleY: Float, targetRadius: Float
        ): Boolean {
            return abs(x - handleX) <= targetRadius && abs(y - handleY) <= targetRadius
        }

        /**
         * Determines if the specified coordinate is in the target touch zone for a horizontal bar handle.
         *
         * @param x the x-coordinate of the touch point
         * @param y the y-coordinate of the touch point
         * @param handleXStart the left x-coordinate of the horizontal bar handle
         * @param handleXEnd the right x-coordinate of the horizontal bar handle
         * @param handleY the y-coordinate of the horizontal bar handle
         * @param targetRadius the target radius in pixels
         * @return true if the touch point is in the target touch zone; false otherwise
         */
        private fun isInHorizontalTargetZone(
            x: Float,
            y: Float,
            handleXStart: Float,
            handleXEnd: Float,
            handleY: Float,
            targetRadius: Float
        ): Boolean {
            return x > handleXStart && x < handleXEnd && abs(y - handleY) <= targetRadius
        }

        /**
         * Determines if the specified coordinate is in the target touch zone for a vertical bar handle.
         *
         * @param x the x-coordinate of the touch point
         * @param y the y-coordinate of the touch point
         * @param handleX the x-coordinate of the vertical bar handle
         * @param handleYStart the top y-coordinate of the vertical bar handle
         * @param handleYEnd the bottom y-coordinate of the vertical bar handle
         * @param targetRadius the target radius in pixels
         * @return true if the touch point is in the target touch zone; false otherwise
         */
        private fun isInVerticalTargetZone(
            x: Float,
            y: Float,
            handleX: Float,
            handleYStart: Float,
            handleYEnd: Float,
            targetRadius: Float
        ): Boolean {
            return abs(x - handleX) <= targetRadius && y > handleYStart && y < handleYEnd
        }

        /**
         * Determines if the specified coordinate falls anywhere inside the given bounds.
         *
         * @param x the x-coordinate of the touch point
         * @param y the y-coordinate of the touch point
         * @param left the x-coordinate of the left bound
         * @param top the y-coordinate of the top bound
         * @param right the x-coordinate of the right bound
         * @param bottom the y-coordinate of the bottom bound
         * @return true if the touch point is inside the bounding rectangle; false otherwise
         */
        private fun isInCenterTargetZone(
            x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float
        ): Boolean {
            return x > left && x < right && y > top && y < bottom
        }
    }
}
