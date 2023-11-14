@file:Suppress("MemberVisibilityCanBePrivate")

package androidx.slidingpanelayout.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.appcompat.R
import androidx.appcompat.util.SeslMisc
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.LAYOUT_DIRECTION_LTR
import kotlin.math.roundToInt

internal class SlidingPaneRoundedCorner  constructor(private val mContext: Context) {
    private var mEndBottomDrawable: Drawable? = null
    private var mEndTopDrawable: Drawable? = null
    private val mRes: Resources = mContext.resources
    private var mRoundedCornerMode = 0
    private var mStartBottomDrawable: Drawable? = null

    @ColorInt
    private var mStartBottomDrawableColor = 0
    private var mStartTopDrawable: Drawable? = null

    @ColorInt
    private var mStartTopDrawableColor = 0


    var roundedCornerRadius = -1

    private val mRoundedCornerBounds = Rect()
    private var mMarginTop = 0
    private var mMarginBottom = 0
    private val mTmpRect = Rect()

    init {
        initRoundedCorner()
    }

    private fun drawRoundedCornerInternal(canvas: Canvas) {
        val rect = mRoundedCornerBounds
        val l = rect.left
        val r = rect.right
        val t = rect.top
        val b = rect.bottom
        if (mRoundedCornerMode == 0) {
            val drawable = mStartTopDrawable
            drawable!!.setBounds(l - roundedCornerRadius, t, l, roundedCornerRadius + t)
            mStartTopDrawable!!.draw(canvas)
            val drawable2 = mStartBottomDrawable
            drawable2!!.setBounds(l - roundedCornerRadius, b - roundedCornerRadius, l, b)
            mStartBottomDrawable!!.draw(canvas)
            return
        }
        val etDrawable = mEndTopDrawable
        etDrawable!!.setBounds(r - roundedCornerRadius, t, r, roundedCornerRadius + t)
        mEndTopDrawable!!.draw(canvas)
        val drawable4 = mEndBottomDrawable
        drawable4!!.setBounds(r - roundedCornerRadius, b - roundedCornerRadius, r, b)
        mEndBottomDrawable!!.draw(canvas)
    }



    @SuppressLint("RestrictedApi", "PrivateResource")
    private fun initRoundedCorner() {
        roundedCornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, RADIUS, mRes.displayMetrics)
                .toInt()
        val isDarkMode = !SeslMisc.isLightTheme(mContext)
        val theme = mContext.theme
        mStartTopDrawable = mRes.getDrawable(R.drawable.sesl_top_right_round, theme)
        mStartBottomDrawable = mRes.getDrawable(R.drawable.sesl_bottom_right_round, theme)
        mEndTopDrawable = mRes.getDrawable(R.drawable.sesl_top_left_round, theme)
        mEndBottomDrawable = mRes.getDrawable(R.drawable.sesl_bottom_left_round, theme)
        val color = if (isDarkMode) {
            mRes.getColor(R.color.sesl_round_and_bgcolor_dark, null)
        }else {
            mRes.getColor(R.color.sesl_round_and_bgcolor_light, null)
        }
        mStartBottomDrawableColor = color
        mStartTopDrawableColor = color
    }

    private fun isLayoutRtlSupport(view: View): Boolean {
        return ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
    }

    private fun removeRoundedCorner(layoutDirection: Int) {
        if (layoutDirection == LAYOUT_DIRECTION_LTR) {
            mStartTopDrawable = null
            mStartBottomDrawable = null
            return
        }
        mEndTopDrawable = null
        mEndBottomDrawable = null
    }

    fun drawRoundedCorner(canvas: Canvas) {
        canvas.getClipBounds(mRoundedCornerBounds)
        drawRoundedCornerInternal(canvas)
    }

    var roundedCorners: Int
        get() = mRoundedCornerMode
        set(roundedCornerMode) {
            mRoundedCornerMode = roundedCornerMode
            if (mStartTopDrawable == null || mStartBottomDrawable == null || mEndTopDrawable == null || mEndBottomDrawable == null) {
                initRoundedCorner()
            }
        }

    fun setMarginBottom(bottomMargin: Int) {
        mMarginBottom = bottomMargin
    }

    fun setMarginTop(topMargin: Int) {
        mMarginTop = topMargin
    }

    fun setRoundedCornerColor(@ColorInt colorInt: Int) {
        if (mStartTopDrawable == null || mStartBottomDrawable == null || mEndTopDrawable == null || mEndBottomDrawable == null) {
            initRoundedCorner()
        }
        val porterDuffColorFilter = PorterDuffColorFilter(colorInt, PorterDuff.Mode.SRC_IN)
        mStartTopDrawableColor = colorInt
        mStartTopDrawable!!.colorFilter = porterDuffColorFilter
        mEndTopDrawable!!.colorFilter = porterDuffColorFilter
        mEndBottomDrawable!!.colorFilter = porterDuffColorFilter
        mStartBottomDrawableColor = colorInt
        mStartBottomDrawable!!.colorFilter = porterDuffColorFilter
    }

    fun drawRoundedCorner(view: View, canvas: Canvas) {
        val left: Int
        val top: Int
        mRoundedCornerMode = if (isLayoutRtlSupport(view)) {
            1
        } else {
            0
        }
        if (view.translationY != 0.0f) {
            left = view.x.roundToInt()
            top = view.y.roundToInt()
        } else {
            left = view.left
            top = view.top
        }
        val finalTop = mMarginTop + top
        val width = view.width + left + roundedCornerRadius
        val height = top + view.height - mMarginBottom
        canvas.getClipBounds(mTmpRect)
        val rect = mTmpRect
        rect.right = rect.left.coerceAtLeast(view.right + roundedCornerRadius)
        canvas.clipRect(mTmpRect)
        mRoundedCornerBounds[left, finalTop, width] = height
        drawRoundedCornerInternal(canvas)
    }

    companion object {
        private const val RADIUS = 16f
        const val TAG = "SeslPaneRoundedCorner"
    }
}