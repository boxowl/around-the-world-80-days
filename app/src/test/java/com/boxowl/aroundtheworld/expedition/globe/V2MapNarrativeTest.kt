package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.passport.PassportSnapshot
import com.boxowl.aroundtheworld.expedition.passport.StampId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2MapNarrativeTest {
    @Test fun nineStampNotesMatchDistinctRouteOccurrences() {
        assertEquals(StampId.entries.size, V2MapNarrative.notes.size)
        assertEquals(StampId.entries.toSet(), V2MapNarrative.notes.map { it.stamp }.toSet())
        assertEquals(9, V2MapNarrative.notes.map { it.occurrenceId }.toSet().size)
        V2MapNarrative.notes.forEach { note ->
            val card = GlobeStopCardsV1.cards.single { it.occurrenceId == note.occurrenceId }
            assertEquals(note.stamp.nodeId, card.nodeId)
            assertTrue(note.text.isNotBlank())
        }
        assertEquals("london#1", V2MapNarrative.notes.first().occurrenceId)
        assertEquals("london#2", V2MapNarrative.notes.last().occurrenceId)
        StampId.entries.forEach { stamp ->
            val occurrenceId = V2MapNarrative.occurrenceIdFor(stamp)
            assertEquals(1, GlobeStopCardsV1.cards.count { it.occurrenceId == occurrenceId })
        }
        assertEquals("london#1", V2MapNarrative.occurrenceIdFor(StampId.LONDON_START))
        assertEquals("london#2", V2MapNarrative.occurrenceIdFor(StampId.LONDON_FINISH))
    }

    @Test fun visibilityUsesPersistedStampEvenAfterDistanceRollback() {
        val suez = GlobeStopCardsV1.cards.single { it.occurrenceId == "suez#1" }
        assertFalse(V2MapNarrative.forCard(suez, PassportSnapshot.of())!!.opened)
        val restored = PassportSnapshot.of(mapOf(StampId.SUEZ_FINISH to null))
        assertTrue(V2MapNarrative.forCard(suez, restored)!!.opened)
        assertTrue(V2MapNarrative.forCard(suez, restored)!!.opened)
    }

    @Test fun londonStartAndFinishDoNotShareAnUnlock() {
        val passport = PassportSnapshot.of(mapOf(StampId.LONDON_START to null))
        assertTrue(V2MapNarrative.forCard(GlobeStopCardsV1.cards.first(), passport)!!.opened)
        assertFalse(V2MapNarrative.forCard(GlobeStopCardsV1.cards.last(), passport)!!.opened)
    }

    @Test fun intermediateCardHasNoChapterNoteYet() {
        val dover = GlobeStopCardsV1.cards.single { it.occurrenceId == "dover#1" }
        assertNull(V2MapNarrative.forCard(dover, PassportSnapshot.of()))
        assertNotNull(V2MapNarrative.forCard(GlobeStopCardsV1.cards.first(), PassportSnapshot.of()))
    }
}
