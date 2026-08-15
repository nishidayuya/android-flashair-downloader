#!/bin/bash

# Installs and starts an Android emulator for checking the app inside the dev
# container.
#
# The emulator and its system image are ~2.8GB together, which is why they are
# not baked into the container image: this script fetches them when they are
# actually wanted. Everything it does is idempotent, so running it again on an
# existing setup just starts the emulator.
#
#   tools/setup_emulator.sh                     # install, create the AVD, boot it
#   tools/setup_emulator.sh --no-start          # install and create only
#   tools/setup_emulator.sh --route-to-stub     # also point 192.168.0.1 at the stub
#
# The app talks to 192.168.0.1, and from inside the emulator the host is
# 10.0.2.2, so --route-to-stub adds a NAT rule that sends the card's address to
# tools/flashair-stub.rb running on the host. See README.md.

set -eu

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
AVD_NAME="${AVD_NAME:-flashair-api36}"
# The "default" image rather than "aosp_atd": the ATD images leave out
# DocumentsUI, and without it the destination folder cannot be picked.
SYSTEM_IMAGE="${SYSTEM_IMAGE:-system-images;android-36;default;x86_64}"
DEVICE_PROFILE="${DEVICE_PROFILE:-pixel_6}"
STUB_PORT="${STUB_PORT:-8080}"

start_emulator=true
route_to_stub=false

while [ $# -gt 0 ]; do
  case "$1" in
    --no-start) start_emulator=false ;;
    --route-to-stub) route_to_stub=true ;;
    --avd) shift; AVD_NAME="$1" ;;
    --image) shift; SYSTEM_IMAGE="$1" ;;
    --stub-port) shift; STUB_PORT="$1" ;;
    -h|--help) awk "NR >= 3 && /^#/ { sub(/^# ?/, \"\"); print; next } NR >= 3 { exit }" "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
adb="$ANDROID_HOME/platform-tools/adb"
emulator="$ANDROID_HOME/emulator/emulator"

if [ ! -x "$sdkmanager" ]; then
  echo "no Android SDK at $ANDROID_HOME; set ANDROID_HOME or rebuild the dev container" >&2
  exit 1
fi

# Hardware acceleration. Without /dev/kvm the emulator falls back to an
# unusably slow software CPU, so this is a hard requirement rather than a nicety.
if [ ! -e /dev/kvm ]; then
  echo "/dev/kvm is missing: the container needs KVM to run the emulator" >&2
  exit 1
fi
if [ ! -w /dev/kvm ]; then
  echo "==> making /dev/kvm writable"
  sudo chmod 666 /dev/kvm
fi

echo "==> installing the emulator and $SYSTEM_IMAGE (skipped when already there)"
yes 2>/dev/null | "$sdkmanager" "emulator" "$SYSTEM_IMAGE" > /dev/null

if "$avdmanager" list avd 2>/dev/null | grep -q "Name: $AVD_NAME\$"; then
  echo "==> AVD $AVD_NAME is already there"
else
  echo "==> creating the AVD $AVD_NAME"
  # The device profile lives in the SDK, not in the system image, so the
  # "could not load devices.xml" complaint from avdmanager is harmless.
  echo no | "$avdmanager" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "$DEVICE_PROFILE" > /dev/null 2>&1
fi

if [ "$start_emulator" = false ]; then
  echo "==> done (not starting the emulator)"
  exit 0
fi

if "$adb" devices | grep -q '^emulator-.*device$'; then
  echo "==> an emulator is already running"
else
  echo "==> starting $AVD_NAME without a window"
  nohup "$emulator" -avd "$AVD_NAME" \
    -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect > /tmp/emulator-"$AVD_NAME".log 2>&1 &
  "$adb" wait-for-device
  echo "==> waiting for the boot to finish"
  until [ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 5
  done
fi
echo "==> the emulator is up"

if [ "$route_to_stub" = true ]; then
  echo "==> routing 192.168.0.1:80 to the host's port $STUB_PORT"
  "$adb" root > /dev/null
  "$adb" wait-for-device
  # -C first, so that running this twice does not stack up rules.
  "$adb" shell "iptables -t nat -C OUTPUT -p tcp -d 192.168.0.1 --dport 80 \
    -j DNAT --to-destination 10.0.2.2:$STUB_PORT 2>/dev/null \
    || iptables -t nat -A OUTPUT -p tcp -d 192.168.0.1 --dport 80 \
    -j DNAT --to-destination 10.0.2.2:$STUB_PORT"
  echo "==> start the stub on the host: ruby tools/flashair-stub.rb --port $STUB_PORT"
fi

cat <<EOF

Next:
  ./gradlew installDebug
  $adb shell am start -n org.j96.flashairdownloader.debug/org.j96.flashairdownloader.ui.MainActivity

The screen is not rendered anywhere, so read it with:
  $adb shell uiautomator dump /sdcard/ui.xml && $adb shell cat /sdcard/ui.xml
EOF
