# Native dependencies

- nlohmann/json 3.11.3 (MIT): `https://raw.githubusercontent.com/nlohmann/json/v3.11.3/single_include/nlohmann/json.hpp`
  SHA-256: `9bea4c8066ef4a1c206b2be5a36302f8926f7fdc6087af5d20b417d0cf103ea6`.
  Used only by the privileged configuration executable, not the injected library.
- libsodium 1.0.20 (ISC): `https://download.libsodium.org/libsodium/releases/libsodium-1.0.20.tar.gz`
  SHA-256: `ebb65ef6ca439333c2bb41a0c1990587288da07f6c7fd07cb3a18cc18d30ce19`.
  The source archive includes its license; `build-sodium.sh` verifies the digest before extraction.
- libsodium.js 0.7.15 (ISC): `https://raw.githubusercontent.com/jedisct1/libsodium.js/0.7.15/dist/browsers/sodium.js`.
  The standalone FingerprintPay-WebUI project converts the browser bundle to ES5 on the remote Linux builder for Android 6 WebView, preserving its browser global binding. Its npm lockfile pins the Babel toolchain. This repository tracks the output in `module/webui/dist/`; `compat.js` supplies old WebView built-ins.
- Zygisk API header: `https://github.com/topjohnwu/zygisk-module-sample/tree/7bb941ac8edfcffd1d23761e401c45ca95409dc1`, API 4 (pinned).
- Design reference: `https://github.com/u9521/zygisk-BiometricPay/tree/9d398f3d3d32d8953bc7746034d83c2adf09df00`.
