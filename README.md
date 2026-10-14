# JackCompass

以 **Kotlin + Jetpack Compose** 開發的 Android 工具箱 app，收納 **指南針**、**水平儀** 與 **GPS** 三項工具。

> ### ⚠️ 專案狀態：骨架階段
> 三個畫面目前都只是佔位符（顯示「尚未實作」），**尚未有任何感測器或定位的實作**。本專案目前無法作為可用的工具使用。

## 設計參考

![JackCompass UI prototype](spec/prototype.jpg)

`spec/prototype.jpg` 是目標介面的設計參考圖，**尚未實作**。實際版面與本檔描述的現況有出入（例如設計圖為四個分頁、指南針與水平儀同屏），差異細節記錄於 [`AGENTS.md`](AGENTS.md#原型落差)。

## 技術組合

| 項目 | 版本 |
|---|---|
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 |
| Gradle | 9.6.0 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 24 |
| JDK | 17 |
| UI 套件 | Jetpack Compose + Material 3 |
| 導覽 | Navigation Compose 2.10.2 |

> AGP 9.0 起 Kotlin 為**內建支援**，建置腳本刻意**不套用** `org.jetbrains.kotlin.android` plugin。網路上多數教學仍會套用，照抄會導致建置失敗。

## 環境需求

- **JDK 17**（AGP 9.4 的最低與預設版本）
- **Android SDK**，需 platform 37 與 build-tools 36.0.0 以上
- **Android Studio**（選用；AGP 9.0 起需 Otter 3 Feature Drop 或更新版本）

## 建置與執行

```powershell
.\gradlew.bat assembleDebug
```

產物：`app/build/outputs/apk/debug/app-debug.apk`

其他常用指令：

```powershell
.\gradlew.bat installDebug   # 安裝至已連線的裝置或 emulator
.\gradlew.bat lint           # 靜態檢查
```

> **請務必使用 `.\gradlew.bat`，不要使用 PATH 上的 `gradle`。**
> 環境中 PATH 上的 Gradle 為 8.1.1，不支援 AGP 9.4 所需的 Gradle 9.6，會在載入 plugin 階段直接失敗。

## 專案結構

```
app/src/main/java/com/wenhsiaoyi/jackcompass/
├── MainActivity.kt               單一 Activity 進入點
└── ui/
    ├── JackCompassApp.kt         Scaffold + 底部導覽列 + NavHost
    ├── theme/                    Material 3 主題與配色
    ├── compass/CompassScreen.kt  指南針畫面（佔位符）
    ├── level/LevelScreen.kt      水平儀畫面（佔位符）
    └── gps/GpsScreen.kt          GPS 畫面（佔位符）
```

原始碼置於 `src/main/java/`（AGP 內建 Kotlin 的預設路徑，同時相容 Java 檔案）。
新增工具分頁時，在 `ui/JackCompassApp.kt` 的 `ToolDestination` enum 增加一筆即可。

## 預計功能

| 工具 | 感測器／資料來源 | 備註 |
|---|---|---|
| 指南針 | `TYPE_ROTATION_VECTOR` | 需處理磁偏角與低通濾波 |
| 水平儀 | `TYPE_ACCELEROMETER` | 預計分「水平儀」與「傾角儀」兩種模式 |
| GPS | Fused Location 或 `LocationManager` | 僅顯示緯度／經度／海拔／速度數值，不含地圖 |

## 開發注意事項

環境陷阱與已驗證的建置限制完整記錄於 [`AGENTS.md`](AGENTS.md)，其中幾點最容易踩到：

- Gradle 必須透過 wrapper 執行
- `JAVA_HOME`（JDK 17）與 PATH 上的 `java`（JDK 25）版本不同，**不可**用 `java -version` 判斷 build 實際使用的 JDK
- `local.properties` 內含機器專屬的 `sdk.dir`，已被 `.gitignore` 排除，**不可提交**
- `androidx.compose.material` 的 icon artifacts 已於 1.7.8 停止更新且不再隨 Compose BOM 帶入，本專案刻意不依賴
- 感測器於 manifest 中宣告為 `required="false"`，但 `ACCESS_FINE_LOCATION` 會讓 AGP 自動加入 `uses-feature android.hardware.location required="true"`，導致無定位硬體的裝置無法安裝

## License

尚未授權。
