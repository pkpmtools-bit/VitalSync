package com.example.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.Gender
import com.example.data.model.LifestyleLevel
import com.example.data.model.UserProfile
import com.example.data.model.WorkIntensity
import com.example.data.model.calculateRecommendedHydrationMl
import com.example.data.model.calculateRecommendedSteps
import com.example.i18n.LocalStrings
import com.example.ui.brand.VitalSyncWordmark
import com.example.ui.components.LanguagePillStrip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVitalColors
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
  profile: UserProfile,
  selectedLanguage: AppLanguage,
  onSelectLanguage: (AppLanguage) -> Unit,
  onCompleteOnboarding: (
    displayName: String,
    language: AppLanguage,
    gender: Gender,
    heightCm: Int,
    weightKg: Double,
    lifestyle: LifestyleLevel,
    workIntensity: WorkIntensity,
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
  var gender by remember(profile.gender) { mutableStateOf(Gender.fromKey(profile.gender)) }
  var heightCmFloat by remember(profile.heightCm) { mutableFloatStateOf(profile.heightCm.toFloat().coerceIn(120f, 220f)) }
  var weightText by remember(profile.weightKg) { mutableStateOf(profile.weightKg.roundToInt().toString()) }
  var lifestyle by remember(profile.lifestyleLevel) { mutableStateOf(LifestyleLevel.fromKey(profile.lifestyleLevel)) }
  var workIntensity by remember(profile.workIntensity) { mutableStateOf(WorkIntensity.fromKey(profile.workIntensity)) }

  val weightKg = (weightText.toDoubleOrNull() ?: profile.weightKg).coerceIn(30.0, 250.0)
  val heightCm = heightCmFloat.roundToInt()
  val calculatedHydrationMl = remember(weightKg, heightCm, gender, lifestyle, workIntensity) {
    calculateRecommendedHydrationMl(
      weightKg = weightKg,
      heightCm = heightCm,
      gender = gender,
      lifestyle = lifestyle,
      work = workIntensity,
    )
  }
  val recommendedSteps = remember(lifestyle) { calculateRecommendedSteps(lifestyle) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 640.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Brand Header + EN/V4 selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Image(
            painter = painterResource(id = R.drawable.img_brand_emblem),
            contentDescription = strings.appName,
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp)),
          )
          Spacer(Modifier.width(10.dp))
          VitalSyncWordmark(fontSize = 24.sp)
        }
      }

      LanguagePillStrip(
        selectedLanguage = selectedLanguage,
        onSelectLanguage = onSelectLanguage,
      )

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = strings.onboardingTitle,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = strings.onboardingSubtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Live Calculated Hydration Recommendation Card
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, vitalColors.hydro.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  vitalColors.hydroContainer.copy(alpha = 0.7f),
                  MaterialTheme.colorScheme.surface,
                )
              )
            )
            .padding(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = vitalColors.hydro,
                modifier = Modifier.size(34.dp),
              )
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = strings.onboardingHydrationPreview,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Rounded.DirectionsWalk,
                    contentDescription = null,
                    tint = vitalColors.fit,
                    modifier = Modifier.size(14.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                  Text(
                    text = "${strings.onboardingStepsPreview}: $recommendedSteps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
            Text(
              text = "$calculatedHydrationMl ${strings.mlUnit}",
              fontFamily = JetBrainsMonoFamily,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.headlineSmall,
              color = vitalColors.hydro,
              modifier = Modifier.testTag("onboarding_calculated_hydration"),
            )
          }
        }
      }

      // Personal Metrics & Lifestyle Card
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, vitalColors.cardBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(strings.displayNameLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          // Gender
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.genderLabel, style = MaterialTheme.typography.labelLarge)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Gender.entries.forEach { item ->
                val selected = gender == item
                FilterChip(
                  selected = selected,
                  onClick = { gender = item },
                  label = { Text(strings.genderName(item)) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.hydroContainer,
                    selectedLabelColor = vitalColors.hydro,
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("onboarding_gender_${item.key}"),
                )
              }
            }
          }

          // Height slider + Weight input
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(strings.heightCmLabel, style = MaterialTheme.typography.labelLarge)
              Text(
                text = "$heightCm cm",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = vitalColors.hydro,
              )
            }
            Slider(
              value = heightCmFloat,
              onValueChange = { heightCmFloat = it },
              valueRange = 120f..220f,
              colors = SliderDefaults.colors(thumbColor = vitalColors.hydro, activeTrackColor = vitalColors.hydro),
              modifier = Modifier.testTag("onboarding_height_slider"),
            )
          }

          OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            label = { Text(strings.weightKgLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("onboarding_weight_input"),
          )

          // Lifestyle (Passive / Recreational / Active)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.lifestyleLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              LifestyleLevel.entries.forEach { level ->
                val selected = lifestyle == level
                FilterChip(
                  selected = selected,
                  onClick = { lifestyle = level },
                  label = { Text(strings.lifestyleName(level)) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.fitContainer,
                    selectedLabelColor = vitalColors.fit,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("onboarding_lifestyle_${level.key}"),
                )
              }
            }
          }

          // Work Intensity (Sedentary / Light / Moderate / Heavy)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.workIntensityLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              WorkIntensity.entries.forEach { work ->
                val selected = workIntensity == work
                FilterChip(
                  selected = selected,
                  onClick = { workIntensity = work },
                  label = { Text(strings.workIntensityName(work)) },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = vitalColors.hydroContainer,
                    selectedLabelColor = vitalColors.hydro,
                  ),
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("onboarding_work_${work.key}"),
                )
              }
            }
          }
        }
      }

      Button(
        onClick = {
          onCompleteOnboarding(
            displayName,
            selectedLanguage,
            gender,
            heightCm,
            weightKg,
            lifestyle,
            workIntensity,
          )
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .testTag("complete_onboarding_button"),
      ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.completeSetupAction, style = MaterialTheme.typography.titleMedium)
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}
