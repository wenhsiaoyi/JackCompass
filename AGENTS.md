# AGENTS.md

## Repo 現況

Kotlin + Jetpack Compose 寫的 Android 工具箱 app：**指南針 / 水平儀 / GPS**。
`0e2f092` 當時只有 `README.md` 與 GitHub 的 Java 版 `.gitignore`；目前已有一份可建置的空專案。

**三個畫面目前都只是佔位符**（顯示「尚未實作」），沒有任何感測器或定位的實作。
`spec/prototype.jpg` 是設計參考圖，**尚未實作**，而且它與目前的 scaffold 有出入（見下方「原型落差」）。

## 版本組合（已實際建置驗證）

| 項目 | 版本 | 位置 |
|---|---|---|
| AGP | 9.4.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.21（Compose compiler plugin） | 同上 |
| Compose BOM | 2026.09.00 | 同上 |
| Gradle | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| compileSdk / targetSdk | 37 | `app/build.gradle.kts` |
| minSdk | 24 | 同上 |
| JDK | 17 | `compileOptions` |

其他相依：`androidx.activity:activity-compose:1.13.0`、`androidx.navigation:navigation-compose:2.10.2`。

## 常用指令

```powershell
.\gradlew.bat assembleDebug        # 產出 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat installDebug         # 裝到已連接的裝置／emulator
.\gradlew.bat lint                 # 靜態檢查
.\gradlew.bat test                 # 目前沒有任何測試
```

**一律用 `.\gradlew.bat`，不要用 PATH 上的 `gradle`。** 見下方工具鏈說明。
第一次建置會下載 Gradle 9.6.0 與全部相依，實測約 12 分鐘；之後約 30 秒。

驗證 SDK 路徑：

```powershell
E:\Android\Sdk\build-tools\37.0.0\aapt2.exe dump badging app\build\outputs\apk\debug\app-debug.apk
```

## 工具鏈陷阱（皆已實測）

- **PATH 上的 `gradle` 是 8.1.1，完全不能用在本專案。** AGP 9.4 要求 Gradle 9.6，
  用全域那顆會在 plugin resolution 就失敗。只能用 wrapper。
- **JDK 分裂**：`JAVA_HOME=C:\Java\jdk-17`，但 `PATH` 上的 `java` 是
  `C:\Java\jdk-25.0.4.7-hotspot`。Gradle 走 `JAVA_HOME`（實測 Gradle 9.6 跑在 JVM 17），
  所以 **`java -version` 不能用來驗證 build 用的 JDK**。遇到不支援的 JDK 錯誤先懷疑這裡。
- **SDK 路徑**：`E:\Android\Sdk`，`ANDROID_HOME` 與 `ANDROID_SDK_ROOT` 都有設。
  `local.properties` 裡也寫了一份 `sdk.dir`。**`local.properties` 已被 gitignore，不要 commit。**
- **`adb` 不在 `PATH`**，要用的話走完整路徑 `E:\Android\Sdk\platform-tools\adb.exe`。
- AGP 在第一次建置時會**自動下載並安裝缺少的 SDK platform**
  （本專案已讓它裝了 `android-37.0`）。這是正常行為，不是錯誤。
- `kotlinc` 未安裝，一切編譯都走 Gradle。

## AGP 9 的兩個大坑

1. **不要套 `org.jetbrains.kotlin.android` plugin。** AGP 9.0 起 Kotlin 是內建支援，
   套了會出現 `Cannot add extension with name 'kotlin'` 之類的錯誤。
   網路上幾乎所有教學都還在套 —— 不要照抄。
   （真的需要退回舊寫法才加 `android.builtInKotlin=false`，但 AGP 10 起失效。）
2. **`androidx.compose.material:material-icons-core` / `-extended` 已停在 1.7.8**
   （2025-02 後不再更新，也不再隨 Compose BOM 帶入）。
   本專案因此**刻意不依賴 icon pack**，底部導覽列目前只有文字標籤。
   之後要加 icon，請自己用 `ImageVector` 或 drawable 畫，不要把已停更的 artifact 加回來。

## 架構

- 單一 `MainActivity`（`ui/theme/Theme.kt` 套用 Material 3 + 動態取色）
- `ui/JackCompassApp.kt`：`Scaffold` + 底部 `NavigationBar` + Navigation Compose `NavHost`，
  三個分頁由 private enum `ToolDestination` 驅動 —— **新增工具在這裡加一筆即可**
- 三個畫面各自獨立：`ui/compass/`、`ui/level/`、`ui/gps/`
- 原始碼放在 **`app/src/main/java/`**（AGP 內建 Kotlin 的預設路徑，同時相容 Java 檔案）
- Manifest 已宣告定位權限，感測器為 `required="false"`

### 預計要接的感測器

- 指南針 → `TYPE_ROTATION_VECTOR`（或 `TYPE_MAGNETIC_FIELD` + `TYPE_ACCELEROMETER`），需低通濾波與磁偏角處理
- 水平儀 → `TYPE_ACCELEROMETER`，預計分「水平儀」與「傾角儀」兩種模式
- GPS → Fused Location 或 `LocationManager`，只在畫面上顯示數值（緯度／經度／海拔／速度），**不含地圖**

`SensorManager` 的 callback 目前沒有封裝層，之後三個功能共用的那一層還沒寫。

## 原型落差

指 `spec/prototype.jpg`。該圖目前**沒有實作**，且與現況不一致，改動前先跟使用者確認：

- prototype 是 **4 個分頁**（Compass / Level / Map / Settings），現況是 3 個且無 Settings
- prototype 把**指南針與水平儀放在同一個畫面**（中間是羅盤盤面），現況是兩個獨立分頁
- prototype 的水平儀有 **VERTICAL / HORIZONTAL** 切換
- prototype 在**羅盤畫面上直接顯示** `ELEVATION 124m · 24.8781°N, 121.0544°E`
- prototype 是深色為主 + 橘色指針 + 綠色氣泡，文案為英文；現況是跟隨系統取色 + 中文標籤

## 已知事項

- `ACCESS_FINE_LOCATION` 會讓 AGP 自動加入 `uses-feature android.hardware.location required="true"`，
  等於**沒有定位硬體的裝置無法安裝**。若要純羅盤裝置也能裝，需改成 `required="false"`。
- 專案沒有設定任何 branch / PR / 發布流程，沿用使用者預設。
