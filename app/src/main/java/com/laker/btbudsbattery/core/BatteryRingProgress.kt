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
        val progressSweep = progressSweepDegrees(level, fullSweepDegrees)
        val effectiveGapDegrees = gapDegrees + (capCompensationDegrees * 2f)
        val trackSweep = (fullSweepDegrees - progressSweep - (effectiveGapDegrees * 2f)).coerceAtLeast(0f)
        return RingSegments(
            progressSweepDegrees = progressSweep,
            trackStartDegrees = -90f + progressSweep + effectiveGapDegrees,
            trackSweepDegrees = trackSweep,
        )
    }

    data class RingSegments(
        val progressSweepDegrees: Float,
        val trackStartDegrees: Float,
        val trackSweepDegrees: Float,
    )
}
