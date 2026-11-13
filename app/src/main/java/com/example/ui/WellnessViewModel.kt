package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppLanguage
import com.example.data.model.DailyActivitySummary
import com.example.data.model.DrinkType
import com.example.data.model.HeartRateReading
import com.example.data.model.HydrationLog
import com.example.data.model.PairedWatchDevice
import com.example.data.model.SleepSession
import com.example.data.model.SyncProtocol
import com.example.data.model.UserProfile
import com.example.data.model.WatchOsType
import com.example.data.model.todayDateKey
import com.example.data.repository.LanguagePreferenceStore
import com.example.data.repository.WellnessRepository
import com.example.data.watch.DiscoveredBleWatch
import com.example.data.watch.FitCloudProBleManager
import com.example.data.watch.HealthConnectManager
import com.example.data.watch.LiveWatchTelemetry
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class WellnessDashboardSnapshot(
  val profile: UserProfile,
  val hydrationLogs: List<HydrationLog>,
  val heartRateReadings: List<HeartRateReading>,
  val sleepSessions: List<SleepSession>,
  val dailyActivity: DailyActivitySummary,
  val watchDevices: List<PairedWatchDevice>,
) {
  val totalRawWaterMl: Long get() = hydrationLogs.sumOf { it.amountMl }
  val totalEffectiveWaterMl: Long get() = hydrationLogs.sumOf { it.effectiveHydrationMl }
  val latestHeartRate: HeartRateReading? get() = heartRateReadings.firstOrNull()
  val latestSleep: SleepSession? get() = sleepSessions.firstOrNull()
}

class WellnessViewModel(
  private val repository: WellnessRepository,
  private val languageStore: LanguagePreferenceStore,
  val bleManager: FitCloudProBleManager,
  val healthConnectManager: HealthConnectManager,
  private val currentUserId: String,
  initialDisplayName: String,
  initialLanguage: AppLanguage,
) : ViewModel() {

  private val _feedbackMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

  private val _isSyncingHealthConnect = MutableStateFlow(false)
  val isSyncingHealthConnect: StateFlow<Boolean> = _isSyncingHealthConnect.asStateFlow()

  private val _healthConnectAuthorized = MutableStateFlow(false)
  val healthConnectAuthorized: StateFlow<Boolean> = _healthConnectAuthorized.asStateFlow()

  val isBleScanning: StateFlow<Boolean> = bleManager.isScanning
  val discoveredBleWatches: StateFlow<List<DiscoveredBleWatch>> = bleManager.discoveredDevices
  val liveWatchTelemetry: StateFlow<LiveWatchTelemetry> = bleManager.liveTelemetry

  init {
    viewModelScope.launch {
      repository.ensureProfileInitialized(initialDisplayName, initialLanguage)
      refreshHealthConnectStatus()
    }
    // Persist real-time BLE watch telemetry packets into Firestore when heart rate updates arrive
    viewModelScope.launch {
      var lastSavedTimestamp = 0L
      bleManager.liveTelemetry.collect { telemetry ->
        if (telemetry.isConnected && telemetry.updatedAtMillis > lastSavedTimestamp && telemetry.bpm != null) {
          lastSavedTimestamp = telemetry.updatedAtMillis
          repository.recordHeartRate(
            bpm = telemetry.bpm,
            spo2Percent = telemetry.spo2Percent ?: 98,
            hrvMs = telemetry.hrvMs ?: 50,
            sourceDevice = telemetry.deviceName.ifBlank { "FitCloudPro Watch" },
            sourceOs = telemetry.osType,
            recordedAtMillis = telemetry.updatedAtMillis,
          )
          if (telemetry.steps != null) {
            repository.upsertDailyActivity(
              steps = telemetry.steps.toLong(),
              activeMinutes = (telemetry.steps / 140L).coerceAtLeast(1L),
              caloriesBurned = (telemetry.caloriesBurned ?: (telemetry.steps * 0.042).toInt()).toLong(),
              distanceMeters = (telemetry.steps * 0.74).toLong(),
              movePoints = (telemetry.steps / 250L).coerceAtLeast(1L),
              sourceOs = telemetry.osType.key,
            )
          }
        }
      }
    }
  }

  private fun unwrapError(error: Throwable): Throwable {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    return firestoreError ?: error
  }

  val dashboardState: StateFlow<UiState<WellnessDashboardSnapshot>> = combine(
    repository.observeProfile(currentUserId),
    repository.observeHydrationLogs(currentUserId, todayDateKey()),
    repository.observeHeartRateReadings(currentUserId),
    repository.observeSleepSessions(currentUserId),
    repository.observeDailyActivity(currentUserId, todayDateKey()),
    repository.observeWatchDevices(currentUserId),
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    val profile = (values[0] as UserProfile?) ?: UserProfile(
      id = currentUserId,
      userId = currentUserId,
      languageCode = initialLanguage.code,
    )
    @Suppress("UNCHECKED_CAST")
    val hydration = values[1] as List<HydrationLog>
    @Suppress("UNCHECKED_CAST")
    val heartRates = values[2] as List<HeartRateReading>
    @Suppress("UNCHECKED_CAST")
    val sleeps = values[3] as List<SleepSession>
    val activity = (values[4] as DailyActivitySummary?) ?: DailyActivitySummary(userId = currentUserId)
    @Suppress("UNCHECKED_CAST")
    val watches = values[5] as List<PairedWatchDevice>

    UiState.Success(
      WellnessDashboardSnapshot(
        profile = profile,
        hydrationLogs = hydration,
        heartRateReadings = heartRates,
        sleepSessions = sleeps,
        dailyActivity = activity,
        watchDevices = watches,
      )
    ) as UiState<WellnessDashboardSnapshot>
  }.catch { error ->
    val root = unwrapError(error)
    Log.w(TAG, "Dashboard observation failed", root)
    emit(UiState.Error(root.message ?: "Failed to load wellness telemetry"))
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = UiState.Loading,
  )

  fun setLanguage(language: AppLanguage) {
    viewModelScope.launch {
      languageStore.setLanguage(language)
      repository.updateProfileLanguage(language)
    }
  }

  fun logHydration(amountMl: Long, drinkType: DrinkType, note: String? = null) {
    viewModelScope.launch {
      val result = repository.logHydration(amountMl, drinkType, note)
      if (result.isSuccess) {
        healthConnectManager.writeHydration(amountMl)
      } else {
        _feedbackMessage.tryEmit(result.exceptionOrNull()?.message ?: "Failed to log drink")
      }
    }
  }

  fun deleteHydrationLog(logId: String) {
    viewModelScope.launch {
      repository.deleteHydrationLog(logId).onFailure {
        _feedbackMessage.tryEmit(it.message ?: "Failed to remove entry")
      }
    }
  }

  fun recordHeartRate(
    bpm: Int,
    spo2Percent: Int,
    hrvMs: Int,
    sourceDevice: String,
    sourceOs: WatchOsType,
  ) {
    viewModelScope.launch {
      val result = repository.recordHeartRate(
        bpm = bpm,
        spo2Percent = spo2Percent,
        hrvMs = hrvMs,
        sourceDevice = sourceDevice,
        sourceOs = sourceOs,
      )
      if (result.isSuccess) {
        healthConnectManager.writeHeartRate(bpm)
      } else {
        _feedbackMessage.tryEmit(result.exceptionOrNull()?.message ?: "Failed to record heart rate")
      }
    }
  }

  fun saveSleepSession(
    deepMinutes: Long,
    lightMinutes: Long,
    remMinutes: Long,
    awakeMinutes: Long,
    avgBpm: Long,
    sourceDevice: String,
  ) {
    viewModelScope.launch {
      val endMillis = System.currentTimeMillis()
      val total = (deepMinutes + lightMinutes + remMinutes + awakeMinutes).coerceAtLeast(30L)
      val startMillis = endMillis - total * 60_000L
      repository.saveSleepSession(
        deepMinutes = deepMinutes,
        lightMinutes = lightMinutes,
        remMinutes = remMinutes,
        awakeMinutes = awakeMinutes,
        avgBpm = avgBpm,
        sourceDevice = sourceDevice,
        startTimeMillis = startMillis,
        endTimeMillis = endMillis,
      ).onFailure {
        _feedbackMessage.tryEmit(it.message ?: "Failed to save sleep session")
      }
    }
  }

  fun updateActivityMetrics(
    steps: Long,
    activeMinutes: Long,
    caloriesBurned: Long,
    distanceMeters: Long,
    movePoints: Long,
    sourceOs: String,
  ) {
    viewModelScope.launch {
      repository.upsertDailyActivity(
        steps = steps,
        activeMinutes = activeMinutes,
        caloriesBurned = caloriesBurned,
        distanceMeters = distanceMeters,
        movePoints = movePoints,
        sourceOs = sourceOs,
      ).onFailure {
        _feedbackMessage.tryEmit(it.message ?: "Failed to update activity")
      }
    }
  }

  fun completeOnboarding(
    displayName: String,
    language: AppLanguage,
    gender: com.example.data.model.Gender,
    heightCm: Int,
    weightKg: Double,
    lifestyle: com.example.data.model.LifestyleLevel,
    workIntensity: com.example.data.model.WorkIntensity,
    successNotice: String,
  ) {
    viewModelScope.launch {
      languageStore.setLanguage(language)
      repository.completeOnboarding(
        displayName = displayName,
        language = language,
        gender = gender,
        heightCm = heightCm,
        weightKg = weightKg,
        lifestyle = lifestyle,
        workIntensity = workIntensity,
      ).onSuccess {
        _feedbackMessage.tryEmit(successNotice)
      }.onFailure {
        _feedbackMessage.tryEmit(it.message ?: "Failed to save onboarding")
      }
    }
  }

  fun updateProfileGoals(
    displayName: String,
    language: AppLanguage,
    gender: com.example.data.model.Gender,
    heightCm: Int,
    weightKg: Double,
    lifestyle: com.example.data.model.LifestyleLevel,
    workIntensity: com.example.data.model.WorkIntensity,
    dailyWaterGoalMl: Long,
    dailyStepsGoal: Long,
    dailySleepGoalMinutes: Long,
    restingHeartRateTarget: Long,
    successNotice: String,
  ) {
    viewModelScope.launch {
      languageStore.setLanguage(language)
      repository.updateProfileGoals(
        displayName = displayName,
        language = language,
        gender = gender,
        heightCm = heightCm,
        weightKg = weightKg,
        lifestyle = lifestyle,
        workIntensity = workIntensity,
        dailyWaterGoalMl = dailyWaterGoalMl,
        dailyStepsGoal = dailyStepsGoal,
        dailySleepGoalMinutes = dailySleepGoalMinutes,
        restingHeartRateTarget = restingHeartRateTarget,
      ).onSuccess {
        _feedbackMessage.tryEmit(successNotice)
      }.onFailure {
        _feedbackMessage.tryEmit(it.message ?: "Failed to save profile")
      }
    }
  }

  // --- BLE & Multi-OS Watch Integration ---
  fun startBleScan() {
    bleManager.startScan()
  }

  fun stopBleScan() {
    bleManager.stopScan()
  }

  fun connectToDiscoveredWatch(watch: DiscoveredBleWatch) {
    val ok = bleManager.connectToWatch(watch.address, watch.name, watch.detectedOs)
    if (ok) {
      viewModelScope.launch {
        repository.saveWatchDevice(
          deviceId = watch.address.replace(":", ""),
          name = watch.name,
          macOrAddress = watch.address,
          osType = watch.detectedOs,
          protocol = SyncProtocol.BLE_GATT.key,
          batteryPercent = 85L,
          firmwareVersion = if (watch.advertisesFitCloudPro) "FitCloudPro GATT" else "BLE 0x180D",
        )
      }
    }
  }

  fun disconnectWatch() {
    bleManager.disconnect()
  }

  fun saveManualWatchDevice(
    name: String,
    macOrAddress: String,
    osType: WatchOsType,
    protocol: SyncProtocol,
    batteryPercent: Long,
  ) {
    viewModelScope.launch {
      repository.saveWatchDevice(
        name = name,
        macOrAddress = macOrAddress,
        osType = osType,
        protocol = protocol.key,
        batteryPercent = batteryPercent,
      ).onFailure {
        _feedbackMessage.tryEmit(it.message ?: "Failed to save watch")
      }
    }
  }

  fun deleteWatchDevice(deviceId: String) {
    viewModelScope.launch {
      repository.deleteWatchDevice(deviceId)
    }
  }

  fun pullWatchApiTelemetry(endpointUrl: String, successNotice: String) {
    viewModelScope.launch {
      bleManager.fetchFromApiBridge(endpointUrl)
        .onSuccess { telemetry ->
          persistTelemetryBundle(telemetry)
          _feedbackMessage.tryEmit(successNotice)
        }
        .onFailure {
          _feedbackMessage.tryEmit(it.message ?: "API bridge request failed")
        }
    }
  }

  fun importCrossOsTelemetryJson(jsonPayload: String, successNotice: String) {
    viewModelScope.launch {
      runCatching { bleManager.parseCrossOsTelemetryJson(jsonPayload) }
        .onSuccess { telemetry ->
          persistTelemetryBundle(telemetry)
          _feedbackMessage.tryEmit(successNotice)
        }
        .onFailure {
          _feedbackMessage.tryEmit(it.message ?: "Invalid telemetry JSON")
        }
    }
  }

  private suspend fun persistTelemetryBundle(telemetry: LiveWatchTelemetry) {
    repository.saveWatchDevice(
      deviceId = telemetry.deviceAddress.replace(":", "_").take(64).ifBlank { null },
      name = telemetry.deviceName.ifBlank { telemetry.osType.displayLabel },
      macOrAddress = telemetry.deviceAddress.ifBlank { "api-bridge" },
      osType = telemetry.osType,
      protocol = SyncProtocol.REST_WEBHOOK.key,
      batteryPercent = (telemetry.batteryPercent ?: 88).toLong(),
      firmwareVersion = "API Bridge",
    )
    telemetry.bpm?.let { bpm ->
      repository.recordHeartRate(
        bpm = bpm,
        spo2Percent = telemetry.spo2Percent ?: 98,
        hrvMs = telemetry.hrvMs ?: 54,
        sourceDevice = telemetry.deviceName.ifBlank { telemetry.osType.displayLabel },
        sourceOs = telemetry.osType,
      )
    }
    if (telemetry.steps != null || telemetry.caloriesBurned != null) {
      val steps = (telemetry.steps ?: 0).toLong()
      val cals = (telemetry.caloriesBurned ?: (steps * 0.04).toInt()).toLong()
      repository.upsertDailyActivity(
        steps = steps,
        activeMinutes = (steps / 130L).coerceAtLeast(10L),
        caloriesBurned = cals,
        distanceMeters = (steps * 0.74).toLong(),
        movePoints = (steps / 240L).coerceAtLeast(5L),
        sourceOs = telemetry.osType.key,
      )
    }
    if (telemetry.sleepDeepMinutes != null || telemetry.sleepLightMinutes != null) {
      val deep = (telemetry.sleepDeepMinutes ?: 80).toLong()
      val light = (telemetry.sleepLightMinutes ?: 220).toLong()
      val rem = (telemetry.sleepRemMinutes ?: 85).toLong()
      val awake = (telemetry.sleepAwakeMinutes ?: 15).toLong()
      val endMs = System.currentTimeMillis()
      val startMs = endMs - (deep + light + rem + awake) * 60_000L
      repository.saveSleepSession(
        deepMinutes = deep,
        lightMinutes = light,
        remMinutes = rem,
        awakeMinutes = awake,
        avgBpm = (telemetry.bpm ?: 58).toLong(),
        sourceDevice = telemetry.deviceName.ifBlank { telemetry.osType.displayLabel },
        startTimeMillis = startMs,
        endTimeMillis = endMs,
      )
    }
  }

  fun refreshHealthConnectStatus() {
    viewModelScope.launch {
      _healthConnectAuthorized.value = healthConnectManager.hasAllPermissions()
    }
  }

  fun syncHealthConnect(successNotice: String) {
    viewModelScope.launch {
      _isSyncingHealthConnect.value = true
      healthConnectManager.readTodaySnapshot()
        .onSuccess { snap ->
          if (snap.steps > 0 || snap.caloriesBurned > 0) {
            repository.upsertDailyActivity(
              steps = snap.steps,
              activeMinutes = (snap.steps / 135L).coerceAtLeast(0L),
              caloriesBurned = snap.caloriesBurned,
              distanceMeters = snap.distanceMeters,
              movePoints = (snap.steps / 250L).coerceAtLeast(0L),
              sourceOs = "health_connect",
            )
          }
          snap.latestBpm?.let { bpm ->
            repository.recordHeartRate(
              bpm = bpm,
              spo2Percent = 98,
              hrvMs = 52,
              sourceDevice = "Health Connect",
              sourceOs = WatchOsType.WEAR_OS,
            )
          }
          val sleepTotal = snap.sleepDeepMinutes + snap.sleepLightMinutes + snap.sleepRemMinutes
          if (sleepTotal > 0 && snap.sleepStartMillis != null && snap.sleepEndMillis != null) {
            repository.saveSleepSession(
              deepMinutes = snap.sleepDeepMinutes,
              lightMinutes = snap.sleepLightMinutes,
              remMinutes = snap.sleepRemMinutes,
              awakeMinutes = snap.sleepAwakeMinutes,
              avgBpm = (snap.latestBpm ?: 58).toLong(),
              sourceDevice = "Health Connect",
              startTimeMillis = snap.sleepStartMillis,
              endTimeMillis = snap.sleepEndMillis,
            )
          }
          _feedbackMessage.tryEmit(successNotice)
        }
        .onFailure {
          _feedbackMessage.tryEmit(it.message ?: "Health Connect sync unavailable")
        }
      _isSyncingHealthConnect.value = false
    }
  }

  override fun onCleared() {
    super.onCleared()
    bleManager.stopScan()
    bleManager.disconnect()
  }

  private companion object {
    const val TAG = "WellnessVM"
  }
}
