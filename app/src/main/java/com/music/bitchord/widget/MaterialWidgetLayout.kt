package com.music.bitchord.widget

/** Layout decisions are shared by old launchers and Android 12's responsive layout map. */
internal enum class MaterialWidgetLayout { COMPACT, TALL, WIDE, PILL, PILL_SLIM }

internal object MaterialWidgetSizing {
    const val MIN_WIDTH = 110f
    const val MIN_HEIGHT = 110f
    const val FULL_TRANSPORT_WIDTH = 164f
    const val SECONDARY_CONTROLS_WIDTH = 260f
    const val TALL_HEIGHT = 190f
    const val WIDE_WIDTH = 300f
    const val WIDE_HEIGHT = 190f
    const val PILL_MIN_WIDTH = 180f
    const val PILL_MIN_HEIGHT = 56f
    const val PILL_EXPANDED_HEIGHT = 96f

    fun layout(width: Float, height: Float, pill: Boolean): MaterialWidgetLayout = when {
        pill && height >= TALL_HEIGHT && width >= WIDE_WIDTH -> MaterialWidgetLayout.WIDE
        pill && height >= TALL_HEIGHT -> MaterialWidgetLayout.TALL
        pill && width >= SECONDARY_CONTROLS_WIDTH && height >= PILL_EXPANDED_HEIGHT -> MaterialWidgetLayout.PILL
        pill -> MaterialWidgetLayout.PILL_SLIM
        width >= WIDE_WIDTH && height >= WIDE_HEIGHT -> MaterialWidgetLayout.WIDE
        height >= TALL_HEIGHT -> MaterialWidgetLayout.TALL
        else -> MaterialWidgetLayout.COMPACT
    }

    fun showPrevious(width: Float, layout: MaterialWidgetLayout): Boolean =
        width >= if (layout == MaterialWidgetLayout.PILL_SLIM) 340f else FULL_TRANSPORT_WIDTH

    fun showSecondaryControls(width: Float, layout: MaterialWidgetLayout): Boolean =
        width >= if (layout == MaterialWidgetLayout.PILL_SLIM) 460f else SECONDARY_CONTROLS_WIDTH

    fun showStatus(height: Float, layout: MaterialWidgetLayout): Boolean =
        layout == MaterialWidgetLayout.TALL || layout == MaterialWidgetLayout.WIDE ||
            (layout != MaterialWidgetLayout.PILL_SLIM && height >= 124f)
}
