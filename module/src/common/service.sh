#!/system/bin/sh
MODDIR=${0%/*}
sh "$MODDIR/runtime-status.sh" watch >/dev/null 2>&1 &
DATA=/data/adb/fingerprintpay
mkdir -p "$DATA/bin"
chmod 0700 "$DATA" "$DATA/bin"
cp "$MODDIR/bin/fpconfig" "$DATA/bin/fpconfig.new"
chmod 0700 "$DATA/bin/fpconfig.new"
mv -f "$DATA/bin/fpconfig.new" "$DATA/bin/fpconfig"
runcon -- u:r:fingerprintpay_config:s0 /system/bin/sh -c 'exec "$1" serve' sh "$DATA/bin/fpconfig" &
if [ -f "$MODDIR/scope.pending" ]; then
  "$DATA/bin/fpconfig" apply-scope "$MODDIR/scope.pending" && mv "$MODDIR/scope.pending" "$MODDIR/scope.applied"
fi

(
  umask 077
  count=0
  while [ "$(getprop sys.boot_completed)" != 1 ] && [ "$count" -lt 90 ]; do
    sleep 1; count=$((count + 1))
  done
  abi=$(getprop ro.product.cpu.abi)
  "$DATA/bin/fpconfig" sensor-defaults "$MODDIR/zygisk/$abi.so" > "$DATA/sensor-default.json.new" 2>/dev/null &&
    mv -f "$DATA/sensor-default.json.new" "$DATA/sensor-default.json"
) &
