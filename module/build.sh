#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
[ "$(uname -s)" = Linux ] || { echo 'Use the remote Linux builder'; exit 1; }
MODULE_GRADLE_TASK="${1:-:module:assembleRelease}"
[ "$#" -le 1 ] || { echo "Usage: $0 [Gradle task]"; exit 1; }
PLUGIN_TYPE_NAME=Zygisk
FINGERPRINT_ROOT="$(cd .. && pwd)"
FRAMEWORK=zygisk
MODULE_TEMPLATE="$PWD/build/template-$FRAMEWORK-all"
MODULE_LIB_NAME="$FRAMEWORK-module-xfingerprint-pay-all"
PAYLOAD_VARIANT=Release
case "$MODULE_GRADLE_TASK" in
  *Debug*) PAYLOAD_VARIANT=Debug ;;
esac
VERSION_NAME=$(sed -n 's/.*versionName "\([^"]*\)".*/\1/p' ../app/build.gradle | head -1)
VERSION_CODE=$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' ../app/build.gradle | head -1)
bash build-sodium.sh
bash ../gradlew -p .. ":app:export${PAYLOAD_VARIANT}Payload"
python3 prepare-payload.py "../app/build/outputs/payload/${PAYLOAD_VARIANT,,}/payload.apk" build/payload
python3 - "$FINGERPRINT_ROOT" "$MODULE_TEMPLATE" "$FRAMEWORK" "$MODULE_LIB_NAME" <<'PY'
import pathlib,shutil,sys
root,out,framework,name=sys.argv[1:];root=pathlib.Path(root);out=pathlib.Path(out)
source=root/'3rdparty/MagiskModuleTemplate'
cpp=out/'module/src/main/cpp';cpp.mkdir(parents=True,exist_ok=True)
shutil.copy2(source/'module/src/main/AndroidManifest.xml',out/'module/src/main/AndroidManifest.xml')
mod=out/'template/magisk_module'
if mod.exists():shutil.rmtree(mod)
mod.mkdir(parents=True)
shutil.copy2(source/'template/magisk_module/module.prop',mod/'module.prop')
shutil.copy2(root/'module/src/installer/verify.sh',mod/'verify.sh')
shutil.copytree(source/'template/magisk_module/META-INF',mod/'META-INF',dirs_exist_ok=True)
(out/'gradle/wrapper').mkdir(parents=True,exist_ok=True)
for n in ['gradlew','gradlew.bat','gradle.properties']:shutil.copy2(root/n,out/n)
for p in (root/'gradle/wrapper').iterdir():shutil.copy2(p,out/'gradle/wrapper'/p.name)
for n in ['settings','build']:shutil.copy2(root/f'module/src/gradle/template-{n}.gradle',out/f'{n}.gradle')
shutil.copy2(root/'module/src/gradle/template-module.gradle',out/'module/build.gradle')
shutil.copy2(root/'module/src/gradle/all.gradle',out/'module.gradle')
for p in (root/'module/src/cpp').iterdir():
    if p.is_file():shutil.copy2(p,out/'module/src/main/cpp'/p.name)
mod=out/'template/magisk_module'
for p in (root/'module/src/common').iterdir():shutil.copy2(p,mod/p.name)
(mod/'customize.sh').write_text('FRAMEWORK='+framework+'\nMODULE_LIB_NAME='+name+'\n'+(root/'module/src/installer/customize.sh').read_text())
shutil.copytree(root/'module/webui/dist',mod/'webroot',dirs_exist_ok=True)
shutil.copy2(root/'module/build/payload/manifest.json',mod/'payload-manifest.json')
PY
"$MODULE_TEMPLATE/gradlew" -p "$MODULE_TEMPLATE" "$MODULE_GRADLE_TASK" -PfingerprintRoot="$FINGERPRINT_ROOT" \
  -PVERSION_NAME="$VERSION_NAME" -PVERSION_CODE="$VERSION_CODE" -PPLUGIN_TYPE_NAME="$PLUGIN_TYPE_NAME" -PMODULE_LIB_NAME="$MODULE_LIB_NAME"
mkdir -p build/release
cp "$MODULE_TEMPLATE"/out/*.zip build/release/
