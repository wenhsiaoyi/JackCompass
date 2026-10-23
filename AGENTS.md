# AGENTS.md

## Repo 現況

Kotlin + Jetpack Compose 寫的 Android 工具箱 App：**指南針 / 水平儀**，指南針並附經緯度（GPS）讀值。
`0e2f092` 當時只有 `README.md` 與 GitHub 的 Java 版 `.gitignore`；目前是一個可建置、可安裝的專案。

**指南針、水平儀與經緯度都已實作**（感測器／定位 → `sensor/` 的 Flow 層 → Compose `Canvas` 盤面）。
畫面是 2 個分頁（指南針／水平儀）＋ 1 個設定頁（右上齒輪進入：語系切換、贊助入口、廣告同意選項）。
經緯度**不是**獨立分頁：它是指南針畫面上、大字度數與盤面之間的一列小字，裝置沒有定位硬體時整列不出現。
`spec/prototype.jpg` 是設計參考圖，盤面已照它做出固定深色＋橘／綠的樣式；其餘出入見下方「原型落差」。

## 工作流程（`.agents/` 的 skill）

`.agents/<name>/SKILL.md` 是每個 skill 的規則正本；**本節只是導覽指標**，說明什麼情況該去讀哪一份。
規則要改就改 SKILL.md，不要在本檔放第二份。
專案這層的 `.agents/<name>/` 不會出現在 skill 清單（`~/.agents/skills/<name>/` 才會），要用 `read` 開檔。

主線：

```text
含糊的想法 → grill-with-docs → to-spec → to-tickets → implement-spec → code-review → pr
大到一個 session 裝不下的工程 → wayfinder 先畫地圖，再回到主線
每個 ticket 的內部 → tdd
事後 → retro
```

| 情況 | Skill |
|---|---|
| 第一次用這套 skill 之前：設定 tracker、標籤、文件佈局 | `setup-matt-pocock-skills` |
| 計畫／設計還含糊，要問到鋒利（順手產出 ADR 與 glossary） | `grill-with-docs` |
| 工程大到一個 session 吃不完：在 tracker 上畫「地圖」，一次解一張決策 ticket | `wayfinder` |
| 對話已經足夠，要合成規格（不訪談）並發到 tracker | `to-spec` |
| 規格要切成可動工的 tracer bullet ticket，標好 blocking edge | `to-tickets` |
| 規格與 ticket 都齊了，要併入 integration branch | `implement-spec` |
| 每個 ticket 的實作方式：紅 → 綠 | `tdd` |
| 收工前／合併前的兩軸審查（Standards / Spec） | `code-review` |
| 要寫 PR body | `pr` |
| 回顧 session，改進 agent 的環境 | `retro` |
| 要寫 skill、`AGENTS.md`、`CLAUDE.md`，或任何給 agent 讀的文件 | `writing-for-agents` |

貫穿所有流程的規則：

- **接縫（seam）**：測試只寫在使用者確認過的公開接縫上；先談定接縫清單，再寫紅的測試。
- **前線（frontier）**：ticket 是 task graph、不是步驟清單；只拿 blockers 全清的那些，其餘等。
- **指標（context pointer）**：subagent 之間靠 spec、ticket、研究筆記、commit 的指標溝通，不重述內容。
- **證據先於宣稱**：講「完成／會過」之前，先把指令跑過，並貼出實際輸出。
- **中文用詞**：本 repo 的台灣正體中文（本檔、`README.md`、`values-zh-rTW/` 文案、程式碼註解）以
  `.agents/tw-translate/` 的詞彙表與風格指南為準；改中文之前先讀 `SKILL.md`，寫完把動過的句子掃過詞彙表。
  （`values-zh-rCN/` 是刻意的簡體語系檔，不在這條規則內。）
- **規格與 ticket 不放檔案路徑與程式碼片段**（會過期）；例外是 prototype 產出、真的承載決策的片段。
- **`disable-model-invocation: true` 的 skill 由使用者點名才跑**（`setup-matt-pocock-skills`、`wayfinder`、
  `grill-with-docs`、`to-spec`、`to-tickets`、`implement-spec`、`retro`），agent 不自己開。

### 本專案對這些 skill 的現況落差

- **`GLOSSARY.md` 與 `docs/adr/` 還不存在**：不存在就靜默略過，等真的有術語被定出來再建
  （見 `docs/agents/domain.md`）。
- **測試有兩條純函式接縫**：`app/src/test/` 只有 `sensor/`（`SensorMathTest.kt`、`LocationDisplayTest.kt`）
  與 `ads/`（`BannerPolicyTest.kt`、`SponsorPolicyTest.kt`）的純函式測試，都是 JVM 可跑，也是 `tdd` 目前的兩條接縫；
  專案**沒有** `androidTest/` source set，`sensor/` 的 Flow 層、`ads/` 碰 SDK 的那一截與 Compose 畫面都還沒測試。
- **部分被引用的同伴 skill 還不存在**：`grill-with-docs` 與 `wayfinder` 要呼叫的
  `grilling`／`domain-modeling`／`research`／`prototype`、`tdd` 指向的 `codebase-design`，在 `.agents/`、
  `~/.agents/skills/`、`~/.claude/skills/` 都找不到。碰到就先告訴使用者缺哪一個，
  然後用對話把那一步直接做完（把題目問清楚、自己寫研究筆記）。
- **2 個外掛 skill 不落在本專案**：`frontend-design` 與 `ui-design` 針對 web／iOS（`ui-design` 的 `/uizze`
  子命令與 scripts 亦然），本專案 UI 是 Android Compose，只取其中的美感與文案原則；
  `kotlin-tooling-native-build-performance` 針對 KMP + iOS 的建置變慢，本專案 Android-only、無 KMP 與 iOS target。

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: one `GLOSSARY.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## 文件

- `docs/AGENTS.html`：本檔的網頁版快照（深色、左側目錄），給人讀的。**改完本檔要重新產生它**，它不會自己跟著更新。

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

其他相依套件：`androidx.activity:activity-compose:1.13.0`、`androidx.navigation:navigation-compose:2.10.2`、
`androidx.appcompat:appcompat:1.8.0`（語系切換）、`kotlinx-coroutines-android:1.10.2`；測試用 `junit:4.13.2`；
廣告用 GMA Next-Gen SDK `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0`
與 UMP `com.google.android.ump:user-messaging-platform:4.0.0`。

**`applicationId` 是 `com.hywen.app.jackcompass`，但 `namespace` 與原始碼目錄仍是 `com.wenhsiaoyi.jackcompass`**
——這是刻意的，不要把它們「修正」成一致。

## 常用指令

```powershell
.\gradlew.bat assembleDebug        # 產出 app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat installDebug         # 裝到已連接的裝置／emulator
.\gradlew.bat lint                 # 靜態檢查
.\gradlew.bat test                 # JVM 單元測試（sensor/ 與 ads/ 的純函式）
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
- **`GRADLE_USER_HOME=E:\gradle-home`（把 Gradle 大檔放在 repo 外）在 git-bash 裡一定要加雙引號並改用斜線**：
  `GRADLE_USER_HOME="E:/gradle-home"`。不加引號時反斜線會被吃掉，變成 `E:gradle-home` 相對路徑，
  實測在 repo 內長出一個 1.1 GB 的 `gradle-home/`。
- AGP 在第一次建置時會**自動下載並安裝缺少的 SDK platform**
  （本專案已讓它裝了 `android-37.0`）。這是正常行為，不是錯誤。
- `kotlinc` 未安裝，一切編譯都走 Gradle。

## AGP 9 的 2 個大地雷

1. **不要套 `org.jetbrains.kotlin.android` plugin。** AGP 9.0 起 Kotlin 是內建支援，
   套了會出現 `Cannot add extension with name 'kotlin'` 之類的錯誤。
   網路上幾乎所有教學都還在套——不要照抄。
   （真的需要退回舊寫法才加 `android.builtInKotlin=false`，但 AGP 10 起失效。）
2. **`androidx.compose.material:material-icons-core` / `-extended` 已停在 1.7.8**
   （2025-02 後不再更新，也不再隨 Compose BOM 帶入）。
   本專案因此**刻意不依賴 icon pack**，底部導覽列目前只有文字標籤。
   之後要加 icon，請自己用 `ImageVector` 或 drawable 畫，不要把已停止更新的 artifact 加回來。

## 架構

- 單一 `MainActivity`（必須是 `AppCompatActivity`，`AppCompatDelegate` 的語系切換才生效）
- `ui/JackCompassApp.kt`：`Scaffold` + 底部 `NavigationBar` + Navigation Compose `NavHost`，
  2 個分頁由 private enum `ToolDestination` 驅動——**新增工具在這裡加一筆即可**；
  設定頁不在分頁列上，由右上齒輪進入
- 2 個工具畫面各自獨立：`ui/compass/`、`ui/level/`；設定頁在 `ui/settings/`
- `sensor/` 是兩個功能共用的感測器與定位層，一律拆成「純函式 ＋ Flow 包裝」兩截：
  `SensorMath.kt`／`LocationMath.kt` 是純函式換算（無 Android 相依、JVM 可測），
  `SensorFlows.kt`（`SensorManager`）／`LocationFlow.kt`（`LocationManager`）把系統回呼包成 `Flow`
  ——進畫面才註冊、離開自動解除，濾波器每個收集者各一份
- `ads/` 是廣告與贊助層，同樣拆成兩截：
  `BannerPolicy.kt`／`SponsorPolicy.kt` 是**純函式**的狀態機（無 Android 相依、JVM 可測，秒數與階段判斷都在這裡），
  `AdMobBanner.kt`／`AdMobInterstitial.kt`／`AdBootstrap.kt`／`AdSession.kt` 是唯一接觸 SDK 的一截；
  `AdSession` 把兩個 policy 的階段鏡射進 `mutableStateOf` 讓 Compose 觀察，並用 `AdSession.ensure()`
  做成 process 層單例——`AppCompatDelegate.setApplicationLocales` 會重建 Activity，廣告進度不能跟著被重置。
  橫幅每次執行只載入一次、顯示 30 秒後收起；插屏只從設定頁的贊助入口播，不自動播。
  任何廣告請求之前都要先過 UMP 同意鏈（`runConsentChain`），閘門只認 `canRequestAds()`。
  實測（2026-10-23 實機）使用者按「Do not consent」時它**仍回 true**——Google 退回非個人化廣告，
  所以拒絕後橫幅與贊助列照樣出現；**已裁決維持這個行為**，AdMob 後台不要改成「拒絕時不投放廣告」。
  `canRequestAds()` 回 false 時橫幅不出現、贊助列隱藏，但「調整廣告同意選項」那一列**永遠可見**。
- `ui/theme/`：Material 3 + API 31 以上動態取色；盤面另用固定深色色板（`DialScreen`／`CompassOrange`／`LevelGreen`…），刻意不跟動態取色
- 語系檔：`values/`（英文，預設）、`values-zh-rTW/`、`values-zh-rCN/`；系統 App 語言清單在 `res/xml/locales_config.xml`
- 原始碼放在 **`app/src/main/java/`**（AGP 內建 Kotlin 的預設路徑，同時相容 Java 檔案）
- Manifest 已宣告定位權限與 `VIBRATE`（指南針刻度震動）；加速計、指南針感測器與兩個 location
  `uses-feature` 全為 `required="false"`

### 感測器與定位

- 指南針（已實作）：`TYPE_ROTATION_VECTOR`，低通濾波後顯示磁北方位，**未做磁偏角修正**
- 水平儀（已實作）：`TYPE_ACCELEROMETER`，HORIZONTAL／VERTICAL 2 種模式
- 經緯度（已實作）：`LocationManager`（框架內建，刻意**不**加 Fused Location 相依），
  在大字度數與盤面之間顯示一列緯度＋經度（南／北、東／西依正負號換成方位字，小數點跟著語系走）；
  未核准權限或還沒定位時顯示 `--.----°` 佔位，裝置沒有定位硬體時整列隱藏。
  **只有經緯度，不含海拔與速度，也沒有地圖**

## 原型落差

指 `spec/prototype.jpg`。盤面樣式（固定深色＋橘／綠）與水平儀的 HORIZONTAL / VERTICAL 切換已照圖實作；
以下是還對不上的地方，改動前先跟使用者確認：

- prototype 是 **4 個分頁**（Compass / Level / Map / Settings），現況是 2 個分頁＋由齒輪進入的設定頁，且沒有 Map
- prototype 把**指南針與水平儀放在同一個畫面**（中間是羅盤盤面），現況是 2 個獨立分頁
- prototype 在**羅盤畫面上直接顯示** `ELEVATION 124m · 24.8781°N, 121.0544°E`；
  現況的羅盤畫面有經緯度那一列（照語系排版，不是 prototype 的英文格式），但**沒有海拔**
- prototype 的文案是英文；現況預設英文，另外備有 zh-TW／zh-CN 語系檔

## 已知事項

- `ACCESS_FINE_LOCATION` 會讓 AGP 自動加入 `uses-feature android.hardware.location required="true"`，
  等於**沒有定位硬體的裝置無法安裝**、「無定位就隱藏經緯度」那條路徑永遠走不到。
  Manifest 已明寫兩個 location `uses-feature` 為 `required="false"` 解掉
  （用 `aapt2 dump badging app\build\outputs\apk\debug\app-debug.apk` 驗證產出是 `uses-feature-not-required`）。
- `android.location.LocationListener` 的 `onStatusChanged`／`onProviderEnabled`／`onProviderDisabled`
  到 API 30 才變成 default 方法。Kotlin 用 lambda／SAM 轉換只會實作 `onLocationChanged`，
  在 API 24–29 上框架一回呼就 `AbstractMethodError`。`LocationFlow.kt` 因此用明確的
  `object : LocationListener` 把四個方法全部補齊——**這不是冗餘的程式碼，不要精簡成 lambda**。
- `.\gradlew.bat lint` 目前會失敗，3 個 error 都是經緯度之前就存在的問題、尚未處理：
  指南針刻度震動用的 `VibrationEffect.createOneShot`／`Vibrator.vibrate` 要 API 26，而 minSdk 是 24
  （API 24/25 上按到震動會當），另外 `app_name` 未翻譯成 zh。
- 專案沒有設定任何 branch / PR / 發布流程，沿用使用者預設。
- **開發期間不得點自己的廣告。** AdMob 會把開發者自己點自己廣告判成無效流量，嚴重者停權；
  程式碼與資源檔裡只允許 Google 官方測試 ID，正式 ID 只放在 `app/src/main/res/values/ads.xml`，
  debug 構型由 `app/src/debug/res/values/ads.xml` 覆寫成測試 ID＋EEA 除錯地區。
  廣告單元 ID 不是機密，可以 commit。
- **UMP 4.0 有兩個不會報錯的坑**（實測踩過，兩邊都讓同意表單永遠不出現）：
  它只從 manifest 的 `com.google.android.gms.ads.APPLICATION_ID` 讀 App ID，Next-Gen
  `InitializationConfig.Builder(appId)` 不會餵給它；而 EEA 除錯地區只認
  `addTestDeviceHashedId` 清單內的裝置，那組 32 碼大寫 hex 是 Google 算的，**用 `ANDROID_ID`
  做 MD5／SHA-1／SHA-256 都對不出來**，只能從 logcat 抄進 `app/src/debug/res/values/ads.xml`。
  更糟的是這兩個坑**不擋廣告**：表單沒出現時 `canRequestAds()` 仍回 true、廣告照播，
  等於靜默違反同意政策。改動 `ads/` 之後一定要在實機看一次表單真的出現。
- 取 Activity 一律用 `androidx.activity.compose.LocalActivity`（`LocalActivity.current`，型別 `Activity?`）。
  它**不在 `compose-ui`**——`AndroidCompositionLocals_androidKt` 沒有 `getLocalActivity`，是
  `androidx.activity:activity-compose` 提供的；只查 `ui.aar` 會誤判成「本專案沒有」，這是踩過的坑。
  平台**沒有公開的 reduce-motion API**（`AccessibilityManager` 查無相關方法，android-33/36/37 都查過），
  「降低動態效果」靠 `Settings.Global` 的 `ANIMATOR_DURATION_SCALE`／`TRANSITION_ANIMATION_SCALE`
  是否歸零判定——網路教學裡的 `isReducedMotionSettingEnabled()` 不存在，`compose-ui` 那個
  `LocalAccessibilityManager...isReduceMotionEnabled()` 也沒有。
