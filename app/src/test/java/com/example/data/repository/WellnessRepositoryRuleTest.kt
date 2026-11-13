package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AppLanguage
import com.example.data.model.DrinkType
import com.example.data.model.Gender
import com.example.data.model.LifestyleLevel
import com.example.data.model.SyncProtocol
import com.example.data.model.WatchOsType
import com.example.data.model.WorkIntensity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WellnessRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureProfileAndOnboardingAndLogSoupHydration_authenticatedOwner_succeedsAndStreams() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = WellnessRepository(firestore, auth)

    val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureProfileInitialized("Marek", AppLanguage.SK)
    }
    assertTrue(profileRes.isSuccess)
    assertFalse(profileRes.getOrThrow().onboardingCompleted)

    val onboardingRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.completeOnboarding(
        displayName = "Marek K.",
        language = AppLanguage.SK,
        gender = Gender.MALE,
        heightCm = 182,
        weightKg = 80.0,
        lifestyle = LifestyleLevel.ACTIVE,
        workIntensity = WorkIntensity.MODERATE_PHYSICAL,
      )
    }
    assertTrue("Expected completeOnboarding success, got ${onboardingRes.exceptionOrNull()}", onboardingRes.isSuccess)

    val updatedProfile = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeProfile(uid).first { it?.onboardingCompleted == true }
    }!!
    assertEquals("active", updatedProfile.lifestyleLevel)
    assertEquals("moderate_physical", updatedProfile.workIntensity)
    assertEquals(182L, updatedProfile.heightCm)
    assertTrue(updatedProfile.dailyWaterGoalMl >= 3500L)

    val soupRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.logHydration(amountMl = 300L, drinkType = DrinkType.SOUP, note = "Lunch soup")
    }
    assertTrue(soupRes.isSuccess)
    val logId = soupRes.getOrThrow()

    val logs = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeHydrationLogs(uid).first { list -> list.any { it.id == logId } }
    }
    val saved = logs.first { it.id == logId }
    assertEquals(300L, saved.amountMl)
    assertEquals(225L, saved.effectiveHydrationMl)
    assertEquals("soup", saved.drinkType)
  }

  @Test
  fun recordHeartRateAndSleepAndActivity_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = WellnessRepository(firestore, auth)

    val hrRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.recordHeartRate(
        bpm = 68,
        spo2Percent = 99,
        hrvMs = 58,
        sourceDevice = "FitCloudPro GTS",
        sourceOs = WatchOsType.FITCLOUDPRO,
      )
    }
    assertTrue(hrRes.isSuccess)

    val sleepRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveSleepSession(
        deepMinutes = 110L,
        lightMinutes = 240L,
        remMinutes = 90L,
        awakeMinutes = 15L,
        avgBpm = 57L,
        sourceDevice = "FitCloudPro GTS",
        startTimeMillis = 1794520000000L,
        endTimeMillis = 1794547000000L,
      )
    }
    assertTrue(sleepRes.isSuccess)

    val activityRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.upsertDailyActivity(
        steps = 9250L,
        activeMinutes = 48L,
        caloriesBurned = 420L,
        distanceMeters = 6800L,
        movePoints = 42L,
        sourceOs = "health_connect",
      )
    }
    assertTrue(activityRes.isSuccess)

    val watchRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveWatchDevice(
        deviceId = "watch_${UUID.randomUUID().toString().take(8)}",
        name = "FitCloudPro Band",
        macOrAddress = "FC:12:34:56:78:90",
        osType = WatchOsType.FITCLOUDPRO,
        protocol = SyncProtocol.BLE_GATT.key,
        batteryPercent = 91L,
      )
    }
    assertTrue(watchRes.isSuccess)

    val watches = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeWatchDevices(uid).first { it.isNotEmpty() }
    }
    assertTrue(watches.any { it.name == "FitCloudPro Band" })
  }

  @Test
  fun crossUserRead_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = WellnessRepository(firestore, auth)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.ensureProfileInitialized("Alice", AppLanguage.EN).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = WellnessRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeHydrationLogs(aliceUid).first()
      }
    } catch (e: Throwable) {
      caught = e
    }
    assertNotNull("Expected PERMISSION_DENIED exception", caught)
    val firestoreException = caught!!.unwrapFirestoreException()
    assertNotNull("Expected nested FirebaseFirestoreException, got $caught", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException!!.code)
  }

  @Test
  fun unauthenticatedWrite_throwsIllegalStateOrPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = WellnessRepository(firestore, auth)
    val result = repo.logHydration(250L, DrinkType.WATER)
    assertTrue(result.isFailure)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_v4@test.com"
    const val BOB_EMAIL = "bob_v4@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
