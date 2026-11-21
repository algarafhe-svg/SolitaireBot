package com.jawaker.solitaire.model

data class BotMove(
    val type: Type,
    val card: Card?,
    val lastCard: Card? = null,
    val description: String,
    /** Normalized source coordinates. */
    val startX: Float,
    val startY: Float,
    /** Normalized destination coordinates. */
    val endX: Float,
    val endY: Float
) {
    enum class Type { DRAG, STOCK_TAP }
}
