package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class VitalDomainColors(
  val hydro: Color,
  val hydroContainer: Color,
  val pulse: Color,
  val pulseContainer: Color,
  val sleep: Color,
  val sleepRem: Color,
  val sleepAwake: Color,
  val sleepContainer: Color,
  val fit: Color,
  val fitContainer: Color,
  val cardBorder: Color,
)

private val DarkVitalDomainColors = VitalDomainColors(
  hydro = BrandTurquoise,
  hydroContainer = Color(0xFF0E384B),
  pulse = PulseCoral,
  pulseContainer = Color(0xFF421622),
  sleep = SleepIndigo,
  sleepRem = BrandTurquoise,
  sleepAwake = SleepAwakeAmber,
  sleepContainer = Color(0xFF281B4B),
  fit = FitEmerald,
  fitContainer = Color(0xFF0B3829),
  cardBorder = SubtleOutlineDark,
)

private val LightVitalDomainColors = VitalDomainColors(
  hydro = BrandTealCyan,
  hydroContainer = Color(0xFFDCF5F8),
  pulse = PulseCoralDark,
  pulseContainer = Color(0xFFFFE4E9),
  sleep = SleepIndigoDark,
  sleepRem = BrandTealCyan,
  sleepAwake = Color(0xFFD97706),
  sleepContainer = Color(0xFFEDE9FE),
  fit = FitEmeraldDark,
  fitContainer = Color(0xFFD1FAE5),
  cardBorder = SubtleOutlineLight,
)

val LocalVitalColors = staticCompositionLocalOf { DarkVitalDomainColors }

private val DarkColorScheme = darkColorScheme(
  primary = BrandTurquoise,
  onPrimary = BrandDeepNavy,
  primaryContainer = Color(0xFF0B3E5E),
  onPrimaryContainer = Color(0xFFCFF7F6),
  secondary = BrandTealCyan,
  onSecondary = BrandPureWhite,
  secondaryContainer = Color(0xFF103F4F),
  onSecondaryContainer = Color(0xFFD1F4FA),
  tertiary = PulseCoral,
  onTertiary = PureSurface,
  tertiaryContainer = Color(0xFF4B1824),
  onTertiaryContainer = Color(0xFFFFD9DF),
  background = MidnightNavy,
  onBackground = InkPrimaryDark,
  surface = DeepSlate,
  onSurface = InkPrimaryDark,
  surfaceVariant = ElevatedSlate,
  onSurfaceVariant = InkSecondaryDark,
  outline = SubtleOutlineDark,
  error = PulseCoral,
)

private val LightColorScheme = lightColorScheme(
  primary = BrandDeepNavy,
  onPrimary = BrandPureWhite,
  primaryContainer = Color(0xFFD7F2F5),
  onPrimaryContainer = BrandDeepNavy,
  secondary = BrandTealCyan,
  onSecondary = BrandPureWhite,
  secondaryContainer = Color(0xFFD7F4F2),
  onSecondaryContainer = BrandDeepNavy,
  tertiary = PulseCoralDark,
  onTertiary = PureSurface,
  tertiaryContainer = Color(0xFFFFE4E8),
  onTertiaryContainer = Color(0xFF4C0519),
  background = GlacierWhite,
  onBackground = InkPrimaryLight,
  surface = PureSurface,
  onSurface = InkPrimaryLight,
  surfaceVariant = SoftMist,
  onSurfaceVariant = InkSecondaryLight,
  outline = SubtleOutlineLight,
  error = PulseCoralDark,
)

private val VitalSyncShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(28.dp),
)

@Composable
fun VitalSyncTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val domainColors = if (darkTheme) DarkVitalDomainColors else LightVitalDomainColors

  CompositionLocalProvider(LocalVitalColors provides domainColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = VitalSyncTypography,
      shapes = VitalSyncShapes,
      content = content,
    )
  }
}
