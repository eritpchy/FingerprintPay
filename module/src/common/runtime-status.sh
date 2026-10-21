#!/system/bin/sh
MODDIR=/data/adb/modules/zygisk-module-xfingerprint-pay-all
NEXT=/data/adb/modules/zygisksu
UPDATE=/data/adb/modules_update/zygisk-module-xfingerprint-pay-all

bounded() {
  command -v timeout >/dev/null 2>&1 || return 127
  timeout -s KILL 2 "$@"
}
probe() {
  framework=Zygisk
  state=unknown
  if [ ! -f "$MODDIR/module.prop" ]; then state=missing; return; fi
  if [ -f "$MODDIR/disable" ] || [ -f "$MODDIR/remove" ]; then state=module_disabled; return; fi
  if [ -d "$NEXT" ]; then
    framework=ZygiskNext
    if [ -f "$NEXT/disable" ] || [ -f "$NEXT/remove" ]; then state=disabled; return; fi
    if [ ! -x "$NEXT/bin/zygiskd" ]; then return; fi
    output=$(bounded "$NEXT/bin/zygiskd" status 2>/dev/null) || return
    if printf '%s\n' "$output" | grep -q '^inject_state:2$'; then state=disabled; return; fi
    if printf '%s\n' "$output" | grep -q '^inject_state:3$'; then state=failed; return; fi
    if ! printf '%s\n' "$output" | grep -q '^inject_state:1$'; then return; fi
    if ! printf '%s\n' "$output" | grep -q '^zygote_state_[0-9]*:1,0,'; then state=unloaded; return; fi
    if printf '%s\n' "$output" | grep -E '^modules(64|32):' | grep -qE ',zygisk-module-xfingerprint-pay-all(,|$)'; then state=active; else state=unloaded; fi
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
    if [ "$ZYGISK_ENABLED" = 1 ]; then state=active; else
      for pid in $(pidof zygote zygote64 2>/dev/null); do
        if grep -q 'zygisk' "/proc/$pid/maps" 2>/dev/null; then state=active; break; fi
      done
    fi
    if [ -f "$MODDIR/zygisk/unloaded" ]; then state=unloaded; fi
  else
    state=unavailable
  fi
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
