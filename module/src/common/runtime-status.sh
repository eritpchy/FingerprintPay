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
probe_target() {
  target_state=unknown
  target_process=unknown
  policy=unknown
  backend=unknown
  [ -n "$target_package" ] || return
  root_version=$(bounded su -v 2>/dev/null)
  case "$root_version" in
    *KernelSU*|*SukiSU*) backend=KernelSU ;;
    *APatch*) backend=APatch ;;
    *MAGISK*|*Magisk*) backend=Magisk ;;
  esac
  if [ "$backend" = unknown ]; then
    if [ -x /data/adb/ap/bin/apd ]; then backend=APatch
    elif [ -x /data/adb/ksud ] || [ -x /data/adb/ksu/bin/ksud ]; then backend=KernelSU
    elif command -v magisk >/dev/null 2>&1; then backend=Magisk
    fi
  fi
  case "$framework" in
    Magisk)
      bounded magisk --denylist status >/dev/null 2>&1
      case "$?" in 0) policy=enforced ;; 1) policy=disabled ;; esac ;;
    ZygiskNext)
      enforcement=$(cat /data/adb/zygisksu/denylist_enforce 2>/dev/null)
      case "$enforcement" in ''|0) policy=disabled ;; 1) policy=enforced ;; 2) policy=unmount_only ;; esac ;;
    # ReZygisk retains module callbacks while unmounting denylisted processes.
    ReZygisk) policy=unmount_only ;;
  esac
  case "$backend" in
    KernelSU|APatch)
      profile=$(bounded "$MODDIR/bin/fpconfig" root-profile "$backend" "$target_package" "$target_user" 2>/dev/null) || return
      case "$profile" in
        missing) target_state=missing; return ;;
        '0|0'|'0|1') match=none ;;
        '1|0'|'1|1') match=main ;;
        *) return ;;
      esac
      ;;
    Magisk)
      entries=$(bounded magisk --denylist ls 2>/dev/null) || return
      match=$(printf '%s\n' "$entries" | (
        match=none
        while IFS='| 	' read -r listed_package listed_process rest; do
          [ "$listed_package" = "$target_package" ] || continue
          if [ "$listed_process" = "$target_package" ]; then match=main
          elif [ "$match" = none ]; then match=partial
          fi
        done
        printf '%s\n' "$match"
      ))
      ;;
    *) return ;;
  esac
  if [ "$framework" = ZygiskNext ] && [ "$backend" = Magisk ]; then
    nonroot=$(cat /data/adb/zygisksu/denylist_policy 2>/dev/null)
    # The non-root whitelist is Magisk-only and needs a root-grant query.
    case "$nonroot" in ''|0) ;; *) return ;; esac
  fi
  target_process=$match
  if [ "$match" = none ]; then target_state=clear
  elif [ "$policy" = enforced ]; then target_state=excluded
  else target_state=listed
  fi
}
report() {
  printf '{"state":"%s","framework":"%s"' "$state" "$framework"
  if [ -n "$target_package" ]; then
    probe_target
    printf ',"target":{"package":"%s","user":%s,"state":"%s","policy":"%s","process":"%s","backend":"%s"}' \
      "$target_package" "$target_user" "$target_state" "$policy" "$target_process" "$backend"
  fi
  printf '}\n'
}
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
target_package=''
target_user=0
if [ "$1" = target ]; then
  [ "$#" = 3 ] || exit 1
  case "$2" in com.tencent.mm|com.eg.android.AlipayGphone|com.taobao.taobao|com.tencent.mobileqq|com.unionpay) target_package=$2 ;; *) exit 1 ;; esac
  case "$3" in ''|*[!0-9]*|0[0-9]*) exit 1 ;; esac
  [ "${#3}" -le 5 ] && [ "$3" -le 21474 ] || exit 1
  target_user=$3
fi
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
