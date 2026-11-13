const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_v4_123";
const BOB_UID = "bob_v4_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user profile or health logs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("hydration_logs").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("heart_rate_readings").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sleep_sessions").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("activity_daily").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("watch_devices").get()
  );
});

test("Authenticated owner can create and update their UserProfile with valid V4/EN locale", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Marek K.",
      languageCode: "sk",
      gender: "male",
      heightCm: 180,
      weightKg: 76.5,
      lifestyleLevel: "active",
      workIntensity: "moderate_physical",
      onboardingCompleted: true,
      dailyWaterGoalMl: 3400,
      dailyStepsGoal: 12000,
      dailySleepGoalMinutes: 480,
      restingHeartRateTarget: 62,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    profileRef.update({
      languageCode: "cs",
      dailyWaterGoalMl: 3500,
      updatedAt: nowTs(),
    })
  );
});

test("Shadow update with ghost field or invalid languageCode fails on UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertFails(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Marek",
      languageCode: "de", // Not EN or V4
      gender: "male",
      heightCm: 180,
      weightKg: 75,
      lifestyleLevel: "recreational",
      workIntensity: "sedentary",
      onboardingCompleted: true,
      dailyWaterGoalMl: 2500,
      dailyStepsGoal: 10000,
      dailySleepGoalMinutes: 480,
      restingHeartRateTarget: 62,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertFails(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Marek",
      languageCode: "sk",
      gender: "male",
      heightCm: 180,
      weightKg: 75,
      lifestyleLevel: "recreational",
      workIntensity: "sedentary",
      onboardingCompleted: true,
      dailyWaterGoalMl: 2500,
      dailyStepsGoal: 10000,
      dailySleepGoalMinutes: 480,
      restingHeartRateTarget: 62,
      isAdmin: true, // Ghost field
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Cross-user isolation prevents Bob from reading or writing Alice's health records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hydration_logs")
      .doc("log_1")
      .set({
        userId: ALICE_UID,
        amountMl: 300,
        effectiveHydrationMl: 300,
        drinkType: "soup",
        dateKey: "2026-11-13",
        loggedAtMillis: 1794586949000,
        createdAt: nowTs(),
        updatedAt: nowTs(),
      })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hydration_logs")
      .doc("log_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hydration_logs")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hydration_logs")
      .doc("log_2")
      .set({
        userId: ALICE_UID,
        amountMl: 250,
        effectiveHydrationMl: 250,
        drinkType: "water",
        dateKey: "2026-11-13",
        loggedAtMillis: 1794586950000,
        createdAt: nowTs(),
        updatedAt: nowTs(),
      })
  );
});

test("Owner can write HeartRateReading, SleepSession, DailyActivity, and WatchDevice", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("heart_rate_readings")
      .doc("hr_1")
      .set({
        userId: ALICE_UID,
        bpm: 72,
        spo2Percent: 98,
        hrvMs: 54,
        zone: "resting",
        sourceDevice: "FitCloudPro GTS 4",
        sourceOs: "fitcloudpro",
        dateKey: "2026-11-13",
        recordedAtMillis: 1794586949000,
        createdAt: nowTs(),
      })
  );

  // HeartRateReading is append-only (updates forbidden)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("heart_rate_readings")
      .doc("hr_1")
      .update({ bpm: 99 })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sleep_sessions")
      .doc("sleep_1")
      .set({
        userId: ALICE_UID,
        dateKey: "2026-11-13",
        totalMinutes: 465,
        deepMinutes: 105,
        lightMinutes: 250,
        remMinutes: 95,
        awakeMinutes: 15,
        qualityScore: 88,
        avgBpm: 58,
        sourceDevice: "FitCloudPro GTS 4",
        startTimeMillis: 1794520000000,
        endTimeMillis: 1794547900000,
        createdAt: nowTs(),
        updatedAt: nowTs(),
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("activity_daily")
      .doc("2026-11-13")
      .set({
        userId: ALICE_UID,
        dateKey: "2026-11-13",
        steps: 8420,
        activeMinutes: 52,
        caloriesBurned: 410,
        distanceMeters: 6250,
        movePoints: 38,
        sourceOs: "health_connect",
        createdAt: nowTs(),
        updatedAt: nowTs(),
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("watch_devices")
      .doc("watch_1")
      .set({
        userId: ALICE_UID,
        name: "FitCloudPro Watch",
        macOrAddress: "FC:24:9A:11:44:B2",
        osType: "fitcloudpro",
        protocol: "ble_gatt",
        batteryPercent: 84,
        firmwareVersion: "2.4.1",
        realtimeHrEnabled: true,
        autoSyncEnabled: true,
        lastSyncMillis: 1794586949000,
        createdAt: nowTs(),
        updatedAt: nowTs(),
      })
  );
});
