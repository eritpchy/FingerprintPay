#!/system/bin/sh
set -eu
MODDIR=${0%/*}
ID=zygisk-module-xfingerprint-pay-all
DATA=/data/adb/fingerprintpay/update
fail() { printf 'FP_UPDATE_ERROR:%s\n' "$1" >&2; exit 1; }
[ "$(id -u)" = 0 ] || fail '请授予 WebUI 宿主 Root 权限'
property() { sed -n "s/^$1=//p" "$2" | head -n 1; }
manager() {
  provider=$(su -v 2>/dev/null || true)
  case "$provider" in
    *APatch*) [ -x /data/adb/ap/bin/apd ] && { printf APatch; return; };;
    *KernelSU*)
      for p in /data/adb/ksud /data/adb/ksu/bin/ksud; do
        [ ! -x "$p" ] || { printf KernelSU; return; }
      done;;
    *MAGISK*|*Magisk*) command -v magisk >/dev/null 2>&1 && { printf Magisk; return; };;
  esac
  printf unknown
}
fetch() {
  command -v curl >/dev/null 2>&1 || fail '当前环境缺少 curl，请在 Root 管理器中更新模块'
  curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' \
    --connect-timeout 10 --max-time "$3" --max-filesize "$4" --output "$2" "$1"
}
case "${1:-}" in
  info)
    version=$(property version "$MODDIR/module.prop"); version=${version#v}
    case "$version" in ''|*[!0-9.]*) fail '无法读取模块版本';; esac
    pending=false
    [ ! -f "/data/adb/modules_update/$ID/module.prop" ] || pending=true
    printf '{"version":"%s","manager":"%s","pending":%s}\n' "$version" "$(manager)" "$pending"
    ;;
  check)
    umask 077
    mkdir -p "$DATA"
    meta=$(mktemp "$DATA/release.XXXXXX")
    trap 'unlink "$meta"' EXIT
    for url in https://api.github.com/repos/eritpchy/FingerprintPay/releases/latest https://accelerate.xdow.net/api/repos/eritpchy/FingerprintPay/releases/latest; do
      if fetch "$url" "$meta" 25 1048576; then cat "$meta"; exit 0; fi
    done
    fail '检查更新失败，请检查网络后重试，或在 Root 管理器中更新模块'
    ;;
  install)
    [ "$#" = 4 ] || fail '更新参数不完整'
    version=$2; digest=$3; size=$4
    case "$version" in ''|*[!0-9.]*|.*|*..*|*.) fail '更新版本无效';; esac
    [ "${#version}" -le 32 ] || fail '更新版本无效'
    case "$digest" in *[!0-9a-f]*) fail '更新校验值无效';; esac
    [ "${#digest}" = 64 ] || fail '更新校验值无效'
    case "$size" in ''|*[!0-9]*) fail '更新文件大小无效';; esac
    [ "${#size}" -le 9 ] && [ "$size" -gt 0 ] && [ "$size" -le 104857600 ] || fail '更新文件大小无效'
    target=$(manager)
    [ "$target" != unknown ] || fail '当前 Root 框架不支持直接安装，请在 Root 管理器中更新模块'
    [ ! -f "/data/adb/modules_update/$ID/module.prop" ] || fail '模块更新已安装，请重启设备后再检查更新'
    umask 077
    mkdir -p "$DATA"
    mkdir "$DATA/lock" 2>/dev/null || fail '已有更新任务正在进行，请稍后重试'
    archive="$DATA/module.zip.part"
    trap 'test ! -f "$archive" || unlink "$archive"; rmdir "$DATA/lock"' EXIT
    name="zygisk-module-xfingerprint-pay-all-v$version-release.zip"
    downloaded=false
    for url in "https://file.xdow.net/d/download/fingerprintpay/$version/$name" "https://github.com/eritpchy/FingerprintPay/releases/download/$version-ci/$name"; do
      if fetch "$url" "$archive" 90 "$size"; then
        actual=$(sha256sum "$archive"); actual=${actual%% *}
        if [ "$(wc -c < "$archive" | tr -d ' ')" = "$size" ] && [ "$actual" = "$digest" ]; then downloaded=true; break; fi
      fi
    done
    [ "$downloaded" = true ] || fail '下载失败或校验不通过，未安装模块，请稍后重试'
    metadata=$(unzip -p "$archive" module.prop) || fail '模块压缩包无效'
    [ "$(printf '%s\n' "$metadata" | sed -n 's/^id=//p')" = "$ID" ] || fail '下载的不是指纹支付模块'
    [ "$(printf '%s\n' "$metadata" | sed -n 's/^version=//p')" = "v$version" ] || fail '模块版本不一致'
    code=$(printf '%s\n' "$metadata" | sed -n 's/^versionCode=//p')
    current=$(property versionCode "$MODDIR/module.prop")
    case "$code:$current" in *[!0-9:]*|:*|*:) fail '模块版本码无效';; esac
    [ "$code" -gt "$current" ] || fail '已安装此版本或更新版本，无需重复安装'
    printf '校验完成，正在安装 %s\n' "$version"
    case "$target" in
      APatch) /data/adb/ap/bin/apd module install "$archive";;
      KernelSU)
        installer=/data/adb/ksud
        [ -x "$installer" ] || installer=/data/adb/ksu/bin/ksud
        "$installer" module install "$archive";;
      Magisk) magisk --install-module "$archive";;
    esac
    staged="/data/adb/modules_update/$ID/module.prop"
    [ -f "$staged" ] && [ "$(property versionCode "$staged")" = "$code" ] || fail '安装程序未生成预期更新，请查看 Root 管理器中的模块状态'
    printf '\nFP_UPDATE_INSTALLED\n'
    ;;
  *) fail '不支持的更新操作';;
esac
