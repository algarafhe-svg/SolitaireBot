package com.jawaker.solitaire.service

import com.jawaker.solitaire.model.Card
import com.jawaker.solitaire.model.CardColor
import com.jawaker.solitaire.model.FoundationSlot
import com.jawaker.solitaire.model.GameState
import com.jawaker.solitaire.model.Rank
import com.jawaker.solitaire.model.Suit
import com.jawaker.solitaire.model.TableauColumn
import com.jawaker.solitaire.model.BotMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JawakerAutoSolverTest {
    private val solver = JawakerAutoSolver()

    private fun emptyColumns() = (0 until 7).map { index ->
        TableauColumn(index + 1, emptyList(), 0, ((index + 0.5f) / 7f) to 0.34f, ((index + 0.5f) / 7f) to 0.34f)
    }

    private fun foundations() = Suit.values().filter { it != Suit.UNKNOWN }.mapIndexed { index, suit ->
        FoundationSlot(suit, emptyList(), (0.13f + index * 0.125f) to 0.22f)
    }

    @Test fun wasteAceMovesToMatchingEmptyFoundation() {
        val ace = Card(Rank.ACE, Suit.HEART)
        val state = GameState(
            wastePileTopCard = ace,
            foundationPiles = foundations(),
            tableauColumns = emptyColumns()
        )
        val move = solver.decideNextMove(state)
        assertNotNull(move)
        assertEquals(BotMove.Type.DRAG, move?.type)
        assertEquals(Suit.HEART, move?.card?.suit)
        assertEquals(0.70f, move?.startX)
    }

    @Test fun wasteCardOnlyMovesToDescendingAlternatingColorColumn() {
        val queen = Card(Rank.QUEEN, Suit.HEART, CardColor.RED, 0.70f, 0.22f)
        val columns = emptyColumns().toMutableList()
        columns[2] = columns[2].copy(cards = listOf(Card(Rank.KING, Suit.SPADE, CardColor.BLACK, 0.35f, 0.38f)))
        val state = GameState(wastePileTopCard = queen, foundationPiles = foundations(), tableauColumns = columns)
        val move = solver.decideNextMove(state)
        assertNotNull(move)
        assertEquals(BotMove.Type.DRAG, move?.type)
        assertEquals(0.70f, move?.startX)
        assertEquals(columns[2].topCardPosition.first, move?.endX)
    }

    @Test fun doesNotMoveCardToSameColorOrWrongRank() {
        val queen = Card(Rank.QUEEN, Suit.HEART, CardColor.RED, 0.70f, 0.22f)
        val columns = emptyColumns().toMutableList()
        columns.indices.forEach { index ->
            columns[index] = columns[index].copy(
                cards = listOf(Card(Rank.KING, Suit.HEART, CardColor.RED, (index + 0.5f) / 7f, 0.38f))
            )
        }
        val state = GameState(
            stockAvailable = false,
            wastePileTopCard = queen,
            foundationPiles = foundations(),
            tableauColumns = columns
        )
        assertNull(solver.decideNextMove(state))
    }

    @Test fun unknownSuitAceIsNotSentToFoundation() {
        val ace = Card(Rank.ACE, Suit.UNKNOWN, CardColor.RED, 0.70f, 0.22f)
        val state = GameState(
            stockAvailable = false,
            wastePileTopCard = ace,
            foundationPiles = foundations(),
            tableauColumns = emptyColumns()
        )
        assertNull(solver.decideNextMove(state))
    }
}
