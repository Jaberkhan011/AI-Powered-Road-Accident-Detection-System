# CrashGuard 🚨

**CrashGuard** is a real-time Android safety and accident detection system that leverages high-frequency sensor fusion, on-device machine learning (TensorFlow Lite), fused location tracking, and automated emergency dispatching to safeguard users on the road and on foot.

---

## 🚀 Key Features & Technical Architecture

### 1. ⚡ 50 Hz Multi-Sensor Fusion & Time-Series Pipeline
- **High-Rate Data Sampling**: Continuously samples Accelerometer and Gyroscope sensors at 50 Hz using Android `HIGH_SAMPLING_RATE_SENSORS`.
- **Temporal Linear Interpolation (`lerp`)**: Utilizes `TreeMap` time-indexed buffers to synchronize asynchronous sensor streams, performing linear interpolation across missing sample intervals.
- **8-D Feature Extraction**: Extracts an 8-dimensional feature vector per timestep:
  $$\text{Feature Vector} = [a_x, a_y, a_z, g_x, g_y, g_z, \|a\|, \|g\|]$$
- **Rolling Sliding Window Buffer**: Maintains a 100-sample sliding window (representing 2.0s of continuous motion data) with feature Z-score normalization ($Z = \frac{x - \mu}{\sigma}$) before model inference.

### 2. 🧠 On-Device TensorFlow Lite ML Inference
- **Quantized 3-Class Classification Model**: Runs an 8-bit quantized TensorFlow Lite model (`accident_3class_quant.tflite`) executing via `org.tensorflow:tensorflow-lite`.
- **Class Categorization**: Predicts 3 motion states: `Normal`, `Vehicle Accident`, and `Pedestrian Accident`.
- **Multi-Frame Temporal Confirmation**: Requires consecutive positive ML predictions across consecutive sliding windows to eliminate transient false positives (e.g., dropping a phone or sudden braking).
- **Sensor Heuristic Overrides**: Overwrites edge-case predictions with vehicle collision classifications if peak G-force exceeds ~6.6G ($\ge 65\text{ m/s}^2$) or angular velocity exceeds $8.0\text{ rad/s}$.

### 3. 🛡️ Resilient Background Safety Service
- **Android Foreground Service (`SafetyService`)**: Keeps protection active continuously with `foregroundServiceType="location"`.
- **Low-Latency WakeLock Integration**: Acquires a `PowerManager.PARTIAL_WAKE_LOCK` to ensure uninterrupted CPU processing during phone sleep.
- **Dedicated Worker Threading**: Offloads all signal processing and TFLite model execution onto a dedicated `HandlerThread` (`CrashDetectorThread`), preventing UI thread frame drops.

### 4. 🚨 Lockscreen Emergency Activity & Countdown
- **Keyguard & Lockscreen Bypass**: Uses `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and `KeyguardManager` APIs to pop an emergency activity directly over locked screens upon collision detection.
- **Adaptive Countdown Engine**: Displays a context-aware countdown (30s for pedestrian impact vs. 15s for vehicle collision) with smooth 50ms `CircularProgressIndicator` updates.
- **Manual Cancellation / Dispatch**: Enables victims to cancel false alerts before automated dispatch, or trigger instant SMS alerts with "Send Now".

### 5. 📍 Fused Location Tracking & SMS Dispatch
- **High-Accuracy Coordinates**: Queries Google Play Services `FusedLocationProviderClient` (`Priority.PRIORITY_HIGH_ACCURACY`) to acquire precise location fixes.
- **Intelligent Location Fallback**: Automatically queries Android `LocationManager` (GPS / Network providers) if network location is unavailable.
- **Automated SMS Dispatch**: Constructs emergency alert messages containing user details, impact severity, and clickable Google Maps location links (`https://maps.google.com/?q=lat,lon`), dispatched via `SmsManager.sendMultipartTextMessage`.

### 6. 📱 Quick-Control App Widgets
- **Home Screen Widgets (`ControlCenterWidget`, `ManualSosWidget`)**: Built using Android `AppWidgetProvider` and `RemoteViews`, allowing users to trigger one-tap Manual SOS signals or toggle protection states directly from their home screen.

---

## 🛠️ Tech Stack & Dependencies

| Category | Technology / Library | Version / Details |
| :--- | :--- | :--- |
| **Language** | Kotlin | 1.9+ |
| **Minimum SDK** | Android 8.0 (API 26) | `minSdk = 26` |
| **Target SDK** | Android 15 (API 36) | `targetSdk = 36` |
| **Machine Learning** | TensorFlow Lite | `2.16.1` (`org.tensorflow:tensorflow-lite`) |
| **Location Services** | Google Play Services Location | `21.1.0` (`play-services-location`) |
| **UI Architecture** | Material Design 3, AndroidX | AppCompat 1.7, Material 1.13, RecyclerView, ConstraintLayout |
| **Background Processing** | Android Foreground Service | `HandlerThread`, `WakeLock`, `PendingIntent`, `AppWidgetProvider` |
| **Data & Persistence** | SharedPreferences | Structured JSON storage for emergency contacts & alert history |

---

## 💼 Resume Bullet Points (Technical Highlights)

Looking to highlight this project on your software engineering resume? Here are bullet points detailing key technical accomplishments:

- **Engineered Real-Time Sensor Fusion & ML Pipeline**: Built an Android accident detection system in Kotlin using 50 Hz Accelerometer and Gyroscope data fusion, 8-dimensional feature extraction, Z-score normalization, and on-device TensorFlow Lite ML inference (`accident_3class_quant.tflite`).
- **Optimized Low-Latency Background Processing**: Offloaded time-series interpolation (`lerp`) and TFLite model execution onto a dedicated `HandlerThread` background worker, maintaining sub-10ms inference latency with 0 main thread UI frame drops.
- **Designed Resilient Foreground Service Architecture**: Implemented an Android `ForegroundService` with `PARTIAL_WAKE_LOCK` and fallback `AlarmManager` service restart mechanisms to guarantee background safety tracking across OS power optimizations.
- **Implemented Keyguard Emergency System Alert**: Developed a lockscreen emergency activity using `KeyguardManager` and `setShowWhenLocked` APIs featuring an adaptive 15s/30s visual countdown timer for user verification prior to dispatch.
- **Integrated Fused Location & Multi-Recipient Emergency SMS Engine**: Utilized Google Play Services `FusedLocationProviderClient` with `Priority.PRIORITY_HIGH_ACCURACY` and fallback `LocationManager` providers to automatically transmit emergency SMS alerts containing Google Maps location URLs via `SmsManager`.
- **Created Interactive Android App Widgets**: Built home screen `AppWidgetProvider` widgets with dynamic `RemoteViews` updates for instant one-tap Manual SOS signaling and real-time safety service state controls.

---

## 📁 Project Structure

```text
com.example.crashguard/
├── AccidentDetection.kt        # High-level accident detection callbacks
├── AuthManager.kt              # User authentication & safety setting preferences
├── EmergencyActivity.kt        # Lockscreen emergency countdown activity
├── HelperUtils.kt              # Utility extensions & helper methods
├── LocationHelper.kt           # Location retrieval helpers
├── LoginActivity.kt            # Login screen & session initialization
├── MainActivity.kt             # Navigation container & main fragment host
├── MfccExtractor.kt            # Signal extraction helper
├── SafetyService.kt            # Foreground service managing crash detection & SMS dispatch
├── SensorCrashDetector.kt      # 50 Hz Sensor fusion, Z-score normalization & TFLite inference
├── SmsHelper.kt                # Emergency SMS formatters & multipart SMS sender
├── addcontact/                 # Contact picker, JSON persistence & Adapter
├── alerthistory/               # Alert records, JSON persistence & History RecyclerView Adapter
├── fragments/                  # Home, Add Contact, Alert History, About, & More Options fragments
└── widgets/                    # ControlCenterWidget & ManualSosWidget AppWidget Providers
```

---

## ⚙️ Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer.
- Android device or emulator running API level 26 (Android 8.0) or higher.
- Physical Android device with SIM card (required for actual SMS alert testing).

### Setup Instructions
1. **Clone the Repository**:
   ```bash
   git clone https.github.com/Jaberkhan011/CrashGuard.git
   cd CrashGuard
   ```
2. **Open in Android Studio**:
   Open Android Studio and select **Open** -> Navigate to the `CrashGuard` folder.
3. **Gradle Sync & Build**:
   Allow Gradle to synchronize dependencies (`gradle sync`), then build the project:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Deploy to Device**:
   Connect your Android device via USB debugging and run the app from Android Studio or via CLI:
   ```bash
   ./gradlew installDebug
   ```

---

## 🔒 Permissions Required
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: High-accuracy location tracking.
- `SEND_SMS`: Emergency alert SMS transmission.
- `READ_CONTACTS`: Importing emergency contacts.
- `FOREGROUND_SERVICE`: Continuous background safety monitoring.
- `SYSTEM_ALERT_WINDOW` & `USE_FULL_SCREEN_INTENT`: Displaying emergency countdown over lockscreen.
- `HIGH_SAMPLING_RATE_SENSORS`: 50 Hz accelerometer & gyroscope sensor access.
