package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppLanguage
import com.example.data.model.DailyActivitySummary
import com.example.data.model.DrinkType
import com.example.data.model.Gender
import com.example.data.model.HeartRateReading
import com.example.data.model.HeartRateZone
import com.example.data.model.HydrationLog
import com.example.data.model.LifestyleLevel
import com.example.data.model.PairedWatchDevice
import com.example.data.model.SleepSession
import com.example.data.model.UserProfile
import com.example.data.model.WatchOsType
import com.example.data.model.WorkIntensity
import com.example.data.model.calculateRecommendedHydrationMl
import com.example.data.model.calculateRecommendedSteps
import com.example.data.model.todayDateKey
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

class WellnessRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    auth = Firebase.auth,
  )

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun <T> Flow<T>.logFirestoreErrors(
    operationType: OperationType,
    path: String,
    uid: String,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
    throw error
  }

  // --- User Profile ---
  fun observeProfile(userId: String): Flow<UserProfile?> = flow {
    val uid = requireUserId()
    val docRef = db.collection("users").document(userId)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .logFirestoreErrors(OperationType.GET, docRef.path, uid)
    )
  }

  suspend fun ensureProfileInitialized(
    displayName: String,
    preferredLanguage: AppLanguage,
  ): Result<UserProfile> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        val profile = existing.toObject(
          UserProfile::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        if (profile != null) return@runCatching profile
      }
      val safeName = displayName.ifBlank { "VitalSync User" }.take(100)
      val payload = mapOf(
        "userId" to uid,
        "displayName" to safeName,
        "languageCode" to preferredLanguage.code,
        "gender" to Gender.MALE.key,
        "heightCm" to 175L,
        "weightKg" to 72.0,
        "lifestyleLevel" to LifestyleLevel.RECREATIONAL.key,
        "workIntensity" to WorkIntensity.SEDENTARY.key,
        "onboardingCompleted" to false,
        "dailyWaterGoalMl" to 2500L,
        "dailyStepsGoal" to 10000L,
        "dailySleepGoalMinutes" to 480L,
        "restingHeartRateTarget" to 62L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      UserProfile(
        id = uid,
        userId = uid,
        displayName = safeName,
        languageCode = preferredLanguage.code,
        onboardingCompleted = false,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun completeOnboarding(
    displayName: String,
    language: AppLanguage,
    gender: Gender,
    heightCm: Int,
    weightKg: Double,
    lifestyle: LifestyleLevel,
    workIntensity: WorkIntensity,
  ): Result<Long> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val safeHeight = heightCm.coerceIn(90, 260)
    val safeWeight = weightKg.coerceIn(20.0, 300.0)
    val calculatedWaterMl = calculateRecommendedHydrationMl(
      weightKg = safeWeight,
      heightCm = safeHeight,
      gender = gender,
      lifestyle = lifestyle,
      work = workIntensity,
    )
    val recommendedSteps = calculateRecommendedSteps(lifestyle)
    val payload = mapOf(
      "displayName" to displayName.trim().ifBlank { "VitalSync User" }.take(100),
      "languageCode" to language.code,
      "gender" to gender.key,
      "heightCm" to safeHeight.toLong(),
      "weightKg" to safeWeight,
      "lifestyleLevel" to lifestyle.key,
      "workIntensity" to workIntensity.key,
      "onboardingCompleted" to true,
      "dailyWaterGoalMl" to calculatedWaterMl,
      "dailyStepsGoal" to recommendedSteps,
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        val fullPayload = payload.toMutableMap().apply {
          if (!existing.contains("dailySleepGoalMinutes")) put("dailySleepGoalMinutes", 480L)
          if (!existing.contains("restingHeartRateTarget")) put("restingHeartRateTarget", 62L)
        }
        docRef.update(fullPayload).await()
      } else {
        val createPayload = payload.toMutableMap().apply {
          put("userId", uid)
          put("dailySleepGoalMinutes", 480L)
          put("restingHeartRateTarget", 62L)
          put("createdAt", FieldValue.serverTimestamp())
        }
        docRef.set(createPayload).await()
      }
      calculatedWaterMl
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateProfileGoals(
    displayName: String,
    language: AppLanguage,
    gender: Gender,
    heightCm: Int,
    weightKg: Double,
    lifestyle: LifestyleLevel,
    workIntensity: WorkIntensity,
    dailyWaterGoalMl: Long,
    dailyStepsGoal: Long,
    dailySleepGoalMinutes: Long,
    restingHeartRateTarget: Long,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    val payload = mapOf(
      "displayName" to displayName.trim().ifBlank { "VitalSync User" }.take(100),
      "languageCode" to language.code,
      "gender" to gender.key,
      "heightCm" to heightCm.coerceIn(90, 260).toLong(),
      "weightKg" to weightKg.coerceIn(20.0, 300.0),
      "lifestyleLevel" to lifestyle.key,
      "workIntensity" to workIntensity.key,
      "onboardingCompleted" to true,
      "dailyWaterGoalMl" to dailyWaterGoalMl.coerceIn(500L, 10000L),
      "dailyStepsGoal" to dailyStepsGoal.coerceIn(1000L, 100000L),
      "dailySleepGoalMinutes" to dailySleepGoalMinutes.coerceIn(180L, 960L),
      "restingHeartRateTarget" to restingHeartRateTarget.coerceIn(35L, 140L),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.update(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun updateProfileLanguage(language: AppLanguage): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      val snap = docRef.get().await()
      if (snap.exists()) {
        val updates = mutableMapOf<String, Any>(
          "languageCode" to language.code,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!snap.contains("gender")) updates["gender"] = Gender.MALE.key
        if (!snap.contains("heightCm")) updates["heightCm"] = 175L
        if (!snap.contains("lifestyleLevel")) updates["lifestyleLevel"] = LifestyleLevel.RECREATIONAL.key
        if (!snap.contains("workIntensity")) updates["workIntensity"] = WorkIntensity.SEDENTARY.key
        if (!snap.contains("onboardingCompleted")) updates["onboardingCompleted"] = false
        docRef.update(updates).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  // --- Hydration (Hydro Coach) ---
  fun observeHydrationLogs(userId: String, dateKey: String = todayDateKey()): Flow<List<HydrationLog>> = flow {
    val uid = requireUserId()
    val path = "users/$userId/hydration_logs"
    emitAll(
      db.collection("users").document(userId).collection("hydration_logs")
        .whereEqualTo("dateKey", dateKey)
        .orderBy("loggedAtMillis", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(HydrationLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  suspend fun logHydration(
    amountMl: Long,
    drinkType: DrinkType,
    note: String? = null,
    dateKey: String = todayDateKey(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("hydration_logs").document()
    val safeAmount = amountMl.coerceIn(10L, 5000L)
    val effective = (safeAmount * drinkType.hydrationFactor).roundToLong().coerceIn(0L, 6000L)
    val cleanNote = note?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
    val payload = buildMap<String, Any> {
      put("userId", uid)
      put("amountMl", safeAmount)
      put("effectiveHydrationMl", effective)
      put("drinkType", drinkType.key)
      if (cleanNote != null) put("note", cleanNote)
      put("dateKey", dateKey)
      put("loggedAtMillis", System.currentTimeMillis())
      put("createdAt", FieldValue.serverTimestamp())
      put("updatedAt", FieldValue.serverTimestamp())
    }
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun deleteHydrationLog(logId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(logId.isNotBlank()) { "Log ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("hydration_logs").document(logId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  // --- Heart Rate & SpO2 (FitCloudPro + Multi-OS) ---
  fun observeHeartRateReadings(userId: String, limitCount: Long = 30L): Flow<List<HeartRateReading>> = flow {
    val uid = requireUserId()
    val path = "users/$userId/heart_rate_readings"
    emitAll(
      db.collection("users").document(userId).collection("heart_rate_readings")
        .orderBy("recordedAtMillis", Query.Direction.DESCENDING)
        .limit(limitCount)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(HeartRateReading::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  suspend fun recordHeartRate(
    bpm: Int,
    spo2Percent: Int = 98,
    hrvMs: Int = 50,
    sourceDevice: String = "FitCloudPro Watch",
    sourceOs: WatchOsType = WatchOsType.FITCLOUDPRO,
    recordedAtMillis: Long = System.currentTimeMillis(),
    dateKey: String = todayDateKey(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("heart_rate_readings").document()
    val safeBpm = bpm.coerceIn(30, 240)
    val payload = mapOf(
      "userId" to uid,
      "bpm" to safeBpm.toLong(),
      "spo2Percent" to spo2Percent.coerceIn(0, 100).toLong(),
      "hrvMs" to hrvMs.coerceIn(0, 300).toLong(),
      "zone" to HeartRateZone.fromBpm(safeBpm).key,
      "sourceDevice" to sourceDevice.trim().ifBlank { "Smartwatch" }.take(80),
      "sourceOs" to sourceOs.key,
      "dateKey" to dateKey,
      "recordedAtMillis" to recordedAtMillis.coerceAtLeast(1L),
      "createdAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  // --- Sleep Monitoring ---
  fun observeSleepSessions(userId: String, limitCount: Long = 365L): Flow<List<SleepSession>> = flow {
    val uid = requireUserId()
    val path = "users/$userId/sleep_sessions"
    emitAll(
      db.collection("users").document(userId).collection("sleep_sessions")
        .orderBy("endTimeMillis", Query.Direction.DESCENDING)
        .limit(limitCount)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(SleepSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  suspend fun saveSleepSession(
    deepMinutes: Long,
    lightMinutes: Long,
    remMinutes: Long,
    awakeMinutes: Long,
    avgBpm: Long,
    sourceDevice: String,
    startTimeMillis: Long,
    endTimeMillis: Long,
    dateKey: String = todayDateKey(),
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("sleep_sessions").document()
    val total = (deepMinutes + lightMinutes + remMinutes).coerceIn(1L, 1440L)
    val deepRatio = (deepMinutes.toDouble() / total).coerceIn(0.0, 1.0)
    val remRatio = (remMinutes.toDouble() / total).coerceIn(0.0, 1.0)
    val durationScore = (total / 480.0 * 60.0).coerceIn(0.0, 60.0)
    val stageScore = ((deepRatio * 80.0) + (remRatio * 80.0)).coerceIn(0.0, 40.0)
    val quality = (durationScore + stageScore).roundToLong().coerceIn(0L, 100L)
    val safeStart = startTimeMillis.coerceAtLeast(1L)
    val safeEnd = endTimeMillis.coerceAtLeast(safeStart)

    val payload = mapOf(
      "userId" to uid,
      "dateKey" to dateKey,
      "totalMinutes" to total,
      "deepMinutes" to deepMinutes.coerceIn(0L, 1440L),
      "lightMinutes" to lightMinutes.coerceIn(0L, 1440L),
      "remMinutes" to remMinutes.coerceIn(0L, 1440L),
      "awakeMinutes" to awakeMinutes.coerceIn(0L, 1440L),
      "qualityScore" to quality,
      "avgBpm" to avgBpm.coerceIn(30L, 200L),
      "sourceDevice" to sourceDevice.trim().ifBlank { "FitCloudPro Watch" }.take(80),
      "startTimeMillis" to safeStart,
      "endTimeMillis" to safeEnd,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  // --- Daily Activity (Google Fit / Health Connect + FitCloudPro) ---
  fun observeDailyActivity(userId: String, dateKey: String = todayDateKey()): Flow<DailyActivitySummary?> = flow {
    val uid = requireUserId()
    val docRef = db.collection("users").document(userId).collection("activity_daily").document(dateKey)
    emitAll(
      docRef
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(DailyActivitySummary::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }
        .logFirestoreErrors(OperationType.GET, docRef.path, uid)
    )
  }

  suspend fun upsertDailyActivity(
    steps: Long,
    activeMinutes: Long,
    caloriesBurned: Long,
    distanceMeters: Long,
    movePoints: Long,
    sourceOs: String = "health_connect",
    dateKey: String = todayDateKey(),
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("activity_daily").document(dateKey)
    val safeSteps = steps.coerceIn(0L, 200000L)
    val safeMinutes = activeMinutes.coerceIn(0L, 1440L)
    val safeCalories = caloriesBurned.coerceIn(0L, 20000L)
    val safeDistance = distanceMeters.coerceIn(0L, 500000L)
    val safePoints = movePoints.coerceIn(0L, 10000L)
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "steps" to safeSteps,
            "activeMinutes" to safeMinutes,
            "caloriesBurned" to safeCalories,
            "distanceMeters" to safeDistance,
            "movePoints" to safePoints,
            "sourceOs" to sourceOs,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "dateKey" to dateKey,
            "steps" to safeSteps,
            "activeMinutes" to safeMinutes,
            "caloriesBurned" to safeCalories,
            "distanceMeters" to safeDistance,
            "movePoints" to safePoints,
            "sourceOs" to sourceOs,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  // --- Paired Smartwatch Devices (FitCloudPro + Multi-OS) ---
  fun observeWatchDevices(userId: String): Flow<List<PairedWatchDevice>> = flow {
    val uid = requireUserId()
    val path = "users/$userId/watch_devices"
    emitAll(
      db.collection("users").document(userId).collection("watch_devices")
        .orderBy("lastSyncMillis", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(PairedWatchDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .logFirestoreErrors(OperationType.LIST, path, uid)
    )
  }

  suspend fun saveWatchDevice(
    deviceId: String? = null,
    name: String,
    macOrAddress: String,
    osType: WatchOsType,
    protocol: String,
    batteryPercent: Long,
    firmwareVersion: String = "1.0.0",
    realtimeHrEnabled: Boolean = true,
    autoSyncEnabled: Boolean = true,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val cleanId = deviceId?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.take(128)
    val docRef = if (!cleanId.isNullOrBlank()) {
      db.collection("users").document(uid).collection("watch_devices").document(cleanId)
    } else {
      db.collection("users").document(uid).collection("watch_devices").document()
    }
    val now = System.currentTimeMillis()
    try {
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "name" to name.trim().ifBlank { "Smartwatch" }.take(80),
            "macOrAddress" to macOrAddress.trim().ifBlank { "unknown" }.take(80),
            "osType" to osType.key,
            "protocol" to protocol,
            "batteryPercent" to batteryPercent.coerceIn(0L, 100L),
            "firmwareVersion" to firmwareVersion.take(40),
            "realtimeHrEnabled" to realtimeHrEnabled,
            "autoSyncEnabled" to autoSyncEnabled,
            "lastSyncMillis" to now,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } else {
        docRef.set(
          mapOf(
            "userId" to uid,
            "name" to name.trim().ifBlank { "Smartwatch" }.take(80),
            "macOrAddress" to macOrAddress.trim().ifBlank { "unknown" }.take(80),
            "osType" to osType.key,
            "protocol" to protocol,
            "batteryPercent" to batteryPercent.coerceIn(0L, 100L),
            "firmwareVersion" to firmwareVersion.take(40),
            "realtimeHrEnabled" to realtimeHrEnabled,
            "autoSyncEnabled" to autoSyncEnabled,
            "lastSyncMillis" to now,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun deleteWatchDevice(deviceId: String): Result<Unit> = runCatching {
    val uid = requireUserId()
    require(deviceId.isNotBlank()) { "Device ID must not be blank" }
    val docRef = db.collection("users").document(uid).collection("watch_devices").document(deviceId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }
}
