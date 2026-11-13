package com.example.ui.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.example.data.model.PairedWatchDevice
import com.example.data.model.SyncProtocol
import com.example.data.model.WatchOsType
import com.example.data.model.formatTimeHHmm
import com.example.data.watch.DiscoveredBleWatch
import com.example.data.watch.HealthConnectManager
import com.example.data.watch.LiveWatchTelemetry
import com.example.i18n.LocalStrings
import com.example.ui.WellnessDashboardSnapshot
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalVitalColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchHubScreen(
  snapshot: WellnessDashboardSnapshot,
  isBleScanning: Boolean,
  bluetoothHardwareAvailable: Boolean,
  discoveredWatches: List<DiscoveredBleWatch>,
  liveTelemetry: LiveWatchTelemetry,
  healthConnectManager: HealthConnectManager,
  healthConnectAuthorized: Boolean,
  isSyncingHealthConnect: Boolean,
  onStartBleScan: () -> Unit,
  onStopBleScan: () -> Unit,
  onConnectWatch: (DiscoveredBleWatch) -> Unit,
  onDisconnectWatch: () -> Unit,
  onSaveManualWatch: (String, String, WatchOsType, SyncProtocol, Long) -> Unit,
  onDeleteWatch: (String) -> Unit,
  onRefreshHealthConnect: () -> Unit,
  onSyncHealthConnect: () -> Unit,
  onPullApiTelemetry: (String) -> Unit,
  onImportJsonTelemetry: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current

  val blePermissions = remember {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
      )
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }

  var hasBlePermissions by remember {
    mutableStateOf(
      blePermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
      }
    )
  }

  val blePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    hasBlePermissions = result.values.all { it }
    if (hasBlePermissions) {
      onStartBleScan()
    }
  }

  val healthConnectAvailable = remember { healthConnectManager.isAvailable() }
  val hcPermissionLauncher = rememberLauncherForActivityResult(
    contract = PermissionController.createRequestPermissionResultContract()
  ) { granted ->
    onRefreshHealthConnect()
    if (granted.isNotEmpty()) {
      onSyncHealthConnect()
    }
  }

  var endpointUrl by remember { mutableStateOf("") }
  var showJsonDialog by remember { mutableStateOf(false) }
  var showAddWatchDialog by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        Text(
          text = strings.watchHubTitle,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }

      // Live Connected Watch Stream Card
      if (liveTelemetry.isConnected || liveTelemetry.updatedAtMillis > 0L) {
        item {
          Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = vitalColors.fitContainer.copy(alpha = 0.55f)),
            border = BorderStroke(1.dp, vitalColors.fit),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.BluetoothConnected, contentDescription = null, tint = vitalColors.fit)
                  Spacer(Modifier.width(8.dp))
                  Column {
                    Text(
                      text = liveTelemetry.deviceName.ifBlank { liveTelemetry.osType.displayLabel },
                      style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                      text = "${strings.connectedStatus} · ${liveTelemetry.osType.displayLabel}",
                      style = MaterialTheme.typography.labelSmall,
                      color = vitalColors.fit,
                    )
                  }
                }
                OutlinedButton(onClick = onDisconnectWatch) {
                  Text(strings.disconnectWatch)
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
              ) {
                Text(
                  text = "HR: ${liveTelemetry.bpm ?: "--"} ${strings.bpmUnit}",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  color = vitalColors.pulse,
                )
                Text(
                  text = "SpO₂: ${liveTelemetry.spo2Percent ?: "--"}%",
                  fontFamily = JetBrainsMonoFamily,
                  fontWeight = FontWeight.Bold,
                  color = vitalColors.hydro,
                )
                Text(
                  text = "${strings.batteryLabel}: ${liveTelemetry.batteryPercent ?: "--"}%",
                  fontFamily = JetBrainsMonoFamily,
                  color = vitalColors.fit,
                )
              }
              if (liveTelemetry.lastPacketHex.isNotBlank()) {
                Text(
                  text = "GATT / API Packet: ${liveTelemetry.lastPacketHex}",
                  fontFamily = JetBrainsMonoFamily,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Real-Time BLE Scanner Card (FitCloudPro + Standard HR GATT)
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(vitalColors.hydroContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (isBleScanning) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                  contentDescription = strings.bleScannerTitle,
                  tint = vitalColors.hydro,
                )
              }
              Spacer(Modifier.width(10.dp))
              Text(strings.bleScannerTitle, style = MaterialTheme.typography.titleMedium)
            }

            if (!bluetoothHardwareAvailable) {
              Text(
                text = strings.bluetoothUnavailable,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else if (!hasBlePermissions) {
              Text(
                text = strings.blePermissionPrompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = { blePermissionLauncher.launch(blePermissions) },
                modifier = Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("grant_ble_permission_button"),
              ) {
                Text(strings.grantPermissions)
              }
            } else {
              Button(
                onClick = { if (isBleScanning) onStopBleScan() else onStartBleScan() },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isBleScanning) vitalColors.pulse else vitalColors.hydro,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("ble_scan_toggle_button"),
              ) {
                if (isBleScanning) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                  )
                  Spacer(Modifier.width(8.dp))
                  Text(strings.stopScan)
                } else {
                  Text(strings.startScan)
                }
              }
            }

            discoveredWatches.forEach { watch ->
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(watch.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                      text = "${watch.detectedOs.displayLabel} · ${watch.address} · ${watch.rssi} dBm",
                      fontFamily = JetBrainsMonoFamily,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Button(
                    onClick = { onConnectWatch(watch) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                  ) {
                    Text(strings.connectWatch)
                  }
                }
              }
            }
          }
        }
      }

      // Google Fit / Health Connect Integration Card
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(vitalColors.fitContainer),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = vitalColors.fit)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(strings.healthConnectTitle, style = MaterialTheme.typography.titleMedium)
                  Text(
                    text = if (healthConnectAvailable) strings.healthConnectAvailable else strings.healthConnectMissing,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (healthConnectAvailable) vitalColors.fit else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            Text(
              text = strings.healthConnectDesc,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (healthConnectAvailable && !healthConnectAuthorized) {
                OutlinedButton(
                  onClick = { hcPermissionLauncher.launch(healthConnectManager.requiredPermissions) },
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("hc_authorize_button"),
                ) {
                  Text(strings.authorizeHealthConnect)
                }
              }
              Button(
                onClick = onSyncHealthConnect,
                enabled = healthConnectAvailable && !isSyncingHealthConnect,
                colors = ButtonDefaults.buttonColors(containerColor = vitalColors.fit),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("hc_sync_button"),
              ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.syncHealthConnect)
              }
            }
          }
        }
      }

      // Cross-OS Smartwatch REST API / JSON Bridge Card
      item {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, vitalColors.cardBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(vitalColors.sleepContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Rounded.Api, contentDescription = null, tint = vitalColors.sleep)
              }
              Spacer(Modifier.width(10.dp))
              Column {
                Text(strings.multiOsBridgeTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = strings.multiOsBridgeDesc,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            OutlinedTextField(
              value = endpointUrl,
              onValueChange = { endpointUrl = it },
              label = { Text(strings.apiBridgeTitle) },
              placeholder = { Text(strings.apiBridgeEndpointHint) },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("api_bridge_endpoint_input"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Button(
                onClick = { onPullApiTelemetry(endpointUrl) },
                enabled = endpointUrl.isNotBlank(),
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("pull_api_telemetry_button"),
              ) {
                Text(strings.fetchApiTelemetry)
              }
              OutlinedButton(
                onClick = { showJsonDialog = true },
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("import_json_telemetry_button"),
              ) {
                Text(strings.importJsonTelemetry)
              }
            }
          }
        }
      }

      // Paired & Multi-OS Watches Registry
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.pairedWatchesTitle, style = MaterialTheme.typography.titleLarge)
          TextButton(
            onClick = { showAddWatchDialog = true },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("add_watch_manual_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(strings.addWatchManual)
          }
        }
      }

      if (snapshot.watchDevices.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Rounded.Watch, contentDescription = null, tint = vitalColors.hydro, modifier = Modifier.size(32.dp))
              Text(strings.emptyWatchesTip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      } else {
        items(snapshot.watchDevices, key = { it.id.ifBlank { it.macOrAddress } }) { device ->
          PairedWatchCard(device = device, onDelete = { onDeleteWatch(device.id) })
        }
      }
    }
  }

  if (showJsonDialog) {
    ImportJsonTelemetryDialog(
      onDismiss = { showJsonDialog = false },
      onImport = { json ->
        onImportJsonTelemetry(json)
        showJsonDialog = false
      },
    )
  }

  if (showAddWatchDialog) {
    AddWatchDeviceDialog(
      onDismiss = { showAddWatchDialog = false },
      onSave = { name, addr, os, proto ->
        onSaveManualWatch(name, addr, os, proto, 90L)
        showAddWatchDialog = false
      },
    )
  }
}

@Composable
private fun PairedWatchCard(
  device: PairedWatchDevice,
  onDelete: () -> Unit,
) {
  val strings = LocalStrings.current
  val vitalColors = LocalVitalColors.current
  val osType = WatchOsType.fromKey(device.osType)
  val protocol = SyncProtocol.fromKey(device.protocol)

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, vitalColors.cardBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(vitalColors.hydroContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.Watch, contentDescription = device.name, tint = vitalColors.hydro, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text(device.name, style = MaterialTheme.typography.titleMedium)
          Text(
            text = "${osType.displayLabel} · ${protocol.displayLabel}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${strings.lastSyncedLabel}: ${formatTimeHHmm(device.lastSyncMillis)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = strings.batteryLabel, tint = vitalColors.fit, modifier = Modifier.size(16.dp))
        Text(
          text = "${device.batteryPercent}%",
          fontFamily = JetBrainsMonoFamily,
          style = MaterialTheme.typography.labelMedium,
          color = vitalColors.fit,
        )
        if (device.id.isNotBlank()) {
          IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = strings.deleteEntry)
          }
        }
      }
    }
  }
}

@Composable
private fun ImportJsonTelemetryDialog(
  onDismiss: () -> Unit,
  onImport: (String) -> Unit,
) {
  val strings = LocalStrings.current
  var jsonText by remember {
    mutableStateOf(
      """{"deviceName":"Apple Watch / Wear Bridge","deviceId":"bridge_01","sourceOs":"watchos_bridge","bpm":69,"spo2":98,"hrvMs":58,"battery":86,"steps":7420,"calories":340,"sleepDeepMin":102,"sleepLightMin":230,"sleepRemMin":94,"sleepAwakeMin":14}"""
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.importJsonTelemetry) },
    text = {
      OutlinedTextField(
        value = jsonText,
        onValueChange = { jsonText = it },
        minLines = 5,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      Button(onClick = { onImport(jsonText) }, enabled = jsonText.isNotBlank()) {
        Text(strings.saveAction)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddWatchDeviceDialog(
  onDismiss: () -> Unit,
  onSave: (String, String, WatchOsType, SyncProtocol) -> Unit,
) {
  val strings = LocalStrings.current
  var name by remember { mutableStateOf("FitCloudPro GTS 4") }
  var address by remember { mutableStateOf("FC:4A:21:88:10:02") }
  var selectedOs by remember { mutableStateOf(WatchOsType.FITCLOUDPRO) }
  var selectedProto by remember { mutableStateOf(SyncProtocol.BLE_GATT) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(strings.addWatchManual) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(strings.watchNameLabel) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = address,
          onValueChange = { address = it },
          label = { Text("MAC / Bridge ID") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(strings.watchOsLabel, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          WatchOsType.entries.forEach { os ->
            FilterChip(
              selected = os == selectedOs,
              onClick = { selectedOs = os },
              label = { Text(os.displayLabel) },
            )
          }
        }
        Text(strings.watchProtocolLabel, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          SyncProtocol.entries.forEach { proto ->
            FilterChip(
              selected = proto == selectedProto,
              onClick = { selectedProto = proto },
              label = { Text(proto.displayLabel) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(name, address, selectedOs, selectedProto) },
        enabled = name.isNotBlank() && address.isNotBlank(),
      ) {
        Text(strings.saveAction)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancelAction) }
    },
  )
}
