package androidx.slidingpanelayout.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout

class SPLToolbarContainer @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attributeSet, defStyleAttr) {
    private var mViewStubCompat: ViewStub? = null
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val viewStubCompat = mViewStubCompat
        if (viewStubCompat != null) {
            viewStubCompat.bringToFront()
            mViewStubCompat!!.invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewStubCompat = mViewStubCompat
        if (viewStubCompat != null) {
            viewStubCompat.bringToFront()
            mViewStubCompat!!.invalidate()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    init {
        val obtainStyledAttributes =
            getContext().obtainStyledAttributes(androidx.appcompat.R.styleable.AppCompatTheme)
        if (!obtainStyledAttributes.getBoolean(
                androidx.appcompat.R.styleable.AppCompatTheme_windowActionModeOverlay,
                false
            )
        ) {
            LayoutInflater.from(context)
                .inflate(androidx.slidingpanelayout.R.layout.sesl_spl_action_mode_view_stub, this as ViewGroup, true)
            mViewStubCompat =
                findViewById<View>(androidx.appcompat.R.id.action_mode_bar_stub) as ViewStub
        }
        obtainStyledAttributes.recycle()
        setWillNotDraw(false)
    }

}