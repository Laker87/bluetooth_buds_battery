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
    fun ringSegments_keepTopGapAndExactProgress() {
        val segments = BatteryRingProgress.segments(
            level = 80,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
        )

        assertEquals(-87f, segments.progressStartDegrees, 0.001f)
        assertEquals(282f, segments.progressSweepDegrees, 0.001f)
        assertEquals(66f, segments.trackSweepDegrees, 0.001f)
        assertEquals(201f, segments.trackStartDegrees, 0.001f)
    }

    @Test
    fun ringSegments_keepTopGapAtFullProgress() {
        val segments = BatteryRingProgress.segments(
            level = 100,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
        )

        assertEquals(-87f, segments.progressStartDegrees, 0.001f)
        assertEquals(354f, segments.progressSweepDegrees, 0.001f)
        assertEquals(0f, segments.trackSweepDegrees, 0.001f)
    }

    @Test
    fun ringSegments_keepsGeometryStableWhenCapCompensationIsProvided() {
        val segments = BatteryRingProgress.segments(
            level = 80,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
            capCompensationDegrees = 4f,
        )

        assertEquals(-83f, segments.progressStartDegrees, 0.001f)
        assertEquals(274f, segments.progressSweepDegrees, 0.001f)
        assertEquals(205f, segments.trackStartDegrees, 0.001f)
        assertEquals(58f, segments.trackSweepDegrees, 0.001f)
    }

    @Test
    fun ringSegments_keepGapBetweenProgressAndTrack() {
        val segments = BatteryRingProgress.segments(
            level = 30,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
            capCompensationDegrees = 4f,
        )

        val progressEnd = segments.progressStartDegrees + segments.progressSweepDegrees
        assertEquals(94f, segments.progressSweepDegrees, 0.001f)
        assertEquals(14f, segments.trackStartDegrees - progressEnd, 0.001f)
        assertEquals(238f, segments.trackSweepDegrees, 0.001f)
    }

    @Test
    fun ringSegments_centerProgressTrackGapAtBottomForFiftyPercent() {
        val segments = BatteryRingProgress.segments(
            level = 50,
            fullSweepDegrees = 360f,
            gapDegrees = 6f,
            capCompensationDegrees = 4f,
        )

        val progressEnd = segments.progressStartDegrees + segments.progressSweepDegrees
        val gapCenter = progressEnd + ((segments.trackStartDegrees - progressEnd) / 2f)
        assertEquals(90f, gapCenter, 0.001f)
    }

    @Test
    fun ringSegments_centerProgressTrackGapAtExpectedPercentAngles() {
        listOf(
            10 to 36f,
            30 to 108f,
            50 to 180f,
            80 to 288f,
            90 to 324f,
        ).forEach { (level, expectedAngleFromTop) ->
            val segments = BatteryRingProgress.segments(
                level = level,
                fullSweepDegrees = 360f,
                gapDegrees = 6f,
                capCompensationDegrees = 4f,
            )

            val progressEnd = segments.progressStartDegrees + segments.progressSweepDegrees
            val canvasGapCenter = progressEnd + ((segments.trackStartDegrees - progressEnd) / 2f)
            val angleFromTop = normalizeDegrees(canvasGapCenter + 90f)
            assertEquals(expectedAngleFromTop, angleFromTop, 0.001f)
        }
    }

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }
}
