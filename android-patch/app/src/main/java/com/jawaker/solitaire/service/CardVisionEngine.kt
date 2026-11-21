package com.jawaker.solitaire.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jawaker.solitaire.model.Card
import com.jawaker.solitaire.model.CardColor
import com.jawaker.solitaire.model.FoundationSlot
import com.jawaker.solitaire.model.GameState
import com.jawaker.solitaire.model.Rank
import com.jawaker.solitaire.model.Suit
import com.jawaker.solitaire.model.TableauColumn
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** OCR-based first-pass reader for a portrait Klondike board. Coordinates are normalized to the captured frame. */
class CardVisionEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyzeScreen(bitmap: Bitmap): GameState {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 200 || height < 300) return GameState()

        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val candidates = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val rank = Rank.fromText(line.text) ?: return@mapNotNull null
                if (box.centerY() < height * 0.14f || box.centerY() > height * 0.94f) return@mapNotNull null
                val suit = Suit.fromText(line.text) ?: Suit.UNKNOWN
                val color = if (suit != Suit.UNKNOWN) suit.color else sampleColor(bitmap, box)
                CardCandidate(
                    Card(rank, suit, color, box.centerX().toFloat() / width, box.centerY().toFloat() / height),
                    box
                )
            }
        }.sortedBy { it.box.centerY() }

        val foundationX = listOf(0.13f, 0.255f, 0.38f, 0.505f)
        val foundations = foundationX.map { x ->
            val topCandidate = candidates
                .filter { it.card.y in 0.14f..0.31f && abs(it.card.x - x) < 0.065f }
                .minByOrNull { abs(it.card.x - x) }
            val card = topCandidate?.card
            FoundationSlot(
                suit = card?.suit ?: Suit.UNKNOWN,
                cards = listOfNotNull(card),
                position = x to 0.22f
            )
        }

        val wastePosition = 0.70f to 0.22f
        val wasteCandidate = candidates
            .filter { it.card.y in 0.14f..0.31f && it.card.x in 0.58f..0.82f }
            .maxByOrNull { it.box.bottom }

        val columnGroups = (0 until 7).map { index ->
            val center = (index + 0.5f) / 7f
            val columnCandidates = candidates
                .filter { it.card.y >= 0.30f && abs(it.card.x - center) < 0.075f }
                .sortedBy { it.card.y }
                .fold(mutableListOf<CardCandidate>()) { acc, candidate ->
                    val duplicate = acc.any {
                        it.card.rank == candidate.card.rank && abs(it.card.y - candidate.card.y) < 0.018f
                    }
                    if (!duplicate) acc.add(candidate)
                    acc
                }
            val faceUpCards = columnCandidates.map { it.card }
            val last = faceUpCards.lastOrNull()
            val topY = last?.y ?: 0.34f
            val hiddenEstimate = max(0, index + 1 - faceUpCards.size)
            TableauColumn(
                columnIndex = index + 1,
                cards = faceUpCards,
                hiddenCards = hiddenEstimate,
                topCardPosition = last?.let { it.x to it.y } ?: (center to topY),
                targetPosition = center to (if (last == null) 0.34f else last.y)
            )
        }

        val waste = wasteCandidate?.card
        val state = GameState(
            stockAvailable = hasStockCard(bitmap),
            wastePileTopCard = waste,
            wastePosition = wastePosition,
            stockPosition = 0.88f to 0.22f,
            foundationPiles = foundations,
            tableauColumns = columnGroups,
            recognizedCardCount = candidates.size
        )
        state.boardFingerprint = buildString {
            append(waste?.shortName() ?: "-")
            append('|')
            foundations.forEach { append(it.topCard?.shortName() ?: "-").append(',') }
            append('|')
            columnGroups.forEach { column ->
                column.cards.forEach { append(it.shortName()).append(':') }
                append(';')
            }
        }
        return state
    }

    private fun sampleColor(bitmap: Bitmap, box: Rect): CardColor {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        var red = 0
        var dark = 0
        var samples = 0
        val stepX = max(1, (right - left) / 10)
        val stepY = max(1, (bottom - top) / 10)
        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r > 115 && r > g * 1.25 && r > b * 1.15) red++
                if (r < 90 && g < 90 && b < 100) dark++
                samples++
            }
        }
        if (samples == 0) return CardColor.UNKNOWN
        return when {
            red >= max(2, dark) -> CardColor.RED
            dark >= 2 -> CardColor.BLACK
            else -> CardColor.UNKNOWN
        }
    }

    private fun hasStockCard(bitmap: Bitmap): Boolean {
        val left = (bitmap.width * 0.835f).toInt().coerceIn(0, bitmap.width - 1)
        val right = (bitmap.width * 0.965f).toInt().coerceIn(left + 1, bitmap.width)
        val top = (bitmap.height * 0.16f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (bitmap.height * 0.30f).toInt().coerceIn(top + 1, bitmap.height)
        var colorful = 0
        var samples = 0
        val stepX = max(1, (right - left) / 16)
        val stepY = max(1, (bottom - top) / 16)
        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val high = max(android.graphics.Color.red(pixel), max(android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)))
                val low = min(android.graphics.Color.red(pixel), min(android.graphics.Color.green(pixel), android.graphics.Color.blue(pixel)))
                if (high - low > 35 || high > 220) colorful++
                samples++
            }
        }
        return samples > 0 && colorful.toFloat() / samples > 0.16f
    }

    fun close() = recognizer.close()

    private data class CardCandidate(val card: Card, val box: Rect)
}
