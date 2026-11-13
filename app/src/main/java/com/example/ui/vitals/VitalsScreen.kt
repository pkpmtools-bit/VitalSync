package com.example.ui.vitals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HeartRateReading
import com.example.data.model.HeartRateZone
import com.example.data.model.SleepSession
import com.example.data.model.SleepTimeframe
import com.example.data.model.WatchOsType
import com.example.data.model.formatTimeHHmm
import com.example.data.model.summarizeSleepSessions
import com.example.data.watch.LiveWatchTelemetry
import com.example.i18n.LocalStrings
import com.example.ui.WellnessDashboardSnapshot
import com.example.ui.components.HeartRatePulseChart
import com.example.ui.components.SleepHypnogramBar
import com.example.ui.components.SleepPeriodTrendChart
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVitalColors
import kotlin.math.roundToLong

@Composable
fun VitalsScreen(
  snapshot: WellnessDashboardSnapshot,
  liveTelemetry: LiveWatchTelemetry,
  onRecordHeartRate: (Int, Int, Int, String, WatchOsType) -> Unit,
  onSaveSleepSession: (Long, Long, Long, Long, Long, String) -> Unit,
  onNavigateWatches: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedSleepTimeframe by remember { mutableStateOf(SleepTimeframe.DAILY) }
  var showLogHrDialog by remember { mutableStateOf(false) }
  var showLogSleepDialog by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = strings.vitalsTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("vitals_tab_hr"),
            ) {
              Text(strings.tabHeartRate)
            }
            SegmentedButton(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("vitals_tab_sleep"),
            ) {
              Text(strings.tabSleep)
            }
          }
        }
      }

      if (selectedTab == 0) {
        val currentBpm = liveTelemetry.bpm ?: snapshot.latestHeartRate?.bpm?.toInt()
        val currentSpo2 = liveTelemetry.spo2Percent ?: snapshot.latestHeartRate?.spo2Percent?.toInt()
        val currentHrv = liveTelemetry.hrvMs ?: snapshot.latestHeartRate?.hrvMs?.toInt()

        item {
          Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, vitalColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(strings.currentHeartRate, style = MaterialTheme.typography.titleMedium)
                  val zoneText = currentBpm?.let { strings.zoneName(HeartRateZone.fromBpm(it)) } ?: "—"
                  Text(
                    text = "${strings.restingTargetLabel}: ${snapshot.profile.restingHeartRateTarget} ${strings.bpmUnit} · $zoneText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = if (currentBpm != null) "$currentBpm" else "--",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.displayLarge,
                  color = vitalColors.pulse,
                )
              }

              HeartRatePulseChart(
                bpmValues = snapshot.heartRateReadings.take(16).reversed().map { it.bpm.toInt() },
                restingTarget = snapshot.profile.restingHeartRateTarget.toInt(),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(110.dp),
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
              ) {
                VitalStatChip(strings.spo2Label, "${currentSpo2 ?: "--"}%", vitalColors.hydro)
                VitalStatChip(strings.hrvLabel, "${currentHrv ?: "--"} ms", vitalColors.sleep)
                VitalStatChip(
                  strings.restingTargetLabel,
                  "${snapshot.profile.restingHeartRateTarget} ${strings.bpmUnit}",
                  vitalColors.fit,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = { showLogHrDialog = true },
                  colors = ButtonDefaults.buttonColors(containerColor = vitalColors.pulse),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("log_hr_button"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(strings.logHeartRate)
                }
                FilledTonalButton(
                  onClick = onNavigateWatches,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("sync_watch_hr_button"),
                ) {
                  Icon(Icons.Rounded.Watch, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(strings.measureFromWatch)
                }
              }
            }
          }
        }

        item {
          Text(strings.recentHrReadings, style = MaterialTheme.typography.titleLarge)
        }

        if (snapshot.heartRateReadings.isEmpty()) {
          item {
            EmptyVitalsCard(icon = Icons.Rounded.Favorite, message = strings.emptyHrTip, tint = vitalColors.pulse)
          }
        } else {
          items(snapshot.heartRateReadings, key = { it.id.ifBlank { "${it.recordedAtMillis}_${it.bpm}" } }) { hr ->
            HeartRateRow(hr)
          }
        }
      } else {
        // SLEEP MONITOR TAB (Daily / Weekly / Monthly / Yearly)
        val periodSummary = summarizeSleepSessions(snapshot.sleepSessions, selectedSleepTimeframe)

        item {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SleepTimeframe.entries.forEachIndexed { index, timeframe ->
              SegmentedButton(
                selected = selectedSleepTimeframe == timeframe,
                onClick = { selectedSleepTimeframe = timeframe },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SleepTimeframe.entries.size),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sleep_period_${timeframe.key}"),
              ) {
                Text(strings.sleepTimeframeName(timeframe), maxLines = 1)
              }
            }
          }
        }

        item {
          Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, vitalColors.cardBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${strings.sleepArchitecture} · ${strings.sleepTimeframeName(selectedSleepTimeframe)}",
                    style = MaterialTheme.typography.titleMedium,
                  )
                  if (periodSummary.sessions.isNotEmpty()) {
                    val primaryText = if (selectedSleepTimeframe == SleepTimeframe.DAILY) {
                      "${periodSummary.avgMinutesPerNight / 60}${strings.hrsUnit} ${periodSummary.avgMinutesPerNight % 60}${strings.minUnit} · ${periodSummary.sessions.first().sourceDevice}"
                    } else {
                      "${strings.sleepNightsLogged}: ${periodSummary.sessions.size} · ${strings.sleepTotalPeriod}: ${periodSummary.totalMinutes / 60}${strings.hrsUnit}"
                    }
                    Text(
                      text = primaryText,
                      fontFamily = JetBrainsMonoFamily,
                      style = MaterialTheme.typography.bodyMedium,
                      color = vitalColors.sleep,
                    )
                  }
                }

                Surface(
                  color = vitalColors.sleepContainer,
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    Text(
                      text = if (periodSummary.sessions.isNotEmpty()) "${periodSummary.avgQualityScore}" else "--",
                      fontFamily = JetBrainsMonoFamily,
                      fontWeight = FontWeight.Bold,
                      style = MaterialTheme.typography.titleLarge,
                      color = vitalColors.sleep,
                    )
                    Text(
                      text = if (selectedSleepTimeframe == SleepTimeframe.DAILY) strings.sleepQuality else strings.sleepAvgQuality,
                      style = MaterialTheme.typography.labelSmall,
                      color = vitalColors.sleep,
                    )
                  }
                }
              }

              if (periodSummary.sessions.isNotEmpty()) {
                SleepHypnogramBar(
                  deepMinutes = periodSummary.avgDeepMinutes,
                  lightMinutes = periodSummary.avgLightMinutes,
                  remMinutes = periodSummary.avgRemMinutes,
                  awakeMinutes = periodSummary.avgAwakeMinutes,
                )

                if (selectedSleepTimeframe != SleepTimeframe.DAILY && periodSummary.buckets.isNotEmpty()) {
                  SleepPeriodTrendChart(
                    buckets = periodSummary.buckets,
                    targetMinutes = snapshot.profile.dailySleepGoalMinutes,
                    modifier = Modifier.fillMaxWidth(),
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  VitalStatChip(strings.sleepAvgBpm, "${periodSummary.avgBpm} ${strings.bpmUnit}", vitalColors.pulse)
                  VitalStatChip(
                    if (selectedSleepTimeframe == SleepTimeframe.DAILY) strings.sleepDuration else strings.sleepAvgNight,
                    "${periodSummary.avgMinutesPerNight / 60}${strings.hrsUnit} ${periodSummary.avgMinutesPerNight % 60}${strings.minUnit}",
                    vitalColors.sleep,
                  )
                  VitalStatChip(
                    strings.sleepNightsLogged,
                    "${periodSummary.sessions.size}",
                    vitalColors.hydro,
                  )
                }
              } else {
                Text(
                  text = strings.emptySleepTip,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Button(
                onClick = { showLogSleepDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = vitalColors.sleep),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("log_sleep_button"),
              ) {
                Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.logSleepSession)
              }
            }
          }
        }

        if (periodSummary.sessions.isEmpty()) {
          item {
            EmptyVitalsCard(icon = Icons.Rounded.Bedtime, message = strings.emptySleepTip, tint = vitalColors.sleep)
          }
        } else {
          items(periodSummary.sessions, key = { it.id.ifBlank { "${it.endTimeMillis}" } }) { session ->
            SleepHistoryCard(session)
          }
        }
      }
    }
  }

  if (showLogHrDialog) {
    LogHeartRateDialog(
      defaultDevice = liveTelemetry.deviceName.ifBlank {
        snapshot.watchDevices.firstOrNull()?.name ?: "FitCloudPro Watch"
      },
      onDismiss = { showLogHrDialog = false },
      onSave = { bpm, spo2, hrv, device, os ->
        onRecordHeartRate(bpm, spo2, hrv, device, os)
        showLogHrDialog = false
      },
    )
  }

  if (showLogSleepDialog) {
    LogSleepSessionDialog(
      defaultDevice = snapshot.watchDevices.firstOrNull()?.name ?: "FitCloudPro Watch",
      onDismiss = { showLogSleepDialog = false },
      onSave = { deep, light, rem, awake, avgBpm, device ->
        onSaveSleepSession(deep, light, rem, awake, avgBpm, device)
        showLogSleepDialog = false
      },
    )
  }
}

@Composable
private fun VitalStatChip(label: String, value: String, tint: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontFamily = JetBrainsMonoFamily,
      fontWeight = FontWeight.Bold,
      style = MaterialTheme.typography.titleMedium,
      color = tint,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun EmptyVitalsCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  message: String,
  tint: Color,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
      Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun HeartRateRow(reading: HeartRateReading) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current
  val zone = HeartRateZone.entries.firstOrNull { it.key == reading.zone } ?: HeartRateZone.RESTING

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, vitalColors.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(vitalColors.pulseContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.Favorite, contentDescription = null, tint = vitalColors.pulse, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = "${reading.sourceDevice} · ${strings.zoneName(zone)}",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "${formatTimeHHmm(reading.recordedAtMillis)} · ${strings.spo2Label} ${reading.spo2Percent}% · ${strings.hrvLabel} ${reading.hrvMs}ms",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text = "${reading.bpm} ${strings.bpmUnit}",
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        color = vitalColors.pulse,
      )
    }
  }
}

@Composable
private fun SleepHistoryCard(session: SleepSession) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, vitalColors.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${session.dateKey} · ${session.sourceDevice}",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          text = "${session.totalMinutes / 60}${strings.hrsUnit} ${session.totalMinutes % 60}${strings.minUnit} (${session.qualityScore}%)",
          fontFamily = JetBrainsMonoFamily,
          fontWeight = FontWeight.Bold,
          color = vitalColors.sleep,
        )
      }
      SleepHypnogramBar(
        deepMinutes = session.deepMinutes,
        lightMinutes = session.lightMinutes,
        remMinutes = session.remMinutes,
        awakeMinutes = session.awakeMinutes,
      )
    }
  }
}

@Composable
private fun LogHeartRateDialog(
  defaultDevice: String,
  onDismiss: () -> Unit,
  onSave: (Int, Int, Int, String, WatchOsType) -> Unit,
) {
  val strings = LocalStrings.current
  var bpm by remember { mutableFloatStateOf(72f) }
  var spo2 by remember { mutableFloatStateOf(98f) }
  var hrv by remember { mutableFloatStateOf(54f) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.logHeartRate) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${bpm.toInt()} ${strings.bpmUnit}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
        Slider(value = bpm, onValueChange = { bpm = it }, valueRange = 40f..190f)

        Text("${strings.spo2Label}: ${spo2.toInt()}%", fontFamily = JetBrainsMonoFamily)
        Slider(value = spo2, onValueChange = { spo2 = it }, valueRange = 85f..100f)

        Text("${strings.hrvLabel}: ${hrv.toInt()} ms", fontFamily = JetBrainsMonoFamily)
        Slider(value = hrv, onValueChange = { hrv = it }, valueRange = 15f..160f)
      }
    },
    confirmButton = {
      Button(onClick = { onSave(bpm.toInt(), spo2.toInt(), hrv.toInt(), defaultDevice, WatchOsType.FITCLOUDPRO) }) {
        Text(strings.saveAction)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
    },
  )
}

@Composable
private fun LogSleepSessionDialog(
  defaultDevice: String,
  onDismiss: () -> Unit,
  onSave: (Long, Long, Long, Long, Long, String) -> Unit,
) {
  val strings = LocalStrings.current
  var deep by remember { mutableFloatStateOf(105f) }
  var light by remember { mutableFloatStateOf(240f) }
  var rem by remember { mutableFloatStateOf(95f) }
  var awake by remember { mutableFloatStateOf(15f) }
  var avgBpm by remember { mutableFloatStateOf(58f) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.logSleepSession) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${strings.sleepDeep}: ${deep.toInt()} ${strings.minUnit}", fontFamily = JetBrainsMonoFamily)
        Slider(value = deep, onValueChange = { deep = it }, valueRange = 20f..240f)

        Text("${strings.sleepLight}: ${light.toInt()} ${strings.minUnit}", fontFamily = JetBrainsMonoFamily)
        Slider(value = light, onValueChange = { light = it }, valueRange = 60f..420f)

        Text("${strings.sleepRem}: ${rem.toInt()} ${strings.minUnit}", fontFamily = JetBrainsMonoFamily)
        Slider(value = rem, onValueChange = { rem = it }, valueRange = 15f..180f)

        Text("${strings.sleepAwake}: ${awake.toInt()} ${strings.minUnit}", fontFamily = JetBrainsMonoFamily)
        Slider(value = awake, onValueChange = { awake = it }, valueRange = 0f..90f)

        Text("${strings.sleepAvgBpm}: ${avgBpm.toInt()} ${strings.bpmUnit}", fontFamily = JetBrainsMonoFamily)
        Slider(value = avgBpm, onValueChange = { avgBpm = it }, valueRange = 40f..100f)
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            deep.roundToLong(),
            light.roundToLong(),
            rem.roundToLong(),
            awake.roundToLong(),
            avgBpm.roundToLong(),
            defaultDevice,
          )
        }
      ) {
        Text(strings.saveAction)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
    },
  )
}
