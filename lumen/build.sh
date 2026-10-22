#!/usr/bin/env bash
# Build Lumen with the bare Android SDK: no Gradle, no downloaded dependencies.
# Needs JDK 17+, the SDK's build-tools (34+) and platforms;android-34.
# Output: build/lumen.apk, signed with the local debug key.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT_VERSION="$(ls "$SDK/build-tools" 2>/dev/null | sort -V | grep -m1 -E '^(3[4-9]|[4-9][0-9])\.' || true)"
BT="$SDK/build-tools/$BT_VERSION"
JAR="$SDK/platforms/android-34/android.jar"
if [ -z "$BT_VERSION" ] || [ ! -f "$JAR" ]; then
  echo "need build-tools 34+ and platforms;android-34 under $SDK (set ANDROID_HOME)" >&2
  exit 1
fi
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/android-build/jdk-17.0.19+10" ]; then
  export JAVA_HOME="$HOME/android-build/jdk-17.0.19+10"
fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"

KEYSTORE="$HOME/.android/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$HOME/.android"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
      -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

rm -rf build
mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -A assets -0 ttf \
    --min-sdk-version 26 --target-sdk-version 34 \
    --java build/gen -o build/unsigned.apk build/res.zip

javac -Xlint:-options -source 1.8 -target 1.8 -bootclasspath "$JAR" -classpath "$BT/core-lambda-stubs.jar" -encoding UTF-8 \
    -d build/classes $(find src build/gen -name '*.java')

"$BT/d8" --release --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)

"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out build/lumen.apk build/aligned.apk 2>/dev/null

echo "built $(pwd)/build/lumen.apk ($(wc -c < build/lumen.apk | tr -d ' ') bytes)"
