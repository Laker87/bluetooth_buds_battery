package com.example.btbattery.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.btbattery.R
import com.example.btbattery.domain.model.BluetoothBatterySnapshot

@Composable
fun FastPairOverlay(
    visible: Boolean,
    snapshot: BluetoothBatterySnapshot?,
    onDismiss: () -> Unit,
) {
    if (snapshot == null) return

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Headset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snapshot.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val levelText = snapshot.primaryLevel?.let { "$it%" }
                            ?: stringResource(R.string.battery_unknown)
                        Text(
                            text = stringResource(R.string.connected_battery_line, levelText),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { (snapshot.primaryLevel ?: 0) / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )

                        val split = buildList {
                            snapshot.leftLevel?.let {
                                add(stringResource(R.string.side_percent, stringResource(R.string.left_short), it))
                            }
                            snapshot.rightLevel?.let {
                                add(stringResource(R.string.side_percent, stringResource(R.string.right_short), it))
                            }
                            snapshot.caseLevel?.let {
                                add(stringResource(R.string.side_percent, stringResource(R.string.case_short), it))
                            }
                        }
                        if (split.isNotEmpty()) {
                            Text(
                                text = split.joinToString(" | "),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    Button(onClick = onDismiss) {
                        Text(text = stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}
