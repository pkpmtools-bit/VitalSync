package com.example.ui.brand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.i18n.LocalStrings
import com.example.ui.theme.BrandCharcoalSlate
import com.example.ui.theme.BrandDeepNavy
import com.example.ui.theme.BrandPureWhite
import com.example.ui.theme.BrandTealCyan
import com.example.ui.theme.BrandTurquoise
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MontserratFamily

@Composable
fun VitalSyncWordmark(
  fontSize: TextUnit = 28.sp,
  fontWeight: FontWeight = FontWeight.Bold,
  onDarkBackground: Boolean = isSystemInDarkTheme(),
  modifier: Modifier = Modifier,
) {
  val vitalColor = if (onDarkBackground) BrandPureWhite else BrandDeepNavy
  Text(
    text = buildAnnotatedString {
      withStyle(SpanStyle(color = vitalColor, fontWeight = fontWeight)) {
        append("Vital")
      }
      withStyle(SpanStyle(color = BrandTurquoise, fontWeight = fontWeight)) {
        append("Sync")
      }
    },
    fontFamily = MontserratFamily,
    fontSize = fontSize,
    letterSpacing = (-0.5).sp,
    modifier = modifier,
  )
}

@Composable
fun VitalSyncSplashOverlay(
  modifier: Modifier = Modifier,
) {
  val strings = LocalStrings.current
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(BrandPureWhite)
      .testTag("vital_splash_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.padding(32.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_brand_emblem),
        contentDescription = strings.appName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
          .size(164.dp)
          .clip(RoundedCornerShape(36.dp)),
      )
      VitalSyncWordmark(
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        onDarkBackground = false,
      )
      Text(
        text = strings.brandSlogan,
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = BrandCharcoalSlate,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrandPackageDialog(
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .testTag("brand_package_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Rounded.Palette,
              contentDescription = strings.brandKitTitle,
              tint = BrandTurquoise,
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = strings.brandKitTitle,
              style = MaterialTheme.typography.titleLarge,
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = strings.closeAction)
          }
        }

        // Feature Graphic 1024x500
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = strings.brandFeatureGraphicLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Image(
            painter = painterResource(id = R.drawable.img_brand_feature_graphic),
            contentDescription = strings.brandFeatureGraphicLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1024f / 500f)
              .clip(RoundedCornerShape(18.dp)),
          )
        }

        // Icon 512x512 + Splash Preview side-by-side
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = strings.brandIconLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              color = BrandPureWhite,
              shape = RoundedCornerShape(24.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_brand_icon),
                contentDescription = strings.brandIconLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(120.dp)
                  .clip(RoundedCornerShape(24.dp)),
              )
            }
          }

          Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = strings.brandSplashLabel,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              color = BrandPureWhite,
              shape = RoundedCornerShape(24.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            ) {
              Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Image(
                  painter = painterResource(id = R.drawable.img_brand_emblem),
                  contentDescription = strings.brandSplashLabel,
                  modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(4.dp))
                VitalSyncWordmark(fontSize = 16.sp, onDarkBackground = false)
                Text(
                  text = strings.brandSlogan,
                  fontFamily = MontserratFamily,
                  fontSize = 9.sp,
                  color = BrandCharcoalSlate,
                )
              }
            }
          }
        }

        // Color Palette Swatches
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = strings.brandPaletteTitle,
            style = MaterialTheme.typography.titleMedium,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            BrandColorSwatch(color = BrandDeepNavy, hex = "#0A365C", role = "Deep Navy")
            BrandColorSwatch(color = BrandTealCyan, hex = "#1E88A8", role = "Teal-Cyan")
            BrandColorSwatch(color = BrandTurquoise, hex = "#34C6C3", role = "Turquoise")
            BrandColorSwatch(color = BrandPureWhite, hex = "#FFFFFF", role = "Pure White")
            BrandColorSwatch(color = BrandCharcoalSlate, hex = "#243030", role = "Charcoal")
          }
        }

        // Typography Specimens (Montserrat Light / Regular / Bold)
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = strings.brandTypographyTitle,
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "VitalSync · Light",
              fontFamily = MontserratFamily,
              fontWeight = FontWeight.Light,
              fontSize = 20.sp,
            )
            Text(
              text = "VitalSync · Regular — ${strings.brandSlogan}",
              fontFamily = MontserratFamily,
              fontWeight = FontWeight.Normal,
              fontSize = 18.sp,
            )
            Text(
              text = "VitalSync · Bold — ${strings.brandHeroTagline}",
              fontFamily = MontserratFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp,
            )
          }
        }

        // Light & Dark Emblem Usage Variants
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = strings.brandLogoVariantsLabel,
            style = MaterialTheme.typography.titleMedium,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              color = BrandPureWhite,
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
              modifier = Modifier.weight(1f),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Image(
                  painter = painterResource(id = R.drawable.img_brand_emblem),
                  contentDescription = null,
                  modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.width(6.dp))
                VitalSyncWordmark(fontSize = 15.sp, onDarkBackground = false)
              }
            }
            Surface(
              color = BrandCharcoalSlate,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier.weight(1f),
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Image(
                  painter = painterResource(id = R.drawable.img_brand_emblem),
                  contentDescription = null,
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                VitalSyncWordmark(fontSize = 15.sp, onDarkBackground = true)
              }
            }
          }
        }

        Button(
          onClick = onDismiss,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        ) {
          Text(strings.closeAction)
        }
      }
    }
  }
}

@Composable
private fun BrandColorSwatch(color: Color, hex: String, role: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Surface(
      color = color,
      shape = CircleShape,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.size(28.dp),
    ) {}
    Column {
      Text(text = hex, fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.labelMedium)
      Text(text = role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
