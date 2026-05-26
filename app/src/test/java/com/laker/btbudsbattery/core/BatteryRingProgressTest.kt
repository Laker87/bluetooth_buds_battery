package com.laker.btbudsbattery.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryRingProgressTest {
    @Test
    fun progressSweepDegrees_usesExactPercentOfFullSweep() {
        val fullSweep = 360f

        assertEquals(288f, BatteryRingProgress.progressSweepDegrees(80, fullSweep), 0.001f)
        assertEquals(72f, fullSweep - BatteryRingProgress.progressSweepDegrees(80, fullSweep), 0.001f)
    }

    @Test
    fun progressSweepDegrees_clampsOutOfRangeLevels() {
        assertEquals(0f, BatteryRingProgress.progressSweepDegrees(-10, 360f), 0.001f)
        assertEquals(360f, BatteryRingProgress.progressSweepDegrees(120, 360f), 0.001f)
        assertEquals(0f, BatteryRingProgress.progressSweepDegrees(null, 360f), 0.001f)
    }

    @Test
    fun ringSegments_keepExactProgressAndReserveTwoGapsFromTrack() {
        val segments = BatteryRingProgress.segments(
            level = 80,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
        )

        assertEquals(288f, segments.progressSweepDegrees, 0.001f)
        assertEquals(60f, segments.trackSweepDegrees, 0.001f)
        assertEquals(204f, segments.trackStartDegrees, 0.001f)
    }

    @Test
    fun ringSegments_hideTrackWhenRemainingSweepCannotFitGaps() {
        val segments = BatteryRingProgress.segments(
            level = 99,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
        )

        assertEquals(356.4f, segments.progressSweepDegrees, 0.001f)
        assertEquals(0f, segments.trackSweepDegrees, 0.001f)
    }

    @Test
    fun ringSegments_compensateRoundCapsWithoutChangingProgressSweep() {
        val segments = BatteryRingProgress.segments(
            level = 80,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
            capCompensationDegrees = 4f,
        )

        assertEquals(288f, segments.progressSweepDegrees, 0.001f)
        assertEquals(212f, segments.trackStartDegrees, 0.001f)
        assertEquals(44f, segments.trackSweepDegrees, 0.001f)
    }
}
