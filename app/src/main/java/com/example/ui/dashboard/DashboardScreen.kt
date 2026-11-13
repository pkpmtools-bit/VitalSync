package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.DrinkType
import com.example.data.model.HeartRateZone
import com.example.data.watch.LiveWatchTelemetry
import com.example.i18n.LocalStrings
import com.example.ui.WellnessDashboardSnapshot
import com.example.ui.components.HeartRatePulseChart
import com.example.ui.components.LanguagePillStrip
import com.example.ui.components.SleepHypnogramBar
import com.example.ui.components.WellnessConcentricRings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVitalColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
  snapshot: WellnessDashboardSnapshot,
  liveTelemetry: LiveWatchTelemetry,
  selectedLanguage: AppLanguage,
  onSelectLanguage: (AppLanguage) -> Unit,
  onQuickHydration: (Long) -> Unit,
  onSyncAll: () -> Unit,
  onNavigateHydration: () -> Unit,
  onNavigateVitals: () -> Unit,
  onNavigateWatches: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  val hydroGoal = snapshot.profile.dailyWaterGoalMl.coerceAtLeast(500L)
  val hydroProgress = (snapshot.totalEffectiveWaterMl.toFloat() / hydroGoal).coerceIn(0f, 1.5f)
  val stepsGoal = snapshot.profile.dailyStepsGoal.coerceAtLeast(1000L)
  val stepsProgress = (snapshot.dailyActivity.steps.toFloat() / stepsGoal).coerceIn(0f, 1.5f)
  val sleepGoal = snapshot.profile.dailySleepGoalMinutes.coerceAtLeast(180L)
  val lastSleepMinutes = snapshot.latestSleep?.totalMinutes ?: 0L
  val sleepProgress = (lastSleepMinutes.toFloat() / sleepGoal).coerceIn(0f, 1.5f)

  val readiness = (
    ((hydroProgress.coerceAtMost(1f) * 35f) +
      (stepsProgress.coerceAtMost(1f) * 35f) +
      (sleepProgress.coerceAtMost(1f) * 30f))
    ).roundToInt().coerceIn(0, 100)

  val activeBpm = liveTelemetry.bpm ?: snapshot.latestHeartRate?.bpm?.toInt()
  val activeSpo2 = liveTelemetry.spo2Percent ?: snapshot.latestHeartRate?.spo2Percent?.toInt()
  val activeHrv = liveTelemetry.hrvMs ?: snapshot.latestHeartRate?.hrvMs?.toInt()

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header + Instant V4/EN Switcher
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.goodDayGreeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = snapshot.profile.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            FilledTonalButton(
              onClick = onSyncAll,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("dashboard_sync_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = strings.syncNow,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(strings.syncNow)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            LanguagePillStrip(
              selectedLanguage = selectedLanguage,
              onSelectLanguage = onSelectLanguage,
            )

            Surface(
              color = if (liveTelemetry.isConnected) vitalColors.fitContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNavigateWatches() }
                .testTag("dashboard_watch_status_pill"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = if (liveTelemetry.isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Watch,
                  contentDescription = null,
                  tint = if (liveTelemetry.isConnected) vitalColors.fit else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = if (liveTelemetry.isConnected) {
                    liveTelemetry.deviceName.ifBlank { strings.liveWatchConnected }
                  } else if (snapshot.watchDevices.isNotEmpty()) {
                    snapshot.watchDevices.first().name
                  } else {
                    strings.noWatchConnected
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = if (liveTelemetry.isConnected) vitalColors.fit else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Hero Concentric Rings + Google Fit / Health Connect Card
      item {
        Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.linearGradient(
                  colors = listOf(
                    vitalColors.fitContainer.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surface,
                    vitalColors.hydroContainer.copy(alpha = 0.25f),
                  )
                )
              )
              .padding(20.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
              WellnessConcentricRings(
                stepsProgress = stepsProgress,
                hydrationProgress = hydroProgress,
                sleepProgress = sleepProgress,
                readinessScore = readiness,
                modifier = Modifier.size(134.dp),
              )

              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  text = strings.activityCardTitle,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                  MetricBadge(
                    icon = Icons.Rounded.DirectionsWalk,
                    value = "${snapshot.dailyActivity.steps}",
                    label = strings.stepsLabel,
                    tint = vitalColors.fit,
                  )
                  MetricBadge(
                    icon = Icons.Rounded.LocalFireDepartment,
                    value = "${snapshot.dailyActivity.caloriesBurned}",
                    label = strings.kcalUnit,
                    tint = vitalColors.pulse,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                  val km = String.format(Locale.US, "%.1f", snapshot.dailyActivity.distanceMeters / 1000.0)
                  MetricBadge(
                    icon = Icons.Rounded.Watch,
                    value = "$km ${strings.kmUnit}",
                    label = strings.distanceLabel,
                    tint = vitalColors.hydro,
                  )
                  MetricBadge(
                    icon = Icons.Rounded.Favorite,
                    value = "${snapshot.dailyActivity.movePoints}",
                    label = strings.movePointsLabel,
                    tint = vitalColors.sleep,
                  )
                }
              }
            }
          }
        }
      }

      // Hydro Coach Card + Quick Add
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateHydration() }
            .testTag("dashboard_hydration_card"),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(vitalColors.hydroContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = strings.hydrationCardTitle,
                    tint = vitalColors.hydro,
                  )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(strings.hydrationCardTitle, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = "${snapshot.totalEffectiveWaterMl} / $hydroGoal ${strings.mlUnit}",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = vitalColors.hydro,
                  )
                }
              }

              val remaining = (hydroGoal - snapshot.totalEffectiveWaterMl).coerceAtLeast(0L)
              Text(
                text = if (remaining == 0L) strings.goalReachedLabel else "${strings.remainingLabel}: $remaining ${strings.mlUnit}",
                style = MaterialTheme.typography.labelSmall,
                color = if (remaining == 0L) vitalColors.fit else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            LinearProgressIndicator(
              progress = { hydroProgress.coerceIn(0f, 1f) },
              color = vitalColors.hydro,
              trackColor = vitalColors.hydro.copy(alpha = 0.16f),
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(150L, 250L, 500L).forEach { amount ->
                Button(
                  onClick = { onQuickHydration(amount) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = vitalColors.hydroContainer,
                    contentColor = vitalColors.hydro,
                  ),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("quick_hydro_$amount"),
                ) {
                  Text(
                    text = "+$amount ${strings.mlUnit}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }
        }
      }

      // Live Heart Rate & SpO2 Telemetry Card
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateVitals() }
            .testTag("dashboard_heart_rate_card"),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(vitalColors.pulseContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = strings.heartRateCardTitle,
                    tint = vitalColors.pulse,
                  )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(strings.heartRateCardTitle, style = MaterialTheme.typography.titleMedium)
                  val zoneLabel = activeBpm?.let { strings.zoneName(HeartRateZone.fromBpm(it)) } ?: "—"
                  Text(
                    text = "$zoneLabel · ${strings.spo2Label} ${activeSpo2 ?: "—"}% · ${strings.hrvLabel} ${activeHrv ?: "—"}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Text(
                text = if (activeBpm != null) "$activeBpm ${strings.bpmUnit}" else "-- ${strings.bpmUnit}",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = vitalColors.pulse,
              )
            }

            HeartRatePulseChart(
              bpmValues = snapshot.heartRateReadings.take(12).reversed().map { it.bpm.toInt() },
              restingTarget = snapshot.profile.restingHeartRateTarget.toInt(),
              modifier = Modifier
                .fillMaxWidth()
                .height(74.dp),
            )
          }
        }
      }

      // Sleep Architecture Card
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateVitals() }
            .testTag("dashboard_sleep_card"),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            val sleep = snapshot.latestSleep
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(vitalColors.sleepContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = strings.sleepCardTitle,
                    tint = vitalColors.sleep,
                  )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(strings.sleepCardTitle, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = if (sleep != null) {
                      "${sleep.totalMinutes / 60}${strings.hrsUnit} ${sleep.totalMinutes % 60}${strings.minUnit} · ${sleep.sourceDevice}"
                    } else {
                      strings.emptySleepTip
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              if (sleep != null) {
                Surface(
                  color = vitalColors.sleepContainer,
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    text = "${sleep.qualityScore}/100",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = vitalColors.sleep,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }

            if (sleep != null) {
              SleepHypnogramBar(
                deepMinutes = sleep.deepMinutes,
                lightMinutes = sleep.lightMinutes,
                remMinutes = sleep.remMinutes,
                awakeMinutes = sleep.awakeMinutes,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  value: String,
  label: String,
  tint: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(14.dp),
      )
      Spacer(Modifier.width(4.dp))
      Text(
        text = value,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
