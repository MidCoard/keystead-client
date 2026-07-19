package top.focess.keystead.client

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
}
