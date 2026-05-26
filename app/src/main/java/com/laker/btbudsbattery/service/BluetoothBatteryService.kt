package com.laker.btbudsbattery.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.app.ForegroundServiceStartNotAllowedException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.laker.btbudsbattery.MainActivity
import com.laker.btbudsbattery.R
import com.laker.btbudsbattery.core.AppAccentColor
import com.laker.btbudsbattery.core.AppTheme
import com.laker.btbudsbattery.core.AppPreferences
import com.laker.btbudsbattery.core.FastPairEventBus
import com.laker.btbudsbattery.core.RuntimePermissionGate
import com.laker.btbudsbattery.data.BluetoothBatteryRepositoryImpl
import com.laker.btbudsbattery.domain.model.BluetoothBatterySnapshot
import com.laker.btbudsbattery.domain.usecase.ObserveBatteryEventsUseCase
import com.laker.btbudsbattery.widget.BatteryWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BluetoothBatteryService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var appPreferences: AppPreferences
    private val stateLock = Any()
    private val lastDelivered = LinkedHashMap<String, BluetoothBatterySnapshot>()
    private val ringBitmapCache = HashMap<String, Bitmap>()
    private var observeJob: Job? = null
    @Volatile
    private var lastObserveRestartAtMs: Long = 0L
    @Volatile
    private var isInForegroundMode = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        appPreferences = AppPreferences(applicationContext)
        createChannels()
        if (!RuntimePermissionGate.hasMonitoringPermissions(this)) {
            hideForegroundNotificationCompletely()
            stopSelf()
            return
        }

        restartObservePipeline(reason = "service_create", force = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!RuntimePermissionGate.hasMonitoringPermissions(this)) {
            hideForegroundNotificationCompletely()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP_MONITORING -> stopSelf()
            ACTION_START_MONITORING -> {
                val requireForeground = intent.getBooleanExtra(EXTRA_REQUIRE_FOREGROUND, false)
                if (requireForeground) {
                    ensureForegroundModeWithCurrentNotification()
                }
                ensureObservePipelineRunning()
                restartObservePipeline(reason = "start_monitoring_connect_event")
            }
            ACTION_REFRESH_NOTIFICATION -> refreshActiveNotification()
            ACTION_REFRESH_WIDGET -> BatteryWidgetProvider.updateAll(this, latestConnectedSnapshot())
            ACTION_BOOT_RESTORE_MONITORING -> {
                val requireForeground = intent.getBooleanExtra(EXTRA_REQUIRE_FOREGROUND, true)
                if (requireForeground) {
                    ensureForegroundModeWithCurrentNotification()
                }
                ensureObservePipelineRunning()
                restartObservePipeline(reason = "boot_restore_or_user_unlock")
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildServiceNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_headphones)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun showFastPairNotification(snapshot: BluetoothBatterySnapshot) {
        if (!canPostNotifications()) return

        val compactRemoteViews = RemoteViews(
            packageName,
            R.layout.layout_fast_pair_notification_compact,
        ).apply {
            bindNotificationContent(this, snapshot, isCompact = true)
        }

        val expandedRemoteViews = RemoteViews(
            packageName,
            R.layout.layout_fast_pair_notification,
        ).apply {
            bindNotificationContent(this, snapshot, isCompact = false)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            99,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_FAST_PAIR)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(snapshot.deviceName)
            .setContentText(buildStatusBarBatteryText(snapshot))
            .setContentInfo(formatBattery(snapshot.primaryLevel))
            .setNumber(snapshot.primaryLevel ?: 0)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCustomContentView(compactRemoteViews)
            .setCustomBigContentView(expandedRemoteViews)
            .setCustomHeadsUpContentView(compactRemoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openAppIntent)
            .build()
        if (!tryStartForeground(FAST_PAIR_NOTIFICATION_ID, notification)) {
            notificationManager.notify(FAST_PAIR_NOTIFICATION_ID, notification)
        }
    }

    private fun bindNotificationContent(
        remoteViews: RemoteViews,
        snapshot: BluetoothBatterySnapshot,
        isCompact: Boolean,
    ) {
        if (isCompact) {
            remoteViews.setTextViewText(R.id.fastPairTitle, snapshot.deviceName)
            remoteViews.setTextViewText(R.id.fastPairSubtitle, getString(R.string.fast_pair_connected))
            if (snapshot.hasSplitLevels) {
                remoteViews.setViewVisibility(R.id.fastPairBatteryValue, View.GONE)
                remoteViews.setViewVisibility(R.id.fastPairSplitBatteryRowCompact, View.VISIBLE)
                remoteViews.setTextViewText(
                    R.id.fastPairLeftBatteryValueCompact,
                    "${getString(R.string.left_short)} ${formatBattery(snapshot.leftLevel)}",
                )
                remoteViews.setTextViewText(
                    R.id.fastPairCaseBatteryValueCompact,
                    "${getString(R.string.case_short)} ${formatBattery(snapshot.caseLevel)}",
                )
                remoteViews.setTextViewText(
                    R.id.fastPairRightBatteryValueCompact,
                    "${getString(R.string.right_short)} ${formatBattery(snapshot.rightLevel)}",
                )
            } else {
                remoteViews.setViewVisibility(R.id.fastPairSplitBatteryRowCompact, View.GONE)
                remoteViews.setViewVisibility(R.id.fastPairBatteryValue, View.VISIBLE)
                remoteViews.setTextViewText(R.id.fastPairBatteryValue, formatBattery(snapshot.primaryLevel))
            }
            return
        }

        remoteViews.setTextViewText(R.id.fastPairTitle, buildExpandedTitle(snapshot))

        val hasSplitValues = snapshot.leftLevel != null || snapshot.rightLevel != null || snapshot.caseLevel != null

        if (hasSplitValues) {
            remoteViews.setViewVisibility(R.id.splitBatteryRow, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.singleBatteryRow, View.GONE)
            remoteViews.setViewVisibility(R.id.leftBatteryItem, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.rightBatteryItem, View.VISIBLE)
            remoteViews.setTextViewText(R.id.leftBatteryLabel, getString(R.string.left_bud))
            remoteViews.setTextViewText(R.id.caseBatteryLabel, getString(R.string.case_battery))
            remoteViews.setTextViewText(R.id.rightBatteryLabel, getString(R.string.right_bud))
            remoteViews.setTextViewText(R.id.caseBatteryValue, formatBattery(snapshot.caseLevel))
            remoteViews.setTextViewText(R.id.leftBatteryValue, formatBattery(snapshot.leftLevel))
            remoteViews.setTextViewText(R.id.rightBatteryValue, formatBattery(snapshot.rightLevel))
            remoteViews.setImageViewBitmap(
                R.id.caseBatteryRing,
                buildRoundedRingBitmap(sizeDp = 68f, level = snapshot.caseLevel),
            )
            remoteViews.setImageViewBitmap(
                R.id.leftBatteryRing,
                buildRoundedRingBitmap(sizeDp = 68f, level = snapshot.leftLevel),
            )
            remoteViews.setImageViewBitmap(
                R.id.rightBatteryRing,
                buildRoundedRingBitmap(sizeDp = 68f, level = snapshot.rightLevel),
            )
        } else {
            remoteViews.setViewVisibility(R.id.splitBatteryRow, View.GONE)
            remoteViews.setViewVisibility(R.id.singleBatteryRow, View.VISIBLE)
            remoteViews.setTextViewText(R.id.singleBatteryLabel, getString(R.string.main_battery))
            remoteViews.setTextViewText(R.id.singleBatteryValue, formatBattery(snapshot.primaryLevel))
            remoteViews.setImageViewBitmap(
                R.id.singleBatteryRing,
                buildRoundedRingBitmap(sizeDp = 74f, level = snapshot.primaryLevel),
            )
        }

    }

    private fun buildExpandedTitle(snapshot: BluetoothBatterySnapshot): String {
        val lowestLevel = listOfNotNull(
            snapshot.primaryLevel,
            snapshot.leftLevel,
            snapshot.rightLevel,
            snapshot.caseLevel,
        ).minOrNull()
        val state = if (lowestLevel != null && lowestLevel <= 20) {
            getString(R.string.low_battery)
        } else {
            getString(R.string.fast_pair_connected)
        }
        return "${snapshot.deviceName} \u2022 $state"
    }

    private fun buildStatusBarBatteryText(snapshot: BluetoothBatterySnapshot): String {
        val splitParts = buildList {
            snapshot.leftLevel?.let { add("L ${formatBattery(it)}") }
            snapshot.caseLevel?.let { add("C ${formatBattery(it)}") }
            snapshot.rightLevel?.let { add("R ${formatBattery(it)}") }
        }
        if (splitParts.isNotEmpty()) return splitParts.joinToString(" \u2022 ")
        return formatBattery(snapshot.primaryLevel)
    }

    private fun formatBattery(level: Int?): String {
        return level?.let { getString(R.string.battery_percent, it) }
            ?: getString(R.string.battery_not_available)
    }

    private fun buildRoundedRingBitmap(sizeDp: Float, level: Int?): Bitmap {
        val clamped = (level ?: 0).coerceIn(0, 100)
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val progressColor = resolveBatteryRingProgressColor(level)
        val themeKey = appPreferences.appTheme.name
        val cacheKey = "$sizePx:${level ?: "null"}:$progressColor:$themeKey"
        synchronized(stateLock) {
            ringBitmapCache[cacheKey]?.let { return it }
        }

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
            color = ContextCompat.getColor(this@BluetoothBatteryService, R.color.fast_pair_ring_track)
        }
        val innerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = resolveRingInnerFillColor()
        }
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = progressColor
        }
        val center = sizePx / 2f
        val innerRadius = (bounds.width() / 2f - strokeWidth).coerceAtLeast(0f)
        canvas.drawCircle(center, center, innerRadius, innerFillPaint)

        if (clamped >= 100) {
            canvas.drawArc(bounds, -90f, 359.9f, false, progressPaint)
            synchronized(stateLock) {
                ringBitmapCache[cacheKey] = bitmap
            }
            return bitmap
        }
        if (clamped <= 0) {
            // Empty state: draw a continuous track ring without any gaps.
            canvas.drawArc(bounds, -90f, 359.9f, false, trackPaint)
            synchronized(stateLock) {
                ringBitmapCache[cacheKey] = bitmap
            }
            return bitmap
        }

        val radius = bounds.width() / 2f
        val capCompensationDeg = if (radius > 0f) {
            ((strokeWidth / 2f) / radius) * (180f / Math.PI.toFloat())
        } else {
            0f
        }
        // Desired visual gap between progress and track.
        val visualGapDeg = 6f
        // Round caps visually add length at both ends, compensate to preserve gap.
        val effectiveGapDeg = visualGapDeg + (capCompensationDeg * 2f)
        val totalGapDeg = effectiveGapDeg * 2f
        val drawableSweep = (360f - totalGapDeg).coerceAtLeast(1f)
        val progressRatio = clamped / 100f
        val minSweep = 0.5f
        val rawProgressSweep = drawableSweep * progressRatio
        val progressSweep = when {
            clamped >= 100 -> drawableSweep
            else -> rawProgressSweep.coerceIn(minSweep, drawableSweep - minSweep)
        }
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

        synchronized(stateLock) {
            ringBitmapCache[cacheKey] = bitmap
        }
        return bitmap
    }

    private fun resolveBatteryRingProgressColor(level: Int?): Int {
        return when {
            level == null -> resolveAccentColorInt()
            level <= 10 -> ContextCompat.getColor(this, R.color.fast_pair_ring_critical)
            level <= 30 -> ContextCompat.getColor(this, R.color.fast_pair_ring_warning)
            else -> resolveAccentColorInt()
        }
    }

    private fun resolveAccentColorInt(): Int {
        return when (appPreferences.appAccentColor) {
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

    private fun resolveRingInnerFillColor(): Int {
        return when (appPreferences.appTheme) {
            AppTheme.DARK -> 0x0DFFFFFF
            AppTheme.LIGHT -> 0x0D000000
        }
    }

    private fun createChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notification_channel_service_name),
            NotificationManager.IMPORTANCE_LOW,
        )

        val fastPairChannel = NotificationChannel(
            CHANNEL_FAST_PAIR,
            getString(R.string.notification_channel_fast_pair_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Fast Pair style heads-up alerts"
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(fastPairChannel)
    }

    private fun canPostNotifications(): Boolean {
        return RuntimePermissionGate.hasNotificationPermission(this)
    }

    private fun shouldShowNotification(
        previous: BluetoothBatterySnapshot?,
        snapshot: BluetoothBatterySnapshot,
    ): Boolean {
        if (previous == null) return true
        if (!previous.isConnected && snapshot.isConnected) return true
        if (previous.primaryLevel != snapshot.primaryLevel) return true
        if (previous.leftLevel != snapshot.leftLevel) return true
        if (previous.rightLevel != snapshot.rightLevel) return true
        if (previous.caseLevel != snapshot.caseLevel) return true
        return false
    }

    private fun hasAnyConnectedDevices(): Boolean {
        synchronized(stateLock) {
            return lastDelivered.values.any { it.isConnected }
        }
    }

    private fun hideForegroundNotificationCompletely() {
        if (isInForegroundMode) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isInForegroundMode = false
        }
        notificationManager.cancel(FAST_PAIR_NOTIFICATION_ID)
    }

    private fun ensureForegroundModeWithCurrentNotification() {
        if (isInForegroundMode) return
        val connectedSnapshot = latestConnectedSnapshot()
        if (connectedSnapshot != null) {
            showFastPairNotification(connectedSnapshot)
            return
        }
        tryStartForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification())
    }

    private fun refreshActiveNotification() {
        synchronized(stateLock) {
            ringBitmapCache.clear()
        }
        latestConnectedSnapshot()?.let { snapshot ->
            showFastPairNotification(snapshot)
        }
    }

    private fun tryStartForeground(notificationId: Int, notification: Notification): Boolean {
        return try {
            startForeground(notificationId, notification)
            isInForegroundMode = true
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    private fun ensureObservePipelineRunning() {
        if (observeJob?.isActive == true) return
        restartObservePipeline(reason = "ensure_running", force = true)
    }

    private fun restartObservePipeline(
        reason: String,
        force: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastObserveRestartAtMs < OBSERVE_RESTART_THROTTLE_MS) {
            return
        }
        lastObserveRestartAtMs = now
        observeJob?.cancel()
        val repository = BluetoothBatteryRepositoryImpl(
            context = applicationContext,
            ioContext = Dispatchers.IO,
        )
        val observeBatteryEventsUseCase = ObserveBatteryEventsUseCase(repository)
        observeJob = serviceScope.launch {
            Log.d(LOG_TAG, "source=observe_restart reason=$reason")
            observeBatteryEventsUseCase()
                .catch { error ->
                    Log.w(LOG_TAG, "source=observe_error reason=$reason error=${error.javaClass.simpleName}")
                }
                .collect { snapshot ->
                    val previousByAddress = synchronized(stateLock) { lastDelivered[snapshot.deviceAddress] }
                    val previousVisible = findPreviousVisibleSnapshot(snapshot)
                    var merged = mergeForReconnect(previousVisible, snapshot)
                    if (merged.isConnected) {
                        synchronized(stateLock) {
                            lastDelivered[snapshot.deviceAddress] = merged
                        }
                    } else {
                        merged = markDeviceFamilyDisconnected(merged)
                    }
                    FastPairEventBus.emit(merged)
                    persistLastKnownBattery(previousByAddress, merged)
                    BatteryWidgetProvider.updateAll(this@BluetoothBatteryService, latestConnectedSnapshot())

                    if (merged.isConnected) {
                        if (shouldShowNotification(previousByAddress, merged)) {
                            showFastPairNotification(merged)
                        }
                    }

                    if (!hasAnyConnectedDevices()) {
                        hideForegroundNotificationCompletely()
                    }
                }
        }
    }

    private fun latestConnectedSnapshot(): BluetoothBatterySnapshot? {
        synchronized(stateLock) {
            return lastDelivered.values
                .asSequence()
                .filter { it.isConnected }
                .maxByOrNull { it.timestamp }
        }
    }

    private fun persistLastKnownBattery(
        previous: BluetoothBatterySnapshot?,
        current: BluetoothBatterySnapshot,
    ) {
        val existing = appPreferences.lastKnownSnapshot
        val merged = mergeForLastKnown(existing, current)
        if (merged != null) {
            appPreferences.lastKnownSnapshot = merged
        }

        val disconnectedSince = appPreferences.disconnectedSinceMillis
        when {
            current.isConnected -> appPreferences.disconnectedSinceMillis = null
            previous?.isConnected == true -> appPreferences.disconnectedSinceMillis = System.currentTimeMillis()
            disconnectedSince == null && !current.isConnected -> {
                appPreferences.disconnectedSinceMillis = System.currentTimeMillis()
            }
        }

        val disconnectedAt = if (current.isConnected) null else appPreferences.disconnectedSinceMillis
        val historySource = merged?.takeIf { it.isSameUserVisibleDevice(current) } ?: current
        val clearSplitLevels = historySource.primaryLevel != null && !historySource.hasSplitLevels
        appPreferences.upsertHeadphoneHistory(
            deviceAddress = current.deviceAddress,
            deviceName = current.deviceName,
            lastBatteryLevel = historySource.primaryLevel,
            lastLeftLevel = historySource.leftLevel,
            lastRightLevel = historySource.rightLevel,
            lastCaseLevel = historySource.caseLevel,
            lastDisconnectedAt = disconnectedAt,
            clearSplitLevels = clearSplitLevels,
        )
    }

    private fun mergeForLastKnown(
        existing: BluetoothBatterySnapshot?,
        current: BluetoothBatterySnapshot,
    ): BluetoothBatterySnapshot? {
        val hasAnyLevel = current.primaryLevel != null ||
            current.leftLevel != null ||
            current.rightLevel != null ||
            current.caseLevel != null
        if (hasAnyLevel) return current
        if (existing == null) return null

        val sameDevice = existing.deviceAddress == current.deviceAddress
        if (!sameDevice) return existing

        return current.copy(
            batteryLevel = current.batteryLevel ?: existing.batteryLevel,
            leftLevel = current.leftLevel ?: existing.leftLevel,
            rightLevel = current.rightLevel ?: existing.rightLevel,
            caseLevel = current.caseLevel ?: existing.caseLevel,
        )
    }

    private fun BluetoothBatterySnapshot.isSameUserVisibleDevice(other: BluetoothBatterySnapshot): Boolean {
        return deviceAddress == other.deviceAddress
    }

    private fun findPreviousVisibleSnapshot(current: BluetoothBatterySnapshot): BluetoothBatterySnapshot? {
        synchronized(stateLock) {
            return lastDelivered.values.lastOrNull { snapshot ->
                snapshot.deviceAddress == current.deviceAddress
            }
        }
    }

    private fun mergeForReconnect(
        previous: BluetoothBatterySnapshot?,
        current: BluetoothBatterySnapshot,
    ): BluetoothBatterySnapshot {
        if (previous == null) return current
        val sameDevice = previous.deviceAddress == current.deviceAddress
        if (!sameDevice) return current
        val isReconnect = current.isConnected && !previous.isConnected
        val keepSplitForFlicker = shouldKeepPreviousSplitForFlicker(previous, current)
        if (keepSplitForFlicker) {
            Log.d(
                LOG_TAG,
                "source=merge_keep_split device=${current.deviceName} prevTs=${previous.timestamp} " +
                    "currTs=${current.timestamp} prevL=${previous.leftLevel} prevR=${previous.rightLevel} " +
                    "prevC=${previous.caseLevel}",
            )
        }
        return current.copy(
            batteryLevel = current.batteryLevel ?: previous.batteryLevel,
            leftLevel = current.leftLevel ?: previous.leftLevel.takeIf { isReconnect || keepSplitForFlicker },
            rightLevel = current.rightLevel ?: previous.rightLevel.takeIf { isReconnect || keepSplitForFlicker },
            caseLevel = current.caseLevel ?: previous.caseLevel.takeIf { isReconnect || keepSplitForFlicker },
        )
    }

    private fun markDeviceFamilyDisconnected(disconnected: BluetoothBatterySnapshot): BluetoothBatterySnapshot {
        synchronized(stateLock) {
            val address = disconnected.deviceAddress

            val keys = lastDelivered.keys.toList()
            keys.forEach { key ->
                val current = lastDelivered[key] ?: return@forEach
                val sameFamily = current.deviceAddress == address
                if (!sameFamily || !current.isConnected) return@forEach
                lastDelivered[key] = current.copy(
                    isConnected = false,
                    timestamp = maxOf(current.timestamp, disconnected.timestamp),
                )
            }

            lastDelivered[address] = disconnected.copy(isConnected = false)
            return lastDelivered[address] ?: disconnected
        }
    }

    private fun shouldKeepPreviousSplitForFlicker(
        previous: BluetoothBatterySnapshot,
        current: BluetoothBatterySnapshot,
    ): Boolean {
        if (!current.isConnected || !previous.isConnected) return false
        if (current.hasSplitLevels) return false
        if (!previous.hasSplitLevels) return false
        val ageMs = (current.timestamp - previous.timestamp).coerceAtLeast(0L)
        return ageMs <= SPLIT_FLICKER_WINDOW_MS
    }

    companion object {
        private const val LOG_TAG = "BtBatteryService"
        private const val CHANNEL_SERVICE = "bt_battery_service"
        private const val CHANNEL_FAST_PAIR = "bt_fast_pair"
        private const val SERVICE_NOTIFICATION_ID = 1001
        private const val FAST_PAIR_NOTIFICATION_ID = SERVICE_NOTIFICATION_ID
        private const val SPLIT_FLICKER_WINDOW_MS = 20_000L
        private const val OBSERVE_RESTART_THROTTLE_MS = 2_500L

        const val ACTION_START_MONITORING = "com.laker.btbudsbattery.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.laker.btbudsbattery.action.STOP_MONITORING"
        const val ACTION_REFRESH_NOTIFICATION = "com.laker.btbudsbattery.action.REFRESH_NOTIFICATION"
        const val ACTION_REFRESH_WIDGET = "com.laker.btbudsbattery.action.REFRESH_WIDGET"
        const val ACTION_BOOT_RESTORE_MONITORING = "com.laker.btbudsbattery.action.BOOT_RESTORE_MONITORING"
        const val EXTRA_REQUIRE_FOREGROUND = "com.laker.btbudsbattery.extra.REQUIRE_FOREGROUND"
    }
}


