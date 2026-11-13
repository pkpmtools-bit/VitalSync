package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVitalColors
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LanguagePillStrip(
  selectedLanguage: AppLanguage,
  onSelectLanguage: (AppLanguage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val vitalColors = LocalVitalColors.current
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppLanguage.entries.forEach { lang ->
      val isSelected = lang == selectedLanguage
      Surface(
        color = if (isSelected) vitalColors.hydroContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
          width = 1.dp,
          color = if (isSelected) vitalColors.hydro else vitalColors.cardBorder,
        ),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(10.dp))
          .clickable { onSelectLanguage(lang) }
          .testTag("lang_pill_${lang.code}"),
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = lang.flagLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) vitalColors.hydro else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun WellnessConcentricRings(
  stepsProgress: Float,
  hydrationProgress: Float,
  sleepProgress: Float,
  readinessScore: Int,
  modifier: Modifier = Modifier,
) {
  val vitalColors = LocalVitalColors.current
  val animSteps by animateFloatAsState(
    targetValue = stepsProgress.coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "steps_ring",
  )
  val animHydro by animateFloatAsState(
    targetValue = hydrationProgress.coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "hydro_ring",
  )
  val animSleep by animateFloatAsState(
    targetValue = sleepProgress.coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "sleep_ring",
  )

  val strings = LocalStrings.current

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = size.minDimension * 0.085f
      val gap = stroke * 0.35f

      fun drawRing(inset: Float, progress: Float, color: Color) {
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        drawArc(
          color = color.copy(alpha = 0.16f),
          startAngle = -90f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
          color = color,
          startAngle = -90f,
          sweepAngle = 360f * progress,
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
      }

      drawRing(stroke / 2f, animSteps, vitalColors.fit)
      drawRing(stroke / 2f + stroke + gap, animHydro, vitalColors.hydro)
      drawRing(stroke / 2f + (stroke + gap) * 2f, animSleep, vitalColors.sleep)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$readinessScore%",
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = strings.wellnessScore,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun HydroWaveGauge(
  progress: Float,
  effectiveMl: Long,
  goalMl: Long,
  modifier: Modifier = Modifier,
) {
  val vitalColors = LocalVitalColors.current
  val animProgress by animateFloatAsState(
    targetValue = progress.coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessLow),
    label = "hydro_wave",
  )
  val strings = LocalStrings.current

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val radius = size.minDimension / 2f
      val center = Offset(size.width / 2f, size.height / 2f)

      // Outer gauge track
      drawCircle(
        color = vitalColors.hydro.copy(alpha = 0.14f),
        radius = radius - 6.dp.toPx(),
        center = center,
        style = Stroke(width = 10.dp.toPx()),
      )

      // Inner clipped liquid circle
      val innerRadius = radius - 16.dp.toPx()
      val fillTop = center.y + innerRadius - (innerRadius * 2f * animProgress)

      val wavePath = Path().apply {
        val leftX = center.x - innerRadius
        val rightX = center.x + innerRadius
        moveTo(leftX, center.y + innerRadius)
        lineTo(leftX, fillTop)
        val steps = 32
        for (i in 0..steps) {
          val x = leftX + (rightX - leftX) * (i.toFloat() / steps)
          val waveOffset = sin((i.toFloat() / steps) * 2f * PI.toFloat()) * 6.dp.toPx() * (if (animProgress in 0.03f..0.97f) 1f else 0.2f)
          lineTo(x, fillTop + waveOffset)
        }
        lineTo(rightX, center.y + innerRadius)
        close()
      }

      // Clip conceptually by drawing arc + wave inside circle bounds
      drawCircle(
        color = vitalColors.hydroContainer.copy(alpha = 0.5f),
        radius = innerRadius,
        center = center,
      )

      if (animProgress > 0.01f) {
        drawPath(
          path = wavePath,
          brush = Brush.verticalGradient(
            colors = listOf(vitalColors.hydro.copy(alpha = 0.72f), vitalColors.hydro.copy(alpha = 0.35f)),
            startY = fillTop,
            endY = center.y + innerRadius,
          ),
        )
      }

      drawArc(
        color = vitalColors.hydro,
        startAngle = -90f,
        sweepAngle = 360f * animProgress,
        useCenter = false,
        topLeft = Offset(center.x - (radius - 6.dp.toPx()), center.y - (radius - 6.dp.toPx())),
        size = Size((radius - 6.dp.toPx()) * 2f, (radius - 6.dp.toPx()) * 2f),
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
      )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = "$effectiveMl",
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "/ $goalMl ${strings.mlUnit}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        text = "${(progress.coerceIn(0f, 1.5f) * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        color = vitalColors.hydro,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun HeartRatePulseChart(
  bpmValues: List<Int>,
  restingTarget: Int,
  modifier: Modifier = Modifier,
) {
  val vitalColors = LocalVitalColors.current
  val points = if (bpmValues.size >= 2) bpmValues else listOf(64, 72, 68, 74, 71, 69)

  Canvas(modifier = modifier) {
    val minBpm = (points.minOrNull() ?: 50).coerceAtMost(restingTarget) - 8
    val maxBpm = ((points.maxOrNull() ?: 120) + 10).coerceAtLeast(minBpm + 20)
    val range = (maxBpm - minBpm).toFloat().coerceAtLeast(1f)

    // Resting target guide line
    val targetY = size.height - ((restingTarget - minBpm) / range) * size.height
    drawLine(
      color = vitalColors.fit.copy(alpha = 0.45f),
      start = Offset(0f, targetY),
      end = Offset(size.width, targetY),
      strokeWidth = 1.5.dp.toPx(),
    )

    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
    val linePath = Path()
    val fillPath = Path()

    points.forEachIndexed { index, bpm ->
      val x = index * stepX
      val y = size.height - ((bpm - minBpm) / range) * (size.height - 12.dp.toPx()) - 6.dp.toPx()
      if (index == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
      if (index == points.lastIndex) {
        fillPath.lineTo(x, size.height)
        fillPath.close()
      }
    }

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(
        colors = listOf(vitalColors.pulse.copy(alpha = 0.35f), Color.Transparent),
      ),
    )

    drawPath(
      path = linePath,
      color = vitalColors.pulse,
      style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )

    points.forEachIndexed { index, bpm ->
      val x = index * stepX
      val y = size.height - ((bpm - minBpm) / range) * (size.height - 12.dp.toPx()) - 6.dp.toPx()
      drawCircle(
        color = vitalColors.pulse,
        radius = if (index == points.lastIndex) 5.dp.toPx() else 3.dp.toPx(),
        center = Offset(x, y),
      )
    }
  }
}

@Composable
fun SleepHypnogramBar(
  deepMinutes: Long,
  lightMinutes: Long,
  remMinutes: Long,
  awakeMinutes: Long,
  modifier: Modifier = Modifier,
) {
  val vitalColors = LocalVitalColors.current
  val strings = LocalStrings.current
  val total = (deepMinutes + lightMinutes + remMinutes + awakeMinutes).coerceAtLeast(1L).toFloat()

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(22.dp)
        .clip(RoundedCornerShape(11.dp)),
    ) {
      val segments = listOf(
        deepMinutes / total to vitalColors.sleep,
        lightMinutes / total to vitalColors.hydro,
        remMinutes / total to vitalColors.sleepRem,
        awakeMinutes / total to vitalColors.sleepAwake,
      )
      var currentX = 0f
      segments.forEach { (fraction, color) ->
        val segWidth = size.width * fraction
        if (segWidth > 0.5f) {
          drawRoundRect(
            color = color,
            topLeft = Offset(currentX, 0f),
            size = Size(segWidth, size.height),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
          )
          currentX += segWidth
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      SleepStageLegendItem(strings.sleepDeep, deepMinutes, vitalColors.sleep)
      SleepStageLegendItem(strings.sleepLight, lightMinutes, vitalColors.hydro)
      SleepStageLegendItem(strings.sleepRem, remMinutes, vitalColors.sleepRem)
      SleepStageLegendItem(strings.sleepAwake, awakeMinutes, vitalColors.sleepAwake)
    }
  }
}

@Composable
private fun SleepStageLegendItem(label: String, minutes: Long, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color)
    )
    Spacer(Modifier.width(5.dp))
    Text(
      text = "$label ${minutes}m",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun SleepPeriodTrendChart(
  buckets: List<com.example.data.model.SleepChartBucket>,
  targetMinutes: Long,
  modifier: Modifier = Modifier,
) {
  val vitalColors = LocalVitalColors.current
  val maxMinutes = ((buckets.maxOfOrNull { it.avgMinutes } ?: targetMinutes).coerceAtLeast(targetMinutes)).coerceAtLeast(180L).toFloat()

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(110.dp),
    ) {
      val targetY = size.height - (targetMinutes / maxMinutes).coerceIn(0f, 1f) * size.height
      drawLine(
        color = vitalColors.fit.copy(alpha = 0.55f),
        start = Offset(0f, targetY),
        end = Offset(size.width, targetY),
        strokeWidth = 1.5.dp.toPx(),
      )

      if (buckets.isNotEmpty()) {
        val slotWidth = size.width / buckets.size
        val barWidth = (slotWidth * 0.54f).coerceAtMost(34.dp.toPx())
        buckets.forEachIndexed { index, bucket ->
          val ratio = (bucket.avgMinutes / maxMinutes).coerceIn(0.05f, 1f)
          val barHeight = size.height * ratio
          val left = index * slotWidth + (slotWidth - barWidth) / 2f
          val top = size.height - barHeight
          drawRoundRect(
            brush = Brush.verticalGradient(
              colors = listOf(vitalColors.sleep, vitalColors.hydro.copy(alpha = 0.85f)),
              startY = top,
              endY = size.height,
            ),
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
          )
        }
      }
    }

    if (buckets.isNotEmpty()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
      ) {
        buckets.forEach { bucket ->
          Text(
            text = bucket.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
