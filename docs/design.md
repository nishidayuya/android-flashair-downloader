# FlashAir Downloader for Android — 設計書

TOSHIBA FlashAir（無線 LAN 内蔵 SD カード）から HTTP 経由でファイルを
ダウンロードする Android アプリケーションの設計書。

- 対象リポジトリ: `android-flashair-downloader`
- 作成日: 2026-08-12
- ステータス: ドラフト（実装未着手）

---

## 1. 目的とスコープ

### 1.1 やること

- FlashAir に Wi-Fi 接続した状態で、カード内のファイル一覧を閲覧する
- 写真・動画を中心とした任意のファイルを端末ストレージにダウンロードする
- 「前回以降に増えたファイルだけ」を差分ダウンロードする（同期）
- バックグラウンド（画面オフ・アプリ切り替え時）でもダウンロードを継続する

### 1.2 やらないこと（初期リリース時点）

- FlashAir へのアップロード（`upload.cgi`）
- FlashAir の設定変更（`config.cgi` / `CONFIG` ファイルの書き換え）
- FlashAir の Station モード経由でのインターネット越しアクセス
- FlashAir 上での Lua スクリプト実行

### 1.3 前提

- FlashAir は AP モード（既定）で動作し、端末がその AP に接続している
- FlashAir のファームウェアは W-03 / W-04 相当（FW 2.00.00 以降）を主対象とする
  - FW 1.x でも動作するよう、拡張 op コードは任意扱いにする
- 通信は **平文 HTTP のみ**（FlashAir は HTTPS 非対応）

---

## 2. FlashAir HTTP API 仕様（実装に必要な範囲）

FlashAir の既定のホストは以下。設定で変更可能にする。

- IP アドレス: `192.168.0.1`
- ホスト名: `flashair` / `flashair.local`（名前解決は環境依存のため **IP 直指定を既定** とする）

### 2.1 `command.cgi`

`http://192.168.0.1/command.cgi?op=<code>&...` の形式。本アプリで使う op:

| op  | 用途 | パラメータ | レスポンス |
|-----|------|-----------|-----------|
| 100 | ファイル一覧 | `DIR=/DCIM` | `WLANSD_FILELIST` ヘッダ行 + CSV 行 |
| 101 | ファイル数 | `DIR=/DCIM` | 数値 |
| 102 | 更新有無（WRITE STATUS） | なし | `1`（更新あり）/ `0` |
| 104 | SSID | なし | 文字列（最大 32 文字） |
| 108 | ファームウェアバージョン | なし | 例 `F19BAW3AW2.00.00` |
| 120 | CID（カード識別子） | なし | 32 桁 hex |
| 121 | 起動からの経過ミリ秒 | なし | 数値（FW 2.00.02+） |
| 140 | 空き容量 | なし | `<空きセクタ>/<全セクタ>,<セクタバイト数>`（FW 1.00.03+） |
| 220 | WebDAV 状態 | なし | `0`/`1`/`2`（FW 3.00.00+） |

補足:

- `op=102` は **読み出すとフラグがクリアされる**。ポーリングによる更新検知に使えるが、
  複数箇所から呼ぶと取りこぼすため、呼び出し口を 1 箇所に集約する。
- `op=120`（CID）は **カードを識別するキー** として使う。複数枚の FlashAir を
  使い分けても同期状態が混ざらないようにする。
- `op=140` は空き容量表示に使う。取得失敗しても機能を止めない（任意扱い）。

### 2.2 `op=100` のレスポンス形式とパース上の注意

```
WLANSD_FILELIST
/DCIM,100__TSB,0,16,9944,129
/DCIM/100__TSB,IMG_0001.JPG,70408,32,17071,28040
```

各行のフィールドは `<directory>,<filename>,<size>,<attribute>,<date>,<time>`。

**重要: ファイル名にカンマを含められるため、単純な `split(",")` は壊れる。**
パーサは次の方針で実装する。

1. 行末から 4 フィールド（`size`, `attribute`, `date`, `time`）を後方から切り出す
2. 先頭の `directory` はリクエストに指定した `DIR` の値と一致する前提で前方から切り出す
3. 残りをすべて `filename` とする（カンマを含んでいてもそのまま復元できる）

その他の注意:

- `DIR=/`（ルート）を指定した場合、`directory` フィールドは空になる
- 改行コードは `\r\n`
- ファイル名の文字コードは実機で要検証（UTF-8 想定、非 ASCII 名で確認する）

### 2.3 `attribute`（FAT 属性ビット）

| ビット | 値 | 意味 |
|-------|----|------|
| 0 | 1  | 読み取り専用 |
| 1 | 2  | 隠しファイル |
| 2 | 4  | システムファイル |
| 3 | 8  | ボリュームラベル |
| 4 | 16 | **ディレクトリ** |
| 5 | 32 | アーカイブ |

ディレクトリ判定（bit4）で再帰走査する。隠し／システム属性は既定でスキップする。

### 2.4 `date` / `time`（16bit FAT 形式）のデコード

```kotlin
fun decodeFatDateTime(date: Int, time: Int): LocalDateTime {
    val year   = ((date shr 9) and 0x7F) + 1980
    val month  = (date shr 5) and 0x0F
    val day    = date and 0x1F
    val hour   = (time shr 11) and 0x1F
    val minute = (time shr 5) and 0x3F
    val second = (time and 0x1F) * 2
    return LocalDateTime.of(year, month, day, hour, minute, second)
}
```

- タイムゾーン情報を持たない（カード内のローカル時刻）。端末の既定タイムゾーンとして扱う
- 不正値（month=0 など）が返ることがあるため、パース失敗時は `null` にフォールバックする

### 2.5 ファイル本体の取得

```
GET http://192.168.0.1/DCIM/100__TSB/IMG_0001.JPG
```

パスをそのまま URL パスとして GET する。パス要素は URL エンコードが必要
（`OkHttp` の `HttpUrl.Builder.addPathSegment()` を使い、自前のエスケープはしない）。

- `Range` ヘッダによるレジューム対応可否は **実機検証が必要**（未対応前提で設計する）
- FlashAir の HTTP サーバは同時接続数が少ない（体感 2〜3）。並列度は既定 1 とする

### 2.6 `thumbnail.cgi`

```
GET http://192.168.0.1/thumbnail.cgi?/DCIM/100__TSB/IMG_0001.JPG
```

- JPEG の Exif サムネイルを返す。**JPEG 以外では使えない**
- FW 3.00.00+ ではレスポンスヘッダに `X-...WIDTH` / `HEIGHT` が付く
- 一覧画面のサムネイル表示に使う。取得失敗時は拡張子アイコンにフォールバック

---

## 3. Android 固有の技術的課題と対策

このアプリの難所は FlashAir の API ではなく **Android のネットワーク／ストレージ制約** にある。
ここを設計で外すと「繋がらない」「保存できない」で詰む。

### 3.1 【最重要】インターネットに出られない Wi-Fi へのルーティング

インターネット到達性のない Wi-Fi に繋いでいても、
アプリの通信は既定でモバイル回線に流れる。FlashAir はインターネットに出られないため、
**何もしないと `192.168.0.1` への通信がモバイル回線に投げられて失敗する。**

対策:

```kotlin
val request = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .build()

connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) { /* この Network を保持する */ }
    override fun onLost(network: Network) { /* 破棄する */ }
})
```

得られた `Network` の使い方は 2 通りある。**後者を採用する。**

| 方式 | 内容 | 判定 |
|------|------|------|
| `bindProcessToNetwork(network)` | プロセス全体を Wi-Fi にバインド | 実装は簡単だが、アプリの他の通信（あれば）も巻き添え。解除忘れが致命的 |
| **`OkHttpClient` に `network.socketFactory` と専用 DNS を設定** | FlashAir 向けクライアントだけを Wi-Fi 経由にする | 影響範囲が限定され、解除漏れの事故がない。**採用** |

```kotlin
OkHttpClient.Builder()
    .socketFactory(network.socketFactory)
    .dns { hostname -> network.getAllByName(hostname).toList() }  // 名前解決も Network 経由
    .build()
```

DNS も `Network` 経由にしないと名前解決だけモバイル回線に飛ぶ。
`flashair` のようなホスト名は解決できないことが多いため、**既定は IP 直指定**とする。

`Network` が切り替わるたびに `OkHttpClient` を作り直す必要があるため、
`FlashAirNetworkProvider` が `StateFlow<Network?>` を公開し、
`OkHttpClient` はその都度生成（コネクションプールは使い捨て）する構成にする。

### 3.2 平文 HTTP の許可

平文 HTTP は既定で禁止される。`res/xml/network_security_config.xml`:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">flashair</domain>
        <domain includeSubdomains="true">flashair.local</domain>
        <domain>192.168.0.1</domain>
    </domain-config>
</network-security-config>
```

**トレードオフ:** この設定はビルド時固定なので、ユーザーが任意の IP に変更した場合に平文通信できない。
- 初期リリース: 上記のホワイトリスト方式（既定 IP のみ許可）で進める
- ユーザー任意 IP を許すなら、`base-config` を `true` にする代わりに、
  接続先を「FlashAir にバインドした `Network` 経由」に限定することで実害を抑える

設定画面でホストを変更できるようにする場合は後者に切り替える。この判断は Phase 5 で行う。

### 3.3 Wi-Fi への接続方法

手段は 2 つ。

| 手段 | 内容 |
|------|------|
| `Settings.Panel.ACTION_WIFI` | Wi-Fi パネルを表示し、ユーザーに手動接続してもらう |
| `WifiNetworkSpecifier` | アプリ専用のローカルオンリー接続（システム UI でユーザーが承認） |

`WifiNetworkSpecifier` は「インターネットなしのローカル専用ネットワーク」に接続する
正規の手段で、まさに FlashAir 向き。ローカルオンリー接続はセカンダリ接続として張られるため、
モバイル回線を維持したまま FlashAir と通信できる。ただし OS のダイアログを経由する UX になる。

`WifiManager.addNetwork()` / `enableNetwork()` による直接接続は使えない（廃止済み）。

**方針:**
- Phase 1〜5: **ユーザーが OS 設定で手動接続**する前提。アプリは 3.1 のバインドのみ行う。
  未接続時は「Wi-Fi 設定を開く」ボタン（`Settings.Panel.ACTION_WIFI`）を出す
- Phase 6: `WifiNetworkSpecifier` によるアプリ内接続をオプション追加

SSID をスキャンして表示する機能を付けるなら `ACCESS_FINE_LOCATION` が必要になる。
初期リリースでは **スキャンしない**ことで位置情報権限を不要にする。

### 3.4 保存先ストレージ（Scoped Storage）

| 方式 | 長所 | 短所 |
|------|------|------|
| **SAF（`ACTION_OPEN_DOCUMENT_TREE`）** | 全ファイル種別・SD カード・任意フォルダに対応。権限宣言不要 | `DocumentFile` が遅い。API がやや扱いにくい |
| MediaStore | 写真・動画がギャラリーにすぐ出る | 画像/動画/音声以外を素直に置けない。保存先の自由度が低い |

**方針:**
- Phase 4: **SAF のツリー選択を基本**とする。`takePersistableUriPermission()` で権限を永続化
  - `DocumentFile.findFile()` は 1 件ごとにクエリが走り大量ファイルで遅い。
    `DocumentsContract.buildChildDocumentsUriUsingTree()` で **子一覧を一括取得してマップ化**し、
    存在確認はメモリ上で行う
  - 書き込み中は `<name>.part` として作り、完了時に `DocumentsContract.renameDocument()` で確定
- Phase 5: 保存した画像・動画を MediaStore に登録する（ギャラリーに出す）オプションを追加

### 3.5 バックグラウンド実行

大量ファイルのダウンロードは数分〜数十分かかる。画面オフでも継続させる。

- **フォアグラウンドサービス**（`foregroundServiceType="dataSync"`）で実行する
  - `FOREGROUND_SERVICE_DATA_SYNC` 権限の宣言が必須
  - 通知表示に `POST_NOTIFICATIONS` の実行時許可が必要
- WorkManager は採用しない。ネットワークのバインド状態と密結合で、
  OS 都合の再スケジュールと相性が悪いため
- Wi-Fi が切れたら即座に一時停止し、`onAvailable` で再開する
- 長時間ダウンロード中に Wi-Fi がスリープしないよう `WifiLock`（`WIFI_MODE_FULL_LOW_LATENCY`）
  と `PARTIAL_WAKE_LOCK` を取得する。取得・解放は必ず `try/finally` で対にする
  - `WIFI_MODE_FULL_HIGH_PERF` は API 29 で非推奨になったのでこちらを使う。
    低遅延化が効くのは「前景かつ画面 ON」のときだけで、それ以外は
    `WIFI_MODE_FULL_HIGH_PERF` 相当に落ちる。このアプリの主戦場（画面 OFF での
    転送）では後者の挙動になるが、そこで欲しいのは Wi-Fi 省電力（PSM）の抑止
    なので実効は変わらない
  - 接続の維持は WifiLock の役割ではない（`WIFI_MODE_FULL` は非機能として
    非推奨）。画面 OFF でも Wi-Fi は繋がったままで、CPU を止めないのは
    `PARTIAL_WAKE_LOCK` の担当

### 3.6 必要なパーミッション

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

ストレージ権限（`READ/WRITE_EXTERNAL_STORAGE`）は SAF を使うため **不要**。

---

## 4. 技術スタック

| 分類 | 採用 | 備考 |
|------|------|------|
| 言語 | Kotlin | |
| UI | Jetpack Compose + Material 3 | |
| 非同期 | Coroutines / Flow | |
| HTTP | OkHttp | `socketFactory` 差し替えが必要なため必須。Retrofit は不要（CGI が REST でない） |
| DI | Hilt | |
| 永続化 | Room（同期履歴）/ DataStore Preferences（設定） | |
| 画像 | Coil | FlashAir 用 `OkHttpClient` を差し替えて `thumbnail.cgi` を読む |
| ナビゲーション | Navigation Compose | |
| テスト | JUnit5 / kotlin-test, MockWebServer, Turbine, Robolectric | |
| ビルド | Gradle (Kotlin DSL) + Version Catalog | |

SDK バージョン:

- `minSdk` / `targetSdk` / `compileSdk = 36`（Android 16）

**対象は Android 16 以降とする。** 下位バージョン向けの分岐（`Build.VERSION.SDK_INT` による
`if` 分岐、非推奨 API のフォールバック、`desugaring`）を一切書かなくてよくなり、
3 章の各対策はすべて単一の実装で済む。その代わり対応端末は Android 16 以降の搭載機に限られる。

---

## 5. アーキテクチャ

単一モジュール（`:app`）+ パッケージ分割で開始する。規模が膨らんだ時点で
`:core:network` / `:core:data` / `:feature:*` に切り出す。

```
org.j96.flashairdownloader
├── di/                      Hilt モジュール
├── net/
│   ├── FlashAirNetworkProvider.kt   NetworkCallback、StateFlow<Network?>
│   └── FlashAirHttpClientFactory.kt Network に紐づく OkHttpClient を生成
├── data/
│   ├── flashair/
│   │   ├── FlashAirApi.kt           command.cgi / thumbnail.cgi / ファイル GET
│   │   ├── FileListParser.kt        op=100 のパース（カンマ問題対応）
│   │   ├── FatDateTime.kt           FAT 日時デコード
│   │   └── model/FlashAirEntry.kt
│   ├── local/
│   │   ├── AppDatabase.kt, DownloadRecordDao.kt, DownloadRecordEntity.kt
│   │   └── SettingsDataStore.kt
│   └── storage/
│       └── SafFileStore.kt          ツリー配下への書き込み、既存一覧の一括取得
├── domain/
│   ├── model/                       CardInfo, RemoteFile, SyncPlan, SyncProgress
│   └── usecase/
│       ├── ProbeCardUseCase.kt      op=104/108/120/140 で接続確認
│       ├── ScanRemoteFilesUseCase.kt 再帰走査
│       ├── BuildSyncPlanUseCase.kt  ローカル記録と突合して差分を出す
│       └── DownloadFilesUseCase.kt  逐次ダウンロード + 進捗 Flow
├── sync/
│   ├── SyncForegroundService.kt
│   └── SyncController.kt            サービスと UI の橋渡し（進捗の単一の情報源）
└── ui/
    ├── home/ browse/ sync/ settings/ history/
    └── theme/
```

依存の向き: `ui → domain → data`。`domain` は Android 依存を持たない（`SafFileStore` は
インタフェースを `domain` 側に置き、実装を `data` に置く）。

---

## 6. データモデル

```kotlin
// FlashAir 上の 1 エントリ
data class FlashAirEntry(
    val directory: String,        // "/DCIM/100__TSB"
    val name: String,             // "IMG_0001.JPG"
    val size: Long,
    val attribute: Int,
    val modifiedAt: LocalDateTime?,  // FAT 日時のデコード結果、失敗時 null
) {
    val path: String get() = if (directory == "/") "/$name" else "$directory/$name"
    val isDirectory: Boolean get() = attribute and 0x10 != 0
    val isHidden: Boolean get() = attribute and 0x02 != 0
    val isSystem: Boolean get() = attribute and 0x04 != 0
}
```

```kotlin
// Room: ダウンロード済み記録
@Entity(primaryKeys = ["cardId", "path"])
data class DownloadRecordEntity(
    val cardId: String,           // op=120 の CID。取得不可なら SSID にフォールバック
    val path: String,
    val size: Long,
    val modifiedAtEpoch: Long?,
    val downloadedAtEpoch: Long,
    val localUri: String?,        // 保存先 document URI
)
```

**同一ファイル判定**は `(cardId, path, size, modifiedAt)` の一致で行う。
サイズか更新日時が変わっていれば「更新された別ファイル」として再取得する。

---

## 7. 同期処理の設計

```
[接続確認] → [リモート走査] → [差分計算] → [逐次ダウンロード] → [記録更新]
```

1. **接続確認**: `op=108`（FW）が返れば疎通 OK。`op=120` で `cardId` を確定
2. **リモート走査**: 対象ディレクトリ（既定 `/DCIM`）から `op=100` で再帰。
   隠し／システム属性はスキップ。深さ上限と件数上限を設けて暴走を防ぐ
3. **差分計算**: Room の記録と突合。フィルタ（拡張子・更新日時の下限・サイズ上限）を適用
4. **ダウンロード**: 既定は **並列度 1**（設定で最大 2）。
   - タイムアウト: connect 10s / read 30s / call なし
   - リトライ: 同一ファイル最大 3 回、指数バックオフ（1s, 2s, 4s）
   - `.part` に書き、完了後にリネームして確定
   - 1 ファイル完了ごとに Room に記録（途中中断しても再開時に無駄がない）
5. **進捗**: `SyncController` が `StateFlow<SyncProgress>` を公開。
   通知と UI は同じ Flow を購読する

エラー分類（UI で出し分ける）:

| 種別 | 例 | UI での案内 |
|------|----|-----------|
| 未接続 | バインド可能な Wi-Fi がない | 「FlashAir の Wi-Fi に接続してください」+ Wi-Fi 設定ボタン |
| 疎通不可 | タイムアウト、接続拒否 | ホスト設定の確認を促す |
| カード側エラー | 4xx/5xx | リトライ後にスキップして継続 |
| 保存先エラー | SAF 権限失効、容量不足 | 保存先の再選択を促す |

**中断されたファイルはスキップして最後まで走らせ、末尾に失敗一覧を出す。**
1 ファイルの失敗で全体を止めない。

---

## 8. 画面構成

| 画面 | 内容 |
|------|------|
| ホーム | 接続状態、カード情報（SSID / FW / 空き容量）、「同期開始」ボタン、前回同期日時 |
| ブラウズ | ディレクトリ階層の閲覧、サムネイル表示、個別／複数選択ダウンロード |
| 同期中 | 全体進捗、現在のファイル、転送速度、残り件数、キャンセル |
| 設定 | ホスト（既定 192.168.0.1）、対象ディレクトリ、保存先（SAF）、拡張子フィルタ、並列度、MediaStore 登録の有無 |
| 履歴 | ダウンロード済み一覧、失敗一覧、記録のリセット |

初期リリースはホーム＋同期中＋設定の 3 画面でも成立する。ブラウズは Phase 3 で追加。

---

## 9. 実装フェーズ

各フェーズの終わりに動作確認できる状態を保つ。

### Phase 0: 開発環境（0.5 日）

- devcontainer に JDK 21 + Android SDK（cmdline-tools, platform 36, build-tools）を追加
- Gradle プロジェクト雛形、Version Catalog、`.gitignore`、ktlint/detekt
- GitHub Actions: `./gradlew test lint assembleDebug`
- **完了条件:** `./gradlew assembleDebug` が通る

### Phase 1: FlashAir クライアント層（1〜2 日）— 実機不要

- `FileListParser`（カンマ入りファイル名、ルート指定、空行、CRLF）
- `FatDateTime` デコード
- `FlashAirApi`（`command.cgi` 各 op、ファイル GET）
- MockWebServer による単体テスト
- **完了条件:** パーサとクライアントのテストが緑。ここが一番バグの出る所なのでテストを厚くする

### Phase 2: ネットワークバインドと疎通（1 日）— 実機必要

- `FlashAirNetworkProvider`（`requestNetwork` + `StateFlow<Network?>`）
- `network_security_config.xml`
- ホーム画面に接続状態とカード情報を表示
- **完了条件:** 実機で FlashAir の SSID に手動接続し、FW バージョンが画面に出る

### Phase 3: ブラウズ UI（1〜2 日）

- ディレクトリ再帰走査、階層ナビゲーション
- Coil + `thumbnail.cgi` によるサムネイル（JPEG のみ、失敗時アイコン）
- **完了条件:** `/DCIM` 配下を辿って一覧できる

### Phase 4: ダウンロードと同期エンジン（2〜3 日）

- SAF 保存先選択、`SafFileStore`
- `SyncForegroundService` + 通知（進捗・キャンセル）
- 逐次ダウンロード、`.part` 方式、リトライ
- Room への記録と差分計算
- **完了条件:** 画面を消しても `/DCIM` 全件がダウンロードでき、2 回目は 0 件と判定される

### Phase 5: 設定・履歴・仕上げ（1〜2 日）

- 設定画面、履歴画面
- MediaStore 登録オプション
- エラーメッセージの整備、空状態、日本語／英語リソース
- **完了条件:** 一通りの操作で落ちない

### Phase 6（任意）: Wi-Fi 自動接続

- `WifiNetworkSpecifier` によるアプリ内接続
- **完了条件:** アプリからボタン 1 つで FlashAir に接続できる

### Phase 7（任意）: 配布

- アイコン、リリースビルド、署名、プライバシーポリシー

---

## 10. テスト戦略

| レベル | 対象 | 手段 |
|--------|------|------|
| 単体 | `FileListParser`, `FatDateTime`, 差分計算 | JUnit。実機・ネットワーク不要 |
| 結合 | `FlashAirApi` | MockWebServer で `command.cgi` の応答を再現 |
| 疑似実機 | 走査〜ダウンロードの通し | ローカルに FlashAir 互換の HTTP スタブを立てる（FlashAir シミュレータ相当） |
| 実機 | ネットワークバインド、SAF、フォアグラウンドサービス | 実 FlashAir + 実機 |

パーサのテストケースは最低限これを含める:

- ファイル名にカンマを含む行
- `DIR=/` のときに `directory` が空になる行
- ディレクトリ行（`attribute=16`）
- 不正な FAT 日時（`month=0`）
- 空一覧（ヘッダ行のみ）
- 巨大サイズ（4GB 超 → `Long` で扱う）

---

## 11. 未確定事項（実装前に確認したい）

| # | 項目 | 既定の想定 |
|---|------|-----------|
| 1 | 対象 FlashAir の世代 | W-04（FW 3.x）を主対象、FW 2.x 互換維持 |
| 2 | 実機テスト用の FlashAir と Android 端末の有無 | あり前提。なければ Phase 2 以降がスタブ検証止まりになる |
| 3 | 配布方法 | 個人利用の APK 直配布。Play 公開なら Phase 7 が増える |
| 4 | 対応言語 | 日本語 + 英語 |
| 5 | `Range` レジューム対応 | 未対応前提。実機検証で対応可なら再開機能を追加 |
| 6 | ファイル名の文字コード | UTF-8 想定。非 ASCII 名で要検証 |

---

## 12. 参考資料

- [FlashAir Developers — command.cgi（アーカイブ）](https://flashair-developers.github.io/website/docs/api/command.cgi.html)
- [FlashAir Developers — thumbnail.cgi](https://flashair-developers.com/en/documents/api/thumbnailcgi/)
- [FlashAir Developers — config.cgi（ミラー）](https://petrst.github.io/flashair-developers-site/en/documents/api/configcgi/index.htm)
- [FlashAir Developers サイトのミラー](https://petrst.github.io/flashair-developers-site/)
- [go-flashair（Go 実装の参考）](https://github.com/asnowfix/go-flashair)
- [tfatool（Python 実装の参考）](https://pypi.org/project/tfatool/)
- [WifiNetworkSpecifier リファレンス](https://learn.microsoft.com/en-us/dotnet/api/android.net.wifi.wifinetworkspecifier)
