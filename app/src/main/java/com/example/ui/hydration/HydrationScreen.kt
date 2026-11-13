package com.example.ui.hydration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DrinkType
import com.example.data.model.HydrationLog
import com.example.data.model.formatTimeHHmm
import com.example.i18n.LocalStrings
import com.example.ui.WellnessDashboardSnapshot
import com.example.ui.components.HydroWaveGauge
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVitalColors
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HydrationScreen(
  snapshot: WellnessDashboardSnapshot,
  onLogDrink: (Long, DrinkType) -> Unit,
  onDeleteLog: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  var selectedDrink by remember { mutableStateOf(DrinkType.WATER) }
  var volumeMl by remember { mutableFloatStateOf(250f) }

  val goalMl = snapshot.profile.dailyWaterGoalMl.coerceAtLeast(500L)
  val progress = (snapshot.totalEffectiveWaterMl.toFloat() / goalMl).coerceIn(0f, 1.5f)
  val weightRecommendedMl = com.example.data.model.calculateRecommendedHydrationMl(
    weightKg = snapshot.profile.weightKg,
    heightCm = snapshot.profile.heightCm.toInt(),
    gender = com.example.data.model.Gender.fromKey(snapshot.profile.gender),
    lifestyle = com.example.data.model.LifestyleLevel.fromKey(snapshot.profile.lifestyleLevel),
    work = com.example.data.model.WorkIntensity.fromKey(snapshot.profile.workIntensity),
  )

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = strings.hydroCoachTitle,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }

      // Hydro Wave Gauge Hero Card
      item {
        Card(
          shape = RoundedCornerShape(26.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            HydroWaveGauge(
              progress = progress,
              effectiveMl = snapshot.totalEffectiveWaterMl,
              goalMl = goalMl,
              modifier = Modifier.size(188.dp),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "${snapshot.totalEffectiveWaterMl} ${strings.mlUnit}",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleMedium,
                  color = vitalColors.hydro,
                )
                Text(
                  text = strings.effectiveHydration,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "${snapshot.totalRawWaterMl} ${strings.mlUnit}",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = strings.rawVolume,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "$weightRecommendedMl ${strings.mlUnit}",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.titleMedium,
                  color = vitalColors.fit,
                )
                Text(
                  text = strings.recommendedByWeight,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Beverage Type + Volume Control Card
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = strings.selectBeverage,
              style = MaterialTheme.typography.titleMedium,
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              DrinkType.entries.forEach { drink ->
                val selected = drink == selectedDrink
                val pct = (drink.hydrationFactor * 100).toInt()
                FilterChip(
                  selected = selected,
                  onClick = { selectedDrink = drink },
                  label = {
                    Text("${strings.drinkName(drink)} ($pct%)")
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.hydroContainer,
                    selectedLabelColor = vitalColors.hydro,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("drink_chip_${drink.key}"),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(strings.customVolume, style = MaterialTheme.typography.bodyMedium)
              val previewEffective = (volumeMl.toLong() * selectedDrink.hydrationFactor).roundToLong()
              Text(
                text = "${volumeMl.toInt()} ${strings.mlUnit} → $previewEffective ${strings.mlUnit}",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = vitalColors.hydro,
              )
            }

            Slider(
              value = volumeMl,
              onValueChange = { volumeMl = (it / 25f).roundToLong() * 25f },
              valueRange = 50f..1000f,
              colors = SliderDefaults.colors(
                thumbColor = vitalColors.hydro,
                activeTrackColor = vitalColors.hydro,
              ),
              modifier = Modifier.testTag("hydration_volume_slider"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(150f, 250f, 330f, 500f).forEach { preset ->
                Surface(
                  color = if (volumeMl == preset) vitalColors.hydroContainer else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(12.dp),
                  onClick = { volumeMl = preset },
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize(),
                ) {
                  Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                      text = "${preset.toInt()}ml",
                      fontFamily = JetBrainsMonoFamily,
                      style = MaterialTheme.typography.labelMedium,
                      color = if (volumeMl == preset) vitalColors.hydro else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            Button(
              onClick = { onLogDrink(volumeMl.toLong(), selectedDrink) },
              colors = ButtonDefaults.buttonColors(
                containerColor = vitalColors.hydro,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("log_hydration_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(
                text = "${strings.addDrink} (${volumeMl.toInt()} ${strings.mlUnit})",
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        }
      }

      // Today's Drink Log Header
      item {
        Text(
          text = strings.todayIntakeLog,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }

      if (snapshot.hydrationLogs.isEmpty()) {
        item {
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
              Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = vitalColors.hydro,
                modifier = Modifier.size(32.dp),
              )
              Text(
                text = strings.emptyHydrationTip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(snapshot.hydrationLogs, key = { it.id.ifBlank { "${it.loggedAtMillis}_${it.amountMl}" } }) { log ->
          HydrationLogRow(log = log, onDelete = { onDeleteLog(log.id) })
        }
      }
    }
  }
}

@Composable
private fun HydrationLogRow(
  log: HydrationLog,
  onDelete: () -> Unit,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current
  val drinkType = DrinkType.fromKey(log.drinkType)

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
            .background(vitalColors.hydroContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = when (drinkType) {
              DrinkType.COFFEE, DrinkType.TEA -> Icons.Rounded.LocalCafe
              DrinkType.SOUP -> Icons.Rounded.RamenDining
              else -> Icons.Rounded.WaterDrop
            },
            contentDescription = strings.drinkName(drinkType),
            tint = vitalColors.hydro,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text(
            text = strings.drinkName(drinkType),
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "${formatTimeHHmm(log.loggedAtMillis)} · ${strings.effectiveHydration}: ${log.effectiveHydrationMl} ${strings.mlUnit}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "+${log.amountMl} ${strings.mlUnit}",
          fontFamily = JetBrainsMonoFamily,
          fontWeight = FontWeight.Bold,
          color = vitalColors.hydro,
        )
        if (log.id.isNotBlank()) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteOutline,
              contentDescription = strings.deleteEntry,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
