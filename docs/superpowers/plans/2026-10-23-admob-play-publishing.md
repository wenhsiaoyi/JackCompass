# 廣告（AdMob）與 Google Play 上架 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 為 JackCompass 加上 AdMob 橫幅與「贊助」插屏（含同意閘門與感謝動畫），並把 `applicationId` 換成上架用的 `com.hywen.app.jackcompass`。

**Architecture:** 沿用 `sensor/` 的兩截式——`ads/` 底下 `BannerPolicy`／`SponsorPolicy` 是純 JVM 規則層（本專案第二條可自動化驗證的接縫），其餘檔案是唯一接觸廣告 SDK 的包裝層；UI 只讀包裝層暴露的 Compose `State`。

**Tech Stack:** Kotlin 2.4.21、AGP 9.4.1（內建 Kotlin）、Compose BOM 2026.09.00、Gradle 9.6.0 wrapper、GMA Next-Gen `ads-mobile-sdk` 1.5.0、`user-messaging-platform` 4.0.0、JUnit 4.13.2。

**Spec:** `docs/superpowers/specs/2026-10-23-admob-play-publishing-design.md`
**設計基準圖：** `spec/sponsor-flow-mock.svg`（四格：設定頁兩區塊／感謝動畫／橫幅倒數中／收起後盤面大小不變）

## Global Constraints

- 一律用 `.\gradlew.bat`，**絕不用** PATH 上的 `gradle`（8.1.1 會在 plugin resolution 就失敗）。
- **不要**套 `org.jetbrains.kotlin.android` plugin——AGP 9 內建 Kotlin，套了會 `Cannot add extension with name 'kotlin'`。
- `namespace` 維持 `com.wenhsiaoyi.jackcompass`，**只改 `applicationId`**；原始碼目錄與 12 個主檔、2 個測試檔都不搬。
- minSdk 24、compileSdk／targetSdk 37、JDK 17。`JAVA_HOME=C:\Java\jdk-17`，但 PATH 上的 `java` 是 JDK 25——**不要用 `java -version` 驗證建置 JDK**。
- **`GRADLE_USER_HOME=E:\gradle-home`**（不是預設的 `%USERPROFILE%\.gradle`）。要從快取挖 aar／jar 時一律用 `"$GRADLE_USER_HOME/caches/modules-2/files-2.1/…"`。
- `local.properties` 已 gitignore，**不可 commit**。
- 開發期間**只允許使用 Google 官方測試 ID**（debug 構型自動覆寫），不得點自己的真廣告：AdMob 會判無效流量，嚴重者停權。
- 中文文案依 `.agents/tw-translate/`（先讀 `SKILL.md`）：zh-TW 用「橫幅廣告」「插屏廣告」「設定」「資料」；品牌名 AdMob／Google 保留英文；中英間加半形空格；標點全形；介面文案不用行銷口吻。
- 底部導覽列**不加 icon**（`material-icons-*` 已停更，是專案刻意事項）。
- **`androidx.compose.ui.platform`（BOM 2026.09.00 → ui 1.12.1、material3 1.4.0）沒有 `LocalActivity`，也沒有 `LocalAccessibilityManager.current.isReduceMotionEnabled()`。** 兩者都以 `javap` 從快取的 `ui.aar`／`material3.aar` 查證過：前者的 `AndroidCompositionLocals_androidKt` 只導出 `getLocalContext`／`getLocalView`／`getLocalLifecycleOwner`…，後者的 `androidx.compose.ui.platform.AccessibilityManager` 介面只剩 `calculateRecommendedTimeoutMillis`。**取 Activity 一律用 `androidx.activity.compose.LocalActivity`**——本專案已經依賴 `activity-compose:1.13.0`，實測其 `classes.jar` 有 `androidx/activity/compose/LocalActivityKt.class`，`LocalActivity` 是 `ProvidableCompositionLocal<Activity?>`，預設值就是沿 `LocalContext` 的 `ContextWrapper` 鏈找上去，型別 `Activity?`。**取「降低動態效果」一律用 Task 8 的 `Settings.Global` 動畫縮放係數檢查——平台也沒有公開的 reduce-motion API**（`javap android-33／36／37.0／37.2` 的 `android.jar`，`AccessibilityManager` 查無任何 reduce／motion 方法，`Settings$Global`／`Settings$Secure` 也沒有 reduce motion 常數）。
  > **勘誤（Task 10 Step 8 查證）**：本條原本寫成「本專案的 Compose 沒有 `LocalActivity`，要自己沿 `ContextWrapper` 找」，並在 Task 5 定義了 `fun Context.asActivity(): Activity?`。那是**只查了 `ui.aar`、漏查 `activity-compose` 的誤判**；`LocalActivity` 一直就在相依套件裡。已刪掉 `asActivity()`，三處 call site 改用 `LocalActivity.current`，Task 5／6／7 的程式碼範例與 import 清單同步改正。
- 規則層（`BannerPolicy`／`SponsorPolicy`）**不得 import 任何 Android 或廣告 SDK 型別**，否則失去 JVM 可測性。
- 每個 Task 結束都 commit；commit 只在該 Task 的驗證指令實際跑過、且輸出貼進報告之後。

## 檔案結構

| 動作 | 路徑 | 責任 |
|---|---|---|
| Modify | `app/build.gradle.kts` | `applicationId`；兩條廣告相依 |
| Modify | `gradle/libs.versions.toml` | `adsMobileSdk`／`userMessagingPlatform` 版本與 library |
| Create | `app/src/main/res/values/ads.xml` | 正式 App ID／廣告位 ID／`ads_force_eea_debug=false` |
| Create | `app/src/debug/res/values/ads.xml` | 測試 ID／`ads_force_eea_debug=true` |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicy.kt` | 純函式：橫幅 30 秒生命週期 |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicy.kt` | 純函式：贊助入口三態 |
| Create | `app/src/test/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicyTest.kt` | 橫幅規則接縫 |
| Create | `app/src/test/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicyTest.kt` | 贊助規則接縫 |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdBootstrap.kt` | 背景執行緒初始化、裝置雜湊 |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdSession.kt` | 行程層級會話：同意鏈、把政策stage橋接成 Compose State |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdMobBanner.kt` | 橫幅的 SDK 接觸面 + `BannerHandle` |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdMobInterstitial.kt` | 插屏的 SDK 接觸面 |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/components/BannerSlot.kt` | `AndroidView` 掛 AdView |
| Create | `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/components/SponsorThanksOverlay.kt` | 感謝動畫覆蓋層 |
| Modify | `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/JackCompassApp.kt` | `bottomBar` 加橫幅槽；持有 `AdSession`；驅動倒數 |
| Modify | `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/settings/SettingsScreen.kt` | 支持開發者／廣告設定兩區塊 |
| Modify | `app/src/main/res/values/strings.xml` | 新英文鍵（各 UI Task 各自補） |
| Create/Modify | `values-zh-rTW/strings.xml`、`values-zh-rCN/strings.xml` | Task 9 一次補齊 |

---

## Task 1: 換 applicationId，並把廣告 ID 拆成 debug／release 兩份資源

**Files:**
- Modify: `app/build.gradle.kts:11`
- Create: `app/src/main/res/values/ads.xml`
- Create: `app/src/debug/res/values/ads.xml`

**Interfaces:**
- Produces: 字串資源 `admob_app_id`、`admob_banner_unit_id`、`admob_interstitial_unit_id`；bool 資源 `ads_force_eea_debug`。後續所有 Task 一律用 `context.getString(R.string.admob_…)` 讀 ID，**不得把 ID 寫進 Kotlin**。

- [ ] **Step 1: 改 `applicationId`**

`app/build.gradle.kts` 第 11 行：

```kotlin
        applicationId = "com.hywen.app.jackcompass"
```

第 7 行的 `namespace = "com.wenhsiaoyi.jackcompass"` **不動**。

- [ ] **Step 2: 建立正式 ID 檔 `app/src/main/res/values/ads.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 廣告 ID。廣告位 ID 不是機密，刻意進版本庫。
     debug 構型由 app/src/debug/res/values/ads.xml 覆寫成 Google 官方測試 ID。 -->
<resources>
    <string name="admob_app_id" translatable="false">ca-app-pub-9662224353354096~1210011940</string>
    <string name="admob_banner_unit_id" translatable="false">ca-app-pub-9662224353354096/8618436515</string>
    <string name="admob_interstitial_unit_id" translatable="false">ca-app-pub-9662224353354096/5418392251</string>

    <!-- 把裝置強制成規範地區（EEA）好驗同意表單。只在 debug 構型為 true。 -->
    <bool name="ads_force_eea_debug">false</bool>
</resources>
```

- [ ] **Step 3: 建立測試 ID 檔 `app/src/debug/res/values/ads.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- debug 構型覆寫：Google 官方測試 ID。開發期間跑的一定是這一版。 -->
<resources>
    <string name="admob_app_id" translatable="false">ca-app-pub-3940256099942544~3347511713</string>
    <string name="admob_banner_unit_id" translatable="false">ca-app-pub-3940256099942544/9214589741</string>
    <string name="admob_interstitial_unit_id" translatable="false">ca-app-pub-3940256099942544/1033173712</string>

    <bool name="ads_force_eea_debug">true</bool>
</resources>
```

- [ ] **Step 4: 建置並驗證 package 與 ID 都被正確合併**

```powershell
.\gradlew.bat assembleDebug
E:\Android\Sdk\build-tools\37.0.0\aapt2.exe dump badging app\build\outputs\apk\debug\app-debug.apk | Select-String "package: name"
E:\Android\Sdk\build-tools\37.0.0\aapt2.exe dump resources app\build\outputs\apk\debug\app-debug.apk | Select-String "admob_app_id" -Context 0,3
```

預期：`package: name='com.hywen.app.jackcompass'`；`admob_app_id` 解析出來的值是 `…3940256099942544~3347511713`（測試 ID，證明 debug 覆寫生效）。**兩項都要在報告裡貼出實際輸出**；若 badging 還是 `com.wenhsiaoyi…`，代表 Step 1 沒生效，停止並修。

已實測（本 Task 執行時跑過，輸出原樣）：

```text
package: name='com.hywen.app.jackcompass' versionCode='1' versionName='1.0' platformBuildVersionName='17' ...
    resource 0x7f0d001b string/admob_app_id
      () "ca-app-pub-3940256099942544~3347511713"
    resource 0x7f050002 bool/ads_force_eea_debug
      () true
```

`aapt2 dump resources` 對 boolean 的印法就是 `() true`／`() false`（空限定符那一組），不是 `#0x00000001` 那種 token——Task 10 Step 7 依賴這個格式。

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/values/ads.xml app/src/debug/res/values/ads.xml
git commit -m "feat: switch applicationId to the Play Store identity and add ad unit ids"
```

---

## Task 2: 加入廣告相依，並查證 SDK 的真實類別全名

spec「風險與實作時必須查證」第 1 點：官方說明把插屏預載類別寫在舊 SDK 的套件名前綴下，跟其他 Next-Gen 類別不一致。**這個 Task 的產出是後續所有包裝層 Task 的依據**，不要跳過。

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: 一份「已驗證類別全名清單」，寫進本檔案下方的〈已驗證 SDK 表面〉小節（Step 5 補）。包裝層只允許使用清單上的名字。

- [ ] **Step 1: 加版本與 library**

`gradle/libs.versions.toml` 的 `[versions]` 追加：

```toml
adsMobileSdk = "1.5.0"
userMessagingPlatform = "4.0.0"
```

`[libraries]` 追加：

```toml
ads-mobile-sdk = { group = "com.google.android.libraries.ads.mobile.sdk", name = "ads-mobile-sdk", version.ref = "adsMobileSdk" }
user-messaging-platform = { group = "com.google.android.ump", name = "user-messaging-platform", version.ref = "userMessagingPlatform" }
```

- [ ] **Step 2: 掛進 app**

`app/build.gradle.kts` 的 `dependencies {` 內、`implementation(libs.kotlinx.coroutines.android)` 之後：

```kotlin
    implementation(libs.ads.mobile.sdk)
    implementation(libs.user.messaging.platform)
```

- [ ] **Step 3: 解析依賴，確認抓得到**

```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath | Select-String "ads-mobile-sdk|user-messaging-platform"
```

預期：兩列都出現且版本為 1.5.0／4.0.0。若解析失敗，先確認 `settings.gradle.kts` 的 `repositories` 有 `google()` 與 `mavenCentral()`。

- [ ] **Step 4: 從 Gradle 快取列出真實類別全名**

```bash
find "$GRADLE_USER_HOME/caches/modules-2/files-2.1/com.google.android.libraries.ads.mobile.sdk" -name '*.aar' | head
```

（`GRADLE_USER_HOME` 是 `E:\gradle-home`，**不是** `C:\Users\wenhs\.gradle`；用錯路徑會找不到，誤判成「依賴沒下載」。）

對列出的每個 aar：

```bash
AAR=<剛找到的 aar 絕對路徑>
D=$(mktemp -d) && unzip -qo "$AAR" -d "$D" && unzip -l "$D/classes.jar" \
  | grep -Eo 'com/google/[a-zA-Z0-9/$_]*\.class' \
  | grep -Ei 'MobileAds|InitializationConfig|AdView|BannerAd|Interstitial|AdRequest|AdSize|AdLoadCallback|FullScreen|Mediation'
```

再列 UMP：

```bash
find "$GRADLE_USER_HOME/caches/modules-2/files-2.1/com.google.android.ump" -name '*.jar' -o -name '*.aar' | head
```

（jar 直接 `unzip -l`，aar 先解出 `classes.jar`。）

方法簽名用 `javap` 查（PATH 上有，本計畫驗證 Compose 表面時實際用過）：

```bash
mkdir -p "$D/x" && (cd "$D/x" && unzip -q ../classes.jar)
javap -classpath "$D/x" <剛查到的完整類別名>
```

- [ ] **Step 5: 把結果寫進本檔案的〈已驗證 SDK 表面〉**

逐項確認這六個符號的**實際全名**，填進檔案末尾該小節的表格：初始化入口、`InitializationConfig`、橫幅 `AdView`、橫幅請求與尺寸型別、插屏型別與回呼、UMP 的 `ConsentInformation`／`UserMessagingPlatform`（含 `showPrivacyOptionsForm` 掛在哪家）。**以清單為準，不照抄官方說明的範例。** 若清單與計畫後續 Task 的 import 不一致，改 Task 的 import，不改清單。

- [ ] **Step 6: 建置確認**

```powershell
.\gradlew.bat assembleDebug
```

預期：`BUILD SUCCESSFUL`。此時尚無任何 Kotlin 程式碼引用這些符號，成功只代表依賴無衝突。

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts docs/superpowers/plans/2026-10-23-admob-play-publishing.md
git commit -m "build: add GMA next-gen ads and user messaging platform dependencies"
```

---

## Task 3: `BannerPolicy` —— 橫幅 30 秒生命週期（純函式）

**Files:**
- Create: `app/src/test/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicyTest.kt`
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicy.kt`

**Interfaces:**
- Produces: `const val BANNER_VISIBLE_DURATION_MS = 30_000L`；`enum class BannerStage { AwaitingConsent, Loading, Showing, Collapsed, Suppressed }`；`class BannerPolicy(durationMillis: Long = BANNER_VISIBLE_DURATION_MS)`，成員 `stage: BannerStage`（read-only）、`isVisible: Boolean`、`mayRequestAd: Boolean`、`onConsentGranted()`、`onConsentDenied()`、`onAdLoaded(nowMillis: Long)`、`onLoadFailed()`、`onTick(nowMillis: Long)`。Task 5／6 依賴這些名字。

- [ ] **Step 1: 寫紅測試**

`app/src/test/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicyTest.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 橫幅生命週期的接縫測試。
 *
 * 時間一律由外部注入（nowMillis 參數），政策層不碰 SystemClock，
 * 所以 30 秒邊界可以精確打到 29_999 與 30_000 兩側。
 */
class BannerPolicyTest {

    @Test
    fun `開始時等同意，不得請求廣告`() {
        val policy = BannerPolicy()
        assertEquals(BannerStage.AwaitingConsent, policy.stage)
        assertFalse("同意前不得產生請求", policy.mayRequestAd)
        assertFalse(policy.isVisible)
    }

    @Test
    fun `同意後進入載入中，且只允許請求一次`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        assertEquals(BannerStage.Loading, policy.stage)
        assertTrue(policy.mayRequestAd)
        policy.onAdLoaded(nowMillis = 1_000L)
        assertFalse("載入中結束後不再是 Loading，包裝層不會再發第二次請求", policy.mayRequestAd)
    }

    @Test
    fun `拒絕同意後整場壓抑，之後的載入事件無效`() {
        val policy = BannerPolicy()
        policy.onConsentDenied()
        assertEquals(BannerStage.Suppressed, policy.stage)
        policy.onAdLoaded(nowMillis = 1_000L)
        assertFalse(policy.isVisible)
        assertEquals(BannerStage.Suppressed, policy.stage)
    }

    @Test
    fun `計時起點是廣告真的顯示出來那一刻`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onTick(nowMillis = 99_000L)
        assertFalse(policy.isVisible)
        policy.onAdLoaded(nowMillis = 100_000L)
        assertTrue(policy.isVisible)
        // 啟動後 99 秒才出現：再 29.999 秒仍在顯示，總時數才剛滿
        policy.onTick(nowMillis = 129_999L)
        assertTrue(policy.isVisible)
    }

    @Test
    fun `顯示滿 30 秒收起，邊界在 30_000 毫秒`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onAdLoaded(nowMillis = 0L)
        policy.onTick(nowMillis = 29_999L)
        assertTrue(policy.isVisible)
        policy.onTick(nowMillis = 30_000L)
        assertFalse(policy.isVisible)
        assertEquals(BannerStage.Collapsed, policy.stage)
    }

    @Test
    fun `收起後任何事件都不讓它再出現`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onAdLoaded(nowMillis = 0L)
        policy.onTick(nowMillis = 30_000L)
        policy.onAdLoaded(nowMillis = 40_000L)
        policy.onConsentGranted()
        assertEquals(BannerStage.Collapsed, policy.stage)
        assertFalse(policy.isVisible)
        assertFalse(policy.mayRequestAd)
    }

    @Test
    fun `載入失敗不重試，本次執行徹底沒有橫幅`() {
        val policy = BannerPolicy()
        policy.onConsentGranted()
        policy.onLoadFailed()
        assertEquals(BannerStage.Suppressed, policy.stage)
        assertFalse(policy.mayRequestAd)
        policy.onConsentGranted()
        assertEquals(BannerStage.Suppressed, policy.stage)
    }

    @Test
    fun `未同意時 onTick 與 onLoadFailed 都是空操作`() {
        val policy = BannerPolicy()
        policy.onTick(nowMillis = 10_000L)
        policy.onLoadFailed()
        assertEquals(BannerStage.AwaitingConsent, policy.stage)
    }

    @Test
    fun `時數可注入，方便測試其他秒數`() {
        val policy = BannerPolicy(durationMillis = 5_000L)
        policy.onConsentGranted()
        policy.onAdLoaded(nowMillis = 1_000L)
        policy.onTick(nowMillis = 5_999L)
        assertTrue(policy.isVisible)
        policy.onTick(nowMillis = 6_000L)
        assertFalse(policy.isVisible)
    }

    @Test
    fun `預設時長就是規格定的 30 秒`() {
        assertEquals(30_000L, BANNER_VISIBLE_DURATION_MS)
    }
}
```

- [ ] **Step 2: 跑測試，確認紅**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*BannerPolicyTest"
```

預期：編譯失敗，訊息含 `unresolved reference: BannerPolicy`（或 `BannerStage`）。這就是紅。**若意外通過，代表檔案已存在，停止並查。**

- [ ] **Step 3: 寫最小實作**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicy.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

/**
 * 橫幅廣告的規則層：純 JVM、無 Android 相依，時間由外部注入。
 *
 * 兩套廣告 SDK 都沒有內建的「顯示 N 秒後停止」開關，所以計時一定由 App 自己擁有。
 * 本類別只決定狀態怎麼變，不碰任何 SDK 型別。
 */

/** 橫幅顯示滿這個毫秒數就收起，本次執行不再出現。 */
const val BANNER_VISIBLE_DURATION_MS = 30_000L

enum class BannerStage {
    /** 還沒問完同意，不允許請求廣告。 */
    AwaitingConsent,

    /** 已通過同意閘門，正在等第一筆（也是本場唯一一筆）廣告。 */
    Loading,

    /** 廣告已顯示，倒數中。 */
    Showing,

    /** 倒數結束，本次執行收幕。 */
    Collapsed,

    /** 使用者拒絕同意，或載入失敗：本次執行不出現。 */
    Suppressed,
}

class BannerPolicy(private val durationMillis: Long = BANNER_VISIBLE_DURATION_MS) {

    var stage: BannerStage = BannerStage.AwaitingConsent
        private set

    private var shownAtMillis: Long? = null

    val isVisible: Boolean get() = stage == BannerStage.Showing

    /** 包裝層只在此值為 true 時向 SDK 要廣告，因此一次執行只會要一次。 */
    val mayRequestAd: Boolean get() = stage == BannerStage.Loading

    fun onConsentGranted() {
        if (stage == BannerStage.AwaitingConsent) stage = BannerStage.Loading
    }

    fun onConsentDenied() {
        if (stage == BannerStage.AwaitingConsent) stage = BannerStage.Suppressed
    }

    fun onAdLoaded(nowMillis: Long) {
        if (stage != BannerStage.Loading) return
        stage = BannerStage.Showing
        shownAtMillis = nowMillis
    }

    fun onLoadFailed() {
        if (stage == BannerStage.Loading) stage = BannerStage.Suppressed
    }

    fun onTick(nowMillis: Long) {
        if (stage != BannerStage.Showing) return
        val started = shownAtMillis ?: return
        if (nowMillis - started >= durationMillis) {
            stage = BannerStage.Collapsed
            shownAtMillis = null
        }
    }
}
```

- [ ] **Step 4: 跑測試，確認綠**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*BannerPolicyTest"
```

預期：`BUILD SUCCESSFUL`，10 個測試全過。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicy.kt app/src/test/java/com/wenhsiaoyi/jackcompass/ads/BannerPolicyTest.kt
git commit -m "feat: add banner lifetime policy as a pure, time-injected rule layer"
```

---

## Task 4: `SponsorPolicy` —— 贊助入口三態（純函式）

**Files:**
- Create: `app/src/test/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicyTest.kt`
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicy.kt`

**Interfaces:**
- Produces: `enum class SponsorStage { Hidden, Ready, Preparing, Playing, Unavailable }`；`class SponsorPolicy`，成員 `stage: SponsorStage`、`isRowVisible: Boolean`、`isBusy: Boolean`、`onConsentGranted()`、`onConsentDenied()`、`onClick(): Boolean`、`onAdShown()`、`onAdDismissed()`、`onAdUnavailable()`、`onShowFailed()`。Task 5／7 依賴這些名字。

- [ ] **Step 1: 寫紅測試**

`app/src/test/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicyTest.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 設定頁贊助入口的規則層測試。
 *
 * 插屏絕不自動播：只有使用者按下、且回傳 true 時，包裝層才允許向 SDK 要廣告。
 * 「準備中」與「正在播」期間重複點擊回傳 false——不排隊、不重疊。
 */
class SponsorPolicyTest {

    @Test
    fun `同意前整列隱藏`() {
        val policy = SponsorPolicy()
        assertEquals(SponsorStage.Hidden, policy.stage)
        assertFalse(policy.isRowVisible)
        assertFalse("隱藏時點了也不允許請求", policy.onClick())
    }

    @Test
    fun `通過同意後進入可點狀態`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        assertEquals(SponsorStage.Ready, policy.stage)
        assertTrue(policy.isRowVisible)
        assertFalse(policy.isBusy)
    }

    @Test
    fun `拒絕同意時整列藏起來`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onConsentDenied()
        assertEquals(SponsorStage.Hidden, policy.stage)
        assertFalse(policy.isRowVisible)
    }

    @Test
    fun `按下進入準備中並回傳允許請求`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        assertTrue(policy.onClick())
        assertEquals(SponsorStage.Preparing, policy.stage)
        assertTrue("準備中算忙碌", policy.isBusy)
    }

    @Test
    fun `準備中重複點擊不被允許，也不會把請求排成隊`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        assertFalse(policy.onClick())
        assertFalse(policy.onClick())
        assertEquals(SponsorStage.Preparing, policy.stage)
    }

    @Test
    fun `廣告呈現期間重複點擊不被允許`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdShown()
        assertEquals(SponsorStage.Playing, policy.stage)
        assertTrue(policy.isBusy)
        assertFalse(policy.onClick())
    }

    @Test
    fun `關掉廣告後回到可點，不設次數上限`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        repeat(3) {
            assertTrue("第 ${it + 1} 次都該允許", policy.onClick())
            policy.onAdShown()
            policy.onAdDismissed()
            assertEquals(SponsorStage.Ready, policy.stage)
        }
    }

    @Test
    fun `載入不到廣告時暫時沒有，之後還能再點`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdUnavailable()
        assertEquals(SponsorStage.Unavailable, policy.stage)
        assertFalse(policy.isBusy)
        assertTrue(policy.isRowVisible)
        assertTrue(policy.onClick())
        assertEquals(SponsorStage.Preparing, policy.stage)
    }

    @Test
    fun `呈現失敗走暫時沒有，且不會進到播放中`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdShown()
        policy.onShowFailed()
        assertEquals(SponsorStage.Unavailable, policy.stage)
    }

    @Test
    fun `非準備中的 onAdShown 是空操作`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onAdShown()
        assertEquals(SponsorStage.Ready, policy.stage)
    }

    @Test
    fun `暫時沒有時收到 onAdDismissed 不會跳到可點以外`() {
        val policy = SponsorPolicy()
        policy.onConsentGranted()
        policy.onClick()
        policy.onAdUnavailable()
        policy.onAdDismissed()
        assertEquals(SponsorStage.Unavailable, policy.stage)
    }
}
```

- [ ] **Step 2: 跑測試，確認紅**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SponsorPolicyTest"
```

預期：編譯失敗，`unresolved reference: SponsorPolicy`。

- [ ] **Step 3: 寫最小實作**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicy.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

/**
 * 贊助入口的規則層：純 JVM、無 Android 相依。
 *
 * 三態對應設計基準圖（spec/sponsor-flow-mock.svg 格 1）的三個膠囊：
 * 可點（Ready）、準備中（Preparing）、暫時沒有可播的廣告（Unavailable）。
 */
enum class SponsorStage {
    /** 使用者拒絕同意或還沒同意：整列不出現。 */
    Hidden,

    /** 可點。 */
    Ready,

    /** 按下後正在跟 SDK 要廣告；重複點擊一律擋掉。 */
    Preparing,

    /** 全螢幕廣告正在播。 */
    Playing,

    /** 這一次拿不到廣告（載入失敗或呈現失敗）；之後仍可再點。 */
    Unavailable,
}

class SponsorPolicy {

    var stage: SponsorStage = SponsorStage.Hidden
        private set

    val isRowVisible: Boolean get() = stage != SponsorStage.Hidden

    val isBusy: Boolean get() = stage == SponsorStage.Preparing || stage == SponsorStage.Playing

    fun onConsentGranted() {
        if (stage == SponsorStage.Hidden) stage = SponsorStage.Ready
    }

    fun onConsentDenied() {
        stage = SponsorStage.Hidden
    }

    /** 回傳 true 代表包裝層這一次**允許**向 SDK 請求並呈現插屏。 */
    fun onClick(): Boolean {
        if (stage != SponsorStage.Ready && stage != SponsorStage.Unavailable) return false
        stage = SponsorStage.Preparing
        return true
    }

    fun onAdShown() {
        if (stage == SponsorStage.Preparing) stage = SponsorStage.Playing
    }

    fun onAdDismissed() {
        if (stage == SponsorStage.Playing) stage = SponsorStage.Ready
    }

    fun onAdUnavailable() {
        if (stage == SponsorStage.Preparing) stage = SponsorStage.Unavailable
    }

    /** 素材與系統版本打架等狀況：走同一句「暫時沒有可播的廣告」，不播感謝動畫。 */
    fun onShowFailed() {
        if (stage == SponsorStage.Playing) stage = SponsorStage.Unavailable
    }
}
```

- [ ] **Step 4: 跑全部 JVM 測試，確認綠且沒弄壞既有測試**

```powershell
.\gradlew.bat testDebugUnitTest
```

預期：`BUILD SUCCESSFUL`；`BannerPolicyTest`、`SponsorPolicyTest` 與既有 `RotationVectorAzimuthTest`／`AccelerometerTiltTest`／`CardinalDirectionTest`／`DisplayedHeadingTest`／`HeadingTickTest`／`LowPassFilterTest`／`CoordinateFormatTest`／`CoordinateCardinalTest`／`LocationAvailabilityTest` 全數通過。spec 要求既有感測器測試必須維持綠燈。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicy.kt app/src/test/java/com/wenhsiaoyi/jackcompass/ads/SponsorPolicyTest.kt
git commit -m "feat: add sponsor entry tri-state policy as a pure rule layer"
```

---

## Task 5: 同意鏈與初始化包裝層

**Files:**
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdBootstrap.kt`
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdSession.kt`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/MainActivity.kt:16-24`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/JackCompassApp.kt:72`（Step 7 的最小橋接：加 `session` 參數）

**Interfaces:**
- Consumes: Task 3 的 `BannerPolicy`、Task 4 的 `SponsorPolicy`（Task 6／7 才把兩者接進 `AdSession`）；Task 1 的 `R.string.admob_app_id`、`R.bool.ads_force_eea_debug`。
- Produces: `object AdBootstrap { fun initializeOnce(context: Context); fun deviceHash(activity: Activity): String }`（冪等、內部開背景執行緒）；`class AdSession(val banner: BannerPolicy, val sponsor: SponsorPolicy)`，成員 `bannerStage: BannerStage`／`sponsorStage: SponsorStage`（皆是 Compose `State`，UI 只讀這兩個）、`nowMillis: Long`、`runConsentChain(activity)`、`showPrivacyOptionsForm(activity, onDismissed)`、`sync()`／`finish(context, granted)` 為 private，另有 `companion object { fun ensure(): AdSession }`——**行程層級單例**，Activity 重建（切換語系會重建）後同一顆 policy 跟著活著，這是「本次執行只播一次」能撐過語系切換的機制。取 Activity 由 UI 端用 `androidx.activity.compose.LocalActivity`（見 Global Constraints 的勘誤）。

- [ ] **Step 1: 查一次符號**

打開 Task 2 Step 5 填好的〈已驗證 SDK 表面〉，確認 `MobileAds`、`InitializationConfig`、`UserMessagingPlatform`、`ConsentRequestParameters`、`ConsentDebugSettings`、`ConsentInformation` 的全名與 import 一致。不一致就以清單為準修正下面的 import。

- [ ] **Step 2: 寫 `AdBootstrap.kt`**

```kotlin
package com.wenhsiaoyi.jackcompass.ads

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.wenhsiaoyi.jackcompass.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 廣告 SDK 的初始化。Next-Gen 的兩條硬性規定都在這裡：
 * App ID 由程式傳入（不寫進 manifest），而且必須在背景執行緒呼叫。
 */
object AdBootstrap {

    private val started = AtomicBoolean(false)

    /** 冪等：同一個行程只初始化一次。 */
    fun initializeOnce(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val appId = appContext.getString(R.string.admob_app_id)
        Thread({
            MobileAds.initialize(appContext, InitializationConfig.Builder(appId).build()) { }
        }, "AdMobInit").start()
    }
}
```

- [ ] **Step 3: 寫 `AdSession.kt`（同意鏈 + 政策層到 Compose 的橋接）**

政策層保持純淨（不 import Android／Compose）；**只有 `AdSession` 允許依賴 Compose runtime**——它把政策的 `stage` 鏡射成 `mutableStateOf`，UI 才重組得到。直接讀 `banner.isVisible`／`sponsor.stage` 那種普通 getter 不會觸發重組，這是本計畫前一版的錯，別重蹈。

`app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdSession.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.wenhsiaoyi.jackcompass.R

/**
 * 一次行程（從啟動到結束）的廣告會話。
 *
 * 刻意做成行程層級單例：切換語系會重建 Activity，若政策跟著重建，
 * 橫幅就會在倒數未滿時重新進入 Loading、多算一次 impression。
 * 放在行程層，30 秒與「只播一次」才撐得過重建。
 *
 * UMP 的回呼都在主執行緒，所以下面的 state 寫入不需要額外同步。
 */
class AdSession(
    val banner: BannerPolicy = BannerPolicy(),
    val sponsor: SponsorPolicy = SponsorPolicy(),
) {
    /** 政策層 stage 的 Compose 鏡像；每個政策事件後由 sync() 更新，UI 只讀這兩個。 */
    var bannerStage by mutableStateOf(BannerStage.AwaitingConsent)
        private set

    var sponsorStage by mutableStateOf(SponsorStage.Hidden)
        private set

    private var information: ConsentInformation? = null

    val nowMillis: Long get() = SystemClock.elapsedRealtime()

    /**
     * 同意鏈，順序不可調（spec「同意鏈」）：
     * 請求更新 → 需要時顯示表單 → 才初始化 SDK（背景執行緒）→ 檢查可否請求。
     */
    fun runConsentChain(activity: Activity) {
        val context = activity.applicationContext
        val info = UserMessagingPlatform.getConsentInformation(context)
        information = info

        val builder = ConsentRequestParameters.Builder()
        if (context.resources.getBoolean(R.bool.ads_force_eea_debug)) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(context)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(AdBootstrap.deviceHash(activity))
                    .build(),
            )
        }

        info.requestConsentInfoUpdate(
            activity,
            builder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    finish(context, info.canRequestAds())
                }
            },
            { _ -> finish(context, canRequestFallback(info)) },
        )
    }

    /** 規範區使用者事後改同意的唯一入口；Google 政策要求一定要有。 */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit) {
        val info = information ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            finish(activity.applicationContext, info.canRequestAds())
            onDismissed()
        }
    }

    private fun canRequestFallback(info: ConsentInformation): Boolean = runCatching {
        info.canRequestAds()
    }.getOrDefault(false)

    private fun sync() {
        bannerStage = banner.stage
        sponsorStage = sponsor.stage
    }

    private fun finish(context: Context, granted: Boolean) {
        if (granted) {
            AdBootstrap.initializeOnce(context)
            banner.onConsentGranted()
            sponsor.onConsentGranted()
        } else {
            banner.onConsentDenied()
            sponsor.onConsentDenied()
        }
        sync()
    }
}
```

三處與網路教學不同、已用 `javap` 查证的 UMP 4.0 形狀：`requestConsentInfoUpdate` **第一個參數是 Activity**（範例常省略）；`showPrivacyOptionsForm` 是 `UserMessagingPlatform` 的靜態方法，**不在 `ConsentInformation` 上**；`OnConsentInfoUpdateSuccessListener.onConsentInfoUpdateSuccess()` **不帶參數**（3.x 的 `boolean isDebugBuild` 已移除），所以 SAM 寫法是 `{ ... }` 而不是 `{ _ -> ... }`。失敗與表單關閉的兩個回呼都收 `FormError`。

- [ ] **Step 4: 補 `AdBootstrap.deviceHash`**

`AdBootstrap` 內追加（`AdSession` 會用）：

```kotlin
    /** UMP 除錯地區只認清單裡的裝置；未列出的裝置會被當成非規範地區。 */
    fun deviceHash(activity: Activity): String {
        val provider = activity.contentResolver
        val id = android.provider.Settings.Secure.getString(
            provider,
            android.provider.Settings.Secure.ANDROID_ID,
        )
        return android.util.Base64.encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()),
            android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE,
        ).take(64)
    }
```

`AdBootstrap.kt` 需 `import android.app.Activity`。

- [ ] **Step 5: 加單例入口**

`AdSession` 類別體內（`finish` 之後）加 companion：

```kotlin
    companion object {
        private var instance: AdSession? = null

        /** 行程層級：Activity 重建後回傳同一顆，政策與倒數不會重置。 */
        @Synchronized
        fun ensure(): AdSession = instance ?: AdSession().also { instance = it }
    }
```

- [ ] **Step 6: 在 `MainActivity` 啟動同意鏈**

`app/src/main/java/com/wenhsiaoyi/jackcompass/MainActivity.kt` 的 `onCreate`（第 16–24 行）改成：

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val session = AdSession.ensure()
        session.runConsentChain(this)
        setContent {
            JackCompassTheme {
                JackCompassApp(session = session)
            }
        }
    }
```

並加 `import com.wenhsiaoyi.jackcompass.ads.AdSession`。`JackCompassApp(session:)` 的簽名在 Task 6 才改，**這個 Task 結束時編譯會過不了**——所以 Step 7 先做最小橋接：把 `JackCompassApp.kt` 的 `fun JackCompassApp()` 改成 `fun JackCompassApp(session: AdSession)`，函式體本輪完全不用 `session`（加一行 `@Suppress("UNUSED_PARAMETER")` 不必要，Compose 參數不會報未用）。

- [ ] **Step 7: 建置並驗證綠**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

預期：兩個都 `BUILD SUCCESSFUL`。若 `showPrivacyOptionsForm` 或 `requestConsentInfoUpdate` 編譯不起來，代表 UMP 4.0 的簽名與此處不同——**以 Task 2 的類別清單為準修正 import 與呼叫**，必要時用 `javap -classpath <解出的 classes.jar> com.google.android.ump.ConsentInformation` 看真實方法簽名，不要猜。

- [ ] **Step 8: 實機跑一次同意鏈（debug ID）**

```powershell
.\gradlew.bat installDebug
```

啟動 App，預期：在非規範地區不會看到任何表單（鏈路直接往下走），App 其餘功能正常。把 `ads_force_eea_debug` 維持 true，若在 realme 上仍看不到表單，先確認 AdMob 後台已建立並發布同意訊息（spec 驗證計畫那條 footgun）。**介面由使用者驗收；agent 只回報啟動有無當機與 logcat 有無初始化錯誤。**

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/wenhsiaoyi/jackcompass/ads/ app/src/main/java/com/wenhsiaoyi/jackcompass/MainActivity.kt app/src/main/java/com/wenhsiaoyi/jackcompass/ui/JackCompassApp.kt
git commit -m "feat: run the consent chain before initializing the ads sdk"
```

---

## Task 6: 橫幅槽掛進 `bottomBar`，並驅動 30 秒倒數

**Files:**
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdMobBanner.kt`
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/components/BannerSlot.kt`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdSession.kt`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/JackCompassApp.kt`

**Interfaces:**
- Consumes: `AdSession.banner: BannerPolicy`、`AdSession.bannerStage`、`AdSession.nowMillis`、`AdSession.sync()`、`R.string.admob_banner_unit_id`。
- Produces: `class BannerHandle(val view: View, private val release: () -> Unit) { fun destroy() }`；`object AdMobBanner { fun load(activity, unitId, onLoaded: (BannerHandle) -> Unit, onFailed: () -> Unit) }`；`AdSession.bannerHandle: BannerHandle?`（Compose State，read-only）、`AdSession.bannerVisible: Boolean`、`AdSession.requestBanner(activity)`、`AdSession.tickBanner()`；`@Composable fun BannerSlot(session: AdSession, modifier: Modifier = Modifier)`。

- [ ] **Step 1: 給 `AdSession` 加橫幅狀態**

`AdSession.kt` 加欄位與方法（本檔已依賴 Compose runtime，見 Task 5；政策層 `BannerPolicy.kt` 仍保持純淨）：

```kotlin
    /**
     * 目前顯示中的橫幅。同一顆 AdView 在顯示期間不重建：重新載入會多算一次
     * impression、也會閃。做成 State 是為了讓收起那一刻能重組。
     */
    var bannerHandle: BannerHandle? by mutableStateOf(null)
        private set

    val bannerVisible: Boolean get() = bannerStage == BannerStage.Showing

    private var bannerRequested = false

    /** 只在政策允許、且本場還沒請求過時發請求——保證一次執行只載入一次。 */
    fun requestBanner(activity: Activity) {
        if (bannerRequested || !banner.mayRequestAd) return
        bannerRequested = true
        AdMobBanner.load(
            activity = activity,
            unitId = activity.getString(R.string.admob_banner_unit_id),
            onLoaded = { handle ->
                banner.onAdLoaded(nowMillis)
                bannerHandle = handle
                sync()
            },
            onFailed = {
                banner.onLoadFailed()
                sync()
            },
        )
    }

    /** UI 每 250 毫秒呼叫一次；收起那一刻由政策層判定，這裡負責銷毀檢視圖。 */
    fun tickBanner() {
        val wasCollapsed = bannerStage == BannerStage.Collapsed
        banner.onTick(nowMillis)
        if (!wasCollapsed && banner.stage == BannerStage.Collapsed) {
            bannerHandle?.destroy()
            bannerHandle = null
        }
        sync()
    }
```

`bannerRequested` 和 policy 一樣活在行程層，所以語系切換重建 Activity 也不會讓它重新請求。Task 6 Step 4 的迴圈會每 250 毫秒呼叫 `requestBanner` 一次，這個旗標就是為了讓那種呼叫變成空操作——**少了它會刷廣告位**。

- [ ] **Step 2: 寫 `AdMobBanner` 包裝層**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdMobBanner.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import android.view.View
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * 一張橫幅檢視圖和它的銷毀方式。只有本檔知道實質型別，
 * 這樣 AdSession 與 UI 都不用 import 廣告 SDK。
 */
class BannerHandle(val view: View, private val release: () -> Unit) {
    fun destroy() = release()
}

/**
 * 橫幅的 SDK 接觸面。全專案只有這裡 import 橫幅型別。
 *
 * 走 `BannerAd.load(...)` 再取 `ad.getView(activity)`，**不用 `AdView` 容器**：
 * `AdView` 假設 imperative 的 XML 容器與 `registerBannerAd`，交給 Compose
 * `AndroidView` 的 factory 會在每次重組時搶生命週期。這裡把 View 交給
 * `BannerHandle` 持有，销毁只呼 `ad.destroy()`（`common.Ad` 繼承來的）。
 */
object AdMobBanner {

    fun load(activity: Activity, unitId: String, onLoaded: (BannerHandle) -> Unit, onFailed: () -> Unit) {
        activity.runOnUiThread {
            val request = BannerAdRequest.Builder(unitId, AdSize.BANNER).build()
            BannerAd.load(
                request,
                object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        onLoaded(BannerHandle(ad.getView(activity)) { ad.destroy() })
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        onFailed()
                    }
                },
            )
        }
    }
}
```

`AdSize.BANNER` 是 320×50 dp（本表已查证的常數）。橫幅寬度由 View 自己量，所以 Task 6 Step 3 的槽**不要寫死高度**；若實測發現 Compose 把高度壓成 0，才改成從 `ad.getAdSize().getHeightInPixels(context)` 取數值帶進 `BannerHandle`。

- [ ] **Step 3: 寫 `BannerSlot`**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ui/components/BannerSlot.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wenhsiaoyi.jackcompass.ads.AdSession

/**
 * 橫幅槽：掛在導覽列正上方，三個分頁與設定頁都看得到。
 * 收起時整個 composable 不輸出任何東西，導覽列自己往下補位。
 *
 * 盤面直徑由寬度決定（CompassScreen 的 minOf(maxWidth, maxHeight)），
 * 所以收起橫幅不會讓盤面變大，只有中心下移——設計基準圖格 4 就是這樣畫的。
 *
 * 高度不寫死：`AdSize.BANNER` 是 320×50 dp，View 自己量得出來。
 *
 * 刻意不寫 AndroidView 的 onRelease 來銷毀：語系切換重建組合層時，
 * 那張橫幅還在倒數中、依然有效，onRelease 會把它當掉。
 * 銷毀只在 AdSession.tickBanner() 判定收起的那一刻做。
 */
@Composable
fun BannerSlot(session: AdSession, modifier: Modifier = Modifier) {
    val handle = if (session.bannerVisible) session.bannerHandle else null
    if (handle == null) return
    AndroidView(
        factory = { handle.view },
        modifier = modifier.fillMaxWidth(),
    )
}
```

- [ ] **Step 4: 在 `JackCompassApp` 掛槽並驅動倒數**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ui/JackCompassApp.kt`：`fun JackCompassApp(session: AdSession)` 開頭（第 73 行附近）加：

```kotlin
    val activity = androidx.activity.compose.LocalActivity.current
    androidx.compose.runtime.LaunchedEffect(session, activity) {
        if (activity == null) return@LaunchedEffect
        while (isActive &&
            session.bannerStage != BannerStage.Collapsed &&
            session.bannerStage != BannerStage.Suppressed
        ) {
            session.requestBanner(activity)
            session.tickBanner()
            kotlinx.coroutines.delay(250L)
        }
    }
```

需要 `import androidx.activity.compose.LocalActivity`、`com.wenhsiaoyi.jackcompass.ads.AdSession`、`ads.BannerStage`。

**這個迴圈不能寫成 `while (session.bannerVisible)`**：啟動時 stage 是 `AwaitingConsent`，那種寫法迴圈一次都不進，同意鏈跑完後也沒人去請求。必須輪詢到 `Collapsed`／`Suppressed` 才退出；重組與否由 `bannerStage` 這個 State 負責。

`bottomBar` 內容（第 78–93 行）從單一個 `NavigationBar` 換成 `Column`：

```kotlin
        bottomBar = {
            androidx.compose.foundation.layout.Column {
                BannerSlot(session = session)
                NavigationBar {
                    ToolDestination.entries.forEach { destination ->
                        /* 既有 selected 計算與 NavigationBarItem 原封不動搬進來 */
                    }
                }
            }
        },
```

- [ ] **Step 5: 建置＋實測**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat installDebug
```

預期：建置與測試綠。實機（realme `ce27901d` 或 emulator `jack_android36`）啟動後，橫幅應出現在導覽列正上方；**不要點它**。30 秒後整條消失、導覽列下移。用截圖比對 `spec/sponsor-flow-mock.svg` 格 3 與格 4：格 4 的盤面直徑要與格 3 **相同**（若變大或變小，代表 `minOf` 假設不成立，回報並修正 spec 與圖）。

- [ ] **Step 6: 驗「語系切換不重置倒數」**

在橫幅出現後 30 秒內進設定頁切語系（Activity 會重建），確認橫幅不會重新載入、也不會多停留。這是 spec「語言切換或畫面重組不得重新載入」的實機驗法。

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wenhsiaoyi/jackcompass/ads/ app/src/main/java/com/wenhsiaoyi/jackcompass/ui/
git commit -m "feat: anchor a once-per-run banner above the navigation bar with a 30s timer"
```

---

## Task 7: 贊助插屏與設定頁兩個新區塊

**Files:**
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdMobInterstitial.kt`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdSession.kt`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AdSession.sponsor: SponsorPolicy`、Task 5 已暴露的 `AdSession.sponsorStage: SponsorStage`（Compose State）、`AdSession.sync()`、`R.string.admob_interstitial_unit_id`。
- Produces: `AdSession.requestSponsor(activity: Activity): Boolean`、`AdSession.showPrivacyOptions(activity: Activity)`、`AdSession.onSponsorDismissed: (() -> Unit)?`（Task 8 消費）；`@Composable fun SettingsScreen(session: AdSession, modifier: Modifier = Modifier)`（簽名從無參變成帶 session）。

- [ ] **Step 1: 寫 `AdMobInterstitial` 包裝層**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ads/AdMobInterstitial.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback

/**
 * 插屏的 SDK 接觸面。全專案只有這裡 import 插屏型別。
 *
 * 全部簽名已用 javap 查證（見〈已驗證 SDK 表面〉）：
 * `InterstitialAd.load(AdRequest, AdLoadCallback<InterstitialAd>)` 是 static，
 * **沒有** activity 參數、**沒有** `InterstitialAdRequest`、**沒有**
 * `InterstitialAdLoadCallback` 這種專屬回呼型別；事件掛點在 Java 是 `setAdEventCallback`，
 * 但它和 `getAdEventCallback` 成對，Kotlin 只把它當成屬性 `adEventCallback` 暴露——
 * 寫 `ad.setAdEventCallback(...)` 會 `Unresolved reference`，要用 `ad.adEventCallback = ...`。
 * 呈現失敗的參數型別是 `FullScreenContentError`（不是 `LoadAdError`）。
 */
object AdMobInterstitial {

    /**
     * 載入並呈現。四個出口都要走到：
     * 拿不到素材 → onUnavailable；呈現失敗 → onShowFailed（不播感謝動畫）；
     * 出現 → onShown；使用者關掉 → onDismissed。
     */
    fun show(
        activity: Activity,
        unitId: String,
        onUnavailable: () -> Unit,
        onShown: () -> Unit,
        onDismissed: () -> Unit,
        onShowFailed: () -> Unit,
    ) {
        InterstitialAd.load(
            AdRequest.Builder(unitId).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    activity.runOnUiThread {
                        ad.adEventCallback = object : InterstitialAdEventCallback {
                            override fun onAdShowedFullScreenContent() = onShown()
                            override fun onAdDismissedFullScreenContent() { ad.destroy(); onDismissed() }
                            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) { ad.destroy(); onShowFailed() }
                        }
                        ad.show(activity)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) = onUnavailable()
            },
        )
    }
}
```

`InterstitialAdEventCallback` 的所有方法都是 default（含 `onAdImpression`／`onAdClicked`／`onAdPaid`），所以只覆寫需要的那三個即可編過。`interstitial.InterstitialAdPreloader` 確實存在，但**本專案不走預載**：`load` 這條路已由 javap 證明可用，預載是 spec 說的「可選」。

- [ ] **Step 2: 給 `AdSession` 加贊助方法**

```kotlin
    /** 使用者按下贊助列。回傳 false 代表政策擋掉（準備中或播放中）。 */
    fun requestSponsor(activity: Activity): Boolean {
        if (!sponsor.onClick()) return false
        AdMobInterstitial.show(
            activity = activity,
            unitId = activity.getString(R.string.admob_interstitial_unit_id),
            onUnavailable = { sponsor.onAdUnavailable(); sync() },
            onShown = { sponsor.onAdShown(); sync() },
            onDismissed = {
                sponsor.onAdDismissed()
                sync()
                onSponsorDismissed?.invoke()
            },
            onShowFailed = { sponsor.onShowFailed(); sync() },
        )
        return true
    }

    var onSponsorDismissed: (() -> Unit)? = null

    fun showPrivacyOptions(activity: Activity) = showPrivacyOptionsForm(activity) {}
```

每個回呼都要跟著 `sync()`，否則 `sponsorStage` 不會動、設定頁不重組。
`onShowFailed` 刻意**不**呼叫 `onSponsorDismissed`——沒播過就不該假裝播過（spec「贊助入口」）。

- [ ] **Step 3: 設定頁加兩區塊**

`SettingsScreen.kt`：`SettingsScreen` 簽名（第 45 行）改成 `fun SettingsScreen(session: AdSession, modifier: Modifier = Modifier)`，`Column` 末尾（`LanguageSelector()` 之後，第 71 行下面）加：

```kotlin
        Spacer(Modifier.height(36.dp))
        SponsorSection(session = session)
        Spacer(Modifier.height(36.dp))
        AdSettingsSection(session = session)
```

同檔新增四個 private composable：

```kotlin
@Composable
private fun SectionLabel(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        color = DialText,
        letterSpacing = 3.sp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RowCard(
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DialFace)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = DialText,
            )
            if (subtitleRes != null) {
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = DialTick,
                )
            }
        }
    }
}

@Composable
private fun SponsorSection(session: AdSession) {
    val stage = session.sponsorStage          // Compose State：政策一變在這裡重組
    if (stage == SponsorStage.Hidden) return
    val activity = LocalActivity.current ?: return
    SectionLabel(R.string.settings_sponsor_section)
    Spacer(Modifier.height(12.dp))
    RowCard(
        titleRes = R.string.settings_sponsor_title,
        subtitleRes = R.string.settings_sponsor_subtitle,
        onClick = { session.requestSponsor(activity) },
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(
            when (stage) {
                SponsorStage.Preparing -> R.string.settings_sponsor_preparing
                SponsorStage.Unavailable -> R.string.settings_sponsor_unavailable
                else -> R.string.settings_sponsor_ready
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = DialTick,
    )
}

@Composable
private fun AdSettingsSection(session: AdSession) {
    val activity = LocalActivity.current ?: return
    SectionLabel(R.string.settings_ads_section)
    Spacer(Modifier.height(12.dp))
    // 即使使用者拒絕同意也要留著：這是規範區事後改同意的唯一入口。
    RowCard(
        titleRes = R.string.settings_ads_privacy,
        subtitleRes = null,
        onClick = { session.showPrivacyOptions(activity) },
    )
}
```

`Playing` 階段對應到 `settings_sponsor_ready`：全螢幕廣告蓋住整個 App 時這段文字根本看不到，插屏關掉後 stage 立刻回到 `Ready`。

本檔要新增的 import：`androidx.activity.compose.LocalActivity`、`androidx.annotation.StringRes`、`androidx.compose.foundation.clickable`、`androidx.compose.foundation.layout.Box`、`androidx.compose.foundation.shape.RoundedCornerShape`、`androidx.compose.ui.draw.clip`、`com.wenhsiaoyi.jackcompass.ads.AdSession`、`com.wenhsiaoyi.jackcompass.ads.SponsorStage`。已有的 `background`／`Column`／`Spacer`／`height`／`fillMaxWidth`／`padding`／`stringResource`／`DialFace`／`DialText` 沿用；`DialTick` 在 `ui/theme/Color.kt:16`，要另外 import。

- [ ] **Step 4: 補英文鍵**

`values/strings.xml`：

```xml
    <string name="settings_sponsor_section">Support development</string>
    <string name="settings_sponsor_title">Watch an ad to support us</string>
    <string name="settings_sponsor_subtitle">Shows a full-screen ad. Nothing is unlocked.</string>
    <string name="settings_sponsor_ready">Tap to watch an ad</string>
    <string name="settings_sponsor_preparing">Loading…</string>
    <string name="settings_sponsor_unavailable">No ad available right now</string>
    <string name="settings_ads_section">Ad settings</string>
    <string name="settings_ads_privacy">Adjust ad consent options</string>
```

- [ ] **Step 5: 在 `JackCompassApp` 傳 session 給設定頁**

`composable(SETTINGS_ROUTE) { SettingsScreen(session = session) }`。

- [ ] **Step 6: 建置＋測試**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

預期：都 `BUILD SUCCESSFUL`。若 SDK 符號編譯不起來，依 Step 1 的 javap 程序修正，**不要改政策層**。

- [ ] **Step 7: 實機驗贊助流程**

```powershell
.\gradlew.bat installDebug
```

進設定頁 → 看到兩個新區塊 → 按贊助列 → 全螢幕插屏出現 → 關掉。**只驗狀態流轉，不要點廣告本身。** 重複快按贊助列，確認不會排隊（第二次被擋）。

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/wenhsiaoyi/jackcompass/ app/src/main/res/values/strings.xml
git commit -m "feat: add the sponsor interstitial entry and ad consent row to settings"
```

---

## Task 8: 感謝贊助動畫覆蓋層

**Files:**
- Create: `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/components/SponsorThanksOverlay.kt`
- Modify: `app/src/main/java/com/wenhsiaoyi/jackcompass/ui/JackCompassApp.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AdSession.onSponsorDismissed`（Task 7）。
- Produces: `@Composable fun SponsorThanksOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier)`。

- [ ] **Step 1: 寫覆蓋層**

`app/src/main/java/com/wenhsiaoyi/jackcompass/ui/components/SponsorThanksOverlay.kt`：

```kotlin
package com.wenhsiaoyi.jackcompass.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wenhsiaoyi.jackcompass.R
import com.wenhsiaoyi.jackcompass.ui.theme.CompassOrange
import com.wenhsiaoyi.jackcompass.ui.theme.DialFace
import com.wenhsiaoyi.jackcompass.ui.theme.DialRing
import com.wenhsiaoyi.jackcompass.ui.theme.DialScreen
import com.wenhsiaoyi.jackcompass.ui.theme.DialText
import com.wenhsiaoyi.jackcompass.ui.theme.DialTickMajor
import kotlinx.coroutines.delay

/*
 * 時序對應設計基準圖格 2：0.0s 淡入 → 0.5s 縮放到 1.0 → 1.6s 後淡出。
 * 這段動畫不解鎖任何功能、不寫入任何「已贊助」狀態。
 */
private const val FADE_IN_MS = 300
private const val SCALE_MS = 500
private const val HOLD_MS = 1_600L
private const val FADE_OUT_MS = 300

/**
 * 「降低動態效果」的偵測。
 *
 * 本專案假定過的 `AccessibilityManager.isReducedMotionSettingEnabled()` **不存在**：
 * 已用 javap 查證 android-33／36／37.0／37.2 的 `android.jar`，`AccessibilityManager`
 * 沒有任何 reduce／motion 相關方法（連 `Settings.Global`／`Settings$Secure` 都沒有
 * reduce motion 常數）。系統那個開關（設定 → 無障礙 → 視覺效果 → 移除動畫）對 App
 * 可見的訊號是 `Settings.Global` 的動畫縮放係數歸零，所以讀它；兩個常數自 API 17
 * 起公開，minSdk 24 全覆蓋。
 */
@Composable
private fun isReduceMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

@Composable
fun SponsorThanksOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = isReduceMotionEnabled()
    var entered by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else FADE_IN_MS),
        label = "thanksAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (entered && !reduceMotion) 1f else 0.8f,
        animationSpec = tween(if (reduceMotion) 0 else SCALE_MS),
        label = "thanksScale",
    )

    LaunchedEffect(Unit) {
        entered = true
        delay(HOLD_MS)
        entered = false
        delay(if (reduceMotion) 0L else FADE_OUT_MS.toLong())
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialScreen.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 外圈橘色光暈：降低動態效果時不出現
                if (!reduceMotion) {
                    Canvas(modifier = Modifier.size(232.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(CompassOrange.copy(alpha = 0.35f), Color.Transparent),
                                radius = size.minDimension / 2f,
                            ),
                            radius = size.minDimension / 2f,
                        )
                    }
                }
                Canvas(modifier = Modifier.size(208.dp).scale(scale)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(DialFace, radius = r * 0.98f, center = center)
                    drawCircle(DialRing, radius = r * 0.90f, center = center, style = Stroke(width = r * 0.03f))
                    drawRose(center, r * 0.80f)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.sponsor_thanks_title),
                style = MaterialTheme.typography.headlineSmall,
                color = DialTickMajor,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sponsor_thanks_body),
                style = MaterialTheme.typography.bodyMedium,
                color = DialText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 羅盤玫瑰：與指南針盤面同一個造型，N 指針橘色、其餘壓暗。 */
private fun DrawScope.drawRose(center: Offset, reach: Float) {
    val half = reach * 0.16f
    fun diamond(tip: Offset, side: Offset) = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(center.x + side.x * half, center.y + side.y * half)
        lineTo(center.x - side.x * half, center.y - side.y * half)
        close()
    }
    drawPath(diamond(Offset(center.x, center.y - reach), Offset(1f, 1f)), CompassOrange)
    drawPath(diamond(Offset(center.x, center.y - reach), Offset(-1f, -1f)), CompassOrange.copy(alpha = 0.72f))
    drawPath(diamond(Offset(center.x, center.y + reach), Offset(1f, 1f)), DialRing)
    drawPath(diamond(Offset(center.x, center.y + reach), Offset(-1f, -1f)), DialRing.copy(alpha = 0.7f))
    drawPath(diamond(Offset(center.x + reach, center.y), Offset(1f, -1f)), Color(0xFF2A313C))
    drawPath(diamond(Offset(center.x + reach, center.y), Offset(-1f, 1f)), Color(0xFF2A313C).copy(alpha = 0.75f))
    drawPath(diamond(Offset(center.x - reach, center.y), Offset(1f, -1f)), Color(0xFF2A313C))
    drawPath(diamond(Offset(center.x - reach, center.y), Offset(-1f, 1f)), Color(0xFF2A313C).copy(alpha = 0.75f))
    drawCircle(DialTickMajor, radius = reach * 0.10f, center = center)
    drawCircle(DialScreen, radius = reach * 0.04f, center = center)
}
```

`Color(0xFF2A313C)` 是指南針盤面東／西指針用的壓暗灰，`ui/theme/Color.kt` 裡沒有對應符號，所以這裡用字面值——要和 `CompassScreen` 畫的玫瑰對得上的話，改之前先兩處比對。

- [ ] **Step 2: 接進 `JackCompassApp`（要蓋住導覽列）**

覆蓋層必須蓋住整個畫面，**包含導覽列**，所以不能放進 `Scaffold` 的 content lambda（那個 `Box(modifier = Modifier.padding(innerPadding))` 在 `JackCompassApp.kt:97`，它的底邊被 innerPadding 掐掉，橫幅與導覽列那一截蓋不到）。把整個 `Scaffold(` … `)` 用一層 `Box` 包住：

```kotlin
    var showThanks by remember { mutableStateOf(false) }
    DisposableEffect(session) {
        session.onSponsorDismissed = { showThanks = true }
        onDispose { session.onSponsorDismissed = null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(…原有內容原封不動…) { innerPadding -> … }
        if (showThanks) {
            SponsorThanksOverlay(onFinished = { showThanks = false })
        }
    }
```

`onDispose` 那行是必須的：`AdSession` 活在行程層，沒清掉的話它會一直握著一個指向**已死亡組合層**的 lambda，語系切換重建之後那次贊助就不會再觸發動畫。

`JackCompassApp` 需補 `import androidx.compose.foundation.layout.fillMaxSize`、`androidx.compose.runtime.DisposableEffect`、`androidx.compose.runtime.mutableStateOf`、`androidx.compose.runtime.remember`、`androidx.compose.runtime.setValue`、`com.wenhsiaoyi.jackcompass.ui.components.SponsorThanksOverlay`。

- [ ] **Step 3: 補英文鍵**

```xml
    <string name="sponsor_thanks_title">Thanks for your support</string>
    <string name="sponsor_thanks_body">Your support keeps this tool free</string>
```

- [ ] **Step 4: 建置＋測試**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 5: 實機驗動畫與無障礙**

```powershell
.\gradlew.bat installDebug
```

走一次贊助流程，確認：關掉插屏後出現感謝動畫、約 1.6 秒自動消失、**沒有**解鎖任何新東西（功能清單前後一致）。再到系統設定開「降低動態效果」重跑一次，預期只剩淡入淡出、羅盤不縮放、無光暈。截圖對 `spec/sponsor-flow-mock.svg` 格 2。**介面由使用者驗收。**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/wenhsiaoyi/jackcompass/ui/ app/src/main/res/values/strings.xml
git commit -m "feat: play a non-unlocking sponsor thank-you animation after the interstitial"
```

---

## Task 9: 三份語系檔補齊

**Files:**
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: Task 7 的 8 個 `settings_*` 鍵與 Task 8 的 2 個 `sponsor_thanks_*` 鍵，共 **10 個**。（橫幅本身沒有可見文字要翻：素材由 Google 主控，`BannerSlot` 只是個 `AndroidView` 槽，所以本輪**不新增** `ads_banner_label`——掛著沒人讀的字串會被 lint 報 `UnusedResources`，而 Task 10 要求廣告相關的新 error 為 0。）

- [ ] **Step 1: 先讀詞彙規則**

```bash
cat .agents/tw-translate/SKILL.md
python .agents/tw-translate/scripts/lookup.py "Watch an ad to support us"
python .agents/tw-translate/scripts/lookup.py "Adjust ad consent options"
```

- [ ] **Step 2: 寫繁中**

`values-zh-rTW/strings.xml`，接在 `settings_open` 之後：

```xml
    <string name="settings_sponsor_section">支持開發者</string>
    <string name="settings_sponsor_title">看廣告支持開發者</string>
    <string name="settings_sponsor_subtitle">播放一段全螢幕廣告，不解鎖任何功能</string>
    <string name="settings_sponsor_ready">按一下就播放</string>
    <string name="settings_sponsor_preparing">正在準備廣告…</string>
    <string name="settings_sponsor_unavailable">暫時沒有可播的廣告</string>
    <string name="settings_ads_section">廣告設定</string>
    <string name="settings_ads_privacy">調整廣告同意選項</string>

    <string name="sponsor_thanks_title">感謝贊助</string>
    <string name="sponsor_thanks_body">你的支持讓這個工具繼續免費</string>
```

- [ ] **Step 3: 寫簡中**

`values-zh-rCN/strings.xml`，同位置：

```xml
    <string name="settings_sponsor_section">支持开发者</string>
    <string name="settings_sponsor_title">看广告支持开发者</string>
    <string name="settings_sponsor_subtitle">播放一段全屏广告，不解锁任何功能</string>
    <string name="settings_sponsor_ready">按一下就播放</string>
    <string name="settings_sponsor_preparing">正在准备广告…</string>
    <string name="settings_sponsor_unavailable">暂时没有可播的广告</string>
    <string name="settings_ads_section">广告设置</string>
    <string name="settings_ads_privacy">调整广告同意选项</string>

    <string name="sponsor_thanks_title">感谢赞助</string>
    <string name="sponsor_thanks_body">你的支持让这个工具继续免费</string>
```

- [ ] **Step 4: 驗三份語系鍵數一致**

```bash
for f in values values-zh-rTW values-zh-rCN; do
  printf '%s ' "$f"; grep -o 'name="[a-z_]*"' app/src/main/res/$f/strings.xml | sort -u | wc -l
done
```

預期：三個數字相同（`values/` 另含 `app_name`，所以會多 1；只要 TW／CN 彼此相等且都等於 `values/` 減 `app_name`）。不一致就補齊缺的鍵。

- [ ] **Step 5: 驗繁中沒有簡體字殘留（帶負對照組）**

`po.py validate` 只吃 `.po` 檔（實查 `--help` 的位置參數寫明「PO 檔案路徑」），喂 XML 無效，所以自己掃。腳本內容全是 ASCII、字用 `\u` 轉義寫——直接貼中文進 heredoc 曾被 shell 弄壞過，別那樣做。輸出也用 `unicode_escape` 印，避免 Windows 控制台編碼炸掉。

```bash
cat > /tmp/zhtw_check.py <<'PY'
import sys
# 只放「簡體專用、繁中絕不出現」的字形，用 \u 轉義寫：
# 针 仪 罗 盘 传 设 语 东 经 纬 没 无 广 锁 选 备 暂 谢 赞 让 续 费 调 发
FORBIDDEN = "\u9488\u4eea\u7f57\u76d8\u4f20\u8bbe\u8bed\u4e1c\u7ecf\u7eac" \
            "\u6ca1\u65e0\u5e7f\u9501\u9009\u5907\u6682\u8c22\u8d5e\u8ba9" \
            "\u7eed\u8d39\u8c03\u53d1"
text = open(sys.argv[1], encoding="utf-8").read()
hits = sorted({c for c in text if c in FORBIDDEN})
print("%s forbidden=%d %s" % (sys.argv[1], len(hits),
      "".join(hits).encode("unicode_escape").decode()))
PY
python /tmp/zhtw_check.py app/src/main/res/values-zh-rTW/strings.xml
python /tmp/zhtw_check.py app/src/main/res/values-zh-rCN/strings.xml
```

預期，**兩條都要貼進報告**：
- zh-rTW → `forbidden=0`。
- zh-rCN → `forbidden` 大於 0（簡體檔是刻意的，它必須被命中）。

第二條是負對照組：它如果也是 0，代表腳本或編碼壞了，這次檢查毫無意義，**先修腳本再繼續**——這是本專案唯一能自動跑的繁中把關。

這個腳本已拿現行檔案實跑過一次：zh-rTW 與 `values/`（英文）都是 `forbidden=0`，zh-rCN 是 `forbidden=12`（`\u4e1c\u4eea\u4f20\u65e0\u6ca1\u76d8\u7eac\u7ecf\u7f57\u8bbe\u8bed\u9488`＝东仪传无没盘纬经罗设语针）。補完本輪十句後 CN 那個數字應該再漲（多出广告、开发者、解锁、选项、准备、暂时、感谢、赞助、让、继续、免费、调整），TW 必須維持 0。

順帶一條教訓：這台機器的控制台是 cp950，**Python 直接把 CJK 印到 stdout 會 `UnicodeEncodeError`**（實測過），所以才用 `unicode_escape` 印。

人工再核一遍 Step 2 的十句：不得出現簡體寫法。

- [ ] **Step 6: 建置＋實測三語系**

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

在 App 內切 英文／繁體中文／简体中文 三輪，確認設定頁兩個新區塊與感謝動畫的文字都跟著翻、沒有缺字或簡繁互竄。**介面由使用者驗收。**

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/values-zh-rTW/strings.xml app/src/main/res/values-zh-rCN/strings.xml
git commit -m "feat: localize the ad and sponsor strings for zh-TW and zh-CN"
```

---

## Task 10: 最終驗證與上架前交接

**Files:**
- Modify: `AGENTS.md`（架構節加 `ads/`、已知事項加廣告條目）
- Modify: `docs/AGENTS.html`（AGENTS.md 改完要重新產生，它不會自己跟著更新）
- Modify: `README.md`（功能清單加廣告；若已提到分頁結構則一併核對）

**Interfaces:**
- Consumes: 前九個 Task 的全部產出。
- Produces: 一份可交給 Play Console 的驗證紀錄與清單。

- [ ] **Step 1: 全套自動化驗證**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lint
```

預期：`test` 與 `assembleDebug` 為 `BUILD SUCCESSFUL`。`lint` **預期仍失敗**，error 數應為 spec 風險第 4 點記的 3 條既存問題（震動用的 `VibrationEffect.createOneShot`／`Vibrator.vibrate` 要 API 26、`app_name` 未翻成 zh）。**逐條比對：廣告相關的新 error 必須為 0。** 若出現新的廣告 error，回到對應 Task 修，不要往下。把三份輸出都貼進報告。

那 3 條既存 error 要不要在上架前清掉，spec 明確把裁決留給使用者，**本計畫不處理、也不要順手修**——它牽涉 minSdk 與翻譯，是另一輪的事。報告裡點名提醒一次即可。

- [ ] **Step 2: 確認沒有誤用正式 ID**

```bash
grep -rn "9662224353354096" app/src/main/java/ app/src/debug/ || echo "OK: 正式 ID 只出現在 main 的 resources"
```

預期：`app/src/debug/` 與所有 Kotlin 檔都不含正式 ID（只在 `app/src/main/res/values/ads.xml`）。

- [x] **Step 3: 驗「一次執行只播一次」**

```powershell
.\gradlew.bat installDebug
```

在 realme 上：冷啟動 → 橫幅出現 → 等滿 30 秒收起 → 切背景再回前台 → 橫幅**不得**再出現 → 完全殺掉進程再冷啟動 → 橫幅再次出現並重新倒數 30 秒。這驗的是 spec「本次執行」的定義。

實測（2026-10-23，RMX2144）：四段全過。15:15:41 出現 → 15:16:11 收起 → HOME 後回前台不再出現 → 殺進程冷啟動 15:18:12 再次出現並重新倒數。

- [x] **Step 4: 驗盤面不受橫幅影響**

橫幅顯示中與收起後各截一張指南針畫面，量盤面直徑（全解析度截圖，不要憑縮圖判斷）。預期：**直徑相同**，只有中心下移。若直徑變了，`minOf(maxWidth, maxHeight)` 的假設在該裝置不成立，回報實測數值並同步修正 spec「介面變更」與 `spec/sponsor-flow-mock.svg` 格 4。

實測：兩張全解析度截圖（1080×2400）逐列掃描，圓盤最寬都是 934 px（x 72–1006），直徑未變；圓心 y 1295 → 1220，只上移約 75 px。假設成立，spec 與 mock 不用改。

- [x] **Step 5: 驗同意鏈順序（規範地區）**

debug 構型的 `ads_force_eea_debug=true` 已是 EEA。確認 AdMob 後台已建立並發布同意訊息，否則表單永遠不出現、回呼直接往下走，會被誤判成整合失敗。驗三件事：首次啟動出現表單；拒絕後橫幅整場不出現、贊助列隱藏、**「調整廣告同意選項」仍可見**；按它能重新開表單。

實測（2026-10-23）：首次啟動出現表單 ✓（前提是把 logcat 給的 hashed id 填進 `app/src/debug/res/values/ads.xml`，沒註冊時 `IABTCF_gdprApplies` 是 0、表單不出現）；「調整廣告同意選項」能重新開表單 ✓；但**第二件事不成立**——清掉 UMP 的 SharedPreferences 重跑、在表單按「Do not consent」（存進去的是 `IABTCF_PurposeConsents = 00000000000`）之後，`canRequestAds()` 仍回 true，橫幅換成非個人化測試素材照樣出現、贊助列也仍在。閘門本身沒寫錯，是「拒絕 = 沒廣告」這個開關在 AdMob 後台的同意訊息設定裡。**使用者已裁決維持現狀**（拒絕者仍看非個人化廣告，換收入），細節見 spec「同意鏈」與「上架前清單」。另外：首次啟動是 GPS 權限對話框先於同意表單，順序暫不調整。

- [x] **Step 6: 驗全螢幕廣告關閉後的版面（spec 風險 3）**

插屏關掉、感謝動畫也消失之後，截一張指南針畫面，和廣告之前的那張比對：盤面大小與位置、導覽列高度、頂部經緯度那列都要回到原樣，**不該有整頁上下抖動或內距跑掉**。Android 15 以上 edge-to-edge 是預設行為，這是已知會踩的坑，只能在實機驗。若有抖動，代表 `BannerSlot`／`NavigationBar` 與 insets 的分工要重新談——把實測截圖與描述寫進報告，不要自行加 insets 補丁。

實測（Android 11 裝置，風險 3 講的是 15 以上，這條只能算半覆蓋）：插屏 → 關掉 → 感謝動畫播完消失後，設定頁 `uiautomator` 逐節點 bounds 與播放前**完全相同**（`設定` 468,252-612,348、贊助卡 72,936-1008,1140、同意卡 72,1434-1008,1590、導覽列 0,2160-1080,2292），無抖動、內距未跑掉。感謝動畫本身為半透明遮罩，底下頁面位置不動。

- [ ] **Step 7: 驗 release 構型的 ID 與除錯地區（spec 風險 2）**

spec 那條問的是「除錯地區開關在 release 是否還有效」，官方沒明文。**本專案不必回答這個問題**，因為 `ads_force_eea_debug` 由資源決定：`app/src/main/res/values/ads.xml` 是 false，只有 `app/src/debug/res/values/ads.xml` 覆寫成 true，所以 release 構型根本不會走到 `ConsentDebugSettings` 那條分支。這條 Step 就是把「release 拿到的確實是 false 與正式 ID」證明出來：

```powershell
.\gradlew.bat assembleRelease
E:\Android\Sdk\build-tools\37.0.0\aapt2.exe dump resources app\build\outputs\apk\release\app-release-unsigned.apk | Select-String "ads_force_eea_debug" -Context 0,2
E:\Android\Sdk\build-tools\37.0.0\aapt2.exe dump resources app\build\outputs\apk\release\app-release-unsigned.apk | Select-String "admob_app_id" -Context 0,3
```

預期：`ads_force_eea_debug` 那一節值是 `() false`、`admob_app_id` 是 `ca-app-pub-9662224353354096~1210011940`。`aapt2 dump resources` 對 boolean 的印法已在 Task 1 Step 4 實測為 `() true`／`() false`，不是 `#0x…` token。把兩條輸出原樣貼進報告；若 token 對不上，先確認是不是限定符分組的差異，不要改指令。產出檔名是 `app-release-unsigned.apk` 屬正常——**release 簽名是非目標，不要在這裡加 signingConfig**。若 `assembleRelease` 因找不到產出路徑而失敗，先用 `Get-ChildItem app\build\outputs\apk\release` 看實際檔名，不要把指令改到別處。

- [ ] **Step 8: 更新文件**

`AGENTS.md`：架構節加 `ads/` 兩截式說明（`BannerPolicy`／`SponsorPolicy` 是第二條純函式接縫，`AdMobBanner`／`AdMobInterstitial`／`AdBootstrap`／`AdSession` 是唯一接觸 SDK 之處）；測試落差節把「只有 sensor/ 這一條接縫」改成兩條；已知事項加兩條——「開發期間不得點自己的廣告」、「取 Activity 用 `androidx.activity.compose.LocalActivity`（`compose-ui` 本身沒有，是 `activity-compose:1.13.0` 提供）；平台沒有公開的 reduce-motion API（`AccessibilityManager` 查無相關方法），「降低動態效果」靠 `Settings.Global` 的 `ANIMATOR_DURATION_SCALE`／`TRANSITION_ANIMATION_SCALE` 是否歸零判定」；工具鏈陷阱節加 `GRADLE_USER_HOME=E:\gradle-home`。改完重新產生 `docs/AGENTS.html`。`README.md` 功能清單加廣告與贊助入口。

- [ ] **Step 9: 列出需要使用者在網頁上做的事（原封不動照 spec「上架前清單」）**

1. 資料安全申報：廣告 SDK 收集 Advertising ID 與廣告互動訊號，如實勾「收集」並寫用途。
2. 位置資料**另列一條**：本 App 已取得 GPS 經緯度，那是獨立的一筆申報。
3. 隱私政策 URL（AdMob 開通與 Play 上架兩邊都要，且能公開連到）。
4. 開發者網站根域的 `app-ads.txt`。
5. 內容評分問卷（有廣告會影響分級結果）。
6. 目標 API 門檻：targetSdk 37 已高於門檻，這關不用做事。
7. **AdMob 主控台把橫幅廣告位的自動重新整理關掉**，否則可能在 30 秒邊界上多載入一則，我方計數與實際 impressions 對不上。

- [ ] **Step 10: Commit**

```bash
git add AGENTS.md docs/AGENTS.html README.md
git commit -m "docs: record the ads architecture and the pre-release checklist"
```

---

## 已驗證 SDK 表面（Task 2 Step 5 填入）

> 本表是包裝層 import 的唯一依據。官方說明的套件名前綴與實際產出不一致時，**以本表為準**。

| 用途 | 已驗證全名 | 取得方式 |
|---|---|---|
| 初始化入口 | `com.google.android.libraries.ads.mobile.sdk.MobileAds`（`static void initialize(Context, InitializationConfig)`、`static boolean isInitialized()`） | aar classes.jar ＋ `javap` |
| `InitializationConfig` | `...sdk.initialization.InitializationConfig`，**只能經 `InitializationConfig.Builder(String applicationId)` 取得**（Builder 另有 `setRequestConfiguration`／`disableSdkCrashReporting`／`build()`；公開建構子是 9 參數的 synthetic，不要直接 new） | 同上 |
| 橫幅載入 | `...sdk.banner.BannerAd`（interface，extends `common.Ad`）：`static void load(BannerAdRequest, AdLoadCallback<BannerAd>)`；執行個體 `View getView(Activity)`、`AdSize getAdSize()`、`setAdEventCallback(BannerAdEventCallback)` | 同上 |
| 橫幅容器（本專案**不用**） | `...sdk.banner.AdView`（`FrameLayout`）：`loadAd(...)`／`registerBannerAd(...)`／`destroy()`——它假設 XML 或 imperative 容器，Compose 改用 `BannerAd.getView()`，見 Task 6 Step 2 | 同上 |
| 橫幅請求／尺寸 | `...sdk.banner.BannerAdRequest.Builder(String adUnitId, AdSize adSize)` → `build()`；`...sdk.banner.AdSize.BANNER`（`BANNER_WIDTH=320`、`BANNER_HEIGHT=50`，單位 dp）；另有 `getCurrentOrientationAnchoredAdaptiveBannerAdSize(Context,int)` | 同上，`javap -constants` |
| 載入回呼（橫幅與插屏共用） | `...sdk.common.AdLoadCallback<AdT>`（interface，兩個 default：`onAdLoaded(ad)`、`onAdFailedToLoad(adError)`——**Kotlin 覆寫時參數名要照 `ad`／`adError`**，否則 `onAdFailedToLoad(error)` 會得到參數名不一致的警告）——**沒有** `BannerAdLoadCallback`／`InterstitialAdLoadCallback` 這種型別 | 同上 |
| 插屏型別與回呼 | `...sdk.interstitial.InterstitialAd`（interface，extends `common.Ad`）：`static void load(common.AdRequest, AdLoadCallback<InterstitialAd>)`、`void show(Activity)`、`setImmersiveMode(boolean)`、`getAdEventCallback()`／`setAdEventCallback(InterstitialAdEventCallback)`——**Java 的 get/set 成對在 Kotlin 是屬性 `adEventCallback`，只能寫 `ad.adEventCallback = cb`**；事件介面 `...sdk.interstitial.InterstitialAdEventCallback extends common.AdEventCallback` | 同上 |
| 插屏請求 | `...sdk.common.AdRequest.Builder(String adUnitId)` → `build()`——**插屏沒有專屬的 `InterstitialAdRequest` 類別** | 同上 |
| 全螢幕事件 | `...sdk.common.AdEventCallback`（全 default）：`onAdShowedFullScreenContent()`、`onAdDismissedFullScreenContent()`、`onAdFailedToShowFullScreenContent(FullScreenContentError)`、`onAdImpression()`、`onAdClicked()`、`onAdPaid(AdValue)` | 同上 |
| 銷毀 | `...sdk.common.Ad.destroy()`——`BannerAd`／`InterstitialAd` 都繼承它，`BannerHandle.release` 就呼這個 | 同上 |
| UMP 入口 | `com.google.android.ump.UserMessagingPlatform`：`static ConsentInformation getConsentInformation(Context)`、`static void loadAndShowConsentFormIfRequired(Activity, ConsentForm.OnConsentFormDismissedListener)`、`static void showPrivacyOptionsForm(Activity, ConsentForm.OnConsentFormDismissedListener)` | UMP aar ＝ 一般 jar 結構，`javap` |
| UMP 同意狀態 | `com.google.android.ump.ConsentInformation`：`requestConsentInfoUpdate(Activity, ConsentRequestParameters, OnConsentInfoUpdateSuccessListener, OnConsentInfoUpdateFailureListener)`、`boolean canRequestAds()`、`PrivacyOptionsRequirementStatus getPrivacyOptionsRequirementStatus()`、`boolean isConsentFormAvailable()`、`reset()` | 同上 |
| UMP 參數與除錯 | `ConsentRequestParameters.Builder()`（**無參构造**）→ `setAdMobAppId(String)`／`setConsentDebugSettings(...)`／`setTagForUnderAgeOfConsent(boolean)`／`build()`；`ConsentDebugSettings.Builder(Context)` → `setForceTesting(boolean)`、`setDebugGeography(int)`、`addTestDeviceHashedId(String)`；常數在 `ConsentDebugSettings$DebugGeography.DEBUG_GEOGRAPHY_EEA` | 同上 |

（本表由 Task 2 的實際 Gradle 產出填入，**它是後續包裝層 import 的唯一依據**。查證過程：`assembleDebug` 把 aar 落進 `E:\gradle-home\caches\modules-2\files-2.1\...`（`gradle :app:dependencies` 只抓 POM，不會下載 aar——別用它的結果判斷「找不到 aar」），解出 `classes.jar` 後 `javap -classpath <解壓目錄> <全名>`。）

與計畫原本的假設不同、已照本表修正的地方：

- 橫幅**不透過 `AdView`**，改走 `BannerAd.load(...)` → `bannerAd.getView(activity)`；`AdView` 那條路是 imperative 容器，跟 Compose 重組搶生命週期。
- 載入回呼是共用的 `common.AdLoadCallback`，不是各格式專屬的 `*LoadCallback`。
- 插屏用 `common.AdRequest`，沒有 `InterstitialAdRequest`。
- UMP 的 `showPrivacyOptionsForm` 掛在 `UserMessagingPlatform` 靜態方法上（不是 `ConsentInformation`）。
- `InitializationConfig` 一律經 `Builder(applicationId)`，不可直接 new。
- **`BannerAd.load(...)` 與 `BannerAd.getView(Activity)` 在 1.5.0 已標記 `@Deprecated`**（編譯會出現 `w: ... is deprecated. Use AdView.loadAd() or BannerAdPreloader instead.`）。本專案**故意繼續用它們**：官方建議的 `AdView` 是 imperative 容器（XML／`registerBannerAd`），交給 Compose `AndroidView` 會在每次重組搶生命週期；`BannerAdPreloader` 是預載路徑，spec 已定調不預載。實測 `assembleDebug` 綠、兩個警告不影響執行。升級 SDK 時這條要重新評估。
