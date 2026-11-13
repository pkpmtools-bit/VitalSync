package com.example.data.watch

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class HealthConnectSnapshot(
  val steps: Long,
  val caloriesBurned: Long,
  val distanceMeters: Long,
  val latestBpm: Int?,
  val sleepDeepMinutes: Long,
  val sleepLightMinutes: Long,
  val sleepRemMinutes: Long,
  val sleepAwakeMinutes: Long,
  val sleepStartMillis: Long?,
  val sleepEndMillis: Long?,
)

class HealthConnectManager(private val context: Context) {
  private val appContext = context.applicationContext

  val requiredPermissions: Set<String> = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getWritePermission(StepsRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getWritePermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getWritePermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(HydrationRecord::class),
    HealthPermission.getWritePermission(HydrationRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
  )

  fun isAvailable(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return try {
      HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE
    } catch (e: Exception) {
      Log.w(TAG, "Health Connect availability check failed: ${e.message}")
      false
    }
  }

  private fun getClientOrNull(): HealthConnectClient? {
    if (!isAvailable()) return null
    return try {
      HealthConnectClient.getOrCreate(appContext)
    } catch (e: Exception) {
      Log.w(TAG, "Health Connect client creation failed: ${e.message}")
      null
    }
  }

  suspend fun hasAllPermissions(): Boolean {
    val client = getClientOrNull() ?: return false
    return try {
      val granted = client.permissionController.getGrantedPermissions()
      granted.containsAll(requiredPermissions)
    } catch (e: Exception) {
      Log.w(TAG, "Permission check failed: ${e.message}")
      false
    }
  }

  suspend fun readTodaySnapshot(): Result<HealthConnectSnapshot> = runCatching {
    val client = getClientOrNull() ?: error("Health Connect is unavailable on this device")
    val zone = ZoneId.systemDefault()
    val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
    val now = Instant.now()

    val aggResponse = client.aggregate(
      AggregateRequest(
        metrics = setOf(
          StepsRecord.COUNT_TOTAL,
          TotalCaloriesBurnedRecord.ENERGY_TOTAL,
          DistanceRecord.DISTANCE_TOTAL,
        ),
        timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
      )
    )
    val steps = aggResponse[StepsRecord.COUNT_TOTAL] ?: 0L
    val calories = (aggResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0).toLong()
    val distance = (aggResponse[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0).toLong()

    val hrResponse = client.readRecords(
      ReadRecordsRequest(
        HeartRateRecord::class,
        timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
      )
    )
    val latestBpm = hrResponse.records
      .flatMap { it.samples }
      .maxByOrNull { it.time }
      ?.beatsPerMinute
      ?.toInt()

    val sleepLookback = now.minusSeconds(36 * 3600L)
    val sleepResponse = client.readRecords(
      ReadRecordsRequest(
        SleepSessionRecord::class,
        timeRangeFilter = TimeRangeFilter.between(sleepLookback, now),
      )
    )
    val latestSleep = sleepResponse.records.maxByOrNull { it.endTime }
    var deep = 0L
    var light = 0L
    var rem = 0L
    var awake = 0L
    latestSleep?.stages?.forEach { stage ->
      val mins = ((stage.endTime.epochSecond - stage.startTime.epochSecond) / 60L).coerceAtLeast(0L)
      when (stage.stage) {
        SleepSessionRecord.STAGE_TYPE_DEEP -> deep += mins
        SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING -> light += mins
        SleepSessionRecord.STAGE_TYPE_REM -> rem += mins
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += mins
      }
    }

    HealthConnectSnapshot(
      steps = steps,
      caloriesBurned = calories,
      distanceMeters = distance,
      latestBpm = latestBpm,
      sleepDeepMinutes = deep,
      sleepLightMinutes = light,
      sleepRemMinutes = rem,
      sleepAwakeMinutes = awake,
      sleepStartMillis = latestSleep?.startTime?.toEpochMilli(),
      sleepEndMillis = latestSleep?.endTime?.toEpochMilli(),
    )
  }

  suspend fun writeHydration(amountMl: Long): Result<Unit> = runCatching {
    val client = getClientOrNull() ?: return@runCatching
    if (!hasAllPermissions()) return@runCatching
    val now = Instant.now()
    val offset = ZoneOffset.systemDefault().rules.getOffset(now)
    val record = HydrationRecord(
      startTime = now.minusSeconds(1),
      startZoneOffset = offset,
      endTime = now,
      endZoneOffset = offset,
      volume = Volume.milliliters(amountMl.toDouble()),
      metadata = Metadata.manualEntry(),
    )
    client.insertRecords(listOf(record))
  }

  suspend fun writeHeartRate(bpm: Int): Result<Unit> = runCatching {
    val client = getClientOrNull() ?: return@runCatching
    if (!hasAllPermissions()) return@runCatching
    val now = Instant.now()
    val offset = ZoneOffset.systemDefault().rules.getOffset(now)
    val record = HeartRateRecord(
      startTime = now.minusSeconds(1),
      startZoneOffset = offset,
      endTime = now,
      endZoneOffset = offset,
      samples = listOf(HeartRateRecord.Sample(time = now, beatsPerMinute = bpm.toLong())),
      metadata = Metadata.autoRecorded(device = Device(type = Device.TYPE_WATCH)),
    )
    client.insertRecords(listOf(record))
  }

  companion object {
    private const val TAG = "HealthConnectMgr"
  }
}
