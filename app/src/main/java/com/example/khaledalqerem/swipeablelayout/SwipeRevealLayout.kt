package com.example.khaledalqerem.swipeablelayout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper

@SuppressLint("RtlHardcoded")
class SwipeRevealLayout : ViewGroup {

    /**
     * Main view is the view which is shown when the layout is closed.
     */
    private var mainView: View? = null

    /**
     * Secondary view is the view which is shown when the layout is opened.
     */
    private var secondaryView: View? = null

    /**
     * The rectangle position of the main view when the layout is closed.
     */
    private val rectMainClose = Rect()

    /**
     * The rectangle position of the main view when the layout is opened.
     */
    private val rectMainOpen = Rect()

    /**
     * The rectangle position of the secondary view when the layout is closed.
     */
    private val rectSecClose = Rect()

    /**
     * The rectangle position of the secondary view when the layout is opened.
     */
    private val rectSecOpen = Rect()

    /**
     * The minimum distance (px) to the closest drag edge that the SwipeRevealLayout
     * will disallow the parent to intercept touch event.
     */
    private var minDistRequestDisallowParent = 0

    private var isOpenBeforeInit = false
    @Volatile
    private var aborted = false
    @Volatile
    private var isScrolling = false
    /**
     * @return true if the drag/swipe motion is currently locked.
     */
    @Volatile
    var isDragLocked = false
        private set

    /**
     * Get the minimum fling velocity to cause the layout to open/close.
     * @return dp per second
     */
    /**
     * Set the minimum fling velocity to cause the layout to open/close.
     * @param velocity dp per second
     */
    var minFlingVelocity = DEFAULT_MIN_FLING_VELOCITY
    private var state = STATE_CLOSE
    private var mode = MODE_NORMAL

    private var lastMainLeft = 0
    private var lastMainTop = 0

    /**
     * Get the edge where the layout can be dragged from.
     * @return Can be one of these
     *
     *  * [.DRAG_EDGE_LEFT]
     *  * [.DRAG_EDGE_TOP]
     *  * [.DRAG_EDGE_RIGHT]
     *  * [.DRAG_EDGE_BOTTOM]
     *
     */
    /**
     * Set the edge where the layout can be dragged from.
     * @param dragEdge Can be one of these
     *
     *  * [.DRAG_EDGE_LEFT]
     *  * [.DRAG_EDGE_TOP]
     *  * [.DRAG_EDGE_RIGHT]
     *  * [.DRAG_EDGE_BOTTOM]
     *
     */
    var dragEdge = DRAG_EDGE_LEFT

    private var dragDist = 0f
    private var prevX = -1f
    private var prevY = -1f

    private var dragHelper: ViewDragHelper? = null
    private var gestureDetector: GestureDetectorCompat? = null

    private var dragStateChangeListener: DragStateChangeListener? =
        null // only used for ViewBindHelper
    private var swipeListener: SwipeListener? = null

    private var onLayoutCount = 0

    /**
     * @return true if layout is fully opened, false otherwise.
     */
    val isOpened: Boolean
        get() = state == STATE_OPEN

    /**
     * @return true if layout is fully closed, false otherwise.
     */
    val isClosed: Boolean
        get() = state == STATE_CLOSE


    private val mainOpenLeft: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT -> return rectMainClose.left + secondaryView!!.width

                DRAG_EDGE_RIGHT -> return rectMainClose.left - secondaryView!!.width

                DRAG_EDGE_TOP -> return rectMainClose.left

                DRAG_EDGE_BOTTOM -> return rectMainClose.left

                else -> return 0
            }
        }

    private val mainOpenTop: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT -> return rectMainClose.top

                DRAG_EDGE_RIGHT -> return rectMainClose.top

                DRAG_EDGE_TOP -> return rectMainClose.top + secondaryView!!.height

                DRAG_EDGE_BOTTOM -> return rectMainClose.top - secondaryView!!.height

                else -> return 0
            }
        }

    private val secOpenLeft: Int
        get() {
            if (mode == MODE_NORMAL || dragEdge == DRAG_EDGE_BOTTOM || dragEdge == DRAG_EDGE_TOP) {
                return rectSecClose.left
            }

            return if (dragEdge == DRAG_EDGE_LEFT) {
                rectSecClose.left + secondaryView!!.width
            } else {
                rectSecClose.left - secondaryView!!.width
            }
        }

    private val secOpenTop: Int
        get() {
            if (mode == MODE_NORMAL || dragEdge == DRAG_EDGE_LEFT || dragEdge == DRAG_EDGE_RIGHT) {
                return rectSecClose.top
            }

            return if (dragEdge == DRAG_EDGE_TOP) {
                rectSecClose.top + secondaryView!!.height
            } else {
                rectSecClose.top - secondaryView!!.height
            }
        }

    private val mGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        var hasDisallowed = false

        override fun onDown(e: MotionEvent): Boolean {
            isScrolling = false
            hasDisallowed = false
            return true
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            isScrolling = true
            return false
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            isScrolling = true

            if (parent != null) {
                val shouldDisallow: Boolean

                if (!hasDisallowed) {
                    shouldDisallow = distToClosestEdge >= minDistRequestDisallowParent
                    if (shouldDisallow) {
                        hasDisallowed = true
                    }
                } else {
                    shouldDisallow = true
                }

                // disallow parent to intercept touch event so that the layout will work
                // properly on RecyclerView or view that handles scroll gesture.
                parent.requestDisallowInterceptTouchEvent(shouldDisallow)
            }

            return false
        }
    }

    private val distToClosestEdge: Int
        get() {
            when (dragEdge) {
                DRAG_EDGE_LEFT -> {
                    val pivotRight = rectMainClose.left + secondaryView!!.width

                    return Math.min(
                        mainView!!.left - rectMainClose.left,
                        pivotRight - mainView!!.left
                    )
                }

                DRAG_EDGE_RIGHT -> {
                    val pivotLeft = rectMainClose.right - secondaryView!!.width

                    return Math.min(
                        mainView!!.right - pivotLeft,
                        rectMainClose.right - mainView!!.right
                    )
                }

                DRAG_EDGE_TOP -> {
                    val pivotBottom = rectMainClose.top + secondaryView!!.height

                    return Math.min(
                        mainView!!.bottom - pivotBottom,
                        pivotBottom - mainView!!.top
                    )
                }

                DRAG_EDGE_BOTTOM -> {
                    val pivotTop = rectMainClose.bottom - secondaryView!!.height

                    return Math.min(
                        rectMainClose.bottom - mainView!!.bottom,
                        mainView!!.bottom - pivotTop
                    )
                }
            }

            return 0
        }

    private val halfwayPivotHorizontal: Int
        get() = if (dragEdge == DRAG_EDGE_LEFT) {
            rectMainClose.left + secondaryView!!.width / 2
        } else {
            rectMainClose.right - secondaryView!!.width / 2
        }

    private val halfwayPivotVertical: Int
        get() = if (dragEdge == DRAG_EDGE_TOP) {
            rectMainClose.top + secondaryView!!.height / 2
        } else {
            rectMainClose.bottom - secondaryView!!.height / 2
        }

    private val mDragHelperCallback = object : ViewDragHelper.Callback() {

        private val slideOffset: Float
            get() {
                when (dragEdge) {
                    DRAG_EDGE_LEFT -> return (mainView!!.left - rectMainClose.left).toFloat() / secondaryView!!.width

                    DRAG_EDGE_RIGHT -> return (rectMainClose.left - mainView!!.left).toFloat() / secondaryView!!.width

                    DRAG_EDGE_TOP -> return (mainView!!.top - rectMainClose.top).toFloat() / secondaryView!!.height

                    DRAG_EDGE_BOTTOM -> return (rectMainClose.top - mainView!!.top).toFloat() / secondaryView!!.height

                    else -> return 0f
                }
            }

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            aborted = false

            if (isDragLocked)
                return false

            dragHelper!!.captureChildView(mainView!!, pointerId)
            return false
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            when (dragEdge) {
                DRAG_EDGE_TOP -> return Math.max(
                    Math.min(top, rectMainClose.top + secondaryView!!.height),
                    rectMainClose.top
                )

                DRAG_EDGE_BOTTOM -> return Math.max(
                    Math.min(top, rectMainClose.top),
                    rectMainClose.top - secondaryView!!.height
                )

                else -> return child.top
            }
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            when (dragEdge) {
                DRAG_EDGE_RIGHT -> return Math.max(
                    Math.min(left, rectMainClose.left),
                    rectMainClose.left - secondaryView!!.width
                )

                DRAG_EDGE_LEFT -> return Math.max(
                    Math.min(left, rectMainClose.left + secondaryView!!.width),
                    rectMainClose.left
                )

                else -> return child.left
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val velRightExceeded = pxToDp(xvel.toInt()) >= minFlingVelocity
            val velLeftExceeded = pxToDp(xvel.toInt()) <= -minFlingVelocity
            val velUpExceeded = pxToDp(yvel.toInt()) <= -minFlingVelocity
            val velDownExceeded = pxToDp(yvel.toInt()) >= minFlingVelocity

            val pivotHorizontal = halfwayPivotHorizontal
            val pivotVertical = halfwayPivotVertical

            when (dragEdge) {
                DRAG_EDGE_RIGHT -> if (velRightExceeded) {
                    close(true)
                } else if (velLeftExceeded) {
                    open(true)
                } else {
                    if (mainView!!.right < pivotHorizontal) {
                        open(true)
                    } else {
                        close(true)
                    }
                }

                DRAG_EDGE_LEFT -> if (velRightExceeded) {
                    open(true)
                } else if (velLeftExceeded) {
                    close(true)
                } else {
                    if (mainView!!.left < pivotHorizontal) {
                        close(true)
                    } else {
                        open(true)
                    }
                }

                DRAG_EDGE_TOP -> if (velUpExceeded) {
                    close(true)
                } else if (velDownExceeded) {
                    open(true)
                } else {
                    if (mainView!!.top < pivotVertical) {
                        close(true)
                    } else {
                        open(true)
                    }
                }

                DRAG_EDGE_BOTTOM -> if (velUpExceeded) {
                    open(true)
                } else if (velDownExceeded) {
                    close(true)
                } else {
                    if (mainView!!.bottom < pivotVertical) {
                        open(true)
                    } else {
                        close(true)
                    }
                }
            }
        }

        override fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {
            super.onEdgeDragStarted(edgeFlags, pointerId)

            if (isDragLocked) {
                return
            }

            val edgeStartLeft = dragEdge == DRAG_EDGE_RIGHT && edgeFlags == ViewDragHelper.EDGE_LEFT

            val edgeStartRight =
                dragEdge == DRAG_EDGE_LEFT && edgeFlags == ViewDragHelper.EDGE_RIGHT

            val edgeStartTop = dragEdge == DRAG_EDGE_BOTTOM && edgeFlags == ViewDragHelper.EDGE_TOP

            val edgeStartBottom =
                dragEdge == DRAG_EDGE_TOP && edgeFlags == ViewDragHelper.EDGE_BOTTOM

            if (edgeStartLeft || edgeStartRight || edgeStartTop || edgeStartBottom) {
                dragHelper!!.captureChildView(mainView!!, pointerId)
            }
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)
            if (mode == MODE_SAME_LEVEL) {
                if (dragEdge == DRAG_EDGE_LEFT || dragEdge == DRAG_EDGE_RIGHT) {
                    secondaryView!!.offsetLeftAndRight(dx)
                } else {
                    secondaryView!!.offsetTopAndBottom(dy)
                }
            }

            val isMoved = mainView!!.left != lastMainLeft || mainView!!.top != lastMainTop
            if (swipeListener != null && isMoved) {
                if (mainView!!.left == rectMainClose.left && mainView!!.top == rectMainClose.top) {
                    swipeListener!!.onClosed(this@SwipeRevealLayout)
                } else if (mainView!!.left == rectMainOpen.left && mainView!!.top == rectMainOpen.top) {
                    swipeListener!!.onOpened(this@SwipeRevealLayout)
                } else {
                    swipeListener!!.onSlide(this@SwipeRevealLayout, slideOffset)
                }
            }

            lastMainLeft = mainView!!.left
            lastMainTop = mainView!!.top
            ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            val prevState = this@SwipeRevealLayout.state

            when (state) {
                ViewDragHelper.STATE_DRAGGING -> this@SwipeRevealLayout.state = STATE_DRAGGING

                ViewDragHelper.STATE_IDLE ->

                    // drag edge is left or right
                    if (dragEdge == DRAG_EDGE_LEFT || dragEdge == DRAG_EDGE_RIGHT) {
                        if (mainView!!.left == rectMainClose.left) {
                            this@SwipeRevealLayout.state = STATE_CLOSE
                        } else {
                            this@SwipeRevealLayout.state = STATE_OPEN
                        }
                    } else {
                        if (mainView!!.top == rectMainClose.top) {
                            this@SwipeRevealLayout.state = STATE_CLOSE
                        } else {
                            this@SwipeRevealLayout.state = STATE_OPEN
                        }
                    }// drag edge is top or bottom
            }

            if (dragStateChangeListener != null && !aborted && prevState != this@SwipeRevealLayout.state) {
                dragStateChangeListener!!.onDragStateChanged(this@SwipeRevealLayout.state)
            }
        }
    }

    internal interface DragStateChangeListener {
        fun onDragStateChanged(state: Int)
    }

    /**
     * Listener for monitoring events about swipe layout.
     */
    interface SwipeListener {
        /**
         * Called when the main view becomes completely closed.
         */
        fun onClosed(view: SwipeRevealLayout)

        /**
         * Called when the main view becomes completely opened.
         */
        fun onOpened(view: SwipeRevealLayout)

        /**
         * Called when the main view's position changes.
         * @param slideOffset The new offset of the main view within its range, from 0-1
         */
        fun onSlide(view: SwipeRevealLayout, slideOffset: Float)
    }

    /**
     * No-op stub for [SwipeListener]. If you only want ot implement a subset
     * of the listener methods, you can extend this instead of implement the full interface.
     */
    class SimpleSwipeListener : SwipeListener {
        override fun onClosed(view: SwipeRevealLayout) {}

        override fun onOpened(view: SwipeRevealLayout) {}

        override fun onSlide(view: SwipeRevealLayout, slideOffset: Float) {}
    }

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector!!.onTouchEvent(event)
        dragHelper!!.processTouchEvent(event)
        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDragLocked) {
            return super.onInterceptTouchEvent(ev)
        }

        dragHelper!!.processTouchEvent(ev)
        gestureDetector!!.onTouchEvent(ev)
        accumulateDragDist(ev)

        val couldBecomeClick = couldBecomeClick(ev)
        val settling = dragHelper!!.viewDragState == ViewDragHelper.STATE_SETTLING
        val idleAfterScrolled =
            dragHelper!!.viewDragState == ViewDragHelper.STATE_IDLE && isScrolling

        // must be placed as the last statement
        prevX = ev.x
        prevY = ev.y

        // return true => intercept, cannot trigger onClick event
        return !couldBecomeClick && (settling || idleAfterScrolled)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // get views
        if (childCount >= 2) {
            secondaryView = getChildAt(0)
            mainView = getChildAt(1)
        } else if (childCount == 1) {
            mainView = getChildAt(0)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        aborted = false

        for (index in 0 until childCount) {
            val child = getChildAt(index)

            var left: Int
            var right: Int
            var top: Int
            var bottom: Int
            bottom = 0
            top = bottom
            right = top
            left = right

            val minLeft = paddingLeft
            val maxRight = Math.max(r - paddingRight - l, 0)
            val minTop = paddingTop
            val maxBottom = Math.max(b - paddingBottom - t, 0)

            var measuredChildHeight = child.measuredHeight
            var measuredChildWidth = child.measuredWidth

            // need to take account if child size is match_parent
            val childParams = child.layoutParams
            var matchParentHeight = false
            var matchParentWidth = false

            if (childParams != null) {
                matchParentHeight = childParams.height ==
                        ViewGroup.LayoutParams.MATCH_PARENT || childParams.height ==
                        ViewGroup.LayoutParams.FILL_PARENT
                matchParentWidth = childParams.width ==
                        ViewGroup.LayoutParams.MATCH_PARENT || childParams.width ==
                        ViewGroup.LayoutParams.FILL_PARENT
            }

            if (matchParentHeight) {
                measuredChildHeight = maxBottom - minTop
                childParams!!.height = measuredChildHeight
            }

            if (matchParentWidth) {
                measuredChildWidth = maxRight - minLeft
                childParams!!.width = measuredChildWidth
            }

            when (dragEdge) {
                DRAG_EDGE_RIGHT -> {
                    left = Math.max(r - measuredChildWidth - paddingRight - l, minLeft)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.max(r - paddingRight - l, minLeft)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                }

                DRAG_EDGE_LEFT -> {
                    left = Math.min(paddingLeft, maxRight)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.min(measuredChildWidth + paddingLeft, maxRight)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                }

                DRAG_EDGE_TOP -> {
                    left = Math.min(paddingLeft, maxRight)
                    top = Math.min(paddingTop, maxBottom)
                    right = Math.min(measuredChildWidth + paddingLeft, maxRight)
                    bottom = Math.min(measuredChildHeight + paddingTop, maxBottom)
                }

                DRAG_EDGE_BOTTOM -> {
                    left = Math.min(paddingLeft, maxRight)
                    top = Math.max(b - measuredChildHeight - paddingBottom - t, minTop)
                    right = Math.min(measuredChildWidth + paddingLeft, maxRight)
                    bottom = Math.max(b - paddingBottom - t, minTop)
                }
            }

            child.layout(left, top, right, bottom)
        }

        // taking account offset when mode is SAME_LEVEL
        if (mode == MODE_SAME_LEVEL) {
            when (dragEdge) {
                DRAG_EDGE_LEFT -> secondaryView!!.offsetLeftAndRight(-secondaryView!!.width)

                DRAG_EDGE_RIGHT -> secondaryView!!.offsetLeftAndRight(secondaryView!!.width)

                DRAG_EDGE_TOP -> secondaryView!!.offsetTopAndBottom(-secondaryView!!.height)

                DRAG_EDGE_BOTTOM -> secondaryView!!.offsetTopAndBottom(secondaryView!!.height)
            }
        }

        initRects()

        if (isOpenBeforeInit) {
            open(false)
        } else {
            close(false)
        }

        lastMainLeft = mainView!!.left
        lastMainTop = mainView!!.top

        onLayoutCount++
    }

    /**
     * {@inheritDoc}
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec = widthMeasureSpec
        var heightMeasureSpec = heightMeasureSpec
        if (childCount < 2) {
            throw RuntimeException("Layout must have two children")
        }

        val params = layoutParams

        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)

        var desiredWidth = 0
        var desiredHeight = 0

        // first find the largest child
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            desiredWidth = Math.max(child.measuredWidth, desiredWidth)
            desiredHeight = Math.max(child.measuredHeight, desiredHeight)
        }
        // create new measure spec using the largest child width
        widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(desiredWidth, widthMode)
        heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(desiredHeight, heightMode)

        val measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = View.MeasureSpec.getSize(heightMeasureSpec)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childParams = child.layoutParams

            if (childParams != null) {
                if (childParams.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    child.minimumHeight = measuredHeight
                }

                if (childParams.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    child.minimumWidth = measuredWidth
                }
            }

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            desiredWidth = Math.max(child.measuredWidth, desiredWidth)
            desiredHeight = Math.max(child.measuredHeight, desiredHeight)
        }

        // taking accounts of padding
        desiredWidth += paddingLeft + paddingRight
        desiredHeight += paddingTop + paddingBottom

        // adjust desired width
        if (widthMode == View.MeasureSpec.EXACTLY) {
            desiredWidth = measuredWidth
        } else {
            if (params.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                desiredWidth = measuredWidth
            }

            if (widthMode == View.MeasureSpec.AT_MOST) {
                desiredWidth = if (desiredWidth > measuredWidth) measuredWidth else desiredWidth
            }
        }

        // adjust desired height
        if (heightMode == View.MeasureSpec.EXACTLY) {
            desiredHeight = measuredHeight
        } else {
            if (params.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                desiredHeight = measuredHeight
            }

            if (heightMode == View.MeasureSpec.AT_MOST) {
                desiredHeight =
                        if (desiredHeight > measuredHeight) measuredHeight else desiredHeight
            }
        }

        setMeasuredDimension(desiredWidth, desiredHeight)
    }

    override fun computeScroll() {
        if (dragHelper!!.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Open the panel to show the secondary view
     * @param animation true to animate the open motion. [SwipeListener] won't be
     * called if is animation is false.
     */
    fun open(animation: Boolean) {
        isOpenBeforeInit = true
        aborted = false

        if (animation) {
            state = STATE_OPENING
            dragHelper!!.smoothSlideViewTo(mainView!!, rectMainOpen.left, rectMainOpen.top)

            if (dragStateChangeListener != null) {
                dragStateChangeListener!!.onDragStateChanged(state)
            }
        } else {
            state = STATE_OPEN
            dragHelper!!.abort()

            mainView!!.layout(
                rectMainOpen.left,
                rectMainOpen.top,
                rectMainOpen.right,
                rectMainOpen.bottom
            )

            secondaryView!!.layout(
                rectSecOpen.left,
                rectSecOpen.top,
                rectSecOpen.right,
                rectSecOpen.bottom
            )
        }

        ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
    }

    /**
     * Close the panel to hide the secondary view
     * @param animation true to animate the close motion. [SwipeListener] won't be
     * called if is animation is false.
     */
    fun close(animation: Boolean) {
        isOpenBeforeInit = false
        aborted = false

        if (animation) {
            state = STATE_CLOSING
            dragHelper!!.smoothSlideViewTo(mainView!!, rectMainClose.left, rectMainClose.top)

            if (dragStateChangeListener != null) {
                dragStateChangeListener!!.onDragStateChanged(state)
            }

        } else {
            state = STATE_CLOSE
            dragHelper!!.abort()

            mainView!!.layout(
                rectMainClose.left,
                rectMainClose.top,
                rectMainClose.right,
                rectMainClose.bottom
            )

            secondaryView!!.layout(
                rectSecClose.left,
                rectSecClose.top,
                rectSecClose.right,
                rectSecClose.bottom
            )
        }

        ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
    }

    fun setSwipeListener(listener: SwipeListener) {
        swipeListener = listener
    }

    /**
     * @param lock if set to true, the user cannot drag/swipe the layout.
     */
    fun setLockDrag(lock: Boolean) {
        isDragLocked = lock
    }

    /** Only used for [ViewBinderHelper]  */
    internal fun setDragStateChangeListener(listener: DragStateChangeListener) {
        dragStateChangeListener = listener
    }

    /** Abort current motion in progress. Only used for [ViewBinderHelper]  */
    fun abort() {
        aborted = true
        dragHelper!!.abort()
    }

    /**
     * In RecyclerView/ListView, onLayout should be called 2 times to display children views correctly.
     * This method check if it've already called onLayout two times.
     * @return true if you should call [.requestLayout].
     */
    fun shouldRequestLayout(): Boolean {
        return onLayoutCount < 2
    }

    private fun initRects() {
        // close position of main view
        rectMainClose.set(
            mainView!!.left,
            mainView!!.top,
            mainView!!.right,
            mainView!!.bottom
        )

        // close position of secondary view
        rectSecClose.set(
            secondaryView!!.left,
            secondaryView!!.top,
            secondaryView!!.right,
            secondaryView!!.bottom
        )

        // open position of the main view
        rectMainOpen.set(
            mainOpenLeft,
            mainOpenTop,
            mainOpenLeft + mainView!!.width,
            mainOpenTop + mainView!!.height
        )

        // open position of the secondary view
        rectSecOpen.set(
            secOpenLeft,
            secOpenTop,
            secOpenLeft + secondaryView!!.width,
            secOpenTop + secondaryView!!.height
        )
    }

    private fun couldBecomeClick(ev: MotionEvent): Boolean {
        return isInMainView(ev) && !shouldInitiateADrag()
    }

    private fun isInMainView(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y

        val withinVertical = mainView!!.top <= y && y <= mainView!!.bottom
        val withinHorizontal = mainView!!.left <= x && x <= mainView!!.right

        return withinVertical && withinHorizontal
    }

    private fun shouldInitiateADrag(): Boolean {
        val minDistToInitiateDrag = dragHelper!!.getTouchSlop()
        return dragDist >= minDistToInitiateDrag
    }

    private fun accumulateDragDist(ev: MotionEvent) {
        val action = ev.action
        if (action == MotionEvent.ACTION_DOWN) {
            dragDist = 0f
            return
        }

        val dragHorizontally = dragEdge == DRAG_EDGE_LEFT || dragEdge == DRAG_EDGE_RIGHT

        val dragged: Float
        if (dragHorizontally) {
            dragged = Math.abs(ev.x - prevX)
        } else {
            dragged = Math.abs(ev.y - prevY)
        }

        dragDist += dragged
    }

    private fun init(context: Context?, attrs: AttributeSet?) {
        if (attrs != null && context != null) {
            val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.SwipeRevealLayout,
                0, 0
            )

            dragEdge = a.getInteger(R.styleable.SwipeRevealLayout_dragEdge, DRAG_EDGE_LEFT)
            minFlingVelocity = a.getInteger(
                R.styleable.SwipeRevealLayout_flingVelocity,
                DEFAULT_MIN_FLING_VELOCITY
            )
            mode = a.getInteger(R.styleable.SwipeRevealLayout_dragMode, MODE_NORMAL)

            minDistRequestDisallowParent = a.getDimensionPixelSize(
                R.styleable.SwipeRevealLayout_minDistRequestDisallowParent,
                dpToPx(DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT)
            )
        }

        dragHelper = ViewDragHelper.create(this, 1.0f, mDragHelperCallback)
        dragHelper!!.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL)

        gestureDetector = GestureDetectorCompat(context, mGestureListener)
    }

    private fun pxToDp(px: Int): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (px / (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    companion object {
        // These states are used only for ViewBindHelper
        val STATE_CLOSE = 0
        val STATE_CLOSING = 1
        val STATE_OPEN = 2
        val STATE_OPENING = 3
        val STATE_DRAGGING = 4

        private val DEFAULT_MIN_FLING_VELOCITY = 300 // dp per second
        private val DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT = 1 // dp

        val DRAG_EDGE_LEFT = 0x1
        val DRAG_EDGE_RIGHT = 0x1 shl 1
        val DRAG_EDGE_TOP = 0x1 shl 2
        val DRAG_EDGE_BOTTOM = 0x1 shl 3

        /**
         * The secondary view will be under the main view.
         */
        val MODE_NORMAL = 0

        /**
         * The secondary view will stick the edge of the main view.
         */
        val MODE_SAME_LEVEL = 1

        fun getStateString(state: Int): String {
            when (state) {
                STATE_CLOSE -> return "state_close"

                STATE_CLOSING -> return "state_closing"

                STATE_OPEN -> return "state_open"

                STATE_OPENING -> return "state_opening"

                STATE_DRAGGING -> return "state_dragging"

                else -> return "undefined"
            }
        }
    }
}
