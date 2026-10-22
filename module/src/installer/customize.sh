SKIPUNZIP=1
[ "$API" -ge 23 ] || abort '! Android 6.0+ required'
case "$ARCH" in arm) ABI=armeabi-v7a; ABI32=armeabi-v7a;; arm64) ABI=arm64-v8a; ABI32=armeabi-v7a;; x86) ABI=x86; ABI32=x86;; x64) ABI=x86_64; ABI32=x86;; *) abort '! Unsupported architecture';; esac
unzip -o "$ZIPFILE" 'verify.sh' -d "$TMPDIR" >&2
[ -f "$TMPDIR/verify.sh" ] || abort '! Missing integrity verifier'
. "$TMPDIR/verify.sh"
for name in module.prop description.original resources.apk payload-manifest.json service.sh runtime-status.sh update.sh uninstall.sh sepolicy.rule; do extract "$ZIPFILE" "$name" "$MODPATH"; done
if unzip -l "$ZIPFILE" | grep -q ' debug-file-dex$'; then extract "$ZIPFILE" debug-file-dex "$MODPATH"; fi
mkdir -p "$MODPATH/bin" "$MODPATH/webroot"
extract "$ZIPFILE" "bin/$ABI/fpconfig" "$MODPATH/bin" true
for name in index.html app.js module-update.js style.css compat.js host-insets.js sodium.js LICENSES.txt manifest.json donate-wechat.svg icons/wechat.svg icons/alipay.svg icons/taobao.svg icons/qq.svg icons/unionpay.svg; do
  destination="$MODPATH/webroot/$(dirname "$name")"
  mkdir -p "$destination"
  extract "$ZIPFILE" "webroot/$name" "$destination" true
done
mkdir -p "$MODPATH/zygisk"
extract "$ZIPFILE" "lib/$ABI32/lib$MODULE_LIB_NAME.so" "$MODPATH/zygisk" true
mv "$MODPATH/zygisk/lib$MODULE_LIB_NAME.so" "$MODPATH/zygisk/$ABI32.so"
if [ "$ABI" != "$ABI32" ]; then
  extract "$ZIPFILE" "lib/$ABI/lib$MODULE_LIB_NAME.so" "$MODPATH/zygisk" true
  mv "$MODPATH/zygisk/lib$MODULE_LIB_NAME.so" "$MODPATH/zygisk/$ABI.so"
fi
if [ "$API" -ge 26 ]; then
  printf '%s\n' 'allow appdomain fingerprintpay_resource_file file map' 'allow zygote fingerprintpay_resource_file file map' >> "$MODPATH/sepolicy.rule" || abort '! Cannot configure resource permissions'
fi
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/bin/fpconfig" 0 0 0755
DATA=/data/adb/fingerprintpay
mkdir -p "$DATA/bin" || abort '! Cannot create configuration directory'
chmod 0700 "$DATA" "$DATA/bin" || abort '! Cannot protect configuration directory'
# Stage choices with the module; apply them only after successful activation.
preserve_scope=false
if [ -f "$DATA/user-0.json" ] || [ -f "$DATA/scope-defaults.json" ]; then preserve_scope=true; fi
legacy_found=false
if [ "$preserve_scope" = false ]; then
  for legacy in /data/adb/modules/zygisk-module-xfingerprint-pay-* /data/adb/modules/riru-module-xfingerprint-pay-* /data/adb/modules/zygisk-biometric-pay; do
    if [ -f "$legacy/module.prop" ] && [ ! -f "$legacy/disable" ] && [ ! -f "$legacy/remove" ]; then legacy_found=true; fi
  done
fi
if [ "$preserve_scope" = true ]; then
  ui_print '- 升级保留现有作用域、设置和密码'
elif [ "$legacy_found" = true ]; then
  ui_print '- 保留旧模块已启用的应用'
else
  ui_print '- 首次安装默认启用微信、支付宝、淘宝、QQ 和云闪付'
fi
ui_print '- 可在模块设置页调整作用域'
: > "$MODPATH/scope.pending" || abort '! Cannot stage scope selection'
for app in wechat alipay taobao qq unionpay; do
  value=true
  if [ "$preserve_scope" = true ]; then
    value=keep
  elif [ "$legacy_found" = true ]; then
    value=false
    for framework in zygisk riru; do
      old="/data/adb/modules/$framework-module-xfingerprint-pay-$app"
      if [ -d "$old" ] && [ ! -f "$old/disable" ] && [ ! -f "$old/remove" ]; then value=true; fi
      old="/data/adb/modules/$framework-module-xfingerprint-pay-all"
      if [ -f "$old/module.prop" ] && [ ! -f "$old/disable" ] && [ ! -f "$old/remove" ]; then value=true; fi
    done
    if [ -f "/data/adb/modules/zygisk-biometric-pay/config/$app" ] && [ ! -f /data/adb/modules/zygisk-biometric-pay/disable ]; then value=true; fi
  fi
  printf '%s=%s\n' "$app" "$value" >> "$MODPATH/scope.pending" || abort '! Cannot save scope selection'
done
# All extraction and integrity checks have completed before retiring predecessors.
for base in /data/adb/modules /data/adb/modules_update; do
  for framework in zygisk riru; do
    for app in wechat alipay taobao qq unionpay all; do
      [ "$framework-module-xfingerprint-pay-$app" = "$MODULE_LIB_NAME" ] && continue
      old="$base/$framework-module-xfingerprint-pay-$app"
      if [ -d "$old" ] && [ ! -L "$old" ]; then
        touch "$old/disable" "$old/remove"
        ui_print "- 已安排卸载旧模块：$framework / $app（重启时清理）"
      fi
    done
  done
  for id in xfingerprint-pay-batch-installer zygisk-biometric-pay; do
    old="$base/$id"
    if [ -d "$old" ] && [ ! -L "$old" ]; then touch "$old/disable" "$old/remove"; fi
  done
done
ui_print '- 安装完成，重启后启用。旧版支付密码需在 WebUI 或应用内重新录入。'
