#!/usr/bin/env bash
# Builds every Rust artifact for all Android ABIs:
#   out/jniLibs/<abi>/libcleaner_starter.so   packaged into the APK (exec'd via su)
#   out/zygisk/<abi>.so                       packaged into the Zygisk module zip
#   out/media_guard.dex                       MediaProvider guard, also in the module zip
set -euo pipefail
cd "$(dirname "$0")"

SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$SDK_DIR"/ndk/* | sort -V | tail -1)}"
# rustup's toolchain carries the Android std libs; a system rustc (e.g. Homebrew) does not.
export PATH="$HOME/.cargo/bin:$PATH"
CARGO="${CARGO:-cargo}"
PROFILE="${PROFILE:-release}"

# abi:rust-triple
ABIS="arm64-v8a:aarch64-linux-android armeabi-v7a:armv7-linux-androideabi x86_64:x86_64-linux-android x86:i686-linux-android"

ndk_targets=()
for pair in $ABIS; do ndk_targets+=(-t "${pair%%:*}"); done

"$CARGO" ndk --platform 29 "${ndk_targets[@]}" build --profile "$PROFILE"

rm -rf out
for pair in $ABIS; do
  abi="${pair%%:*}"
  dir="target/${pair##*:}/$PROFILE"
  mkdir -p "out/jniLibs/$abi" out/zygisk
  cp "$dir/cleaner-starter" "out/jniLibs/$abi/libcleaner_starter.so"
  cp "$dir/libcleaner_zygisk.so" "out/zygisk/$abi.so"
done

# MediaProvider guard: plain Java against the public SDK (framework internals are
# reached by reflection), dexed for the lowest supported platform.
ANDROID_JAR="$(ls -d "$SDK_DIR"/platforms/android-* | sort -V | tail -1)/android.jar"
D8="$(ls -d "$SDK_DIR"/build-tools/* | sort -V | tail -1)/d8"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
guard_build="out/media-guard"
mkdir -p "$guard_build/classes" "$guard_build/dex"
"$JAVAC" -Xlint:all -Xlint:-options -Werror -source 8 -target 8 -bootclasspath "$ANDROID_JAR" \
  -d "$guard_build/classes" $(find media-guard/src -name '*.java')
"$D8" --release --min-api 29 --lib "$ANDROID_JAR" --output "$guard_build/dex" \
  $(find "$guard_build/classes" -name '*.class')
cp "$guard_build/dex/classes.dex" out/media_guard.dex

# Flashable KernelSU / Magisk module: ../module template + zygisk/<abi>.so + guard dex.
stage="out/module"
mkdir -p "$stage" out/assets
cp -R ../module/. "$stage/"
cp -R out/zygisk "$stage/zygisk"
cp out/media_guard.dex "$stage/media_guard.dex"
(cd "$stage" && rm -f ../cleaner_isolation.zip && zip -qr9 ../cleaner_isolation.zip . -x '.*')
# Also bundled into the APK so the app can install it through ksud / magisk.
cp out/cleaner_isolation.zip out/assets/cleaner_isolation.zip
echo "native artifacts in $(pwd)/out (module: out/cleaner_isolation.zip)"
