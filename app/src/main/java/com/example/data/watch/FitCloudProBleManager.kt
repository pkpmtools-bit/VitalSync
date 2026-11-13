package com.example.data.watch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.model.WatchOsType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class DiscoveredBleWatch(
  val name: String,
  val address: String,
  val rssi: Int,
  val detectedOs: WatchOsType,
  val advertisesHeartRate: Boolean,
  val advertisesFitCloudPro: Boolean,
)

data class LiveWatchTelemetry(
  val deviceName: String = "",
  val deviceAddress: String = "",
  val osType: WatchOsType = WatchOsType.FITCLOUDPRO,
  val isConnected: Boolean = false,
  val bpm: Int? = null,
  val spo2Percent: Int? = null,
  val hrvMs: Int? = null,
  val batteryPercent: Int? = null,
  val steps: Int? = null,
  val caloriesBurned: Int? = null,
  val sleepDeepMinutes: Int? = null,
  val sleepLightMinutes: Int? = null,
  val sleepRemMinutes: Int? = null,
  val sleepAwakeMinutes: Int? = null,
  val lastPacketHex: String = "",
  val updatedAtMillis: Long = 0L,
)

class FitCloudProBleManager(context: Context) {
  private val appContext = context.applicationContext
  private val bluetoothManager =
    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter?
    get() = bluetoothManager?.adapter

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .build()

  private var activeGatt: BluetoothGatt? = null
  private var activeScanCallback: ScanCallback? = null

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleWatch>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredBleWatch>> = _discoveredDevices.asStateFlow()

  private val _liveTelemetry = MutableStateFlow(LiveWatchTelemetry())
  val liveTelemetry: StateFlow<LiveWatchTelemetry> = _liveTelemetry.asStateFlow()

  fun isBluetoothHardwareAvailable(): Boolean = bluetoothAdapter != null

  fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

  @SuppressLint("MissingPermission")
  fun startScan(): Boolean {
    val adapter = bluetoothAdapter ?: return false
    if (!adapter.isEnabled) return false
    val scanner = adapter.bluetoothLeScanner ?: return false

    stopScan()
    _discoveredDevices.value = emptyList()

    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device ?: return
        val name = try {
          device.name ?: result.scanRecord?.deviceName ?: return
        } catch (_: SecurityException) {
          return
        }
        if (name.isBlank()) return
        val uuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
        val hasHr = uuids.contains(UUID_HEART_RATE_SERVICE)
        val hasFcp = uuids.any { it in FITCLOUD_SERVICE_UUIDS } ||
          name.contains("FitCloud", ignoreCase = true) ||
          name.startsWith("FC", ignoreCase = true) ||
          name.contains("DT", ignoreCase = true) ||
          name.contains("HK", ignoreCase = true)
        val detectedOs = inferWatchOs(name, hasFcp)
        val entry = DiscoveredBleWatch(
          name = name,
          address = device.address,
          rssi = result.rssi,
          detectedOs = detectedOs,
          advertisesHeartRate = hasHr,
          advertisesFitCloudPro = hasFcp,
        )
        val current = _discoveredDevices.value.toMutableList()
        val idx = current.indexOfFirst { it.address == entry.address }
        if (idx >= 0) current[idx] = entry else current.add(entry)
        _discoveredDevices.value = current.sortedByDescending { it.rssi }
      }

      override fun onScanFailed(errorCode: Int) {
        Log.w(TAG, "BLE scan failed with code=$errorCode")
        _isScanning.value = false
      }
    }

    activeScanCallback = callback
    val settings = ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .build()
    return try {
      scanner.startScan(null, settings, callback)
      _isScanning.value = true
      true
    } catch (e: SecurityException) {
      Log.w(TAG, "Missing Bluetooth scan permission", e)
      _isScanning.value = false
      false
    }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    val callback = activeScanCallback ?: return
    try {
      bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
    } catch (e: Exception) {
      Log.w(TAG, "stopScan ignored: ${e.message}")
    } finally {
      activeScanCallback = null
      _isScanning.value = false
    }
  }

  @SuppressLint("MissingPermission")
  fun connectToWatch(address: String, fallbackName: String = "Smartwatch", osType: WatchOsType): Boolean {
    stopScan()
    disconnect()
    val adapter = bluetoothAdapter ?: return false
    return try {
      val device: BluetoothDevice = adapter.getRemoteDevice(address)
      _liveTelemetry.value = LiveWatchTelemetry(
        deviceName = device.name ?: fallbackName,
        deviceAddress = address,
        osType = osType,
        isConnected = false,
      )
      activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
      } else {
        device.connectGatt(appContext, false, gattCallback)
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "connectToWatch failed", e)
      false
    }
  }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    try {
      activeGatt?.disconnect()
      activeGatt?.close()
    } catch (e: Exception) {
      Log.w(TAG, "disconnect ignored: ${e.message}")
    } finally {
      activeGatt = null
      _liveTelemetry.value = _liveTelemetry.value.copy(isConnected = false)
    }
  }

  private val gattCallback = object : BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      if (newState == BluetoothProfile.STATE_CONNECTED) {
        _liveTelemetry.value = _liveTelemetry.value.copy(isConnected = true)
        gatt.discoverServices()
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        _liveTelemetry.value = _liveTelemetry.value.copy(isConnected = false)
      }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      if (status != BluetoothGatt.GATT_SUCCESS) return

      // 1. Standard Heart Rate Measurement (0x180D / 0x2A37)
      gatt.getService(UUID_HEART_RATE_SERVICE)
        ?.getCharacteristic(UUID_HEART_RATE_MEASUREMENT)
        ?.let { enableNotifications(gatt, it) }

      // 2. Standard Battery Level (0x180F / 0x2A19)
      gatt.getService(UUID_BATTERY_SERVICE)
        ?.getCharacteristic(UUID_BATTERY_LEVEL)
        ?.let { gatt.readCharacteristic(it) }

      // 3. FitCloudPro / Nordic UART / Vendor Telemetry Characteristics
      for (serviceUuid in FITCLOUD_SERVICE_UUIDS) {
        val service = gatt.getService(serviceUuid) ?: continue
        service.characteristics.forEach { char ->
          val props = char.properties
          if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            enableNotifications(gatt, char)
          }
        }
      }
    }

    @Suppress("DEPRECATION")
    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      status: Int,
    ) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        handleCharacteristicBytes(characteristic.uuid, characteristic.value ?: byteArrayOf())
      }
    }

    override fun onCharacteristicRead(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
      status: Int,
    ) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        handleCharacteristicBytes(characteristic.uuid, value)
      }
    }

    @Suppress("DEPRECATION")
    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
    ) {
      handleCharacteristicBytes(characteristic.uuid, characteristic.value ?: byteArrayOf())
    }

    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray,
    ) {
      handleCharacteristicBytes(characteristic.uuid, value)
    }
  }

  @SuppressLint("MissingPermission")
  private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
    gatt.setCharacteristicNotification(characteristic, true)
    val cccd = characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG) ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    } else {
      @Suppress("DEPRECATION")
      cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
      @Suppress("DEPRECATION")
      gatt.writeDescriptor(cccd)
    }
  }

  private fun handleCharacteristicBytes(uuid: UUID, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    val hex = bytes.joinToString(" ") { "%02X".format(it) }
    val current = _liveTelemetry.value
    when (uuid) {
      UUID_HEART_RATE_MEASUREMENT -> {
        val parsed = parseStandardHeartRatePacket(bytes)
        _liveTelemetry.value = current.copy(
          bpm = parsed.first,
          hrvMs = parsed.second ?: current.hrvMs,
          lastPacketHex = hex,
          updatedAtMillis = System.currentTimeMillis(),
        )
      }
      UUID_BATTERY_LEVEL -> {
        val battery = bytes[0].toInt() and 0xFF
        _liveTelemetry.value = current.copy(
          batteryPercent = battery.coerceIn(0, 100),
          lastPacketHex = hex,
          updatedAtMillis = System.currentTimeMillis(),
        )
      }
      else -> {
        // FitCloudPro vendor packet parsing (protocol frame header 0xAB / 0xAA / 0x5A)
        val parsedFcp = parseFitCloudProPacket(bytes)
        _liveTelemetry.value = current.copy(
          bpm = parsedFcp.bpm ?: current.bpm,
          spo2Percent = parsedFcp.spo2 ?: current.spo2Percent,
          steps = parsedFcp.steps ?: current.steps,
          batteryPercent = parsedFcp.battery ?: current.batteryPercent,
          lastPacketHex = hex,
          updatedAtMillis = System.currentTimeMillis(),
        )
      }
    }
  }

  /**
   * Pulls live biometric JSON telemetry from an external Watch REST/Webhook API endpoint
   * (supports Wear OS companion servers, watchOS HealthKit shortcuts, Zepp/Garmin/HarmonyOS bridges).
   */
  suspend fun fetchFromApiBridge(endpointUrl: String): Result<LiveWatchTelemetry> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanUrl = endpointUrl.trim()
        require(cleanUrl.startsWith("https://") || cleanUrl.startsWith("http://")) {
          "Endpoint must start with https:// or http://"
        }
        val request = Request.Builder().url(cleanUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            error("HTTP ${response.code}: ${response.message}")
          }
          val body = response.body?.string().orEmpty()
          parseCrossOsTelemetryJson(body)
        }
      }
    }

  /**
   * Parses a real cross-OS JSON telemetry payload into [LiveWatchTelemetry].
   */
  fun parseCrossOsTelemetryJson(jsonText: String): LiveWatchTelemetry {
    val root = JSONObject(jsonText)
    val osKey = root.optString("sourceOs", root.optString("os", "fitcloudpro"))
    val osType = WatchOsType.fromKey(osKey)
    val parsed = LiveWatchTelemetry(
      deviceName = root.optString("deviceName", osType.displayLabel),
      deviceAddress = root.optString("deviceId", root.optString("mac", "api-bridge")),
      osType = osType,
      isConnected = true,
      bpm = root.takeIf { it.has("bpm") }?.optInt("bpm"),
      spo2Percent = root.takeIf { it.has("spo2") }?.optInt("spo2"),
      hrvMs = root.takeIf { it.has("hrvMs") }?.optInt("hrvMs"),
      batteryPercent = root.takeIf { it.has("battery") }?.optInt("battery"),
      steps = root.takeIf { it.has("steps") }?.optInt("steps"),
      caloriesBurned = root.takeIf { it.has("calories") }?.optInt("calories"),
      sleepDeepMinutes = root.takeIf { it.has("sleepDeepMin") }?.optInt("sleepDeepMin"),
      sleepLightMinutes = root.takeIf { it.has("sleepLightMin") }?.optInt("sleepLightMin"),
      sleepRemMinutes = root.takeIf { it.has("sleepRemMin") }?.optInt("sleepRemMin"),
      sleepAwakeMinutes = root.takeIf { it.has("sleepAwakeMin") }?.optInt("sleepAwakeMin"),
      lastPacketHex = "JSON_API_OK",
      updatedAtMillis = System.currentTimeMillis(),
    )
    _liveTelemetry.value = parsed
    return parsed
  }

  private fun inferWatchOs(name: String, isFitCloud: Boolean): WatchOsType = when {
    isFitCloud -> WatchOsType.FITCLOUDPRO
    name.contains("Pixel", ignoreCase = true) || name.contains("Galaxy Watch", ignoreCase = true) ||
      name.contains("Wear", ignoreCase = true) -> WatchOsType.WEAR_OS
    name.contains("Apple", ignoreCase = true) -> WatchOsType.WATCHOS_BRIDGE
    name.contains("Amazfit", ignoreCase = true) || name.contains("Zepp", ignoreCase = true) -> WatchOsType.ZEPP_OS
    name.contains("HUAWEI", ignoreCase = true) || name.contains("Honor", ignoreCase = true) -> WatchOsType.HARMONY_OS
    name.contains("Garmin", ignoreCase = true) -> WatchOsType.GARMIN_CONNECT
    else -> WatchOsType.FITCLOUDPRO
  }

  private data class ParsedFcpFrame(
    val bpm: Int? = null,
    val spo2: Int? = null,
    val steps: Int? = null,
    val battery: Int? = null,
  )

  private fun parseFitCloudProPacket(bytes: ByteArray): ParsedFcpFrame {
    if (bytes.size < 4) return ParsedFcpFrame()
    val header = bytes[0].toInt() and 0xFF
    val cmd = bytes[1].toInt() and 0xFF
    // Standard FitCloudPro / TopStep / Goodix BLE frame parsing
    return when {
      (header == 0xAB || header == 0xAA) && cmd == 0x31 && bytes.size >= 6 -> {
        val bpm = bytes[4].toInt() and 0xFF
        val spo2 = bytes[5].toInt() and 0xFF
        ParsedFcpFrame(
          bpm = bpm.takeIf { it in 35..230 },
          spo2 = spo2.takeIf { it in 70..100 },
        )
      }
      (header == 0xAB || header == 0xAA) && cmd == 0x51 && bytes.size >= 7 -> {
        val steps = ((bytes[4].toInt() and 0xFF) shl 16) or
          ((bytes[5].toInt() and 0xFF) shl 8) or
          (bytes[6].toInt() and 0xFF)
        ParsedFcpFrame(steps = steps.takeIf { it in 0..200000 })
      }
      (header == 0xAB || header == 0xAA) && cmd == 0x91 && bytes.size >= 4 -> {
        val battery = bytes[3].toInt() and 0xFF
        ParsedFcpFrame(battery = battery.takeIf { it in 0..100 })
      }
      else -> ParsedFcpFrame()
    }
  }

  companion object {
    private const val TAG = "FitCloudProBle"

    val UUID_HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val UUID_HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val UUID_BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val UUID_BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val UUID_CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // FitCloudPro & common smartwatch UART/Telemetry GATT services
    val FITCLOUD_SERVICE_UUIDS = setOf(
      UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), // Nordic UART (FitCloudPro / Amazfit)
      UUID.fromString("0000fee7-0000-1000-8000-00805f9b34fb"), // FitCloud / WeChat Sports BLE
      UUID.fromString("000056ff-0000-1000-8000-00805f9b34fb"), // TopStep / FitCloudPro
      UUID.fromString("ba5c49d2-04a3-4071-a0b5-35853eb08307"), // FitCloudPro V2
    )

    /**
     * Parses Bluetooth SIG Heart Rate Measurement (0x2A37) packet into (BPM, optional HRV_ms).
     */
    fun parseStandardHeartRatePacket(bytes: ByteArray): Pair<Int, Int?> {
      if (bytes.isEmpty()) return 0 to null
      val flags = bytes[0].toInt() and 0xFF
      val isUint16 = (flags and 0x01) != 0
      var offset = 1
      val bpm = if (isUint16 && bytes.size >= 3) {
        val v = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        v
      } else if (bytes.size >= 2) {
        val v = bytes[offset].toInt() and 0xFF
        offset += 1
        v
      } else {
        0
      }

      // Skip energy expended if present (bit 3)
      if ((flags and 0x08) != 0) offset += 2

      // Parse RR-intervals (bit 4) in units of 1/1024 sec to estimate RMSSD HRV
      var hrvMs: Int? = null
      if ((flags and 0x10) != 0 && bytes.size >= offset + 4) {
        val rrList = mutableListOf<Double>()
        while (offset + 1 < bytes.size) {
          val rawRr = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
          rrList.add(rawRr * (1000.0 / 1024.0))
          offset += 2
        }
        if (rrList.size >= 2) {
          val diffs = rrList.zipWithNext { a, b -> abs(b - a) }
          hrvMs = diffs.average().roundToInt().coerceIn(5, 250)
        }
      }
      return bpm to hrvMs
    }
  }
}
