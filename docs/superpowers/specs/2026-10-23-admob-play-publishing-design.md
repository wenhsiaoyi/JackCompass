---
date: 2026-10-23
status: 待使用者覆核
supersedes: none
---

# 廣告（AdMob）與 Google Play 上架 — 設計定稿

一句話：在指南針 App 加上 AdMob 廣告（橫幅 ＋ 設定頁插屏），把 App 識別改成上架用的 ID，
並把同意閘門排在任何廣告請求之前。

## 目標

1. App 識別（`applicationId`）換成 `com.hywen.app.jackcompass`。
2. 橫幅廣告：每次啟動載入一次、顯示 30 秒後收起，本次執行不再出現。
3. 插屏廣告：只由設定頁的「支持開發者」入口觸發；使用者把插屏關掉後播一段感謝動畫。
4. 同意閘門（Google 的使用者訊息平台）在任何廣告請求之前完成。
5. 廣告行為的「規則」可以用單元測試證明，不靠實機運氣。

## 非目標（這輪不做）

- release 簽名、AAB 產出、R8 程式碼縮減。
- Play Console 上的資料安全申報、內容評分、`app-ads.txt`、隱私政策頁——程式碼碰不到，只在下方列成清單。
- 自動插屏、開屏（App Open）廣告、激勵廣告、付費移除廣告。
- 把原始碼目錄與 `namespace` 一起改名（只要 App 識別變，產生碼的位置不動）。

## 已定的設計裁決

| 議題 | 裁決 | 代價／理由 |
|---|---|---|
| 廣告 SDK | GMA Next-Gen（`ads-mobile-sdk` 1.5.0，2026-09-24，stable） | 官方現在的首選，minSdk 24 與 compileSdk 需求剛好對應本專案；但生態系還很新，社群可查的解答少 |
| 舊 SDK 路線 | 不採用（`play-services-ads` 25.5.0 已進入維護模式） | 官方說明自己建議遷移，選舊的要背一次未來遷移 |
| 橫幅收起條件 | 顯示滿 30 秒就收起，一則就好 | 兩套 SDK 都沒有內建的「播放 N 則後停止」開關，計數一定要 App 自己做 |
| 插屏出現時機 | 絕不自動播；只在設定頁贊助入口 | 使用者明確要求 |
| App ID 擺法 | 由程式初始化時傳入（Next-Gen 的做法），不寫在 manifest | Next-Gen 不在 manifest 宣告 App ID，這是它跟網路教學最不一樣的地方 |
| 正式 ID 與測試 ID | 正式 ID 給 release，debug 構型覆寫成 Google 官方測試 ID | 開發期間反覆載入或誤點自己的真廣告，AdMob 會判無效流量，嚴重者停權 |
| 套件名稱改動範圍 | 只改 App 識別；產生碼位置與原始碼目錄維持原樣 | 改動剩一行，12 個主原始碼檔與 2 個測試檔都不必搬 |

使用者提供的正式 ID（廣告位 ID 不是機密，會進版本庫）：

- App ID：`ca-app-pub-9662224353354096~1210011940`
- 橫幅：`ca-app-pub-9662224353354096/8618436515`
- 插屏：`ca-app-pub-9662224353354096/5418392251`

## 架構：沿用兩截式

本專案的感測器層已經是「純函式 ＋ 包裝層」兩截，廣告層照同一個形。

- **規則層**（純 JVM、無 Android 相依）：橫幅的 30 秒生命週期、本次執行是否已用掉、同意閘門判定、
  贊助入口的三態轉換。這是本專案第二條純函式接縫。
- **包裝層**（唯一接觸廣告 SDK 與平台 SDK 型別的地方）：初始化、同意鏈、橫幅檢視圖的產生與銷毀、
  插屏的載入與呈現，把廣告 SDK 的回呼翻譯成可觀察的狀態給 UI 讀。

分成兩截不是預留抽象：專案沒有 instrumented 測試的原始碼目錄，實機之外的驗證手段只有純函式這一條路，
而「30 秒」「一次執行一則」「未同意不得請求」這幾條規則恰好全是純判定。

## 橫幅狀態機

```text
未就緒 → 等待同意 → 載入中 → 顯示中（倒數 30 秒） → 本次執行已收起
                     ↘ 載入失敗 → 本次執行不出現
```

- **計時起點是廣告真正顯示出來**，不是 App 啟動。慢裝置上載入可能花 2～4 秒，用啟動當起點會讓
  使用者只看得到 26 秒，甚至還沒看到就被收起，一則 impressions 都沒進帳。
- 計時用單調時鐘；App 切到背景**不暫停**計時。
- **載入失敗就不重試**，本次執行徹底沒有橫幅。重試會把同一個廣告位刷好幾次，是 AdMob
  判定無效流量的典型行為。
- 語言切換或畫面重組**不得重新載入**：橫幅由組合層持有同一顆檢視圖（重新載入會多算 impressions，也會閃）。
- 30 秒寫成一處常數，不留可調選項（使用者沒要求設定頁能改）。
- 橫幅掛在底部導覽列正上方的 `bottomBar` 槽，所以指南針／水平儀／設定頁三處都看得到，
  30 秒一到整條一起消失。

## 同意鏈（順序不可調）

1. **每次啟動**都請求同意資訊更新。只在首次啟動跑一次是常見的錯，會讓使用者事後改同意不生效。
2. 需要時載入並顯示同意表單。
3. 表單流程結束後才初始化廣告 SDK，而且**必須丟到背景執行緒**（Next-Gen 的硬性要求，漏了會初始化失敗）。
4. 檢查「可否請求廣告」，為準才放行橫幅載入與贊助入口。
5. 地區由 SDK 自動判定（規範地區為歐洲經濟區、英國、瑞士），非規範地區不會看到任何表單，
  鏈路直接往下走。

另外設定頁要有一列「廣告設定」開啟隱私選項表單——那是規範區使用者事後改同意的唯一入口，
Google 的政策要求一定要有這個入口。

使用者**拒絕同意**時：App 端的閘門是 `canRequestAds()`。實測（EEA 除錯地區 + realme）在
`IABTCF_PurposeConsents` 全 0 的拒絕狀態下它仍回 **true**，因為 Google 會退回非個人化廣告（NPA）——
所以「拒絕後橫幅整場不出現」不是 App 端能決定的事，開關在 AdMob 後台的同意訊息設定
（選「顯示訊息且不提供廣告」才會讓 `canRequestAds()` 回 false）。
程式碼維持只認 `canRequestAds()`：它回 false 時橫幅整場不出現、贊助入口整列藏起來，
App 其餘功能完全不受影響。「廣告設定」那一列則要留著，否則使用者沒有反悔的管道。

✅ **2026-10-23 裁決：維持現狀。** 後台用預設的「投放非個人化廣告」，拒絕同意的人照樣看到橫幅與贊助入口，
換取收入；不額外做「拒絕就全關」的 App 端開關。

## 贊助入口

設定頁新增一列「看廣告支持開發者」，三個狀態：可點、準備中、暫時沒有可播的廣告。
按下去 → 全螢幕插屏 → 使用者關掉 → 感謝動畫覆蓋層。

- 載入成功但**呈現失敗**（例如廣告素材跟系統版本打架）：走同一句「暫時沒有可播的廣告」，
  **不播感謝動畫**——沒播過就不該假裝播過。
- 播完後入口回到可點，**不設次數上限**（每一次都是使用者自己點的，政策上沒有「給獎勵換點擊」的疑慮），
  但「準備中」與廣告正在播的期間不得重複觸發，也不得把請求排隊。

✅ **這裡改掉了使用者的一句話，並已在 2026-10-23 獲裁決（「確認獎勵廣告換純動畫的方案」）**：
原話是「完成收到**獎勵**後顯示感謝」。插屏格式沒有獎勵機制（要的是另一種 rewarded 格式），
而且政策上不能承諾「看完廣告給你東西」。所以這輪做成：
使用者自願看一支全屏廣告 → 播一段感謝動畫，**不解鎖任何功能、不寫入任何「已贊助」狀態**，
文案也不出現「獎勵」兩字。若要真的發獎勵，得改走 rewarded 格式並多做一套會留下來的狀態。

## 介面變更

- 橫幅槽（新）：位於導覽列正上方；收起時導覽列自己往下補位。
  **盤面直徑不會變大**——指南針盤面是 `minOf(maxWidth, maxHeight)`、被寬度綁死，
  收起橫幅只讓盤面中心往下移約橫幅高度的一半；多出來的空間成為上下留白。
  目前 `enableEdgeToEdge()` 已開、系統內距（insets）由導覽列自行處理，橫幅不重複處理 insets。
- 設定頁新增兩區塊：支持開發者、廣告設定（隱私選項表單）。現有內容只有語系切換，新區塊接在它下面。
- 感謝動畫：全畫面覆蓋層，中央是羅盤玫瑰（跟指南針盤面同一顆母圖）加一圈橘色光暈，
  從 0.8 縮放到 1.0 並淡入，下方「感謝贊助」＋一行支持語，約 1.6 秒自動消失；
  系統開啟「降低動態效果」時只做淡入淡出，不縮放、不出現光暈。畫面見 `spec/sponsor-flow-mock.svg`。

## 本地化

三份語系檔都要補（英／繁／簡）。繁體依 `.agents/tw-translate` 的規則：用「橫幅廣告」「插屏廣告」
「設定」「資料」；品牌名 AdMob、Google 保留英文；中英之間加半形空格；標點用全形。
介面文案不要寫成行銷口吻，按鈕動詞要說清楚按下之後會發生什麼事。

## 測試接縫

規則層的紅測試至少蓋：

- 30 秒邊界（29.999 秒仍可見、30.000 秒收起）。
- 一次執行只載入一次；收起後任何事件都不得讓它再出現。
- 載入失敗的分支：不重試、本次執行就此收幕。
- 同意未完成前，橫幅與插屏都不得產生請求。
- 贊助入口三態的轉換（含「準備中」重複點擊不排隊）。

既有的感測器純函式測試必須維持綠燈。

## 驗證計畫

- JVM 單元測試蓋住上面那條接縫。
- debug 構型（測試 ID）在 realme 與 emulator 實跑：橫幅 30 秒前／後截圖（比對盤面直徑應不變、
  只有中心下移）、插屏 → 感謝動畫全程。
- 同意表單本身要驗，得把裝置強制成規範地區（平台 SDK 有除錯地區開關），而且**你的 AdMob 後台必須
  已經建立並發布同意訊息**——沒建立時表單永遠不出現，回呼直接往下走，看起來會像「整合失敗」。
- 表單畫面由 Google 主控，會改版，所以本定稿不放它的截圖；等實裝後截真圖附在驗證紀錄。
- 正式 ID 只在 release 或使用者手動切換後驗一次；開發期間不得點自己的廣告。

### 實測紀錄（2026-10-23，realme RMX2144 / Android 11 / debug 測試 ID）

- 同意鏈**在首次啟動出現表單**；把裝置註冊進除錯地區之前 `IABTCF_gdprApplies` 是 0、表單不出現，
  註冊後變 1、表單出現。除錯地區只認 `addTestDeviceHashedId`，而那個 hash 只能從 logcat 抄，
  不能用 `ANDROID_ID` 自己算（MD5／SHA-1／SHA-256 全部比對過，都不對）。
- 橫幅：出現 → 滿 30 秒收起 → 退到背景再回來**不再出現** → 殺行程重新啟動才再出現一次、重新倒 30 秒。
- 盤面：有橫幅與無橫幅兩張全解析度截圖實測，圓盤水平最寬都是 **934 px**（x 72–1006），
  直徑完全沒變；橫幅只把整盤往上推約 75 px（圓心 y 1295 → 1220）。本輪預測成立，`spec/sponsor-flow-mock.svg` 不用改。
- 插屏 → 感謝動畫全程正常；動畫結束後設定頁每個節點的 bounds 與播放前**逐筆相同**，沒有 insets 抖動（spec 風險 3 解除）。
- 「調整廣告同意選項」可重新開表單（Google 主控的表單依定規不附截圖）。
- 拒絕同意（`IABTCF_PurposeConsents` = `00000000000`）後橫幅**仍出現**，素材換成非個人化的測試橫幅，
  贊助列也仍在——即上方那條裁決的由來。
- 首次啟動時**GPS 定位權限對話框先於同意表單**出現（spec 原本判定為無法驗證）。
  現況順序是：系統權限框 → 同意表單 → 主畫面。要不要把同意表單排到最前面，另輪裁決。

## 上架前清單

### 已查證（2026-10-23 在本機實跑，不是推測）

- `.\gradlew.bat bundleRelease` **通過**。**當天兩次快照要分清**：
  - 第一次（補簽章前）：`app-release.aab` 14,237,992 bytes／538 筆 entry，
    頂層**沒有任何簽章檔**（只有各函式庫帶進來的 `base/root/META-INF/*.version`），
    那時 `release` 型別確實沒有 `signingConfig`。Play Console 不收未簽章的 AAB，
    所以**當時真正卡住上傳的是這一條**，不是下面那 3 條 lint。
  - 第二次（補簽章後，見下方「簽章」節）：541 筆 entry／14.27 MB，頂層有
    `META-INF/UPLOAD.SF`／`UPLOAD.RSA`，**已用 upload key 簽好**。
- **既存 lint error 三條不會擋 release 建置**：實測 `lintVitalRelease` 通過——它只擋 fatal 級，
  這三條是 error 級。本檔風險 4 原本寫「上架前建議一併清掉」，現已修正為事實描述。
  其中兩條仍值得處理，因為是**真的會當**：`VibrationEffect.createOneShot`／`Vibrator.vibrate` 要 API 26
  而 minSdk 24，API 24/25 的裝置按到刻度震動就 crash。第三條（`app_name` 沒翻成中文）只是清單瑕疵。
- **16 KB page size 這關過**：AAB 內只有 `libandroidx.graphics.path.so` 一個原生庫（4 個 ABI），
  四者的 `PT_LOAD` 對齊全是 `0x4000`（= 16 KB）。廣告 SDK 沒有帶進任何 `.so`。
- **release 變體用的是正式 ID，且除錯開關全關**：合併後的資源實查
  `ads_force_eea_debug = false`、`ads_debug_device_hashed_id` 為空字串，三條廣告 ID 都是正式的
  （`…~1210011940`／`…/8618436515`／`…/5418392251`），一筆測試 ID 都不在 release 裡。
  所以風險 2 那條「除錯地區在 release 是否失效」不需要答案——我們不靠 SDK 自行忽略，
  是 release 自己把開關關掉、註冊清單留空。
- **UMP 那個靜默坑在 release 也封閉**：release 的 bundle manifest 有
  `com.google.android.gms.ads.APPLICATION_ID` 那一列（值 `@string/admob_app_id`，release 解析成正式 App ID）。
- 其他 release manifest 實查：`debuggable` 未出現（即 false）、`package="com.hywen.app.jackcompass"`、
  `versionCode 1`／`versionName 1.0`；權限是 `ACCESS_FINE_LOCATION`／`ACCESS_COARSE_LOCATION`／`VIBRATE`
  三條自己的，加上廣告函式庫帶入的 `INTERNET`／`ACCESS_NETWORK_STATE`／`READ_BASIC_PHONE_STATE`／`WAKE_LOCK`／`FOREGROUND_SERVICE`。
- **目標 API 門檻過**：Play 自 2026-08-31 起要求新 App 與更新都 target API 36 以上，現況 `targetSdk = 37`。
  （來源：[Play 目標 API 要求](https://developer.android.com/google/play/requirements/target-sdk)、
  [開發者支援頁](https://support.google.com/googleplay/android-developer/answer/11926878)）

### 簽章（2026-10-23 已補上，實測過）

- **這把金鑰只做 upload key**（2026-10-23 裁決：走 Play App Signing，app signing key 由 Google 保管）。
  所以密碼或檔案遺失時，**在 Play Console 重置 upload key 就能接手**，不會丟掉這款 App。
- 產生方式：`keytool -genkeypair -storetype JKS -alias upload -keyalg RSA -keysize 2048 -validity 10000`，
  DName 是 `CN=JackCompass, OU=Release, O=hywen, L=Taipei, C=TW`，到期日 2054-03-10、自簽證書。
- 兩個檔案，**都已被 `.gitignore` 蓋住**（`git check-ignore` 驗過）：
  `app/upload-keystore.jks` 與根目錄的 `keystore.properties`（內含隨機 32 字元密碼，
  `storePassword` 與 `keyPassword` 同值，純 ASCII 存檔——Java 的 `Properties.load` 走 ISO-8859-1，
  註解塞非 ASCII 會變亂碼，所以刻意不用）。
- `app/build.gradle.kts` 的接法：`signingConfigs { create("upload") }` 只在 `keystore.properties`
  **存在**時建立，`release.signingConfig = signingConfigs.findByName("upload")`。
  實測過缺檔那條路：把 properties 改名後 `bundleRelease` 仍 BUILD SUCCESSFUL，
  產出頂層 `META-INF/` 是空的（未簽章）——換機器不會被簽章檔卡住建置。
- **已簽章的證據（不是「應該會簽」）**：`app-release.aab`（541 筆 entry、14.27 MB）頂層有
  `META-INF/UPLOAD.SF`／`UPLOAD.RSA`／`MANIFEST.MF`；解 `UPLOAD.RSA` 裡的 PKCS#7 證書，
  跟 `keytool -exportcert` 從 keystore 匯出的 DER **逐位元組相同**，
  SHA-256 都是 `00:86:08:A2:75:E2:81:76:B3:BC:A1:89:0A:4C:60:19:CD:C0:30:F8:31:72:9D:C1:23:DD:28:96:B9:BE:A5:28`。
- **注意**：跑過「缺 properties」那次驗證之後，盤上的 AAB 會變成未簽章版；
  要上傳前請確認 `keystore.properties` 在位，並重跑 `bundleRelease`。
- **上傳走 Play Developer API 這條已經查證過了（2026-10-23 用你的 service account 實打 API）**：
  - 身分是 `playapi-hywen@gen-lang-client-0845794124.iam.gserviceaccount.com`（金鑰在
    `Documents\Google\` 底下，**在 repo 外、沒被 track**，這是對的）。JWT 換 token 回 200，
    `androidpublisher` scope 有效。
  - **權限是夠的**：`POST applications/com.hywen.app.jackcompass/edits` 回 200（真的建立到 edit session），
    在該 edit 內 `tracks.list`／`tracks.get`／`details` 都回 200，探測完用 `edits.delete` 回 204 丟掉，
    **沒有任何東西被 commit**。能開 edit 就是上傳 AAB 需要的那一層權限。
  - **這款 App 在 Play 上已經存在但還是空的**：`details` 回 `defaultLanguage = zh-TW`（上架資料的骨架建好了），
    `production`／`internal`／`beta` 三個 track 都**沒有 releases** → 你這一次是**首次發布**，不是更新。
  - **兩個還沒驗的**：`bundles.upload` 與 `tracks.update`／`edits.commit` 沒試——那需要一支**已簽章**的 AAB
    才有意義。另外「能開 edit」無法百分百區分角色是「管理草稿與發布」還是更大的權限。
  - **`docs/upload_to_play.py` 今天跑不起來**：本機沒有 `googleapiclient`（`ModuleNotFoundError`），
    `google.oauth2` 也沒有；上面的探測是用本機已有的 `cryptography`＋`requests`＋PyJWT 直接簽 JWT 打的，
    沒裝任何套件。要沿用那個腳本就得先補依賴，或改用這種直接打 REST 的寫法。

### 網頁上：Play Console 的 11 項必要工作（2026-10-23 晚間實測）

**這節的狀態是活的**：寫下的當下是 **已完成 4 項，共 11 項**（請說明應用程式的內容、設定隱私權政策、
登入詳細資料、廣告 已轉綠）。來源是我用你已登入的 Chrome 唯讀抓的——只 navigate 與讀文字，
沒按過任何「儲存／提交／送審」。**要引用那個數字就重新讀一次資訊主頁**，它會自己動。

- 兩個最好用的頁面：`…/app-dashboard`（11 項的勾與箭頭都在這）、`…/app-content/overview`
  （那頁的「需要處理 (N)」是逐項聲明最快的清單）。
- 我猜過 `financial-feature`、`category-contact` 兩個 slug，都被 Console 退回 `/home`。
  那兩項請從資訊主頁點進去，別貼我編出來的網址。

#### 已經上線的三樣（Firebase Hosting，專案 `jackcompass-site`）

- `https://jackcompass-site.web.app/app-ads.txt` → 200、`text/plain; charset=utf-8`、59 bytes，
  內容逐字元等於 `google.com, pub-9662224353354096, DIRECT, f08c47fec0942fa0`。
  格式出處：[AdMob 官方 app-ads.txt 說明](https://developers.google.com/admob/android/app-ads)；
  少了它廣告會被標未授權，而且**放了到生效之間有延遲**，所以要越早放越好——今天（10-23）已經上線。
- `https://jackcompass-site.web.app/` → 200，開發人員網站根頁。**刻意沒放商店連結**：
  目前 App 只是 internal track 上的 draft，公開的商店 URL 會 404。
- **隱私政策網址要填 `https://jackcompass-site.web.app/privacy.html`，`.html` 不能省**——
  實測 `/privacy` 回的是 Firebase 的 404 頁，「Hosting 的 clean URL 會同時服務 `/privacy`」是錯的。
  這一頁是中英同頁（繁中在上、English 在下），聯絡信箱 `wenhsiaoyi@gmail.com`（2026-10-23 裁決），
  廣告那節的四類資料是直接照 Google 的 Play data-disclosure 頁抄的。
  **它的第七節寫死了「不針對 13 歲以下兒童設計」**，所以下面第 1 項的目標年齡層若選了含未成年，
  政策頁那一節要跟著改，兩邊不能各說各話。
- `web.app` 能不能通過 Play 的開發人員網站網域驗證**尚未查證**，見下方「風險」第 6 條。

#### 剩下 7 項要做的事（第 1–5 條就是那 7 項，其中第 4 條一次涵蓋三張 yes／no；第 6、7 條是 11 項之外的兩道門）

1. **目標對象**（`app-content/target-audience-content`，5 步：目標年齡層 → 應用程式詳細資料 → 廣告 →
   商店發布 → 摘要）。**先做它**，因為 `app-content/overview` 明文寫著
   「請協助我們瞭解您的應用程式是否使用廣告 ID。**如未填完這個部分，您就無法提交以 Android 13
   為目標的版本**」，而那道題藏在這個精靈的第 3 步；本 App 的 targetSdk 是 37，不填就死。
   - 廣告 ID：**有使用**。實據是 release 的 merged manifest 裡有
     `com.google.android.gms.permission.AD_ID`（由 GMA SDK 合併進來，不是我們手加的），
     同一併帶進 `INTERNET`、`ACCESS_NETWORK_STATE`、`WAKE_LOCK`、`FOREGROUND_SERVICE`、
     `READ_BASIC_PHONE_STATE`。
   - 目標年齡層建議選 **18 歲以上**。實據：`ads/` 裡沒有任何 child-directed／TFUA 旗標
     （`childdirect|tfua|tag_for_child` 掃過，0 命中），受眾一含未成年，AdMob 的未成年投放限制
     就落在我們頭上而程式碼不符合。**這是建議，不是裁決**；而且內容分級那頁自己寫
     「如果您對應用程式所做的變更與先前填答的內容有所出入，請重新提交問卷」——年齡層改了要回去重填。
2. **內容分級**：它自己的頁面顯示「IARC 狀態 已完成、已提交 2026年10月23日 下午 6:27、
   電子郵件地址 wenhsiaoyi@gmail.com」（18:26 與 18:27 兩筆），但資訊主頁那一列**既沒有 check
   也沒有箭頭**。我推它是等第 1 項定案才轉綠——**這條是推測，不是實測**。先別動它。
3. **資料安全性**（`app-content/data-privacy-security`，5 步）——**這一條取代舊清單的兩條說法**：
   - Play 在那頁給的定義是明文：「『收集』是指資料從使用者的裝置傳出，由您或第三方接收」；
     而同頁要求揭露「由應用程式使用的程式庫和 SDK，從使用者裝置送出的任何使用者資料，
     不論這項資訊是傳送給您（開發人員）或第三方」。另有一條「暫時處理」的註記：暫時處理的
     *收集*仍要揭露，但不會出現在商店資訊裡。
   - 所以：**本 App 自己取的 GPS 經緯度不算收集**——查證過它從不離裝置、也不落盤
     （`app/src/` 沒有網路程式碼、`ads/` 完全不碰定位）。**舊清單那條「位置資料另列一條」作廢**；
     申報精確位置會跟已上線的政策頁「不會上傳、不會存檔、不會傳給我們或任何第三方」直接矛盾。
   - **要申報的是 AdMob 送出的那四類**，勾「收集」、用途勾 投放廣告／分析／防詐欺・安全・診斷：
     裝置或其他識別碼（Advertising ID、App Set ID、帳戶識別碼）、大致位置（由 IP 位址推得）、
     App 活動（與產品／廣告的互動）、App 資訊與效能（診斷）。**分享＝否**：我們沒有伺服器，經手不了。
     這四類跟政策頁的表格逐字對得上，兩邊不會打架。
4. **政府應用程式／金融功能／健康**：三張 yes／no，沒有相依，各十秒。政府＝**否**
   （那頁還寫「在您回答這個部分的問題前，我們會假設您的應用程式不是政府應用程式」）；
   金融功能＝**否**；健康＝選**「我的應用程式沒有任何健康功能」**。
5. **管理應用程式的設定和呈現方式**（選取應用程式類別並提供詳細聯絡資料）：類別建議「工具」；
   聯絡信箱填 `wenhsiaoyi@gmail.com`，跟政策頁與內容分級用同一個，保持一致。電話與地址得你本人填。
6. **設定商店資訊**：**這一整節還沒逐項量測**，所以不假裝知道缺什麼。已知的是發布總覽把「商店資訊」
   列在已變更項目、寫著「預設商店資訊…已提供應用程式名稱 (Jack's Compass) 和其餘必要資訊」，
   另有 `繁體中文 – zh-TW` 語系；圖示與螢幕截圖齊不齊要進那一頁才算。
7. **送審**：「將應用程式送交審查」目前 disabled，頁面自己寫「如要將變更送審，請完成應用程式資訊主頁
   中的必要步驟」；封閉式測試、開放式測試、預先註冊、「建立並發布版本」四條都掛著
   `lock 請先完成初始設定工作`。11 項全綠才會解鎖。

#### AdMob 主控台（兩項要辦，但今天都還驗證不了）

- 把這個橫幅廣告位的**自動重新整理關掉**，否則它可能在 30 秒邊界上多載入一則，
  我方計數會跟實際 impressions 對不上。
- 同意訊息裡「使用者拒絕時」**維持預設的「投放非個人化廣告」**——2026-10-23 已裁決（換收入；
  實測即此行為，拒絕後橫幅與贊助列都會出現）。後台**不要**改選「顯示訊息且不提供廣告」，
  那才是會改變現狀的選項。
- 這兩項在 2026-10-23 是**未能驗證**，不是「確認未設定」：`admob.google.com/v2/apps/1210011940/overview`
  顯示「需審核」且 0 requests／0 impressions（也就是現在根本還沒有廣告在播），
  `ad-units` 頁掃不出表格，連結也找不到，試了三次。

## 風險與實作時必須查證

1. **Next-Gen 的官方說明自我矛盾**：插屏預載的類別在說明上被寫進舊 SDK 的套件名前綴，跟其他 Next-Gen 類別
   不一致。實作時以 Gradle 解析出的真實類別為準，**不照抄說明**；查不到就改用一般的載入路徑，
   預載本來是可選的。
2. ~~**除錯地區設定在 release 是否失效**~~ → **已解（2026-10-23 查 release 產物）**：不需要這個答案。
   release 自己的資源就是 `ads_force_eea_debug = false` 且註冊清單是空字串，那段除錯程式碼在 release
   根本不會進到；不靠 SDK 自行忽略設定。
3. **全螢幕廣告關閉後的 insets 抖動**：Android 15 以上 edge-to-edge 是預設行為，已知有人踩過，需實機驗。
4. **既存 lint error 三條**（震動用的 API 級別、App 名稱未翻成中文）還在，廣告不會新增它們。
   2026-10-23 實測修正：**它們不擋上架**——`bundleRelease` 帶著這三條照樣綠，
   `lintVitalRelease` 只擋 fatal 級。真正的問題是那兩條 `NewApi`：minSdk 24 的裝置上
   按到指南針刻度震動會 crash（`VibrationEffect.createOneShot`／`vibrate` 要 API 26）。
   要不要處理（補版本判斷，或把 minSdk 拉到 26）由你裁決。
5. **Next-Gen 生態系還新**：遇到說明沒寫的行為，退回官方 sample 專案比退回舊 SDK 便宜。
6. **`web.app` 能不能通過 Play 的「開發人員網站」網域驗證——仍未查證（2026-10-23）**。
   兩個相關官方說明頁（User Data 政策、建立開發帳號必要資訊）都是 JS rendered 殼，抓回來只有 CSS，
   所以不能宣稱 `jackcompass-site.web.app` 一定可用。**這條要在你 Console 的實介面才會看到真切要求。**
   若它堅持要你持有一個自有網域，解法是買一個網域指回同一個 Firebase 專案，**不是換主機**；
   `app-ads.txt` 本身放 `web.app` 沒問題——AdMob 那頁自己點名 Firebase Hosting（含 `PROJECT_ID.web.app`）。
   另外注意：AdMob 抓的是**商店資訊裡那個「開發人員網站」欄位**的根路徑，兩邊填的網址要完全一致。

## 術語候選（真的有 GLOSSARY.md 時再收進去）

- 本次執行：一次 App 從啟動到結束的期間，橫幅的「只播一次」以它為單位。
- 同意閘門：任何廣告請求之前必須通過的檢查，不是畫面元件。
- 橫幅廣告／插屏廣告：兩種格式的台灣慣用譯名。
