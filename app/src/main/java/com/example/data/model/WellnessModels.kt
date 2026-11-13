package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

enum class AppLanguage(val code: String, val flagLabel: String, val nativeName: String) {
  EN("en", "EN", "English"),
  SK("sk", "SK", "Slovenčina"),
  CS("cs", "CZ", "Čeština"),
  HU("hu", "HU", "Magyar"),
  PL("pl", "PL", "Polski");

  companion object {
    fun fromCode(code: String?): AppLanguage =
      entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
  }
}

enum class Gender(val key: String) {
  MALE("male"),
  FEMALE("female"),
  OTHER("other");

  companion object {
    fun fromKey(key: String?): Gender =
      entries.firstOrNull { it.key == key } ?: MALE
  }
}

enum class LifestyleLevel(val key: String, val hydrationBonusMl: Long, val recommendedSteps: Long) {
  PASSIVE("passive", 0L, 6000L),
  RECREATIONAL("recreational", 350L, 9000L),
  ACTIVE("active", 750L, 12500L);

  companion object {
    fun fromKey(key: String?): LifestyleLevel =
      entries.firstOrNull { it.key == key } ?: RECREATIONAL
  }
}

enum class WorkIntensity(val key: String, val hydrationBonusMl: Long) {
  SEDENTARY("sedentary", 0L),
  LIGHT_PHYSICAL("light_physical", 250L),
  MODERATE_PHYSICAL("moderate_physical", 550L),
  HEAVY_PHYSICAL("heavy_physical", 900L);

  companion object {
    fun fromKey(key: String?): WorkIntensity =
      entries.firstOrNull { it.key == key } ?: SEDENTARY
  }
}

enum class DrinkType(val key: String, val hydrationFactor: Double) {
  WATER("water", 1.0),
  SPARKLING("sparkling", 1.0),
  TEA("tea", 0.95),
  COFFEE("coffee", 0.80),
  ISOTONIC("isotonic", 1.05),
  JUICE("juice", 0.85),
  SOUP("soup", 0.75);

  companion object {
    fun fromKey(key: String?): DrinkType =
      entries.firstOrNull { it.key == key } ?: WATER
  }
}

enum class SleepTimeframe(val key: String) {
  DAILY("daily"),
  WEEKLY("weekly"),
  MONTHLY("monthly"),
  YEARLY("yearly");
}

data class SleepChartBucket(
  val label: String,
  val avgMinutes: Long,
  val avgQuality: Long,
  val sessionCount: Int,
)

data class SleepPeriodSummary(
  val timeframe: SleepTimeframe,
  val sessions: List<SleepSession>,
  val totalMinutes: Long,
  val avgMinutesPerNight: Long,
  val avgDeepMinutes: Long,
  val avgLightMinutes: Long,
  val avgRemMinutes: Long,
  val avgAwakeMinutes: Long,
  val avgQualityScore: Long,
  val avgBpm: Long,
  val buckets: List<SleepChartBucket>,
)

fun calculateRecommendedHydrationMl(
  weightKg: Double,
  heightCm: Int,
  gender: Gender,
  lifestyle: LifestyleLevel,
  work: WorkIntensity,
): Long {
  val safeWeight = weightKg.coerceIn(30.0, 250.0)
  val safeHeight = heightCm.coerceIn(110, 230)
  val baseRatePerKg = when (gender) {
    Gender.MALE -> 35.0
    Gender.FEMALE -> 32.0
    Gender.OTHER -> 33.5
  }
  val heightAdjustmentMl = ((safeHeight - 170) * 5L).coerceIn(-250L, 350L)
  val raw = (safeWeight * baseRatePerKg).roundToLong() +
    heightAdjustmentMl +
    lifestyle.hydrationBonusMl +
    work.hydrationBonusMl
  // Round to nearest 50 ml for a clean Hydro Coach target
  return (((raw + 25L) / 50L) * 50L).coerceIn(1200L, 6500L)
}

fun calculateRecommendedSteps(lifestyle: LifestyleLevel): Long = lifestyle.recommendedSteps

fun summarizeSleepSessions(
  sessions: List<SleepSession>,
  timeframe: SleepTimeframe,
  nowMillis: Long = System.currentTimeMillis(),
): SleepPeriodSummary {
  val sorted = sessions.sortedByDescending { it.endTimeMillis }
  val cutoffMillis = when (timeframe) {
    SleepTimeframe.DAILY -> nowMillis - 24L * 3600_000L
    SleepTimeframe.WEEKLY -> nowMillis - 7L * 24L * 3600_000L
    SleepTimeframe.MONTHLY -> nowMillis - 30L * 24L * 3600_000L
    SleepTimeframe.YEARLY -> nowMillis - 365L * 24L * 3600_000L
  }

  val filtered = when (timeframe) {
    SleepTimeframe.DAILY -> {
      val recent = sorted.filter { it.endTimeMillis >= cutoffMillis }
      if (recent.isNotEmpty()) listOf(recent.first()) else sorted.take(1)
    }
    else -> sorted.filter { it.endTimeMillis >= cutoffMillis }
  }

  if (filtered.isEmpty()) {
    return SleepPeriodSummary(
      timeframe = timeframe,
      sessions = emptyList(),
      totalMinutes = 0L,
      avgMinutesPerNight = 0L,
      avgDeepMinutes = 0L,
      avgLightMinutes = 0L,
      avgRemMinutes = 0L,
      avgAwakeMinutes = 0L,
      avgQualityScore = 0L,
      avgBpm = 0L,
      buckets = emptyList(),
    )
  }

  val count = filtered.size.toLong()
  val total = filtered.sumOf { it.totalMinutes }
  val avgMin = total / count
  val avgDeep = filtered.sumOf { it.deepMinutes } / count
  val avgLight = filtered.sumOf { it.lightMinutes } / count
  val avgRem = filtered.sumOf { it.remMinutes } / count
  val avgAwake = filtered.sumOf { it.awakeMinutes } / count
  val avgQuality = filtered.sumOf { it.qualityScore } / count
  val avgBpm = filtered.sumOf { it.avgBpm } / count

  val buckets = when (timeframe) {
    SleepTimeframe.DAILY -> {
      val s = filtered.first()
      listOf(
        SleepChartBucket(
          label = s.dateKey.takeLast(5),
          avgMinutes = s.totalMinutes,
          avgQuality = s.qualityScore,
          sessionCount = 1,
        )
      )
    }
    SleepTimeframe.WEEKLY -> {
      val dayFmt = SimpleDateFormat("EE", Locale.US)
      filtered.reversed().takeLast(7).map { s ->
        SleepChartBucket(
          label = dayFmt.format(Date(s.endTimeMillis)),
          avgMinutes = s.totalMinutes,
          avgQuality = s.qualityScore,
          sessionCount = 1,
        )
      }
    }
    SleepTimeframe.MONTHLY -> {
      val cal = Calendar.getInstance()
      filtered.groupBy { session ->
        cal.timeInMillis = session.endTimeMillis
        "W${cal.get(Calendar.WEEK_OF_MONTH)}"
      }.entries.take(5).map { (label, group) ->
        SleepChartBucket(
          label = label,
          avgMinutes = group.sumOf { it.totalMinutes } / group.size,
          avgQuality = group.sumOf { it.qualityScore } / group.size,
          sessionCount = group.size,
        )
      }
    }
    SleepTimeframe.YEARLY -> {
      val monthFmt = SimpleDateFormat("MMM", Locale.US)
      filtered.reversed().groupBy { session ->
        monthFmt.format(Date(session.endTimeMillis))
      }.entries.take(12).map { (label, group) ->
        SleepChartBucket(
          label = label,
          avgMinutes = group.sumOf { it.totalMinutes } / group.size,
          avgQuality = group.sumOf { it.qualityScore } / group.size,
          sessionCount = group.size,
        )
      }
    }
  }

  return SleepPeriodSummary(
    timeframe = timeframe,
    sessions = filtered,
    totalMinutes = total,
    avgMinutesPerNight = avgMin,
    avgDeepMinutes = avgDeep,
    avgLightMinutes = avgLight,
    avgRemMinutes = avgRem,
    avgAwakeMinutes = avgAwake,
    avgQualityScore = avgQuality,
    avgBpm = avgBpm,
    buckets = buckets,
  )
}

enum class WatchOsType(val key: String, val displayLabel: String) {
  FITCLOUDPRO("fitcloudpro", "FitCloudPro"),
  WEAR_OS("wear_os", "Wear OS / Google Fit"),
  WATCHOS_BRIDGE("watchos_bridge", "watchOS Bridge"),
  HARMONY_OS("harmony_os", "HarmonyOS"),
  ZEPP_OS("zepp_os", "Zepp OS"),
  GARMIN_CONNECT("garmin_connect", "Garmin Connect");

  companion object {
    fun fromKey(key: String?): WatchOsType =
      entries.firstOrNull { it.key == key } ?: FITCLOUDPRO
  }
}

enum class SyncProtocol(val key: String, val displayLabel: String) {
  BLE_GATT("ble_gatt", "BLE GATT Real-Time"),
  HEALTH_CONNECT("health_connect", "Health Connect API"),
  REST_WEBHOOK("rest_webhook", "REST / Webhook API"),
  COMPANION_SYNC("companion_sync", "Companion Bridge");

  companion object {
    fun fromKey(key: String?): SyncProtocol =
      entries.firstOrNull { it.key == key } ?: BLE_GATT
  }
}

enum class HeartRateZone(val key: String) {
  RESTING("resting"),
  FAT_BURN("fat_burn"),
  CARDIO("cardio"),
  PEAK("peak");

  companion object {
    fun fromBpm(bpm: Int): HeartRateZone = when {
      bpm < 95 -> RESTING
      bpm < 125 -> FAT_BURN
      bpm < 155 -> CARDIO
      else -> PEAK
    }
  }
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "VitalSync User",
  val languageCode: String = AppLanguage.EN.code,
  val gender: String = Gender.MALE.key,
  val heightCm: Long = 175L,
  val weightKg: Double = 72.0,
  val lifestyleLevel: String = LifestyleLevel.RECREATIONAL.key,
  val workIntensity: String = WorkIntensity.SEDENTARY.key,
  val onboardingCompleted: Boolean = false,
  val dailyWaterGoalMl: Long = 2500L,
  val dailyStepsGoal: Long = 10000L,
  val dailySleepGoalMinutes: Long = 480L,
  val restingHeartRateTarget: Long = 62L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HydrationLog(
  @DocumentId val id: String = "",
  val userId: String = "",
  val amountMl: Long = 250L,
  val effectiveHydrationMl: Long = 250L,
  val drinkType: String = DrinkType.WATER.key,
  val note: String? = null,
  val dateKey: String = todayDateKey(),
  val loggedAtMillis: Long = System.currentTimeMillis(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HeartRateReading(
  @DocumentId val id: String = "",
  val userId: String = "",
  val bpm: Long = 70L,
  val spo2Percent: Long = 98L,
  val hrvMs: Long = 52L,
  val zone: String = HeartRateZone.RESTING.key,
  val sourceDevice: String = "FitCloudPro Watch",
  val sourceOs: String = WatchOsType.FITCLOUDPRO.key,
  val dateKey: String = todayDateKey(),
  val recordedAtMillis: Long = System.currentTimeMillis(),
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SleepSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val dateKey: String = todayDateKey(),
  val totalMinutes: Long = 450L,
  val deepMinutes: Long = 100L,
  val lightMinutes: Long = 240L,
  val remMinutes: Long = 95L,
  val awakeMinutes: Long = 15L,
  val qualityScore: Long = 86L,
  val avgBpm: Long = 58L,
  val sourceDevice: String = "FitCloudPro Watch",
  val startTimeMillis: Long = System.currentTimeMillis() - 7 * 3600_000L,
  val endTimeMillis: Long = System.currentTimeMillis(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DailyActivitySummary(
  @DocumentId val id: String = "",
  val userId: String = "",
  val dateKey: String = todayDateKey(),
  val steps: Long = 0L,
  val activeMinutes: Long = 0L,
  val caloriesBurned: Long = 0L,
  val distanceMeters: Long = 0L,
  val movePoints: Long = 0L,
  val sourceOs: String = "health_connect",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PairedWatchDevice(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "FitCloudPro Watch",
  val macOrAddress: String = "FC:00:00:00:00:01",
  val osType: String = WatchOsType.FITCLOUDPRO.key,
  val protocol: String = SyncProtocol.BLE_GATT.key,
  val batteryPercent: Long = 85L,
  val firmwareVersion: String = "2.4.0",
  val realtimeHrEnabled: Boolean = true,
  val autoSyncEnabled: Boolean = true,
  val lastSyncMillis: Long = System.currentTimeMillis(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

fun todayDateKey(): String =
  SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun formatTimeHHmm(millis: Long): String =
  SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
