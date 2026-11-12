package com.boxowl.aroundtheworld.expedition.globe

import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2StopCardDetailsTest {
    @Test fun londonStartAndFinishStaySeparate() {
        val cards = GlobeStopCardsV1.cards
        assertEquals(25, cards.size)
        val start = V2StopCardDetails.of(cards.first(), 0L)
        val finish = V2StopCardDetails.of(cards.last(), 0L)
        assertEquals("london#1", start.occurrenceId)
        assertEquals("Старт маршрута", start.arrival)
        assertTrue(start.reached)
        assertEquals("london#2", finish.occurrenceId)
        assertFalse(finish.reached)
    }

    @Test fun suezReachedStateUsesConfirmedMetersAndCanRollBack() {
        val card = GlobeStopCardsV1.cards.first { it.nodeId == "suez" }
        val firstReachedMeter = ceil(card.thresholdBookKm * 1000.0).toLong()
        assertFalse(V2StopCardDetails.of(card, firstReachedMeter - 1L).reached)
        assertTrue(V2StopCardDetails.of(card, firstReachedMeter).reached)
        assertFalse(V2StopCardDetails.of(card, 0L).reached)
        assertEquals(card.arrivalChapter, V2StopCardDetails.of(card, 0L).chapter)
    }
}
