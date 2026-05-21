package com.laker.btbudsbattery.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot

class BatteryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val snapshot: BluetoothBatterySnapshot? = null
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildRemoteViews(context, snapshot))
        }
    }

    companion object {
        fun updateAll(context: Context, snapshot: BluetoothBatterySnapshot?) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, BatteryWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return
            val views = buildRemoteViews(context, snapshot)
            appWidgetManager.updateAppWidget(ids, views)
        }

        private fun buildRemoteViews(
            context: Context,
            snapshot: BluetoothBatterySnapshot?,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_battery)
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
                    buildRoundedRingBitmap(context, appTheme, sizeDp = 64f, level = null),
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
                    buildRoundedRingBitmap(context, appTheme, sizeDp = 64f, level = snapshot.primaryLevel),
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
                color = if (appTheme == AppTheme.DARK) {
                    ContextCompat.getColor(context, R.color.fast_pair_ring_track)
                } else {
                    ContextCompat.getColor(context, R.color.widget_ring_track_light)
                }
            }
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = resolveBatteryRingProgressColor(context, level)
            }

            if (clamped >= 100) {
                canvas.drawArc(bounds, -90f, 359.9f, false, progressPaint)
                return bitmap
            }
            if (clamped <= 0) {
                canvas.drawArc(bounds, -90f, 359.9f, false, trackPaint)
                return bitmap
            }

            val radius = bounds.width() / 2f
            val capCompensationDeg = if (radius > 0f) {
                ((strokeWidth / 2f) / radius) * (180f / Math.PI.toFloat())
            } else {
                0f
            }
            val visualGapDeg = 6f
            val effectiveGapDeg = visualGapDeg + (capCompensationDeg * 2f)
            val totalGapDeg = effectiveGapDeg * 2f
            val drawableSweep = (360f - totalGapDeg).coerceAtLeast(1f)
            val progressRatio = clamped / 100f
            val minSweep = 0.5f
            val rawProgressSweep = drawableSweep * progressRatio
            val progressSweep = rawProgressSweep.coerceIn(minSweep, drawableSweep - minSweep)
            val trackSweep = (drawableSweep - progressSweep).coerceAtLeast(0f)
            val start = -90f + (effectiveGapDeg / 2f)

            if (progressSweep > 0f) {
                canvas.drawArc(bounds, start, progressSweep, false, progressPaint)
            }
            if (trackSweep > 0f) {
                canvas.drawArc(
                    bounds,
                    start + progressSweep + effectiveGapDeg,
                    trackSweep,
                    false,
                    trackPaint,
                )
            }
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
            val secondary = if (isDark) {
                ContextCompat.getColor(context, R.color.fast_pair_text_secondary)
            } else {
                ContextCompat.getColor(context, R.color.widget_text_secondary_light)
            }
            val backgroundRes = if (isDark) R.drawable.bg_widget_card_dark else R.drawable.bg_widget_card_light

            views.setInt(R.id.widgetRoot, "setBackgroundResource", backgroundRes)

            views.setTextColor(R.id.widgetTitle, primary)
            views.setTextColor(R.id.widgetSubtitle, secondary)
            views.setTextColor(R.id.widgetLeftValue, primary)
            views.setTextColor(R.id.widgetCaseValue, primary)
            views.setTextColor(R.id.widgetRightValue, primary)
            views.setTextColor(R.id.widgetSingleValue, primary)
            views.setTextColor(R.id.widgetLeftLabel, secondary)
            views.setTextColor(R.id.widgetCaseLabel, secondary)
            views.setTextColor(R.id.widgetRightLabel, secondary)
            views.setTextColor(R.id.widgetSingleLabel, secondary)
        }
    }
}
