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

## エミュレーターでの動作確認

コンテナー内で API 36 のエミュレーターを動かせる（`/dev/kvm` が使える場合）。
イメージが大きいので Dockerfile には含めず、必要になったときに入れる。

```sh
sudo chmod 666 /dev/kvm
sdkmanager "emulator" "system-images;android-36;aosp_atd;x86_64"
avdmanager create avd -n fad-api36 -k "system-images;android-36;aosp_atd;x86_64" -d pixel_6
emulator -avd fad-api36 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n org.j96.flashairdownloader.debug/org.j96.flashairdownloader.ui.MainActivity
```

`-no-window` だと `adb exec-out screencap` は真っ黒になるので、画面の確認は
`adb shell uiautomator dump /sdcard/ui.xml` の結果を読む。

### FlashAir スタブと組み合わせる

`tools/flashair-stub.rb` が FlashAir 互換の HTTP サーバー（設計書 10 の
「疑似実機」）で、`tools/fixtures/card` を SD カードの中身として配信する。

```sh
ruby tools/flashair-stub.rb --root tools/fixtures/card --port 8080 &
```

エミュレーターからは、ホストが `10.0.2.2` に見える。アプリが接続するのは
`192.168.0.1`（平文 HTTP を許可しているのはこのアドレスだけ）なので、
エミュレーター内で宛先を書き換える。

```sh
adb root
adb shell iptables -t nat -A OUTPUT -p tcp -d 192.168.0.1 --dport 80 \
  -j DNAT --to-destination 10.0.2.2:8080
```

これでアプリは実機と同じコードパスのまま、スタブのカードを読む。
fixture にはカンマ入りのファイル名と非 ASCII のファイル名が入っている。

## SDK バージョンの方針

- `minSdk` / `targetSdk` = 36（Android 16）: 対応端末を決める値。設計どおり。
- `compileSdk` = 37: 現行の AndroidX が「API 37 以降でコンパイルすること」を
  要求するため 1 世代先にしている。コンパイル時に見える API が増えるだけで、
  動作対象の端末は変わらない。
