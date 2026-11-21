package com.jawaker.solitaire.model

enum class CardColor { RED, BLACK, UNKNOWN }

enum class Suit(val color: CardColor, val symbol: String) {
    HEART(CardColor.RED, "♥"),
    DIAMOND(CardColor.RED, "♦"),
    SPADE(CardColor.BLACK, "♠"),
    CLUB(CardColor.BLACK, "♣"),
    UNKNOWN(CardColor.UNKNOWN, "?");

    companion object {
        fun fromText(text: String): Suit? {
            val value = text.uppercase()
            return when {
                value.contains('♥') || value.matches(Regex(".*\\bH\\b.*")) -> HEART
                value.contains('♦') || value.matches(Regex(".*\\bD\\b.*")) -> DIAMOND
                value.contains('♠') || value.matches(Regex(".*\\bS\\b.*")) -> SPADE
                value.contains('♣') || value.matches(Regex(".*\\bC\\b.*")) -> CLUB
                else -> null
            }
        }
    }
}
