package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.AppLanguage
import com.example.data.model.WatchOsType
import com.example.data.repository.LanguagePreferenceStore
import com.example.data.repository.WellnessRepository
import com.example.data.watch.FitCloudProBleManager
import com.example.data.watch.HealthConnectManager
import com.example.i18n.LocalStrings
import com.example.i18n.stringsForLanguage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.UiState
import com.example.ui.WellnessDashboardSnapshot
import com.example.ui.WellnessViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.brand.VitalSyncSplashOverlay
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.hydration.HydrationScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.settings.ProfileSettingsScreen
import com.example.ui.theme.VitalSyncTheme
import com.example.ui.vitals.VitalsScreen
import com.example.ui.watch.WatchHubScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

const val ROUTE_OVERVIEW = "overview"
const val ROUTE_HYDRATION = "hydration"
const val ROUTE_VITALS = "vitals"
const val ROUTE_WATCHES = "watches"
const val ROUTE_PROFILE = "profile"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      VitalSyncTheme {
        AppRoot()
      }
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppRoot() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val languageStore = remember { LanguagePreferenceStore(context.applicationContext) }
  val selectedLanguage by languageStore.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.EN)
  val strings = remember(selectedLanguage) { stringsForLanguage(selectedLanguage) }

  val auth = Firebase.auth
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  var showSplash by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    delay(1100L)
    showSplash = false
  }

  CompositionLocalProvider(LocalStrings provides strings) {
    Box(modifier = Modifier.fillMaxSize()) {
      val user = currentUser
      if (user == null) {
        AuthScreen(
          selectedLanguage = selectedLanguage,
          onSelectLanguage = { lang ->
            scope.launch { languageStore.setLanguage(lang) }
          },
          onAuthSuccess = {},
        )
      } else {
        androidx.compose.runtime.key(user.uid) {
          MainWellnessShell(
            currentUserId = user.uid,
            initialDisplayName = user.displayName ?: "VitalSync User",
            selectedLanguage = selectedLanguage,
            languageStore = languageStore,
          )
        }
      }

      AnimatedVisibility(
        visible = showSplash,
        exit = fadeOut(),
      ) {
        VitalSyncSplashOverlay()
      }
    }
  }
}

private data class NavDestinationItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

@Composable
fun MainWellnessShell(
  currentUserId: String,
  initialDisplayName: String,
  selectedLanguage: AppLanguage,
  languageStore: LanguagePreferenceStore,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val strings = LocalStrings.current
  val credentialManager = remember { CredentialManager.create(context) }

  val viewModel: WellnessViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        WellnessViewModel(
          repository = WellnessRepository(db),
          languageStore = languageStore,
          bleManager = FitCloudProBleManager(app),
          healthConnectManager = HealthConnectManager(app),
          currentUserId = currentUserId,
          initialDisplayName = initialDisplayName,
          initialLanguage = selectedLanguage,
        )
      }
    },
  )

  val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
  val isBleScanning by viewModel.isBleScanning.collectAsStateWithLifecycle()
  val discoveredWatches by viewModel.discoveredBleWatches.collectAsStateWithLifecycle()
  val liveTelemetry by viewModel.liveWatchTelemetry.collectAsStateWithLifecycle()
  val healthConnectAuthorized by viewModel.healthConnectAuthorized.collectAsStateWithLifecycle()
  val isSyncingHealthConnect by viewModel.isSyncingHealthConnect.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.feedbackMessage.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_OVERVIEW

  val navItems = listOf(
    NavDestinationItem(ROUTE_OVERVIEW, strings.navOverview, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    NavDestinationItem(ROUTE_HYDRATION, strings.navHydration, Icons.Rounded.WaterDrop, Icons.Outlined.WaterDrop),
    NavDestinationItem(ROUTE_VITALS, strings.navVitals, Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder),
    NavDestinationItem(ROUTE_WATCHES, strings.navWatches, Icons.Rounded.Watch, Icons.Outlined.Watch),
    NavDestinationItem(ROUTE_PROFILE, strings.navProfile, Icons.Rounded.Person, Icons.Outlined.Person),
  )

  fun navigateTab(route: String) {
    navController.navigate(route) {
      popUpTo(ROUTE_OVERVIEW) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val isOnboarded = (dashboardState as? UiState.Success<WellnessDashboardSnapshot>)?.data?.profile?.onboardingCompleted == true

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useRail && isOnboarded) {
          NavigationBar(windowInsets = WindowInsets.navigationBars) {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.testTag("nav_${item.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail && isOnboarded) {
          NavigationRail {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTab(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = { Text(item.label, maxLines = 1) },
                modifier = Modifier.testTag("nav_rail_${item.route}"),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          when (val state = dashboardState) {
            is UiState.Loading -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            }
            is UiState.Error -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                  text = state.message,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.padding(24.dp),
                )
              }
            }
            is UiState.Success<WellnessDashboardSnapshot> -> {
              val snapshot = state.data
              if (!snapshot.profile.onboardingCompleted) {
                OnboardingScreen(
                  profile = snapshot.profile,
                  selectedLanguage = selectedLanguage,
                  onSelectLanguage = viewModel::setLanguage,
                  onCompleteOnboarding = { name, lang, gender, heightCm, weightKg, lifestyle, workIntensity ->
                    viewModel.completeOnboarding(
                      displayName = name,
                      language = lang,
                      gender = gender,
                      heightCm = heightCm,
                      weightKg = weightKg,
                      lifestyle = lifestyle,
                      workIntensity = workIntensity,
                      successNotice = strings.goalsSavedMessage,
                    )
                  },
                )
              } else {
                NavHost(
                  navController = navController,
                  startDestination = ROUTE_OVERVIEW,
                  modifier = Modifier.fillMaxSize(),
                ) {
                  composable(ROUTE_OVERVIEW) {
                    DashboardScreen(
                      snapshot = snapshot,
                      liveTelemetry = liveTelemetry,
                      selectedLanguage = selectedLanguage,
                      onSelectLanguage = viewModel::setLanguage,
                      onQuickHydration = { amount ->
                        viewModel.logHydration(amount, com.example.data.model.DrinkType.WATER)
                      },
                      onSyncAll = {
                        viewModel.syncHealthConnect(strings.syncSuccessMessage)
                      },
                      onNavigateHydration = { navigateTab(ROUTE_HYDRATION) },
                      onNavigateVitals = { navigateTab(ROUTE_VITALS) },
                      onNavigateWatches = { navigateTab(ROUTE_WATCHES) },
                    )
                  }
                  composable(ROUTE_HYDRATION) {
                    HydrationScreen(
                      snapshot = snapshot,
                      onLogDrink = { amount, type -> viewModel.logHydration(amount, type) },
                      onDeleteLog = viewModel::deleteHydrationLog,
                    )
                  }
                  composable(ROUTE_VITALS) {
                    VitalsScreen(
                      snapshot = snapshot,
                      liveTelemetry = liveTelemetry,
                      onRecordHeartRate = viewModel::recordHeartRate,
                      onSaveSleepSession = viewModel::saveSleepSession,
                      onNavigateWatches = { navigateTab(ROUTE_WATCHES) },
                    )
                  }
                  composable(ROUTE_WATCHES) {
                    WatchHubScreen(
                      snapshot = snapshot,
                      isBleScanning = isBleScanning,
                      bluetoothHardwareAvailable = viewModel.bleManager.isBluetoothHardwareAvailable(),
                      discoveredWatches = discoveredWatches,
                      liveTelemetry = liveTelemetry,
                      healthConnectManager = viewModel.healthConnectManager,
                      healthConnectAuthorized = healthConnectAuthorized,
                      isSyncingHealthConnect = isSyncingHealthConnect,
                      onStartBleScan = viewModel::startBleScan,
                      onStopBleScan = viewModel::stopBleScan,
                      onConnectWatch = viewModel::connectToDiscoveredWatch,
                      onDisconnectWatch = viewModel::disconnectWatch,
                      onSaveManualWatch = viewModel::saveManualWatchDevice,
                      onDeleteWatch = viewModel::deleteWatchDevice,
                      onRefreshHealthConnect = viewModel::refreshHealthConnectStatus,
                      onSyncHealthConnect = { viewModel.syncHealthConnect(strings.syncSuccessMessage) },
                      onPullApiTelemetry = { url -> viewModel.pullWatchApiTelemetry(url, strings.syncSuccessMessage) },
                      onImportJsonTelemetry = { json -> viewModel.importCrossOsTelemetryJson(json, strings.syncSuccessMessage) },
                    )
                  }
                  composable(ROUTE_PROFILE) {
                    ProfileSettingsScreen(
                      snapshot = snapshot,
                      selectedLanguage = selectedLanguage,
                      onSelectLanguage = viewModel::setLanguage,
                      onSaveGoals = { name, lang, gender, height, weight, lifestyle, work, water, steps, sleepMin, hr ->
                        viewModel.updateProfileGoals(
                          displayName = name,
                          language = lang,
                          gender = gender,
                          heightCm = height,
                          weightKg = weight,
                          lifestyle = lifestyle,
                          workIntensity = work,
                          dailyWaterGoalMl = water,
                          dailyStepsGoal = steps,
                          dailySleepGoalMinutes = sleepMin,
                          restingHeartRateTarget = hr,
                          successNotice = strings.goalsSavedMessage,
                        )
                      },
                      onSignOut = {
                        signOut(credentialManager = credentialManager, scope = scope)
                      },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
