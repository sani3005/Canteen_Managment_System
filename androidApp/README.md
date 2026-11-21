# Canteen Student App — VS Code Android Kotlin Starter

This is an **Android Kotlin + Jetpack Compose** Student Module starter. It opens directly in VS Code; Android Studio is not required.

## Included features
- Today's menu (local sample data)
- Add-to-cart, quantity controls, totals
- Order Now / Pre-Order selection
- Parcel / Eat-in selection
- Local demonstration orders and order history

## Not yet implemented
- Firebase / shared owner database, authentication, real-time menu, order statuses
- Actual advance payments (pre-orders are intentionally disabled until payment verification exists)
- Secure OTP generation and owner-side verification
- Durable persistence of local demo orders

**Do not treat demo orders as real orders.** No order is sent to the canteen.

## Requirements
- Windows 11, VS Code, JDK 21, Android SDK platform 36 and build-tools 36.0.0
- Internet for first build (downloads Gradle and Maven dependencies)
- PowerShell with access to `Invoke-WebRequest`

## Build
1. Extract ZIP anywhere, e.g. `C:\Users\ADMIN\CanteenApp\StudentApp`.
2. Open the extracted **CanteenStudentApp** folder in VS Code.
3. Make sure `ANDROID_HOME` points to your Android SDK root. To locate SDK manager in PowerShell: `Get-Command sdkmanager | Select-Object -ExpandProperty Source`.
4. In VS Code PowerShell terminal run: `powershell -ExecutionPolicy Bypass -File .\setup-and-build.ps1`
5. After first build, use `.\gradlew.bat assembleDebug` for subsequent builds.
6. APK is at `app\build\outputs\apk\debug\app-debug.apk`.
7. With phone connected and USB debugging enabled: `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

The bootstrap script downloads a temporary Gradle distribution and generates the official Gradle Wrapper in your project. The ZIP does not contain a prebuilt APK or Gradle Wrapper JAR.

## Versions
Android Gradle Plugin 8.13.2; Gradle 8.13; Kotlin 2.0.21; compileSdk 36; Java target 17 (runs on JDK 21).

## Team integration plan
Replace local demo data with repository interfaces, shared Firestore/API contracts, server-verified payment, server-created one-time pickup OTP, and owner-only OTP verification. Never store OTP secrets or payment verification authority solely in the student app.
