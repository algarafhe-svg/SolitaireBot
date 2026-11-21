package com.jawaker.solitaire.model

data class GameState(
    var stockAvailable: Boolean = true,
    var wastePileTopCard: Card? = null,
    var wastePosition: Pair<Float, Float> = 0.70f to 0.22f,
    var stockPosition: Pair<Float, Float> = 0.88f to 0.22f,
    var foundationPiles: List<FoundationSlot> = emptyList(),
    var tableauColumns: List<TableauColumn> = emptyList(),
    var recognizedCardCount: Int = 0,
    var boardFingerprint: String = ""
)

data class FoundationSlot(
    var suit: Suit = Suit.UNKNOWN,
    var cards: List<Card> = emptyList(),
    var position: Pair<Float, Float> = 0f to 0f
) {
    val topCard: Card? get() = cards.lastOrNull()
}

data class TableauColumn(
    val columnIndex: Int,
    /** Face-up cards ordered from bottom to top. */
    val cards: List<Card>,
    /** Approximation only; unknown facedown count must not be treated as ground truth. */
    val hiddenCards: Int = 0,
    val topCardPosition: Pair<Float, Float>,
    val targetPosition: Pair<Float, Float>
) {
    val topCard: Card? get() = cards.lastOrNull()
}
