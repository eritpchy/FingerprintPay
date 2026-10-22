#!/system/bin/sh
MODDIR=/data/adb/modules/zygisk-module-xfingerprint-pay-all
NEXT=/data/adb/modules/zygisksu
REZYGISK=/data/adb/modules/rezygisk
UPDATE=/data/adb/modules_update/zygisk-module-xfingerprint-pay-all

bounded() {
  command -v timeout >/dev/null 2>&1 || return 127
  timeout -s KILL 2 "$@"
}
runtime_active() {
  [ "$ZYGISK_ENABLED" = 1 ] && return 0
  for pid in $(pidof zygote zygote64 2>/dev/null); do
    if grep -qi 'zygisk' "/proc/$pid/maps" 2>/dev/null; then return 0; fi
  done
  return 1
}
magisk_active() {
  command -v magisk >/dev/null 2>&1 || return 1
  for pid in $(pidof zygote zygote64 2>/dev/null); do
    if grep -qE '/system/lib(64)?/libzygisk\.so' "/proc/$pid/maps" 2>/dev/null; then return 0; fi
  done
  magisk_path=$(bounded magisk --path 2>/dev/null) || return 1
  [ -n "$magisk_path" ] || return 1
  for pid in $(pidof zygiskd zygiskd32 zygiskd64 2>/dev/null); do
    executable=$(readlink "/proc/$pid/exe" 2>/dev/null)
    case "$executable" in "$magisk_path/magisk"|"$magisk_path/magisk32"|"$magisk_path/magisk64") return 0 ;; esac
  done
  return 1
}
probe_framework() {
  framework=Zygisk
  state=unknown
  provider=''
  disabled_provider=''
  # Installed providers and next-boot settings do not identify the running injector.
  if magisk_active; then framework=Magisk; state=active; return; fi
  for candidate in "$NEXT" "$REZYGISK"; do
    [ -d "$candidate" ] || continue
    [ -n "$disabled_provider" ] || disabled_provider=$candidate
    if [ ! -f "$candidate/disable" ] && [ ! -f "$candidate/remove" ]; then provider=$candidate; break; fi
  done
  if [ -n "$provider" ]; then
    if [ "$provider" = "$NEXT" ]; then framework=ZygiskNext; else framework=ReZygisk; fi
  fi
  if [ "$provider" = "$REZYGISK" ]; then
    output=$(bounded "$MODDIR/bin/fpconfig" rezygisk-status 2>/dev/null) || return
    case "$output" in active|disabled|failed|unloaded|unknown) state=$output ;; esac
  elif [ "$provider" = "$NEXT" ]; then
    if [ ! -x "$NEXT/bin/zygiskd" ]; then return; fi
    output=$(bounded "$NEXT/bin/zygiskd" status 2>/dev/null) || return
    if printf '%s\n' "$output" | grep -q '^inject_state:2$'; then state=disabled; return; fi
    if printf '%s\n' "$output" | grep -q '^inject_state:3$'; then state=failed; return; fi
    if ! printf '%s\n' "$output" | grep -q '^inject_state:1$'; then return; fi
    if printf '%s\n' "$output" | grep -q '^zygote_state_[0-9]*:'; then
      if ! printf '%s\n' "$output" | grep -q '^zygote_state_[0-9]*:1,0,'; then state=unloaded; return; fi
      state=active
    fi
    if printf '%s\n' "$output" | grep -qE '^modules(64|32):'; then
      if printf '%s\n' "$output" | grep -E '^modules(64|32):' | grep -qE ',zygisk-module-xfingerprint-pay-all(,|$)'; then state=active; else state=unloaded; fi
    fi
    if printf '%s\n' "$output" | grep '^modules_with_issue:' | grep -q ',zygisk-module-xfingerprint-pay-all='; then state=unloaded; fi
    if printf '%s\n' "$output" | grep -q '^zygisk_jni_err_[0-9]*:.'; then state=failed; fi
  elif command -v magisk >/dev/null 2>&1; then
    framework=Magisk
    configured=$(bounded magisk --sqlite "SELECT value FROM settings WHERE key='zygisk'" 2>/dev/null) || return
    case "$configured" in
      ''|value=0) state=disabled; return ;;
      value=1) ;;
      *) return ;;
    esac
    runtime_active && state=active
  elif [ -n "$disabled_provider" ]; then
    if [ "$disabled_provider" = "$NEXT" ]; then framework=ZygiskNext; else framework=ReZygisk; fi
    state=disabled
  else
    state=unknown
  fi
}
probe() {
  framework=Zygisk
  if [ ! -f "$MODDIR/module.prop" ]; then state=missing; return; fi
  if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ]; then state=module_disabled; return; fi
  if [ -f "$MODDIR/zygisk/unloaded" ]; then state=unloaded; return; fi
  probe_framework
  # Compatible providers need not expose a vendor-specific status command.
  case "$state" in
    unknown|unavailable) runtime_active && state=active ;;
    disabled) if [ -z "$provider" ] && runtime_active; then framework=Zygisk; state=active; fi ;;
  esac
  if [ "$state" = active ] && [ -f "$UPDATE/module.prop" ]; then state=pending; fi
}
report() { printf '{"state":"%s","framework":"%s"}\n' "$state" "$framework"; }
update_card() {
  [ -r "$MODDIR/description.original" ] || return
  base=$(cat "$MODDIR/description.original")
  case "$state" in
    active) prefix='' ;;
    disabled|unavailable) prefix='[Zygisk 未启用] ' ;;
    module_disabled) prefix='[模块未启用] ' ;;
    unloaded) prefix='[模块未加载] ' ;;
    failed) prefix='[Zygisk 注入异常] ' ;;
    pending) prefix='[待重启生效] ' ;;
    *) prefix='[Zygisk 状态未知] ' ;;
  esac
  desired="description=$prefix$base"
  grep -qxF "$desired" "$MODDIR/module.prop" && return
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in description=*) printf '%s\n' "$desired" ;; *) printf '%s\n' "$line" ;; esac
  done < "$MODDIR/module.prop" > "$MODDIR/module.prop.new" || return
  chmod 0644 "$MODDIR/module.prop.new"
  mv -f "$MODDIR/module.prop.new" "$MODDIR/module.prop"
}
if [ "$1" = watch ]; then
  while [ -f "$MODDIR/module.prop" ]; do
    probe
    update_card
    sleep 15
  done
else
  probe
  [ "$1" = card ] && update_card
  report
fi
