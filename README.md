# Close Ties 🎲📞 When you are out of sight, you are out of heart —Turkish idiom

> **A minimalist, privacy-first, stochastic personal CRM for Android.**

Instead of rigid scheduled reminders (*"Call Mom every Sunday at 4:00 PM"*), **Close Ties** uses a weighted random lottery with dynamic cooldowns and native call log awareness to nudge you serendipitously to stay in touch with people who matter most.

---

## 🌟 Key Features & Privacy Guarantees:

- **100% Offline & Private:** Zero network permissions (`android.permission.INTERNET` is not requested). No telemetry, no tracking SDKs, no cloud servers. All data resides strictly in your local encrypted Room database.
- **Privacy-Friendly Calling:** No `CALL_PHONE` permission. Directing calls uses `Intent.ACTION_DIAL`, preserving full user autonomy and avoiding Play Store / F-Droid permission friction.
- **Serendipitous Weighted Lottery:** Contacts are arranged into a visual **5-tier Ladder (Shelves 1 to 5)** representing stake weights. Daily draws nudge you toward meaningful connections without feeling robotic.
- **Dynamic Cooldown Algorithm:** When a contact is drawn or called in real life ($\ge 45$ seconds), they enter a cooldown state and are excluded from future draws until the cooldown expires.
- **Native Call Log Reconciliation:** Reconciles phone call logs in the background. If you called a tracked contact in the last 24 hours, the daily quota is met and no annoying notifications are sent.
- **Minimalist Material 3 Design:** Refined monochrome / slate aesthetic with Material You dynamic color support, clean typography, and fast 1-tap shelf management.

---

## 📐 Mathematical Model

### 1. Ladder / Stakes Hierarchy

Contacts are placed on one of 5 visual shelves:
- **Shelf 5:** $S_i = 5$ stakes
- **Shelf 4:** $S_i = 4$ stakes
- **Shelf 3:** $S_i = 3$ stakes
- **Shelf 2:** $S_i = 2$ stakes
- **Shelf 1:** $S_i = 1$ stake

Let $S_{total} = \sum S_i$ be the sum of stakes across all eligible contacts in the pool. The expected frequency displayed on each contact card is:

$$\text{Expected Frequency (Days)} = \mathrm{round}\left(\frac{S_{total}}{S_i}\right)$$

### 2. Cooldown Algorithm

When a contact is drawn or when a completed phone call ($\ge 45$ seconds) is detected:

$$\text{Cooldown Days} = \max\left(1, \left\lfloor \frac{S_{total}}{4 \times S_i} \right\rfloor\right)$$

$$\text{cooldownUntilTimestamp} = \text{currentTimestamp} + (\text{Cooldown Days} \times 86,400,000\text{ ms})$$

While in cooldown ($\text{now} < \text{cooldownUntilTimestamp}$), the contact is temporarily removed from the lottery pool.

---

## 🏗️ Architecture & Tech Stack

- **Language:** Kotlin 2.3+
- **UI:** Jetpack Compose + Material 3
- **Database:** Room 2.8+ with KSP (Kotlin Symbol Processing)
- **Background Scheduling:** WorkManager 2.11+
- **Architecture:** Clean Architecture + MVI/MVVM (Repository pattern, UseCases, StateFlow)
- **Toolchain:** Android Gradle Plugin 9.0+, Gradle 9.1, targetSdk 36, minSdk 26

```
app/src/main/java/com/closeties/app/
├── CloseTiesApplication.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── ContactDao.kt
│   │   └── ContactEntity.kt
│   └── repository/
│       ├── CallLogRepository.kt
│       └── ContactRepository.kt
├── domain/
│   ├── model/
│   │   └── TrackedContact.kt
│   └── usecase/
│       ├── CalculateCooldownUseCase.kt
│       ├── RunLotteryDrawUseCase.kt
│       └── SyncCallLogsUseCase.kt
├── receiver/
│   └── NotificationActionReceiver.kt
├── ui/
│   ├── MainActivity.kt
│   ├── components/
│   │   ├── ContactCard.kt
│   │   └── ShelfView.kt
│   ├── ladder/
│   │   ├── LadderScreen.kt
│   │   ├── LadderUiState.kt
│   │   └── LadderViewModel.kt
│   ├── picker/
│   │   ├── ContactPickerBottomSheet.kt
│   │   └── ContactPickerViewModel.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
└── worker/
    └── DailyLotteryWorker.kt
```

---

## 📦 Getting the APK (Artifact)

### Option 1: Download from GitHub Actions (No build tools required)
1. Go to the **[Actions tab](https://github.com/medre22932-png/CloseTies/actions)** of the repository.
2. Click on the latest workflow run under **Build APK**.
3. Scroll down to the **Artifacts** section at the bottom of the page and click **`app-debug`** to download the pre-compiled APK zip.

---

### Option 2: Clone & Build from Source

#### Prerequisites
- **Git**
- **JDK 17** (e.g. OpenJDK 17 or Azul Zulu 17)
- **Android SDK** (API 36, available via Android Studio or Android Command-line Tools)

#### 1. Clone the Repository
```bash
git clone https://github.com/medre22932-png/CloseTies.git
cd CloseTies
```

#### 2. Build the Debug APK
The repository includes the Gradle Wrapper (`gradlew`), so manual Gradle installation is not required.

- **macOS / Linux:**
  ```bash
  chmod +x gradlew
  ./gradlew assembleDebug
  ```

- **Windows:**
  ```cmd
  gradlew.bat assembleDebug
  ```

#### 3. Locate the Artifact
After `BUILD SUCCESSFUL`, your APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

#### (Optional) Build Release APK / App Bundle
- **Release APK:**
  ```bash
  ./gradlew assembleRelease
  # Output: app/build/outputs/apk/release/app-release-unsigned.apk
  ```
- **Android App Bundle (.aab for Google Play):**
  ```bash
  ./gradlew bundleRelease
  # Output: app/build/outputs/bundle/release/app-release.aab
  ```

---

## 🧪 Testing

### Running Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## 📄 License
Open source under the [Apache License 2.0](LICENSE).
