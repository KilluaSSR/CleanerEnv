#!/system/bin/sh
[ -f /data/adb/cleaner/autostart ] || exit 0

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done

APK=$(pm path killua.dev.cleaner 2>/dev/null | head -n 1 | cut -d: -f2)
[ -n "$APK" ] || exit 0

LIB="$(dirname "$APK")/lib"
for abi in arm64 arm x86_64 x86; do
  STARTER="$LIB/$abi/libcleaner_starter.so"
  if [ -x "$STARTER" ]; then
    exec "$STARTER" --apk="$APK"
  fi
done
