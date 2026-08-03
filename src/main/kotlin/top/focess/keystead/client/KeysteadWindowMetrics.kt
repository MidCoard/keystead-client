package top.focess.keystead.client

import kotlin.math.roundToInt

enum class KeysteadLayoutMode {
    COMPACT,
    WIDE,
}

object KeysteadWindowMetrics {
    const val MinimumWidthDp: Int = 960
    const val MinimumHeightDp: Int = 680
    const val WideBreakpointDp: Int = 1120

    fun modeForWidth(widthDp: Float): KeysteadLayoutMode =
        if (widthDp >= WideBreakpointDp) KeysteadLayoutMode.WIDE else KeysteadLayoutMode.COMPACT

    fun minimumWidthPixels(displayScale: Double): Int =
        scaledPixels(MinimumWidthDp, displayScale)

    fun minimumHeightPixels(displayScale: Double): Int =
        scaledPixels(MinimumHeightDp, displayScale)

    private fun scaledPixels(dp: Int, displayScale: Double): Int {
        require(displayScale.isFinite() && displayScale > 0.0) {
            "Display scale must be positive and finite"
        }
        return (dp * displayScale).roundToInt()
    }
}
