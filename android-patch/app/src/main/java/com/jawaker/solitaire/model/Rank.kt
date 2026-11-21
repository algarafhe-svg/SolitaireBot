package com.jawaker.solitaire.model

enum class Rank(val value: Int, val label: String) {
    ACE(1, "A"), TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"),
    SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"),
    JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K");

    companion object {
        fun fromText(text: String): Rank? {
            val token = text.trim().uppercase().replace("O", "0")
                .replace("|", "I").replace(" ", "")
            return when {
                token.startsWith("10") -> TEN
                token.startsWith("A") -> ACE
                token.startsWith("K") -> KING
                token.startsWith("Q") -> QUEEN
                token.startsWith("J") -> JACK
                else -> token.firstOrNull()?.digitToIntOrNull()?.let { n ->
                    values().firstOrNull { it.value == n }
                }
            }
        }
    }
}
