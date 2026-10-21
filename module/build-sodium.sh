#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
[ "$(uname -s)" = Linux ] || { echo 'Use the remote Linux builder'; exit 1; }
NDK_VERSION=$(sed -n 's/^ndkVersion=//p' ../gradle/android-toolchain.properties)
NDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}/ndk/$NDK_VERSION"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
SODIUM_ROOT="$PWD/build/sodium"
mkdir -p "$SODIUM_ROOT/source"
python3 - <<'PY'
import hashlib,pathlib
p=pathlib.Path('vendor/libsodium-1.0.20.tar.gz')
assert hashlib.sha256(p.read_bytes()).hexdigest() == 'ebb65ef6ca439333c2bb41a0c1990587288da07f6c7fd07cb3a18cc18d30ce19'
PY
if [ ! -f "$SODIUM_ROOT/source/configure" ]; then tar -xzf vendor/libsodium-1.0.20.tar.gz --strip-components=1 -C "$SODIUM_ROOT/source"; fi
for spec in 'arm64-v8a aarch64-linux-android aarch64-linux-android' 'armeabi-v7a armv7a-linux-androideabi arm-linux-androideabi' 'x86 i686-linux-android i686-linux-android' 'x86_64 x86_64-linux-android x86_64-linux-android'; do
  read -r ABI TARGET HOST_TARGET <<< "$spec"
  if [ -f "$SODIUM_ROOT/$ABI/lib/libsodium.a" ]; then continue; fi
  mkdir -p "$SODIUM_ROOT/obj-$ABI"
  (
    cd "$SODIUM_ROOT/obj-$ABI"
    CC="$TOOLCHAIN/bin/${TARGET}23-clang" AR="$TOOLCHAIN/bin/llvm-ar" RANLIB="$TOOLCHAIN/bin/llvm-ranlib" CFLAGS='-O2 -fPIC' \
      "$SODIUM_ROOT/source/configure" --host="$HOST_TARGET" --prefix="$SODIUM_ROOT/$ABI" --disable-shared --enable-static --disable-asm
    make -j4
    make install
  )
done
