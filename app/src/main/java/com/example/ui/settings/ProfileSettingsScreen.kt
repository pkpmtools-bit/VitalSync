package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.Gender
import com.example.data.model.LifestyleLevel
import com.example.data.model.WorkIntensity
import com.example.data.model.calculateRecommendedHydrationMl
import com.example.data.model.calculateRecommendedSteps
import com.example.i18n.LocalStrings
import com.example.ui.WellnessDashboardSnapshot
import com.example.ui.brand.BrandPackageDialog
import com.example.ui.brand.VitalSyncWordmark
import com.example.ui.theme.LocalVitalColors
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
  snapshot: WellnessDashboardSnapshot,
  selectedLanguage: AppLanguage,
  onSelectLanguage: (AppLanguage) -> Unit,
  onSaveGoals: (
    displayName: String,
    language: AppLanguage,
    gender: Gender,
    heightCm: Int,
    weightKg: Double,
    lifestyle: LifestyleLevel,
    workIntensity: WorkIntensity,
    waterGoalMl: Long,
    stepsGoal: Long,
    sleepGoalMinutes: Long,
    restingHrTarget: Long,
  ) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  var showBrandKitDialog by remember { mutableStateOf(false) }

  var displayName by remember(snapshot.profile.displayName) {
    mutableStateOf(snapshot.profile.displayName)
  }
  var gender by remember(snapshot.profile.gender) {
    mutableStateOf(Gender.fromKey(snapshot.profile.gender))
  }
  var heightText by remember(snapshot.profile.heightCm) {
    mutableStateOf(snapshot.profile.heightCm.toString())
  }
  var weightText by remember(snapshot.profile.weightKg) {
    mutableStateOf(snapshot.profile.weightKg.toString())
  }
  var lifestyle by remember(snapshot.profile.lifestyleLevel) {
    mutableStateOf(LifestyleLevel.fromKey(snapshot.profile.lifestyleLevel))
  }
  var workIntensity by remember(snapshot.profile.workIntensity) {
    mutableStateOf(WorkIntensity.fromKey(snapshot.profile.workIntensity))
  }
  var waterGoalText by remember(snapshot.profile.dailyWaterGoalMl) {
    mutableStateOf(snapshot.profile.dailyWaterGoalMl.toString())
  }
  var stepsGoalText by remember(snapshot.profile.dailyStepsGoal) {
    mutableStateOf(snapshot.profile.dailyStepsGoal.toString())
  }
  var sleepGoalHoursText by remember(snapshot.profile.dailySleepGoalMinutes) {
    mutableStateOf((snapshot.profile.dailySleepGoalMinutes / 60.0).toString())
  }
  var restingHrText by remember(snapshot.profile.restingHeartRateTarget) {
    mutableStateOf(snapshot.profile.restingHeartRateTarget.toString())
  }

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
          text = strings.profileTitle,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }

      // Brand Identity & Complete Brand Package Card
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_brand_icon),
                contentDescription = strings.appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(56.dp)
                  .clip(RoundedCornerShape(14.dp)),
              )
              Column(modifier = Modifier.weight(1f)) {
                VitalSyncWordmark(fontSize = 20.sp)
                Text(
                  text = strings.brandSlogan,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            FilledTonalButton(
              onClick = { showBrandKitDialog = true },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("open_brand_kit_button"),
            ) {
              Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(strings.brandKitButtonLabel)
            }
          }
        }
      }

      // Language Switcher Card (EN + SK / CZ / HU / PL)
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Rounded.Language, contentDescription = strings.languageSelectorTitle, tint = vitalColors.hydro)
              Spacer(Modifier.width(8.dp))
              Text(strings.languageSelectorTitle, style = MaterialTheme.typography.titleMedium)
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              AppLanguage.entries.forEach { lang ->
                val selected = lang == selectedLanguage
                FilterChip(
                  selected = selected,
                  onClick = { onSelectLanguage(lang) },
                  label = { Text("${lang.flagLabel} · ${lang.nativeName}") },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.hydroContainer,
                    selectedLabelColor = vitalColors.hydro,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("profile_lang_${lang.code}"),
                )
              }
            }
          }
        }
      }

      // Biometrics, Lifestyle & Goals Configuration Card
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
            OutlinedTextField(
              value = displayName,
              onValueChange = { displayName = it },
              label = { Text(strings.displayNameLabel) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )

            Text(strings.genderLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Gender.entries.forEach { item ->
                FilterChip(
                  selected = gender == item,
                  onClick = { gender = item },
                  label = { Text(strings.genderName(item)) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.hydroContainer,
                    selectedLabelColor = vitalColors.hydro,
                  ),
                  modifier = Modifier.minimumInteractiveComponentSize(),
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text(strings.heightCmLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text(strings.weightKgLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }

            Text(strings.lifestyleLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              LifestyleLevel.entries.forEach { level ->
                FilterChip(
                  selected = lifestyle == level,
                  onClick = { lifestyle = level },
                  label = { Text(strings.lifestyleName(level)) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.fitContainer,
                    selectedLabelColor = vitalColors.fit,
                  ),
                  modifier = Modifier.minimumInteractiveComponentSize(),
                )
              }
            }

            Text(strings.workIntensityLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              WorkIntensity.entries.forEach { work ->
                FilterChip(
                  selected = workIntensity == work,
                  onClick = { workIntensity = work },
                  label = { Text(strings.workIntensityName(work)) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.hydroContainer,
                    selectedLabelColor = vitalColors.hydro,
                  ),
                  modifier = Modifier.minimumInteractiveComponentSize(),
                )
              }
            }

            FilledTonalButton(
              onClick = {
                val h = (heightText.toIntOrNull() ?: 175).coerceIn(110, 230)
                val w = (weightText.toDoubleOrNull() ?: 72.0).coerceIn(30.0, 250.0)
                val recomputedWater = calculateRecommendedHydrationMl(w, h, gender, lifestyle, workIntensity)
                val recomputedSteps = calculateRecommendedSteps(lifestyle)
                waterGoalText = recomputedWater.toString()
                stepsGoalText = recomputedSteps.toString()
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("recalculate_hydration_button"),
            ) {
              Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(strings.recalculateHydration)
            }

            OutlinedTextField(
              value = waterGoalText,
              onValueChange = { waterGoalText = it },
              label = { Text(strings.waterGoalLabel) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = stepsGoalText,
              onValueChange = { stepsGoalText = it },
              label = { Text(strings.stepsGoalLabel) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = sleepGoalHoursText,
              onValueChange = { sleepGoalHoursText = it },
              label = { Text(strings.sleepGoalHoursLabel) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = restingHrText,
              onValueChange = { restingHrText = it },
              label = { Text(strings.restingHrTargetLabel) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )

            Button(
              onClick = {
                val height = heightText.toIntOrNull() ?: 175
                val weight = weightText.toDoubleOrNull() ?: 72.0
                val water = waterGoalText.toLongOrNull() ?: 2500L
                val steps = stepsGoalText.toLongOrNull() ?: 10000L
                val sleepMin = ((sleepGoalHoursText.toDoubleOrNull() ?: 8.0) * 60.0).roundToLong()
                val hrTarget = restingHrText.toLongOrNull() ?: 62L
                onSaveGoals(
                  displayName,
                  selectedLanguage,
                  gender,
                  height,
                  weight,
                  lifestyle,
                  workIntensity,
                  water,
                  steps,
                  sleepMin,
                  hrTarget,
                )
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_goals_button"),
            ) {
              Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(strings.saveGoals)
            }
          }
        }
      }

      // Sign Out Card
      item {
        OutlinedButton(
          onClick = onSignOut,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = strings.signOut)
          Spacer(Modifier.width(8.dp))
          Text(strings.signOut)
        }
      }
    }
  }

  if (showBrandKitDialog) {
    BrandPackageDialog(onDismiss = { showBrandKitDialog = false })
  }
}
