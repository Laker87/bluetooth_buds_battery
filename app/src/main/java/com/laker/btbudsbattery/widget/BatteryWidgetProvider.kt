package com.laker.btbudsbattery.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.laker.btbudsbattery.MainActivity
import com.laker.btbudsbattery.R
import com.laker.btbudsbattery.core.AppAccentColor
import com.laker.btbudsbattery.core.AppTheme
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.BatteryRingProgress
import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot = AppPreferences(context).widgetSnapshot
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId, snapshot)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, AppPreferences(context).widgetSnapshot)
    }

    companion object {
        fun updateAll(context: Context, snapshot: BluetoothBatterySnapshot?) {
            AppPreferences(context).widgetSnapshot = snapshot
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BatteryWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return
            ids.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId, snapshot)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            snapshot: BluetoothBatterySnapshot?,
        ) {
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context, snapshot, options))
        }

        private fun buildRemoteViews(
            context: Context,
            snapshot: BluetoothBatterySnapshot?,
            options: Bundle?,
        ): RemoteViews {
            val useCompactSingleLayout = shouldUseCompactSingleLayout(snapshot, options)
            val layoutId = if (useCompactSingleLayout) {
                R.layout.widget_battery_compact
            } else {
                R.layout.widget_battery
            }
            val views = RemoteViews(context.packageName, layoutId)
            val appTheme = AppPreferences(context).appTheme
            applyTheme(views, context, appTheme)
            val openAppIntent = PendingIntent.getActivity(
                context,
                201,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent)

            if (snapshot == null || !snapshot.isConnected) {
                views.setTextViewText(R.id.widgetTitle, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widgetSubtitle, context.getString(R.string.waiting_for_headphones))
                views.setViewVisibility(R.id.widgetSplitRow, View.GONE)
                views.setViewVisibility(R.id.widgetSingleRow, View.VISIBLE)
                views.setTextViewText(R.id.widgetSingleValue, context.getString(R.string.battery_not_available))
                views.setTextViewText(R.id.widgetSingleLabel, context.getString(R.string.main_battery))
                views.setImageViewBitmap(
                    R.id.widgetSingleRing,
                    buildRoundedRingBitmap(
                        context,
                        appTheme,
                        sizeDp = if (useCompactSingleLayout) 56f else 64f,
                        level = null,
                    ),
                )
                return views
            }

            views.setTextViewText(R.id.widgetTitle, snapshot.deviceName)
            views.setTextViewText(
                R.id.widgetSubtitle,
                if (snapshot.isConnected) {
                    context.getString(R.string.fast_pair_connected)
                } else {
                    context.getString(R.string.history_last_battery, formatBattery(context, snapshot.primaryLevel))
                },
            )

            if (snapshot.hasSplitLevels) {
                views.setViewVisibility(R.id.widgetSplitRow, View.VISIBLE)
                views.setViewVisibility(R.id.widgetSingleRow, View.GONE)

                views.setTextViewText(R.id.widgetLeftValue, formatBattery(context, snapshot.leftLevel))
                views.setTextViewText(R.id.widgetCaseValue, formatBattery(context, snapshot.caseLevel))
                views.setTextViewText(R.id.widgetRightValue, formatBattery(context, snapshot.rightLevel))
                views.setTextViewText(R.id.widgetLeftLabel, context.getString(R.string.left_bud))
                views.setTextViewText(R.id.widgetCaseLabel, context.getString(R.string.case_battery))
                views.setTextViewText(R.id.widgetRightLabel, context.getString(R.string.right_bud))
                views.setImageViewBitmap(
                    R.id.widgetLeftRing,
                    buildRoundedRingBitmap(context, appTheme, sizeDp = 58f, level = snapshot.leftLevel),
                )
                views.setImageViewBitmap(
                    R.id.widgetCaseRing,
                    buildRoundedRingBitmap(context, appTheme, sizeDp = 58f, level = snapshot.caseLevel),
                )
                views.setImageViewBitmap(
                    R.id.widgetRightRing,
                    buildRoundedRingBitmap(context, appTheme, sizeDp = 58f, level = snapshot.rightLevel),
                )
            } else {
                views.setViewVisibility(R.id.widgetSplitRow, View.GONE)
                views.setViewVisibility(R.id.widgetSingleRow, View.VISIBLE)
                views.setTextViewText(R.id.widgetSingleValue, formatBattery(context, snapshot.primaryLevel))
                views.setTextViewText(R.id.widgetSingleLabel, context.getString(R.string.main_battery))
                views.setImageViewBitmap(
                    R.id.widgetSingleRing,
                    buildRoundedRingBitmap(
                        context,
                        appTheme,
                        sizeDp = if (useCompactSingleLayout) 56f else 64f,
                        level = snapshot.primaryLevel,
                    ),
                )
            }

            return views
        }

        private fun formatBattery(context: Context, level: Int?): String {
            return level?.let { context.getString(R.string.battery_percent, it) }
                ?: context.getString(R.string.battery_not_available)
        }

        private fun buildRoundedRingBitmap(
            context: Context,
            appTheme: AppTheme,
            sizeDp: Float,
            level: Int?,
        ): Bitmap {
            val clamped = (level ?: 0).coerceIn(0, 100)
            val density = context.resources.displayMetrics.density
            val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val strokeWidth = sizePx * (7f / 76f)
            val halfStroke = strokeWidth / 2f
            val bounds = RectF(
                halfStroke + 1f,
                halfStroke + 1f,
                sizePx - halfStroke - 1f,
                sizePx - halfStroke - 1f,
            )

            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = resolveRingTrackColor(context, appTheme)
            }
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = resolveBatteryRingProgressColor(context, level)
            }

            if (clamped <= 0) {
                canvas.drawArc(bounds, -90f, BatteryRingProgress.FULL_SWEEP_DEGREES, false, trackPaint)
                return bitmap
            }

            val radius = bounds.width() / 2f
            val capCompensationDeg = if (radius > 0f) {
                ((strokeWidth / 2f) / radius) * (180f / Math.PI.toFloat())
            } else {
                0f
            }
            val segments = BatteryRingProgress.segments(
                level = clamped,
                capCompensationDegrees = capCompensationDeg,
            )
            if (segments.trackSweepDegrees > 0f) {
                canvas.drawArc(
                    bounds,
                    segments.trackStartDegrees,
                    segments.trackSweepDegrees,
                    false,
                    trackPaint,
                )
            }
            canvas.drawArc(
                bounds,
                -90f,
                segments.progressSweepDegrees,
                false,
                progressPaint,
            )
            return bitmap
        }

        private fun resolveBatteryRingProgressColor(context: Context, level: Int?): Int {
            return when {
                level == null -> resolveAccentColorInt(context)
                level <= 10 -> ContextCompat.getColor(context, R.color.fast_pair_ring_critical)
                level <= 30 -> ContextCompat.getColor(context, R.color.fast_pair_ring_warning)
                else -> resolveAccentColorInt(context)
            }
        }

        private fun resolveAccentColorInt(context: Context): Int {
            return when (AppPreferences(context).appAccentColor) {
                AppAccentColor.BLUE -> 0xFF3B82F6.toInt()
                AppAccentColor.GREEN -> 0xFF22C55E.toInt()
                AppAccentColor.PURPLE -> 0xFF8B5CF6.toInt()
                AppAccentColor.LIME -> 0xFF84CC16.toInt()
                AppAccentColor.BROWN -> 0xFF8B5E34.toInt()
                AppAccentColor.PINK -> 0xFFEC4899.toInt()
                AppAccentColor.TEAL -> 0xFF14B8A6.toInt()
                AppAccentColor.CYAN -> 0xFF06B6D4.toInt()
                AppAccentColor.INDIGO -> 0xFF6366F1.toInt()
                AppAccentColor.AMBER -> 0xFFF59E0B.toInt()
            }
        }

        private fun resolveRingTrackColor(context: Context, appTheme: AppTheme): Int {
            val trackRes = when (appTheme) {
                AppTheme.DARK -> R.color.fast_pair_ring_track_dark
                AppTheme.LIGHT -> R.color.fast_pair_ring_track_light
            }
            return ContextCompat.getColor(context, trackRes)
        }

        private fun shouldUseCompactSingleLayout(
            snapshot: BluetoothBatterySnapshot?,
            options: Bundle?,
        ): Boolean {
            if (snapshot?.hasSplitLevels == true) return false
            val minWidthDp = options?.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                Int.MAX_VALUE,
            ) ?: Int.MAX_VALUE
            return minWidthDp <= COMPACT_SINGLE_MAX_WIDTH_DP
        }

        private fun applyTheme(
            views: RemoteViews,
            context: Context,
            appTheme: AppTheme,
        ) {
            val isDark = appTheme == AppTheme.DARK
            val primary = if (isDark) {
                ContextCompat.getColor(context, R.color.fast_pair_text_primary)
            } else {
                ContextCompat.getColor(context, R.color.widget_text_primary_light)
            }

            val backgroundRes = if (isDark) R.drawable.bg_widget_card_dark else R.drawable.bg_widget_card_light

            views.setInt(R.id.widgetRoot, "setBackgroundResource", backgroundRes)

            views.setTextColor(R.id.widgetTitle, primary)
            views.setTextColor(R.id.widgetSubtitle, primary)
            views.setTextColor(R.id.widgetLeftValue, primary)
            views.setTextColor(R.id.widgetCaseValue, primary)
            views.setTextColor(R.id.widgetRightValue, primary)
            views.setTextColor(R.id.widgetSingleValue, primary)
            views.setTextColor(R.id.widgetLeftLabel, primary)
            views.setTextColor(R.id.widgetCaseLabel, primary)
            views.setTextColor(R.id.widgetRightLabel, primary)
            views.setTextColor(R.id.widgetSingleLabel, primary)
        }

        private const val COMPACT_SINGLE_MAX_WIDTH_DP = 110
    }
}
