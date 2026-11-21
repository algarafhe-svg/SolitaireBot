package com.jawaker.solitaire.service

import com.jawaker.solitaire.model.BotMove
import com.jawaker.solitaire.model.Card
import com.jawaker.solitaire.model.CardColor
import com.jawaker.solitaire.model.GameState
import com.jawaker.solitaire.model.Rank
import com.jawaker.solitaire.model.Suit
import com.jawaker.solitaire.model.TableauColumn

/** Rule-based Klondike solver. It only emits moves whose source card and destination are known. */
class JawakerAutoSolver {

    fun decideNextMove(state: GameState): BotMove? {
        if (state.tableauColumns.size != 7 || state.foundationPiles.size != 4) return null

        val waste = state.wastePileTopCard
        val visibleTops = state.tableauColumns.mapNotNull { column ->
            column.topCard?.let { Triple(it, column, column.cards.lastIndex) }
        }

        // Aces and twos should be moved immediately whenever the OCR can identify their suit.
        val immediate = buildList {
            if (waste != null) add(waste to state.wastePosition)
            visibleTops.forEach { add(it.first to (it.first.x to it.first.y)) }
        }.firstNotNullOfOrNull { (card, source) ->
            if (card.rank.value <= Rank.TWO.value) foundationMove(card, source, state) else null
        }
        if (immediate != null) return immediate

        // Prefer moving a complete face-up run off a column when that exposes a hidden card.
        val uncoveringMove = findTableauMoves(state)
            .filter { move ->
                val source = state.tableauColumns.firstOrNull { column ->
                    column.cards.any { it == move.card } && column.cards.lastOrNull() == move.lastCard
                }
                source != null && source.hiddenCards > 0 && source.cards.firstOrNull() == move.card
            }
            .firstOrNull()
        if (uncoveringMove != null) return uncoveringMove

        // Waste-to-tableau is often the strongest ordinary move because it preserves the stock cycle.
        if (waste != null) {
            for (target in state.tableauColumns) {
                if (canPlaceOnTableau(waste, target)) {
                    return drag(
                        waste, null, state.wastePosition, target.targetPosition,
                        "انقل ${waste.shortName()} من الرزمة إلى العمود ${target.columnIndex}"
                    )
                }
            }
        }

        // Move a King to an empty column, giving preference to a column with hidden cards.
        val kingMove = findTableauMoves(state).firstOrNull()
        if (kingMove != null) return kingMove

        // Move safe higher cards to foundations only when it is unlikely to block alternating-color runs.
        val safeFoundation = buildList {
            if (waste != null) add(waste to state.wastePosition)
            visibleTops.forEach { add(it.first to (it.first.x to it.first.y)) }
        }.firstNotNullOfOrNull { (card, source) ->
            if (card.rank.value > Rank.TWO.value && isSafeFoundationMove(card, state)) {
                foundationMove(card, source, state)
            } else null
        }
        if (safeFoundation != null) return safeFoundation

        // Only draw/recycle stock after checking all currently legal visible moves.
        if (state.stockAvailable) {
            val p = state.stockPosition
            return BotMove(
                BotMove.Type.STOCK_TAP, null, null, "اسحب من كومة السحب",
                p.first, p.second, p.first, p.second
            )
        }
        return null
    }

    private fun foundationMove(card: Card, source: Pair<Float, Float>, state: GameState): BotMove? {
        if (card.suit == Suit.UNKNOWN) return null
        val target = state.foundationPiles.firstOrNull { slot ->
            when {
                slot.cards.isEmpty() -> card.rank == Rank.ACE && (slot.suit == Suit.UNKNOWN || slot.suit == card.suit)
                slot.suit != card.suit -> false
                else -> slot.topCard?.rank?.value == card.rank.value - 1
            }
        } ?: return null
        return drag(card, null, source, target.position, "انقل ${card.shortName()} إلى الأساس")
    }

    private fun isSafeFoundationMove(card: Card, state: GameState): Boolean {
        if (card.suit == Suit.UNKNOWN) return false
        val target = state.foundationPiles.firstOrNull { it.suit == card.suit } ?: return false
        if (target.topCard?.rank?.value != card.rank.value - 1) return false
        val oppositeColor = if (card.color == CardColor.RED) CardColor.BLACK else CardColor.RED
        val minimumOppositeFoundation = state.foundationPiles
            .filter { it.suit.color == oppositeColor }
            .mapNotNull { it.topCard?.rank?.value }
            .minOrNull() ?: 0
        return minimumOppositeFoundation >= card.rank.value - 2
    }

    private fun findTableauMoves(state: GameState): List<BotMove> {
        val moves = mutableListOf<Pair<Int, BotMove>>()
        for (source in state.tableauColumns) {
            if (source.cards.isEmpty()) continue
            for (startIndex in source.cards.indices) {
                val sequence = source.cards.subList(startIndex, source.cards.size)
                if (!isValidRun(sequence)) continue
                val first = sequence.first()
                for (target in state.tableauColumns) {
                    if (target.columnIndex == source.columnIndex || !canPlaceOnTableau(first, target)) continue
                    val revealsHidden = startIndex == 0 && source.hiddenCards > 0
                    val emptiesColumn = startIndex == 0 && sequence.size == source.cards.size
                    val score = (if (revealsHidden) 100 else 0) +
                        (if (emptiesColumn && target.cards.isEmpty()) 35 else 0) +
                        (if (target.cards.isEmpty()) 5 else 0) + sequence.size
                    val sourceCard = sequence.first()
                    val destination = target.cards.lastOrNull()?.let { it.x to it.y } ?: target.targetPosition
                    moves += score to drag(
                        sourceCard, sequence.last(), sourceCard.x to sourceCard.y, destination,
                        "انقل تسلسل ${sourceCard.shortName()} من العمود ${source.columnIndex} إلى ${target.columnIndex}"
                    )
                }
            }
        }
        return moves.sortedByDescending { it.first }.map { it.second }
    }

    private fun isValidRun(cards: List<Card>): Boolean {
        if (cards.isEmpty()) return false
        for (index in 1 until cards.size) {
            val lower = cards[index]
            val upper = cards[index - 1]
            if (upper.rank.value - lower.rank.value != 1) return false
            if (upper.color == CardColor.UNKNOWN || lower.color == CardColor.UNKNOWN || upper.color == lower.color) return false
        }
        return true
    }

    private fun canPlaceOnTableau(card: Card, target: TableauColumn): Boolean {
        val top = target.topCard ?: return card.rank == Rank.KING
        if (card.color == CardColor.UNKNOWN || top.color == CardColor.UNKNOWN) return false
        return card.rank.value == top.rank.value - 1 && card.color != top.color
    }

    private fun drag(
        card: Card,
        lastCard: Card?,
        source: Pair<Float, Float>,
        destination: Pair<Float, Float>,
        description: String
    ) = BotMove(
        BotMove.Type.DRAG, card, lastCard, description,
        source.first, source.second, destination.first, destination.second
    )
}
