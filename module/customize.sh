#!/system/bin/sh
SKIPUNZIP=0

if [ "$API" -lt 29 ]; then
  abort "! Android 10 (API 29) or newer is required"
fi

if [ "$KSU" = "true" ]; then
  ui_print "- KernelSU detected ($KSU_VER_CODE)"
  if [ ! -d /data/adb/modules/zygisksu ] && [ ! -d /data/adb/modules/rezygisk ]; then
    ui_print "! No Zygisk provider found. Install ZygiskNext to activate isolation."
  fi
elif [ -n "$MAGISK_VER_CODE" ]; then
  ui_print "- Magisk detected ($MAGISK_VER_CODE). Make sure Zygisk is enabled."
fi

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  [ -f "$MODPATH/zygisk/$abi.so" ] || ui_print "! missing zygisk/$abi.so"
done
[ -f "$MODPATH/media_guard.dex" ] || ui_print "! missing media_guard.dex (system provider handling disabled)"

DATA=/data/adb/cleaner
mkdir -p "$DATA/rules" "$DATA/run" "$DATA/history"
chown -R 0:0 "$DATA"
chmod 0700 "$DATA"

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
