package com.example.data.watch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AppLanguage
import com.example.data.model.DrinkType
import com.example.data.model.Gender
import com.example.data.model.LifestyleLevel
import com.example.data.model.SleepSession
import com.example.data.model.SleepTimeframe
import com.example.data.model.WatchOsType
import com.example.data.model.WorkIntensity
import com.example.data.model.calculateRecommendedHydrationMl
import com.example.data.model.summarizeSleepSessions
import com.example.i18n.stringsForLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchTelemetryUnitTest {

  @Test
  fun parseStandardHeartRatePacket_uint8WithRrIntervals_extractsBpmAndHrv() {
    // Flags = 0x10 (RR interval present, UINT8 BPM), BPM = 74, RR1 = 800 (781ms), RR2 = 850 (830ms)
    val packet = byteArrayOf(
      0x10,
      74.toByte(),
      0x20, 0x03, // 800 little-endian
      0x52, 0x03, // 850 little-endian
    )
    val (bpm, hrv) = FitCloudProBleManager.parseStandardHeartRatePacket(packet)
    assertEquals(74, bpm)
    assertNotNull(hrv)
  }

  @Test
  fun parseCrossOsTelemetryJson_watchOsBridge_parsesAllBiometrics() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = FitCloudProBleManager(context)
    val json = """
      {
        "deviceName": "Watch Bridge S10",
        "deviceId": "apple_s10",
        "sourceOs": "watchos_bridge",
        "bpm": 66,
        "spo2": 99,
        "hrvMs": 62,
        "battery": 88,
        "steps": 6540,
        "calories": 310,
        "sleepDeepMin": 95,
        "sleepLightMin": 220,
        "sleepRemMin": 85,
        "sleepAwakeMin": 12
      }
    """.trimIndent()

    val parsed = manager.parseCrossOsTelemetryJson(json)
    assertEquals(WatchOsType.WATCHOS_BRIDGE, parsed.osType)
    assertEquals(66, parsed.bpm)
    assertEquals(99, parsed.spo2Percent)
    assertEquals(6540, parsed.steps)
    assertEquals(95, parsed.sleepDeepMinutes)
  }

  @Test
  fun calculateRecommendedHydrationMl_reflectsBiometricsLifestyleAndWork() {
    val sedentaryFemale = calculateRecommendedHydrationMl(
      weightKg = 60.0,
      heightCm = 165,
      gender = Gender.FEMALE,
      lifestyle = LifestyleLevel.PASSIVE,
      work = WorkIntensity.SEDENTARY,
    )
    val activeHeavyMale = calculateRecommendedHydrationMl(
      weightKg = 85.0,
      heightCm = 185,
      gender = Gender.MALE,
      lifestyle = LifestyleLevel.ACTIVE,
      work = WorkIntensity.HEAVY_PHYSICAL,
    )
    assertTrue(sedentaryFemale in 1500L..2200L)
    assertTrue(activeHeavyMale > sedentaryFemale + 1500L)
    assertEquals(0L, activeHeavyMale % 50L)
  }

  @Test
  fun drinkTypeSoup_hasExpectedHydrationFactor() {
    assertEquals(0.75, DrinkType.SOUP.hydrationFactor, 0.001)
    assertEquals(DrinkType.SOUP, DrinkType.fromKey("soup"))
  }

  @Test
  fun summarizeSleepSessions_aggregatesDailyWeeklyMonthlyYearlyPeriods() {
    val now = 1_800_000_000_000L
    val dayMs = 24L * 3600_000L
    val sessions = listOf(
      SleepSession(id = "1", dateKey = "2026-11-13", totalMinutes = 480L, deepMinutes = 110L, lightMinutes = 260L, remMinutes = 95L, awakeMinutes = 15L, qualityScore = 90L, avgBpm = 56L, endTimeMillis = now - 2 * 3600_000L),
      SleepSession(id = "2", dateKey = "2026-11-10", totalMinutes = 420L, deepMinutes = 90L, lightMinutes = 230L, remMinutes = 85L, awakeMinutes = 15L, qualityScore = 82L, avgBpm = 60L, endTimeMillis = now - 3 * dayMs),
      SleepSession(id = "3", dateKey = "2026-10-25", totalMinutes = 450L, deepMinutes = 100L, lightMinutes = 240L, remMinutes = 95L, awakeMinutes = 15L, qualityScore = 86L, avgBpm = 58L, endTimeMillis = now - 19 * dayMs),
      SleepSession(id = "4", dateKey = "2026-07-15", totalMinutes = 390L, deepMinutes = 80L, lightMinutes = 220L, remMinutes = 75L, awakeMinutes = 15L, qualityScore = 76L, avgBpm = 62L, endTimeMillis = now - 120 * dayMs),
    )

    val daily = summarizeSleepSessions(sessions, SleepTimeframe.DAILY, now)
    assertEquals(1, daily.sessions.size)
    assertEquals(480L, daily.avgMinutesPerNight)

    val weekly = summarizeSleepSessions(sessions, SleepTimeframe.WEEKLY, now)
    assertEquals(2, weekly.sessions.size)
    assertEquals(900L, weekly.totalMinutes)
    assertEquals(450L, weekly.avgMinutesPerNight)

    val monthly = summarizeSleepSessions(sessions, SleepTimeframe.MONTHLY, now)
    assertEquals(3, monthly.sessions.size)
    assertEquals(1350L, monthly.totalMinutes)

    val yearly = summarizeSleepSessions(sessions, SleepTimeframe.YEARLY, now)
    assertEquals(4, yearly.sessions.size)
    assertEquals(1740L, yearly.totalMinutes)
    assertTrue(yearly.buckets.isNotEmpty())
  }

  @Test
  fun allV4AndEnglishLanguages_resolveNonEmptyStrings() {
    AppLanguage.entries.forEach { lang ->
      val bundle = stringsForLanguage(lang)
      assertEquals("VitalSync", bundle.appName)
      assertTrue(bundle.signInWithGoogle.isNotBlank())
      assertTrue(bundle.hydroCoachTitle.isNotBlank())
      assertTrue(bundle.drinkSoup.isNotBlank())
      assertTrue(bundle.onboardingTitle.isNotBlank())
      assertTrue(bundle.sleepPeriodWeekly.isNotBlank())
      assertTrue(bundle.sleepPeriodMonthly.isNotBlank())
      assertTrue(bundle.sleepPeriodYearly.isNotBlank())
      assertTrue(bundle.brandKitTitle.isNotBlank())
    }
  }
}
