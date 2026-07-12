package com.arashivision.sdk.demo.ui.capture

import android.content.Context
import android.util.AttributeSet
import com.arashivision.sdk.demo.ui.capture.adapter.CaptureModeAdapter
import com.arashivision.sdk.demo.view.discretescrollview.DSVOrientation
import com.arashivision.sdk.demo.view.discretescrollview.DiscreteScrollView
import com.arashivision.sdk.demo.view.discretescrollview.FadingEdgeDecoration
import com.arashivision.sdk.demo.view.discretescrollview.transform.ScaleTransformer

class CaptureModeCarousel
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : DiscreteScrollView(context, attrs) {
        private val adapter = CaptureModeAdapter()

        private var onModeChangedListener: ((Int) -> Unit)? = null
        private var onModeClickedListener: ((Int) -> Unit)? = null

        init {
            setAdapter(adapter)
            setOrientation(DSVOrientation.HORIZONTAL)
            setOverScrollEnabled(true)
            setSlideOnFling(true)
            setSlideOnFlingThreshold(FLING_THRESHOLD)
            setItemTransitionTimeMillis(ITEM_TRANSITION_TIME_MS)
            setItemTransformer(
                ScaleTransformer.Builder().setMinScale(0.8f).build(),
            )
            addItemDecoration(FadingEdgeDecoration())

            addOnItemChangedListener { _, position ->
                onModeChangedListener?.invoke(position)
            }

            adapter.setItemClickListener { _, position ->
                onModeClickedListener?.invoke(position)
                smoothScrollToPosition(position)
            }
        }

        fun setModes(
            modes: List<String>,
            currentIndex: Int,
        ) {
            adapter.setData(modes.toMutableList())
            scrollToPosition(currentIndex)
        }

        fun onModeChanged(listener: (Int) -> Unit) {
            onModeChangedListener = listener
        }

        fun onModeClicked(listener: (Int) -> Unit) {
            onModeClickedListener = listener
        }

        companion object {
            private const val FLING_THRESHOLD = 1300
            private const val ITEM_TRANSITION_TIME_MS = 180
        }
    }
