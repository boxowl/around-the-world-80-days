package com.boxowl.aroundtheworld.expedition.credit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StepDistanceTest {
    @Test fun defaultStrideConvertsOneDayWithoutRoundingUp() {
        assertEquals(7_500L, StepDistance.realMeters(10_000L, StepDistance.DEFAULT_STRIDE_MILLIMETERS))
        assertEquals(0L, StepDistance.realMeters(1L, StepDistance.DEFAULT_STRIDE_MILLIMETERS))
        assertEquals(1L, StepDistance.realMeters(2L, StepDistance.DEFAULT_STRIDE_MILLIMETERS))
    }

    @Test fun zeroIsAConfirmedValue() {
        assertEquals(0L, StepDistance.realMeters(0L, StepDistance.DEFAULT_STRIDE_MILLIMETERS))
    }

    @Test fun rejectsInvalidInputAndOverflow() {
        assertThrows(IllegalArgumentException::class.java) { StepDistance.realMeters(-1L, 750L) }
        assertThrows(IllegalArgumentException::class.java) { StepDistance.realMeters(1L, 0L) }
        assertThrows(ArithmeticException::class.java) { StepDistance.realMeters(Long.MAX_VALUE, 750L) }
    }
}
