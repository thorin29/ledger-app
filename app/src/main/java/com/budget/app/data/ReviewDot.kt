package com.budget.app.data

/**
 * More's amber dot: there's nearly always something to review, so the dot only shows for items
 * that came in since Review was last opened. Pure, so it is unit-tested.
 */
object ReviewDot {
    /** The review count in Today's "N to review" item, or 0. */
    fun count(text: String?): Int = text?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    fun waiting(count: Int, seen: Int?): Boolean = count > (seen ?: 0)

    /**
     * What to remember as seen: the current count once Review is open, and a lower count when
     * items were cleared elsewhere (so the next new one shows the dot). Null when nothing changes.
     */
    fun nextSeen(count: Int, seen: Int?, reviewOpen: Boolean): Int? = when {
        reviewOpen && seen != count -> count
        seen != null && count < seen -> count
        else -> null
    }
}
