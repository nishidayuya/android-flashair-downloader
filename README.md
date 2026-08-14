# FlashAir Downloader for Android

TOSHIBA FlashAir（無線 LAN 内蔵 SD カード）から HTTP 経由でファイルを
ダウンロードする Android アプリケーション。

設計は [docs/design.md](docs/design.md) を参照。

## 動作環境

- Android 16（API 36）以降

## 開発環境

`.devcontainer` に JDK 21 と Android SDK が入っている。VS Code の
Dev Containers、Dev Container CLI、DevPod のいずれからでも起動できる。

- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`
- `ANDROID_HOME=/opt/android-sdk`（platform-tools, `platforms;android-37.0`,
  `build-tools;37.0.0`）

Gradle はリポジトリーに同梱した wrapper（Gradle 9.7.0）を使う。

## ビルドと検査

```sh
./gradlew assembleDebug   # デバッグ APK
./gradlew test            # JVM 単体テスト（JUnit 5）
./gradlew ktlintCheck     # ktlint
./gradlew detekt          # detekt
./gradlew lint            # Android Lint
```

CI（`.github/workflows/ci.yml`）は上記をまとめて実行する。

## SDK バージョンの方針

- `minSdk` / `targetSdk` = 36（Android 16）: 対応端末を決める値。設計どおり。
- `compileSdk` = 37: 現行の AndroidX が「API 37 以降でコンパイルすること」を
  要求するため 1 世代先にしている。コンパイル時に見える API が増えるだけで、
  動作対象の端末は変わらない。
