/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("MemberVisibilityCanBePrivate", "unused")



package androidx.slidingpanelayout.widget


import android.R.attr
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.accessibility.AccessibilityEvent
import android.widget.Toolbar
import androidx.annotation.IntDef
import androidx.annotation.Px
import androidx.annotation.VisibleForTesting
import androidx.appcompat.util.SeslMisc
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.use
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.forEach
import androidx.customview.widget.Openable
import androidx.customview.widget.ViewDragHelper
import androidx.slidingpanelayout.R
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/**
 * A custom layout based [androidx.slidingpanelayout.widget.SlidingPaneLayout]
 * heavily modified to emulate SESL behavior
 *
 */


class SlidingPaneLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ViewGroup(context, attrs, defStyle), Openable {

    var sliderFadeColor = DEFAULT_FADE_COLOR
    var coveredFadeColor = 0
    var slideRange = 0
        private set

    private var mShadowDrawable: Drawable? = null
    private val mOverhangSize: Int

    private var disableResize: Boolean = false
    private var setResizeChild = false
    private var resizeChild: View? = null
    private var resizeChildList: ArrayList<View>? = null

    private var drawRoundedCorner: Boolean = true
    private var mRoundedColor: Int = -1
    private var mSlidingPaneRoundedCorner: SlidingPaneRoundedCorner? = null
    private var slideableView: View? = null
    private var drawerPanel: View? = null
    private var currentSlideOffset = 0f
    private var mParallaxOffset = 0f

    private var isUnableToDrag = false
    private var mParallaxBy = 0
    private var initialMotionX = 0f
    private var initialMotionY = 0f
    private var mSetCustomPendingAction = false

    private var startMargin = 0
    private var startSlideX = 0
    private var mIsLock = false
    private var mPendingAction = -1
    private var mDoubleCheckState = -1

    private val mPanelSlideListeners = CopyOnWriteArrayList<PanelSlideListener>()
    private var mPanelSlideListener: PanelSlideListener? = null
    private val dragHelper: ViewDragHelper

    private var preservedOpenState = false
    private var awaitingFirstLayout = true
    private val tmpRect = Rect()
    private val mPostedRunnables = ArrayList<DisableLayerRunnable>()

    private var lastValidVelocity = 0
    private var mIsNeedClose = false
    private var mIsNeedOpen = false

    class SeslSlidingState internal constructor() {
        var state = 2
            private set

        fun onStateChanged(i10: Int) {
            state = i10
        }
    }

    interface PanelSlideListener {
        fun onPanelSlide(panel: View, slideOffset: Float)
        fun onPanelOpened(panel: View)
        fun onPanelClosed(panel: View)
    }


    class SimplePanelSlideListener : PanelSlideListener {
        override fun onPanelSlide(panel: View, slideOffset: Float) {}
        override fun onPanelOpened(panel: View) {}
        override fun onPanelClosed(panel: View) {}
    }

    private var mPrevOrientation = 0
    private var initialX = 0f

    private var velocityTracker: VelocityTracker? = null
    private var mIsNeedBlockDim = false
    private var drawerMarginBottom = 0
    private var drawerMarginTop = 0
    private var isSinglePanel = false
    private var mPrefContentWidthTypedValue: TypedValue? = null
    private var mPrefDrawerWidthTypedValue: TypedValue? = null
    private var userPreferredDrawerSize: Int = -1
    private var userPreferredContentSize: Int = -1

    private var mSlidingPaneDragArea = 0
    private val mSlidingState: SeslSlidingState?

    private var mPrevMotionX = 0f
    private var isSlideableViewTouched = false


    private var mSmoothWidth = 0
    private var isAnimating = false
    private var mStartOffset = 0f


    /**
     * Check if both the list and detail view panes in this layout can fully fit side-by-side. If
     * not, the content pane has the capability to slide back and forth. Note that the lock mode
     * is not taken into account in this method. This method is typically used to determine
     * whether the layout is showing two-pane or single-pane.
     */
    var isSlideable = false
        private set

    init{
        val density = context.resources.displayMetrics.density
        mOverhangSize = (DEFAULT_OVERHANG_SIZE * density + 0.5f).toInt()
        setWillNotDraw(false)

        val defaultSlidingPanelBgColor: Int = if (SeslMisc.isLightTheme(context)) {
            resources.getColor(R.color.sesl_sliding_pane_background_light, null)
        } else {
            resources.getColor(R.color.sesl_sliding_pane_background_dark, null)
        }

        context.obtainStyledAttributes(attrs, R.styleable.SlidingPaneLayout, defStyle, 0).use {
            isSinglePanel = it.getBoolean(R.styleable.SlidingPaneLayout_seslIsSinglePanel, false)
            drawRoundedCorner = it.getBoolean(R.styleable.SlidingPaneLayout_seslDrawRoundedCorner, true)
            mRoundedColor = it.getColor(
                R.styleable.SlidingPaneLayout_seslDrawRoundedCornerColor,
                defaultSlidingPanelBgColor
            )
            disableResize = it.getBoolean(R.styleable.SlidingPaneLayout_seslResizeOff, false)
            drawerMarginTop = it.getDimensionPixelSize(R.styleable.SlidingPaneLayout_seslDrawerMarginTop, 0)
            drawerMarginBottom = it.getDimensionPixelSize(R.styleable.SlidingPaneLayout_seslDrawerMarginBottom, 0)
            val prefDrawerWidthSize = R.styleable.SlidingPaneLayout_seslPrefDrawerWidthSize
            if (it.hasValue(prefDrawerWidthSize)) {
                val typedValue = TypedValue()
                it.getValue(prefDrawerWidthSize, typedValue)
                mPrefDrawerWidthTypedValue = typedValue
            }
            val prefContentWidthSize = R.styleable.SlidingPaneLayout_seslPrefContentWidthSize
            if (it.hasValue(prefContentWidthSize)) {
                val typedValue2 = TypedValue()
                it.getValue(prefContentWidthSize, typedValue2)
                mPrefContentWidthTypedValue = typedValue2
            }
        }

        ViewCompat.setAccessibilityDelegate(this, AccessibilityDelegate())
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)

        dragHelper = ViewDragHelper.seslCreate(this, 0.5f, DragHelperCallback())
        dragHelper.minVelocity = MIN_FLING_VELOCITY * density
        dragHelper.seslSetUpdateOffsetLR(disableResize)
        if (drawRoundedCorner) {
            mSlidingPaneRoundedCorner = SlidingPaneRoundedCorner(context).apply {
                roundedCorners = 0
                setMarginTop(drawerMarginTop)
                setMarginBottom(drawerMarginBottom)
            }
        }

        val defaultOpen = resources.getBoolean(R.bool.sesl_sliding_layout_default_open)
        mSlidingPaneDragArea = resources.getDimensionPixelSize(R.dimen.sesl_sliding_pane_contents_drag_width_default)
        mPendingAction = if (defaultOpen) PENDING_ACTION_EXPANDED else PENDING_ACTION_COLLAPSED
        mPrevOrientation = resources.configuration.orientation
        mSlidingState = SeslSlidingState()
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        LOCK_MODE_UNLOCKED,
        LOCK_MODE_LOCKED_OPEN,
        LOCK_MODE_LOCKED_CLOSED,
        LOCK_MODE_LOCKED
    )
    internal annotation class LockMode

    /**
     * The lock mode that controls how the user can swipe between the panes.
     */
    @get:LockMode
    @LockMode
    var lockMode =LOCK_MODE_UNLOCKED
        get() {
            Log.e(TAG, "getLockMode not work on SESL5")
            return LOCK_MODE_UNLOCKED
        }
        set(value) {
            Log.e(TAG,"setLockMode not work on SESL5")
            field = value
        }

    /**
     * Distance to parallax the lower pane by when the upper pane is in its
     * fully closed state, in pixels. The lower pane will scroll between this position and
     * its fully open state.
     */
    @get:Px
    var parallaxDistance: Int
        get() = mParallaxBy
        /**
         * The distance the lower pane will parallax by when the upper pane is fully closed.
         */
        set(@Px parallaxBy) {
            mParallaxBy = parallaxBy
            requestLayout()
        }


    /**
     * Set to true to disable auto resizing  width of contents in the contents pane.
     * The contents will just slide out when navigation rail/drawer is expanded.
     *
     * Default value: false
     *
     * @see seslSetResizeChild
     */
    fun seslSetResizeOff(resize: Boolean) {
        disableResize = resize
        dragHelper.seslSetUpdateOffsetLR(resize)
    }


    /**
     * Set specific views to be resized inside contents pane
     *
     * @see [seslSetResizeOff]
     */
    fun seslSetResizeChild(vararg view: View) {
        when{
            view.isEmpty() -> {
                setResizeChild = false
                resizeChildList = null
                resizeChild = null
            }
            view.size == 1 -> {
                setResizeChild = true
                resizeChild = view[0]
                resizeChildList = null
            }
            else -> {
                setResizeChild = true
                resizeChild = null
                resizeChildList = ArrayList(view.asList())
            }
        }
    }

    private fun findResizeChild(view: View) {
        if (!setResizeChild && view is ViewGroup) {
            if (view.childCount == 2) {
                resizeChild = view.getChildAt(1)
            }
        }
    }

    fun seslSetRoundedCornerColor(color: Int) {
        mRoundedColor = color
    }


    fun seslSetRoundedCornerOff(){
        this.drawRoundedCorner = false
        mSlidingPaneRoundedCorner = null
    }

    fun seslSetRoundedCornerOn(@Px radius: Int? = null){
        this.drawRoundedCorner = true
        mSlidingPaneRoundedCorner = SlidingPaneRoundedCorner(context).apply {
            if (radius != null) {
                roundedCornerRadius = radius
            }
            roundedCorners = 0
            setMarginTop(drawerMarginTop)
            setMarginBottom(drawerMarginBottom)
        }
    }


    fun addPanelSlideListener(listener: PanelSlideListener) {
        if (mPanelSlideListeners.contains(listener)) return
        mPanelSlideListeners.add(listener)
    }

    fun removePanelSlideListener(listener: PanelSlideListener) {
        mPanelSlideListeners.remove(listener)
    }

    fun dispatchOnPanelSlide(panel: View?) {
        for (listener in mPanelSlideListeners) {
            listener.onPanelSlide(panel!!, currentSlideOffset)
        }
        if (disableResize) {
            return
        }
        resizeSlideableView(currentSlideOffset)
    }

    fun dispatchOnPanelOpened(panel: View?) {
        mStartOffset = currentSlideOffset
        for (listener in mPanelSlideListeners) {
            listener.onPanelOpened(panel!!)
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    fun dispatchOnPanelClosed(panel: View?) {
        mStartOffset = currentSlideOffset
        for (listener in mPanelSlideListeners) {
            listener.onPanelClosed(panel!!)
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    fun updateObscuredViewsVisibility(panel: View?) {
        val isLayoutRtl = isLayoutRtlSupport
        val startBound = if (isLayoutRtl) width - paddingRight else paddingLeft
        val endBound = if (isLayoutRtl) paddingLeft else width - paddingRight
        val topBound = paddingTop
        val bottomBound = height - paddingBottom

        val left: Int
        val right: Int
        val top: Int
        val bottom: Int

        if (panel != null && hasOpaqueBackground(panel)) {
            left = panel.left
            right = panel.right
            top = panel.top
            bottom = panel.bottom
        } else{
            left = 0
            right = 0
            top = 0
            bottom = 0
        }

        forEach { child ->
            if (child === panel) {
                // There are still more children above the panel but they won't be affected.
                return
            }
            if (child.visibility != GONE) {
                val clampedChildLeft = (if (isLayoutRtl) endBound else startBound).coerceAtLeast(child.left)
                val clampedChildTop = topBound.coerceAtLeast(child.top)
                val clampedChildRight = (if (isLayoutRtl) startBound else endBound).coerceAtMost(child.right)
                val clampedChildBottom = bottomBound.coerceAtMost(child.bottom)
                child.visibility = if (clampedChildLeft >= left &&
                    clampedChildTop >= top &&
                    clampedChildRight <= right &&
                    clampedChildBottom <= bottom
                ) INVISIBLE else VISIBLE

            }
        }
    }

    fun setAllChildrenVisible() {
        var i = 0
        val childCount = childCount
        while (i < childCount) {
            val child = getChildAt(i)
            if (child.visibility == INVISIBLE) {
                child.visibility = VISIBLE
            }
            i++
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        awaitingFirstLayout = true
    }

    private var mPrevWindowVisibility = 0
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && mPrevWindowVisibility != VISIBLE) {
            mPendingAction = if (isOpen) {
                PENDING_ACTION_EXPANDED
            } else {
                PENDING_ACTION_COLLAPSED
            }
        }
        if (mPrevWindowVisibility != visibility) {
            mPrevWindowVisibility = visibility
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        awaitingFirstLayout = true
        val size = mPostedRunnables.size
        for (i in 0 until size) {
            mPostedRunnables[i].run()
        }
        mPostedRunnables.clear()
    }

    private val isLayoutRtlSupport: Boolean
        get() = ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        check(widthMode == MeasureSpec.EXACTLY) { "Width must have an exact value or MATCH_PARENT" }
        check(heightMode != MeasureSpec.UNSPECIFIED) { "Height must not be UNSPECIFIED" }

        var layoutHeight = 0
        var maxLayoutHeight = 0
        when (heightMode) {
            MeasureSpec.EXACTLY -> {
                maxLayoutHeight = heightSize - paddingTop - paddingBottom
                layoutHeight = maxLayoutHeight
            }
            MeasureSpec.AT_MOST -> {
                maxLayoutHeight = heightSize - paddingTop - paddingBottom
            }

            MeasureSpec.UNSPECIFIED -> {
                layoutHeight = 0
                maxLayoutHeight = 0
            }
        }

        var weightSum = 0f
        var canSlide = false
        val widthAvailable = widthSize - paddingLeft - paddingRight.coerceAtLeast(0)
        var widthRemaining = widthAvailable

        val childCount = childCount
        if (childCount > 2) {
            Log.e(TAG, "onMeasure: More than two child views are not supported.")
        }

        // We'll find the current one below.
        slideableView = null
        drawerPanel = null

        // First pass. Measure based on child LayoutParams width/height.
        // Weight will incur a second pass.
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams

            if (child.visibility == GONE) {
                lp.dimWhenOffset = false
                continue
            }

            if (lp.weight > 0f) {
                weightSum += lp.weight

                // If we have no width, weight is the only contributor to the final size.
                // Measure this view on the weight pass only.
                if (lp.width == 0) continue
            }

            val horizontalMargin = lp.leftMargin + lp.rightMargin
            val childWidthSize = (widthAvailable - horizontalMargin).coerceAtLeast(0)

            // var childWidthSpec: Int
            // When the parent width spec is UNSPECIFIED, measure each of child to get its
            // desired width
            val childWidthSpec = when (lp.width) {
                WRAP_CONTENT -> { //-2
                    if (lp.slideable) {
                        MeasureSpec.makeMeasureSpec(childWidthSize, MeasureSpec.AT_MOST)
                    } else {

                        var prefDrawerWidth: Int
                        if (userPreferredDrawerSize != -1) {
                            prefDrawerWidth = userPreferredDrawerSize
                        } else {
                            if (mPrefDrawerWidthTypedValue == null) {
                                mPrefDrawerWidthTypedValue = TypedValue()
                                resources.getValue(R.dimen.sesl_sliding_pane_drawer_width, mPrefDrawerWidthTypedValue, true)
                            }
                            prefDrawerWidth = when (mPrefDrawerWidthTypedValue!!.type) {
                                TypedValue.TYPE_FLOAT -> (windowWidth * mPrefDrawerWidthTypedValue!!.float).toInt()
                                TypedValue.TYPE_DIMENSION -> mPrefDrawerWidthTypedValue!!.getDimension(resources.displayMetrics).toInt()
                                else -> widthSize
                            }
                        }

                        if (prefDrawerWidth > widthSize) {
                            prefDrawerWidth = widthSize
                        }

                        MeasureSpec.makeMeasureSpec(prefDrawerWidth, MeasureSpec.EXACTLY)
                    }
                }

                MATCH_PARENT -> { //-1
                    MeasureSpec.makeMeasureSpec(childWidthSize, MeasureSpec.EXACTLY)
                }

                else -> {
                    var prefDrawerWidth: Int? = null
                    if (userPreferredDrawerSize != -1) {
                        prefDrawerWidth = userPreferredDrawerSize
                    }
                    MeasureSpec.makeMeasureSpec(prefDrawerWidth?:lp.width, MeasureSpec.EXACTLY)
                }
            }


            val childHeightSpec: Int  = when (lp.height) {
                WRAP_CONTENT -> {//-2
                    val heightSpec: Int = if (lp.slideable) {
                        maxLayoutHeight
                    } else {
                        (maxLayoutHeight - drawerMarginTop) - drawerMarginBottom
                    }
                    MeasureSpec.makeMeasureSpec(heightSpec, MeasureSpec.AT_MOST)
                }

                MATCH_PARENT -> {//-1
                    val heightSpec: Int = if (lp.slideable) {
                        maxLayoutHeight
                    } else {
                        (maxLayoutHeight - drawerMarginTop) - drawerMarginBottom
                    }
                    MeasureSpec.makeMeasureSpec(heightSpec, MeasureSpec.EXACTLY)
                }

                else -> {
                    MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                }
            }


            child.measure(childWidthSpec, childHeightSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (heightMode == MeasureSpec.AT_MOST && childHeight > layoutHeight) {
                layoutHeight = childHeight.coerceAtMost(maxLayoutHeight)
            }

            widthRemaining -= childWidth

            lp.slideable = widthRemaining < 0
            canSlide = canSlide or lp.slideable

            if (lp.slideable) {
                slideableView = child
            }else{
                drawerPanel = child
            }
        }


        // Resolve weight and make sure non-sliding panels are smaller than the full screen.
        if (canSlide || weightSum > 0) {

            val fixedPanelWidthLimit = widthSize - mOverhangSize

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == GONE) continue
                val lp = child.layoutParams as LayoutParams

                val skippedFirstPass = lp.width == 0 && lp.weight > 0
                val measuredWidth = if (skippedFirstPass) 0 else child.measuredWidth

                if (canSlide && child != slideableView) {
                    if (lp.width < 0 && (measuredWidth > fixedPanelWidthLimit || lp.weight > 0)) {
                        // Fixed panels in a sliding configuration should
                        // be clamped to the fixed panel limit.
                        val childHeightSpec = if (skippedFirstPass) {
                            // Do initial height measurement if we skipped measuring this view
                            // the first time around.
                            when (lp.height) {
                                WRAP_CONTENT -> MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.AT_MOST)
                                MATCH_PARENT -> MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.EXACTLY)
                                else -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                            }
                        } else {
                            MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
                        }
                        val childWidthSpec = MeasureSpec.makeMeasureSpec(fixedPanelWidthLimit, MeasureSpec.EXACTLY)

                        child.measure(childWidthSpec, childHeightSpec)
                    }
                } else if (lp.weight > 0) {
                    val childHeightSpec = if (lp.width == 0) {
                        // This was skipped the first time; figure out a real height spec.
                        when (lp.height) {
                            WRAP_CONTENT -> MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.AT_MOST)
                            MATCH_PARENT -> MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.EXACTLY)
                            else -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                        }
                    } else {
                        MeasureSpec.makeMeasureSpec(child.measuredHeight, MeasureSpec.EXACTLY)
                    }

                    if (canSlide) {
                        // Consume available space
                        val horizontalMargin = lp.leftMargin + lp.rightMargin
                        val newWidth = widthSize - horizontalMargin
                        val childWidthSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
                        if (measuredWidth != newWidth) { child.measure(childWidthSpec, childHeightSpec) }
                    } else {
                        // Distribute the extra width proportionally similar to LinearLayout
                        val widthToDistribute = widthRemaining.coerceAtLeast(0)
                        val addedWidth = (lp.weight * widthToDistribute / weightSum).toInt()
                        val childWidthSpec = MeasureSpec.makeMeasureSpec(
                            measuredWidth + addedWidth, MeasureSpec.EXACTLY
                        )
                        child.measure(childWidthSpec, childHeightSpec)
                    }
                }
            }
        }
        setMeasuredDimension(widthSize, layoutHeight)
        isSlideable = canSlide

        if (dragHelper.viewDragState != ViewDragHelper.STATE_IDLE && !canSlide) {
            // Cancel scrolling in progress, it's no longer relevant.
            dragHelper.abort()
        }
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val isLayoutRtl = isLayoutRtlSupport
        if (isLayoutRtl) {
            dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_RIGHT)
        } else {
            dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT)
        }

        val width = r - l
        val paddingStart = if (isLayoutRtl) paddingRight else paddingLeft
        val paddingEnd = if (isLayoutRtl) paddingLeft else paddingRight
        val paddingTop = paddingTop
        val childCount = childCount

        var xStart = paddingStart
        var nextXStart = xStart

        if (awaitingFirstLayout) {
            currentSlideOffset = if (isSlideable && preservedOpenState) 1f else 0f
        }

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) {
                continue
            }

            val lp = child.layoutParams as LayoutParams
            val childWidth = child.measuredWidth
            var offset = 0

            if (lp.slideable) {
                val margin = lp.leftMargin + lp.rightMargin
                val range = nextXStart.coerceAtMost(childWidth - paddingEnd - mOverhangSize) - xStart - margin
                slideRange = range
                val lpMargin = if (isLayoutRtl) lp.rightMargin else lp.leftMargin
                startMargin = lpMargin
                lp.dimWhenOffset = xStart + lpMargin + range + childWidth / 2 > width - paddingEnd
                val pos = (range * currentSlideOffset).toInt()
                xStart += pos + lpMargin
                currentSlideOffset = pos.toFloat() / slideRange
            } else if (isSlideable && mParallaxBy != 0) {
                offset = ((1 - currentSlideOffset) * mParallaxBy).toInt()
                xStart = nextXStart
            } else {
                xStart = nextXStart
            }


            val childRight: Int
            val childLeft: Int
            if (isLayoutRtl) {
                childRight = width - xStart + offset
                childLeft = childRight - childWidth
            } else {
                childLeft = xStart - offset
                childRight = childLeft + childWidth
            }


            val childTop = paddingTop + if (i == 0) drawerMarginTop else 0
            val childBottom = childTop + child.measuredHeight

            child.layout(childLeft, childTop, childRight, childBottom)

            nextXStart += child.width

            if (lp.slideable){
                startSlideX = if (isLayoutRtl) lp.rightMargin else lp.leftMargin
            }else{
                fixedPaneStartX = if (isLayoutRtl) lp.rightMargin else 0
            }
        }

        if (awaitingFirstLayout) {
            if (isSlideable) {
                if (mParallaxBy != 0) {
                    parallaxOtherViews(currentSlideOffset)
                }
                if ((slideableView!!.layoutParams as LayoutParams).dimWhenOffset) {
                    dimChildView(slideableView, currentSlideOffset, sliderFadeColor)
                }
            } else {
                // Reset the dim level of all children; it's irrelevant when nothing moves.
                for (i in 0 until childCount) {
                    dimChildView(getChildAt(i), 0f, sliderFadeColor)
                }
            }
            updateObscuredViewsVisibility(slideableView)
        }
        awaitingFirstLayout = false

        updateSlidingState()

        when (mPendingAction) {
            PENDING_ACTION_EXPANDED -> { //1
                if (mIsLock) {
                    resizeSlideableView(1.0f)
                }
                openPane(false)
                mPendingAction = PENDING_ACTION_NONE
            }

            PENDING_ACTION_COLLAPSED -> {//2
                if (mIsLock) {
                    resizeSlideableView(0.0f)
                }
                closePane(false)
                mPendingAction = PENDING_ACTION_NONE
            }

            PENDING_ACTION_EXPANDED_LOCK -> { //257
                mIsLock = false
                openPane(false)
                // mDoubleCheckState = 1
                mIsLock = true
                mPendingAction = PENDING_ACTION_NONE
            }

            PENDING_ACTION_COLLAPSED_LOCK -> { //258
                mIsLock = false
                closePane(false)
                //mDoubleCheckState = 0
                mIsLock = true
                mPendingAction = PENDING_ACTION_NONE
            }
        }


        /*if (mDoubleCheckState != -1) {
            if (mDoubleCheckState == 1) {
                openPane(0, true)
            } else if (mDoubleCheckState == 0) {
                closePane(0, true)
            }
            mDoubleCheckState = -1
            return
        }*/
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Recalculate sliding panes and their details
        if (w != oldw) {
            awaitingFirstLayout = true
        }
    }

    fun seslSetPendingAction(action: Int) {
        if (action != PENDING_ACTION_NONE &&
            action != PENDING_ACTION_COLLAPSED &&
            action != PENDING_ACTION_EXPANDED &&
            action != PENDING_ACTION_EXPANDED_LOCK &&
            action != PENDING_ACTION_COLLAPSED_LOCK) {
            mSetCustomPendingAction = false
            Log.e(
                TAG,
                "pendingAction value is wrong ==> Your pending action value is [$action] / Now set pendingAction value as default"
            )
            return
        }
        mSetCustomPendingAction = true
        mPendingAction = action
    }



    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        if (!mSetCustomPendingAction) {
            if (configuration.orientation != mPrevOrientation) {
                mPendingAction =  if (isOpen){
                    PENDING_ACTION_EXPANDED
                } else {
                    PENDING_ACTION_COLLAPSED
                }
            }
        }
        if (mIsLock) {
            mPendingAction = if (isOpen) {
                PENDING_ACTION_EXPANDED
            } else {
                PENDING_ACTION_COLLAPSED
            }
        }
        mPrevOrientation = configuration.orientation
        seslSetDrawerPaneWidth()
    }

    private fun seslSetDrawerPaneWidth() {
        val drawerWidth: Int
        if (drawerPanel == null) {
            Log.e(TAG, "mDrawerPanel is null")
            return
        }
        val typedValue = TypedValue()
        resources.getValue(R.dimen.sesl_sliding_pane_drawer_width, typedValue, true)
        val drawerWidthRes = typedValue.type
        drawerWidth = when (drawerWidthRes) {
            TypedValue.TYPE_FLOAT -> (windowWidth * typedValue.float).toInt()
            TypedValue.TYPE_DIMENSION -> typedValue.getDimension(resources.displayMetrics).toInt()
            else -> MATCH_PARENT
        }
        if (drawerWidth != MATCH_PARENT) {
            val layoutParams = drawerPanel!!.layoutParams
            layoutParams.width = drawerWidth
            drawerPanel!!.layoutParams = layoutParams
        }
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        if (!isInTouchMode && !isSlideable) {
            preservedOpenState = child === slideableView
        }
    }


    private fun setVelocityTracker(motionEvent: MotionEvent) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        } else {
            velocityTracker!!.clear()
        }
        velocityTracker!!.addMovement(motionEvent)
    }



    fun resizeSlideableView(slideOffset: Float) {
        var preferredWidth: Int
        val availableWidth = width - paddingLeft - paddingRight

        if (slideableView is ViewGroup) {
            val slideViewHorizontalPadding = slideableView!!.paddingStart + slideableView!!.paddingEnd
            val childCount = (slideableView as ViewGroup).childCount

            for (i in 0 until childCount) {
                val slideViewChild = (slideableView as ViewGroup).getChildAt(i)
                val slideViewChildLP = slideViewChild.layoutParams ?: continue

                var maxWidth = (
                        (width - startSlideX)
                                - (slideRange * slideOffset).toInt()
                                - slideViewHorizontalPadding
                                - (slideViewChild.paddingStart + slideViewChild.paddingEnd)
                        )

                if (userPreferredContentSize != -1) {
                    preferredWidth = userPreferredContentSize
                } else {
                    if (mPrefContentWidthTypedValue == null) {
                        mPrefContentWidthTypedValue = TypedValue()
                        resources.getValue(R.dimen.sesl_sliding_pane_contents_width, mPrefContentWidthTypedValue, true)
                    }
                    preferredWidth = when (mPrefContentWidthTypedValue!!.type) {
                        TypedValue.TYPE_FLOAT -> (availableWidth * mPrefContentWidthTypedValue!!.float).toInt()
                        TypedValue.TYPE_DIMENSION -> mPrefContentWidthTypedValue!!.getDimension(resources.displayMetrics).toInt()
                        else -> maxWidth
                    }
                }


                if (setResizeChild) {
                    if (resizeChildList == null) {
                        if (resizeChild != null) {
                            val resizeChildLp = resizeChild!!.layoutParams as MarginLayoutParams
                            val resizeChildHorizontalMargin = resizeChildLp.leftMargin + resizeChildLp.rightMargin
                            val resizeChildWidth = min(maxWidth - resizeChildHorizontalMargin, preferredWidth)
                            resizeChildLp.width = resizeChildWidth
                        }
                    } else {
                        for (v in resizeChildList!!) {
                            val resizeChildLp = v.layoutParams as MarginLayoutParams
                            val resizeChildHorizontalMargin = resizeChildLp.leftMargin + resizeChildLp.rightMargin
                            val resizeChildWidth = min(maxWidth - resizeChildHorizontalMargin, preferredWidth)
                            resizeChildLp.width = resizeChildWidth
                        }
                    }
                } else if (isSinglePanel && !isToolbar(slideViewChild)) {
                    if (slideViewChild is CoordinatorLayout) {
                        findResizeChild(slideViewChild)
                        if (resizeChild != null) {
                            val resizeChildLp = resizeChild!!.layoutParams as MarginLayoutParams
                            val resizeChildHorizontalMargin = resizeChildLp.leftMargin + resizeChildLp.rightMargin
                            val resizeChildWidth = min(maxWidth - resizeChildHorizontalMargin, preferredWidth)
                            resizeChildLp.width = resizeChildWidth
                        }
                    } else {
                        maxWidth =  min(maxWidth, preferredWidth)
                    }
                }

                slideViewChildLP.width = maxWidth
                slideViewChild.requestLayout()
            }
        }
    }


    private val windowWidth: Int
        get() = resources.displayMetrics.widthPixels

    private  fun completeSlideIfNeeded(): Boolean {
        mDoubleCheckState = -1
        if (!isAnimating) {
            if (currentSlideOffset != 0.0f && currentSlideOffset != 1.0f) {
                if (currentSlideOffset >= 0.5f) {
                    mDoubleCheckState = 1
                    seslOpenPane(true)
                } else {
                    mDoubleCheckState = 0
                    seslClosePane(true)
                }
                return true
            }
        }
        return false
    }



    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {

        if (!isSlideable || mIsLock) {
            return super.onTouchEvent(ev)
        }

        dragHelper.processTouchEvent(ev)
        setVelocityTracker(ev)
        val action = ev.actionMasked

        val wantTouchEvents = true

        when (action) {

            MotionEvent.ACTION_DOWN -> {
                val x = ev.x
                val y = ev.y
                initialMotionX = x
                initialMotionY = y
                mStartOffset = currentSlideOffset
                mIsNeedOpen = false
                mIsNeedClose = false
                mPrevMotionX = x
                mSmoothWidth = 0

            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL-> {

                if (isDimmed(slideableView)) {
                    val x = ev.x
                    val y = ev.y
                    val dx = x - initialMotionX
                    val dy = y - initialMotionY
                    val slop = dragHelper.touchSlop
                    if (dx * dx + dy * dy < slop * slop &&
                        dragHelper.isViewUnder(slideableView, x.toInt(), y.toInt())
                    ) {
                        // Taps close a dimmed open pane.
                        closePane()
                    }
                }

                if (!isUnableToDrag){
                    mSmoothWidth = slideableView!!.width
                    completeSlideIfNeeded()
                }
            }

            MotionEvent.ACTION_MOVE ->{
                val x = ev.x
                val dx =  x - initialMotionX
                val adx = abs(x - initialMotionX)
                /*var incx = x - mPrevMotionX
                if (mPrevMotionX != x) {
                    mPrevMotionX = x
                }*/

                if (!isUnableToDrag && adx > dragHelper.touchSlop) {
                    if (!isSlideableViewTouched) {
                        val newLeft = getNewLeftForSwipe(dx)
                        onPanelDragged(newLeft)
                    }
                }
            }
        }
        return wantTouchEvents
    }


    private fun getNewLeftForSwipe(dx: Float): Int {
        val addOffset = (dx / slideRange)
        return if (!isLayoutRtlSupport) {
            val newOffset = (mStartOffset + addOffset).coerceIn(0f, 1f)
            val remainingRange = (newOffset * slideRange).toInt()
            startSlideX + remainingRange
        }else{
            val newOffset = (mStartOffset - addOffset).coerceIn(0f, 1f)
            (newOffset * slideRange).toInt() * -1
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        // Preserve the open state based on the last view that was touched.
        if (!isSlideable && action == MotionEvent.ACTION_DOWN && childCount > 1) {
            // After the first things will be slideable.
            if (slideableView != null) {
                preservedOpenState = !dragHelper.isViewUnder(slideableView, ev.x.toInt(), ev.y.toInt())
            }
        }

        if (!isSlideable || isUnableToDrag && action != MotionEvent.ACTION_DOWN) {
            dragHelper.cancel()
            return super.onInterceptTouchEvent(ev)
        }

        if ( action ==  MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            dragHelper.cancel()
            return completeSlideIfNeeded()
        }

        var interceptTap = false

        when (action) {

            MotionEvent.ACTION_DOWN -> { //0

                isUnableToDrag = false
                mSmoothWidth = 0
                mIsNeedOpen = false
                mIsNeedClose = false

                val x = ev.x
                val y = ev.y
                initialMotionX = x
                initialMotionY = y
                mStartOffset = currentSlideOffset

                val slideViewStart = if (isLayoutRtlSupport) slideableView!!.right else slideableView!!.left

                if (isLayoutRtlSupport) {
                    if (x < slideViewStart - mSlidingPaneDragArea || mIsLock) {
                        dragHelper.cancel()
                        isUnableToDrag = true
                    }
                } else if (x > slideViewStart + mSlidingPaneDragArea || mIsLock) {
                    dragHelper.cancel()
                    isUnableToDrag = true
                }

                isSlideableViewTouched = dragHelper.isViewUnder(slideableView, x.toInt(), y.toInt())

                if (isSlideableViewTouched && isDimmed(slideableView)) {
                    interceptTap = true
                }

            }


            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - initialMotionX
                val adx = abs(dx)

                if (!isUnableToDrag && adx > dragHelper.touchSlop) {
                    if (!isSlideableViewTouched) {
                        val newLeft = getNewLeftForSwipe(dx)
                        onPanelDragged(newLeft)
                        return true
                    }
                }
            }
        }

        if (abs(mStartOffset - currentSlideOffset) < 0.1f) {
            return false
        }


        return interceptTap || dragHelper.shouldInterceptTouchEvent(ev)
    }

    private fun isTouchWithinView(touchX: Float, view: View): Boolean {
        return touchX >= view.left && touchX <= view.right
    }

    fun seslOpenPane(animate: Boolean) {
        lastValidVelocity = 0
        mIsNeedOpen = true
        mIsNeedClose = false
        openPane(animate)
    }
    fun seslClosePane(animate: Boolean) {
        lastValidVelocity = 0
        mIsNeedOpen = false
        mIsNeedClose = true
        closePane(animate)

    }

    fun seslGetLock(): Boolean {
        return mIsLock
    }

    fun seslGetPreferredContentPixelSize(): Int {
        return userPreferredContentSize
    }

    fun seslGetPreferredDrawerPixelSize(): Int {
        return userPreferredDrawerSize
    }


    fun seslRequestPreferredDrawerPixelSize(drawerPixelSize: Int) {
        userPreferredDrawerSize = drawerPixelSize
        resizeSlideableView(currentSlideOffset)
    }

    fun seslSetBlockDimWhenOffset(needBlockDim: Boolean) {
        mIsNeedBlockDim = needBlockDim
    }
    fun seslGetResizeOff(): Boolean {
        return disableResize
    }
    private fun isToolbar(view: View): Boolean {
        return view is Toolbar || view is SPLToolbarContainer
    }

    fun setSinglePanel(isSinglePanel: Boolean) {
        this.isSinglePanel = isSinglePanel
    }

    fun getSinglePanelStatus(): Boolean {
        return isSinglePanel
    }



    /**
     * Collapse the nav rail pane.
     *
     * @return true if the nav rail pane is now opened/in the process of opening
     */
    private fun openPane(): Boolean {
        mIsNeedOpen = true
        mIsNeedClose = false
        return openPane(true xor shouldSkipScroll())
    }


    private var fixedPaneStartX = 0


    private fun openPane(smoothSlide: Boolean): Boolean {

        if (isAnimating) {
            return true
        }

        if (slideableView == null || mIsLock) {
            return false
        }

        if (smoothSlide) {
            if (awaitingFirstLayout || smoothSlideTo(1.0f)) {
                preservedOpenState = true
                mIsNeedOpen = false
                return true
            }
            return false
        }

        val newLeft =  if (isLayoutRtlSupport) fixedPaneStartX - slideRange else startSlideX + slideRange
        onPanelDragged(newLeft)

        if (disableResize) {
            val windowWidth = windowWidth
            if (isLayoutRtlSupport) {
                slideableView!!.right = windowWidth - startMargin - slideRange
                slideableView!!.left = slideableView!!.right - (windowWidth - startMargin)
            } else {
                slideableView!!.left = newLeft
                slideableView!!.right = newLeft + windowWidth - startMargin
            }
        } else {
            resizeSlideableView(1.0f)
        }
        preservedOpenState = true
        return true
    }



    /**
     * Collapses the nav rail pane.
     *
     * @return true if the nav rail pane is now closed/in the process of closing
     */
    private fun closePane(): Boolean {
        mIsNeedOpen = false
        mIsNeedClose = true
        return closePane(true xor shouldSkipScroll())
    }



    private fun closePane(animate: Boolean): Boolean {

        if (isAnimating) {
            return true
        }

        if (slideableView == null || mIsLock) {
            return false
        }

        if (animate) {
            if (awaitingFirstLayout || smoothSlideTo(0.0f)) {
                preservedOpenState = false
                //Fix on RTL
                mIsNeedClose = false
                return true
            }
            return false
        }

        val newLeft = if (isLayoutRtlSupport) slideRange else startMargin
        onPanelDragged(newLeft)

        if (disableResize) {
            if (isLayoutRtlSupport) {
                slideableView!!.right = windowWidth - startMargin
                slideableView!!.left = slideableView!!.right - windowWidth + startMargin
            } else {
                slideableView!!.left = if (isLayoutRtlSupport) slideRange else startMargin
            }
        }else{
            resizeSlideableView(0.0f)
        }
        preservedOpenState = false
        return true
    }




    private fun onPanelDragged(newLeft: Int) {

        if (slideableView == null) {
            // This can happen if we're aborting motion during layout because everything now fits.
            currentSlideOffset = 0f
            return
        }

        if (mIsLock) {
            return
        }

        val isLayoutRtl = isLayoutRtlSupport
        val lp = slideableView!!.layoutParams as LayoutParams
        var childWidth = slideableView!!.width
        val paddingStart = if (isLayoutRtl) paddingRight else paddingLeft
        val lpMargin = if (isLayoutRtl) lp.rightMargin else lp.leftMargin
        val startBound = paddingStart + lpMargin

        if (isLayoutRtl && disableResize) {
            childWidth = width - startBound
        } else if (mIsNeedClose) {
            childWidth = max(width - slideRange - startBound, mSmoothWidth)
        } else if (mIsNeedOpen) {
            if (mSmoothWidth == 0) {
                mSmoothWidth = width - startBound
            }
            childWidth = min(width - startBound, mSmoothWidth)
        }

        val newStart = if (isLayoutRtl) {
            width - newLeft - childWidth
        }  else {
            newLeft
        }

        val offset = ((newStart - startBound) * 1f / slideRange)
        currentSlideOffset = if (offset <= 1) max(0f, offset) else 1f

        if (velocityTracker != null && velocityTracker!!.xVelocity != 0.0f) {
            lastValidVelocity = velocityTracker!!.xVelocity.toInt()
        }

        updateSlidingState()

        if (mParallaxBy != 0) {
            parallaxOtherViews(currentSlideOffset)
        }
        if (lp.dimWhenOffset) {
            dimChildView(slideableView, currentSlideOffset, sliderFadeColor)
        }

        dispatchOnPanelSlide(slideableView)
    }



    private fun dimChildView(v: View?, mag: Float, fadeColor: Int) {
        val lp = v!!.layoutParams as LayoutParams
        if (mag > 0 && fadeColor != 0) {
            val baseAlpha = fadeColor and -0x1000000 ushr 24
            val imag = (baseAlpha * mag).toInt()
            val color = imag shl 24 or (fadeColor and 0xffffff)
            if (lp.dimPaint == null) {
                lp.dimPaint = Paint()
            }
            lp.dimPaint!!.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_OVER)
            if (v.layerType != LAYER_TYPE_HARDWARE) {
                v.setLayerType(LAYER_TYPE_HARDWARE, lp.dimPaint)
            }
            invalidateChildRegion(v)
        } else if (v.layerType != LAYER_TYPE_NONE) {
            if (lp.dimPaint != null) {
                lp.dimPaint!!.colorFilter = null
            }
            val dlr = DisableLayerRunnable(v)
            mPostedRunnables.add(dlr)
            ViewCompat.postOnAnimation(this, dlr)
        }
    }

    override fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long
    ): Boolean {
        /*if (isSlideable) {
            val gestureInsets = systemGestureInsets
            if (isLayoutRtlSupport xor isOpen) {
                overlappingPaneHandler.setEdgeTrackingEnabled(
                    ViewDragHelper.EDGE_LEFT,
                    gestureInsets?.left ?: 0
                )
            } else {
                overlappingPaneHandler.setEdgeTrackingEnabled(
                    ViewDragHelper.EDGE_RIGHT,
                    gestureInsets?.right ?: 0
                )
            }
        } else {
            overlappingPaneHandler.disableEdgeTracking()
        }*/
        val lp = child.layoutParams as LayoutParams
        val save = canvas.save()
        if (isSlideable && !lp.slideable && slideableView != null) {
            // Clip against the slider; no sense drawing what will immediately be covered.
            canvas.getClipBounds(tmpRect)
            if (isLayoutRtlSupport) {
                tmpRect.left = max(tmpRect.left, slideableView!!.right)
            } else {
                tmpRect.right = min(tmpRect.right, slideableView!!.left)
            }
            canvas.clipRect(tmpRect)
        }

        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restoreToCount(save)
        return result
    }

    private fun invalidateChildRegion(v: View?) {
        IMPL.invalidateChildRegion(this, v)
    }


    fun smoothSlideTo(slideOffset: Float): Boolean {
        isAnimating = false

        if (!isSlideable) {
            // Nothing to do.
            return false
        }

        val lp = slideableView!!.layoutParams as LayoutParams
        val x: Int = if (isLayoutRtlSupport) {
            val startBound = paddingRight + lp.rightMargin
            val childWidth = slideableView!!.width
            (width - (startBound + slideOffset * slideRange + childWidth)).toInt()
        } else {
            val startBound = paddingLeft + lp.leftMargin
            (startBound + slideOffset * slideRange).toInt()
        }

        if (dragHelper.smoothSlideViewTo(slideableView!!, x, slideableView!!.top)) {
            setAllChildrenVisible()
            ViewCompat.postInvalidateOnAnimation(this)
            isAnimating = true
            return true
        }
        return false
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            if (!isSlideable) {
                dragHelper.abort()
                return
            }
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }


    fun setShadowDrawable(d: Drawable?) {
        mShadowDrawable = d
    }


    fun setShadowResource(resId: Int) {
        setShadowDrawable(resources.getDrawable(resId, context.theme))
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (drawRoundedCorner && slideableView != null) {
            mSlidingPaneRoundedCorner!!.setRoundedCornerColor(mRoundedColor)
            mSlidingPaneRoundedCorner!!.drawRoundedCorner(slideableView!!, canvas)
        }
    }

    override fun draw(c: Canvas) {
        super.draw(c)

        val shadowView = if (childCount > 1) getChildAt(1) else null
        if (shadowView == null || mShadowDrawable == null) {
            // No need to draw a shadow if we don't have one.
            return
        }

        val top = shadowView.top
        val bottom = shadowView.bottom
        val shadowWidth = mShadowDrawable!!.intrinsicWidth

        val left: Int
        val right: Int
        if (isLayoutRtlSupport) {
            left = shadowView.right
            right = left + shadowWidth
        } else {
            right = shadowView.left
            left = right - shadowWidth
        }

        mShadowDrawable!!.setBounds(left, top, right, bottom)
        mShadowDrawable!!.draw(c)
    }

    private fun parallaxOtherViews(slideOffset: Float) {
        val slideLp = slideableView!!.layoutParams as LayoutParams
        val dimViews = slideLp.dimWhenOffset && slideLp.leftMargin <= 0
        val childCount = childCount
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v === slideableView) continue
            val oldOffset = ((1 - mParallaxOffset) * mParallaxBy).toInt()
            mParallaxOffset = slideOffset
            val newOffset = ((1 - slideOffset) * mParallaxBy).toInt()
            val dx = oldOffset - newOffset
            v.offsetLeftAndRight(if (isLayoutRtlSupport) -dx else dx)
            if (dimViews) {
                dimChildView(v, 1 - mParallaxOffset, coveredFadeColor)
            }
        }
    }

    @VisibleForTesting
    fun canScroll(v: View, checkV: Boolean, dx: Int, x: Int, y: Int): Boolean {
        if (v is ViewGroup) {
            val scrollX = v.getScrollX()
            val scrollY = v.getScrollY()
            val count = v.childCount
            // Count backwards - let topmost views consume scroll distance first.
            for (i in count - 1 downTo 0) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                val child = v.getChildAt(i)
                if (x + scrollX >= child.left &&
                    x + scrollX < child.right &&
                    y + scrollY >= child.top &&
                    y + scrollY < child.bottom &&
                    canScroll(
                        child, true, dx, x + scrollX - child.left, y + scrollY - child.top
                    )
                ) {
                    return true
                }
            }
        }
        return checkV && v.canScrollHorizontally(if (isLayoutRtlSupport) dx else -dx)
    }

    fun isDimmed(child: View?): Boolean {
        if (child == null) {
            return false
        }
        val lp = child.layoutParams as LayoutParams
        return isSlideable && lp.dimWhenOffset && currentSlideOffset > 0
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams()
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams {
        return if (p is MarginLayoutParams) LayoutParams(
            p
        ) else LayoutParams(p)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean {
        return p is LayoutParams && super.checkLayoutParams(p)
    }

    override fun generateLayoutParams(attrs: AttributeSet): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.isOpen = if (isSlideable) isOpen else preservedOpenState
        return ss
    }

    override fun onRestoreInstanceState(parcelable: Parcelable) {
        if (parcelable !is SavedState) {
            super.onRestoreInstanceState(parcelable)
            return
        }
        super.onRestoreInstanceState(parcelable.superState)
        if (parcelable.isOpen) {
            openPane()
        } else {
            closePane()
        }
        preservedOpenState = parcelable.isOpen

    }

    private inner class DragHelperCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            return if (isUnableToDrag) {
                false
            } else (child.layoutParams as LayoutParams).slideable
        }

        override fun onViewDragStateChanged(state: Int) {
            if (dragHelper.viewDragState == ViewDragHelper.STATE_IDLE) {
                isAnimating = false
                preservedOpenState = if (currentSlideOffset == 0f) {
                    updateObscuredViewsVisibility(slideableView)
                    dispatchOnPanelClosed(slideableView)
                    false
                } else {
                    dispatchOnPanelOpened(slideableView)
                    true
                }
            }
        }

        override fun onViewCaptured(capturedChild: View, activePointerId: Int) {
            // Make all child views visible in preparation for sliding things around
            setAllChildrenVisible()
        }
        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            if (mStartOffset != 0.0f ||
                lastValidVelocity <= 0 ||
                currentSlideOffset <= 0.2f
            ) {
                if (mStartOffset == 1.0f &&
                    lastValidVelocity < 0 &&
                    currentSlideOffset < 0.8f &&
                    dx > 0
                ) return

            } else if (dx < 0) return

            onPanelDragged(left)
            invalidate()
        }


        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val lp = releasedChild.layoutParams as LayoutParams
            var left: Int
            if (isLayoutRtlSupport) {
                var startToRight = paddingRight + lp.rightMargin
                if (xvel < 0 || xvel == 0f && currentSlideOffset > 0.5f) {
                    startToRight += slideRange
                }
                val childWidth = slideableView!!.width
                left = width - startToRight - childWidth
            } else {
                left =  paddingLeft + lp.leftMargin
                if (xvel > 0 || xvel == 0f && currentSlideOffset > 0.5f) {
                    left += slideRange
                }
            }
            dragHelper.settleCapturedViewAt(left, releasedChild.top)
            invalidate()
        }

        override fun getViewHorizontalDragRange(child: View): Int {
            return slideRange
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            var newLeft = left
            val lp = slideableView!!.layoutParams as LayoutParams
            newLeft = if (isLayoutRtlSupport) {
                val startBound = width - (paddingRight + lp.rightMargin + slideableView!!.width)
                val endBound = startBound - slideRange
                newLeft.coerceIn(endBound, startBound)
            } else {
                val startBound =  paddingLeft + lp.leftMargin
                val endBound = startBound + slideRange
                newLeft.coerceIn(startBound, endBound)
            }
            return newLeft
        }

        override fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {
            dragHelper.captureChildView(slideableView!!, pointerId)
        }
    }

    class LayoutParams : MarginLayoutParams {

        var weight = 0f
        var slideable = false
        var dimWhenOffset = false
        var dimPaint: Paint? = null

        constructor() : super(MATCH_PARENT, MATCH_PARENT)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: MarginLayoutParams?) : super(source)
        constructor(source: LayoutParams) : super(source) {
            weight = source.weight
        }

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            val a = c.obtainStyledAttributes(attrs, ATTRS)
            weight = a.getFloat(0, 0f)
            a.recycle()
        }

        companion object {
            private val ATTRS = intArrayOf(attr.layout_weight)
        }
    }

    internal class SavedState : BaseSavedState {
        var isOpen = false

        @LockMode
        var mLockMode = 0
        constructor(superState: Parcelable?) : super(superState)
        private constructor(parcelIn: Parcel) : super(parcelIn) {
            isOpen = parcelIn.readInt() != 0
            mLockMode = parcelIn.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (isOpen) 0 else 1)
            out.writeInt(mLockMode)

        }

        companion object  CREATOR: Creator<SavedState> {
            override fun createFromParcel(p0: Parcel): SavedState {
                return SavedState(p0)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    interface SlidingPanelLayoutImpl {
        fun invalidateChildRegion(parent: SlidingPaneLayout?, child: View?)
    }

    internal open class SlidingPanelLayoutImplBase : SlidingPanelLayoutImpl {
        override fun invalidateChildRegion(parent: SlidingPaneLayout?, child: View?) {
            ViewCompat.postInvalidateOnAnimation(
                parent!!, child!!.left, child.top,
                child.right, child.bottom
            )
        }
    }


    internal class SlidingPanelLayoutImplJBMR1 : SlidingPanelLayoutImplBase() {
        override fun invalidateChildRegion(parent: SlidingPaneLayout?, child: View?) {
            ViewCompat.setLayerPaint(child!!, (child.layoutParams as LayoutParams).dimPaint)
        }
    }

    internal inner class AccessibilityDelegate : AccessibilityDelegateCompat() {
        private val mTmpRect = Rect()
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat
        ) {
            val superNode = AccessibilityNodeInfoCompat.obtain(info)
            super.onInitializeAccessibilityNodeInfo(host, superNode)
            info.setSource(host)
            val parent = ViewCompat.getParentForAccessibility(host)
            if (parent is View) {
                info.setParent(parent as View)
            }
            copyNodeInfoNoChildren(info, superNode)
            superNode.recycle()
            val childCount = childCount
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (noFilter(child)) {
                    info.addChild(child)
                }
            }
        }

        override fun onRequestSendAccessibilityEvent(
            host: ViewGroup, child: View,
            event: AccessibilityEvent
        ): Boolean {
            return if (noFilter(child)) {
                super.onRequestSendAccessibilityEvent(host, child, event)
            } else false
        }

        fun noFilter(child: View?): Boolean {
            return !isDimmed(child)
        }


        private fun copyNodeInfoNoChildren(
            dest: AccessibilityNodeInfoCompat,
            src: AccessibilityNodeInfoCompat
        ) {
            val rect = mTmpRect
            src.getBoundsInScreen(rect)
            dest.setBoundsInScreen(rect)
            dest.isVisibleToUser = src.isVisibleToUser
            dest.packageName = src.packageName
            dest.className = src.className
            dest.contentDescription = src.contentDescription
            dest.isEnabled = src.isEnabled
            dest.isClickable = src.isClickable
            dest.isFocusable = src.isFocusable
            dest.isFocused = src.isFocused
            dest.isAccessibilityFocused = src.isAccessibilityFocused
            dest.isSelected = src.isSelected
            dest.isLongClickable = src.isLongClickable
            dest.addAction(src.actions)
        }
    }

    private fun shouldSkipScroll(): Boolean {
        return Settings.System.getInt(context.contentResolver, "remove_animations", 0) == 1
    }

    private fun updateSlidingState() {
        if (mSlidingState != null && slideableView != null) {
            if (currentSlideOffset == 0.0f) {
                if (mSlidingState.state != SESL_STATE_CLOSE) {
                    mSlidingState.onStateChanged(SESL_STATE_CLOSE)
                    dispatchOnPanelClosed(slideableView!!)
                }
            } else if (currentSlideOffset == 1.0f) {
                if (mSlidingState.state != SESL_STATE_OPEN) {
                    mSlidingState.onStateChanged(SESL_STATE_OPEN)
                    dispatchOnPanelOpened(slideableView!!)
                }
            } else if (mSlidingState.state != SESL_STATE_IDLE) {
                mSlidingState.onStateChanged(SESL_STATE_IDLE)
            }
        }
    }

    private inner class DisableLayerRunnable(val mChildView: View) : Runnable {
        override fun run() {
            if (mChildView.parent === this@SlidingPaneLayout) {
                mChildView.setLayerType(LAYER_TYPE_NONE, null)
                invalidateChildRegion(mChildView)
            }
            mPostedRunnables.remove(this)
        }
    }

    companion object {
        private const val TAG = "SlidingPaneLayout"
        private const val DRAWER_ANIMATION_DURATION = 240L
        private const val DRAWER_FLING_THRESHOLD = 3500L
        /**
         * a "physical" edge to grab to pull it closed.
         */
        private const val DEFAULT_OVERHANG_SIZE = 32 // dp;
        private const val DEFAULT_FADE_COLOR = -0x33333334

        private const val MIN_FLING_VELOCITY = 400 // dips per second


        const val PENDING_ACTION_COLLAPSED = 2
        const val PENDING_ACTION_COLLAPSED_LOCK = 258
        const val PENDING_ACTION_EXPANDED = 1
        const val PENDING_ACTION_EXPANDED_LOCK = 257
        const val PENDING_ACTION_NONE = 0
        private const val SESL_EXTRA_AREA_SENSITIVITY = 0.1f
        const val SESL_STATE_CLOSE = 0
        const val SESL_STATE_IDLE = 2
        const val SESL_STATE_OPEN = 1

        val IMPL: SlidingPanelLayoutImpl = SlidingPanelLayoutImplJBMR1()

        /**
         * The user cannot swipe between list and detail panes, though the app can open or close the
         * detail pane programmatically.
         */
        const val LOCK_MODE_LOCKED = 3

        /**
         * The detail pane is locked in a closed position. The user cannot swipe to open the detail
         * pane, but the app can open the detail pane programmatically.
         */
        const val LOCK_MODE_LOCKED_CLOSED = 2

        /**
         * The detail pane is locked in an open position. The user cannot swipe to close the detail
         * pane, but the app can close the detail pane programmatically.
         */
        const val LOCK_MODE_LOCKED_OPEN = 1

        /**
         * User can freely swipe between list and detail panes.
         */
        const val LOCK_MODE_UNLOCKED = 0

        private fun hasOpaqueBackground(view: View): Boolean {
            val backgroundTintList = ViewCompat.getBackgroundTintList(view)
            if (backgroundTintList == null || backgroundTintList.defaultColor == Color.TRANSPARENT) {
                return false
            }
            return Color.alpha(backgroundTintList.defaultColor) == 255
        }

    }

    /**
     * Check if the nav rail pane is completely open.
     *
     * @return true if the sliding pane is completely open
     */
    override fun isOpen(): Boolean {
        return (!isSlideable || currentSlideOffset == 1.0f)
    }

    /**
     * Completely open the sliding pane.
     */
    override fun open() {
        lastValidVelocity = 0
        openPane()

    }

    /**
     *  Close the sliding pane.
     */
    override fun close() {
        lastValidVelocity = 0
        closePane()

    }
}

