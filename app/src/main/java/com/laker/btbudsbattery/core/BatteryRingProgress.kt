package com.laker.btbudsbattery.core

object BatteryRingProgress {
    const val FULL_SWEEP_DEGREES = 359.9f
    const val GAP_DEGREES = 6f

    fun progressSweepDegrees(level: Int?, fullSweepDegrees: Float = FULL_SWEEP_DEGREES): Float {
        val clamped = (level ?: 0).coerceIn(0, 100)
        return fullSweepDegrees * (clamped / 100f)
    }

    fun segments(
        level: Int?,
        fullSweepDegrees: Float = FULL_SWEEP_DEGREES,
        gapDegrees: Float = GAP_DEGREES,
        capCompensationDegrees: Float = 0f,
    ): RingSegments {
        val effectiveGapDegrees = (gapDegrees + (capCompensationDegrees * 2f)).coerceAtLeast(0f)
        val progressPercentSweep = progressSweepDegrees(level, fullSweepDegrees)
        val topGapEnd = -90f + (effectiveGapDegrees / 2f)
        val topGapStart = -90f + fullSweepDegrees - (effectiveGapDegrees / 2f)
        val progressTrackGapCenter = -90f + progressPercentSweep
        val progressEnd = progressTrackGapCenter - (effectiveGapDegrees / 2f)
        val trackStart = progressTrackGapCenter + (effectiveGapDegrees / 2f)

        val progressSweep = (progressEnd - topGapEnd).coerceIn(
            minimumValue = 0f,
            maximumValue = (fullSweepDegrees - effectiveGapDegrees).coerceAtLeast(0f),
        )
        val trackSweep = if (trackStart < topGapStart) {
            topGapStart - trackStart
        } else {
            0f
        }
        return RingSegments(
            progressStartDegrees = topGapEnd,
            progressSweepDegrees = progressSweep,
            trackStartDegrees = trackStart,
            trackSweepDegrees = trackSweep,
        )
    }

    data class RingSegments(
        val progressStartDegrees: Float,
        val progressSweepDegrees: Float,
        val trackStartDegrees: Float,
        val trackSweepDegrees: Float,
    )
}
