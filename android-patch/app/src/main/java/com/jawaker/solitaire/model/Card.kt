package com.jawaker.solitaire.model

data class Card(
    val rank: Rank,
    val suit: Suit,
    val color: CardColor = suit.color,
    /** Normalized screen position of the card's rank corner (0..1). */
    val x: Float = 0f,
    val y: Float = 0f
) {
    fun shortName(): String = rank.label + suit.symbol
}
