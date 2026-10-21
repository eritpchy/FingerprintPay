#!/bin/bash
set -e
cd ${0%/*}
MODULE_GRALDE_TASK="$1"
MODULE_GRALDE_FILE="$2"
PLUGIN_TYPE_NAME="$3"
MODULE_TEMPLATE="../3rdparty/MagiskModuleTemplate"
VERSION_NAME=$(cat ../app/build.gradle| grep versionName | sed -E 's/.+"(.+)".*/\1/g')
VERSION_CODE=$(cat ../app/build.gradle| grep versionCode | sed -E 's/.+versionCode +([0-9]+).*/\1/g')
APP_PRODUCT_TARGET=$(echo "$MODULE_GRALDE_FILE"|sed -E 's/.+\/(.+)\..+/\1/g')
MODULE_LIB_NAME="$(echo "$PLUGIN_TYPE_NAME" | tr '[:upper:]' '[:lower:]')-module-xfingerprint-pay-$APP_PRODUCT_TARGET"
echo VERSION_NAME: $VERSION_NAME
echo VERSION_CODE: $VERSION_CODE
bash ./reset.sh
cp ../gradlew ../gradlew.bat "$MODULE_TEMPLATE/"
cp ../gradle/wrapper/* "$MODULE_TEMPLATE/gradle/wrapper/"
cp ../gradle.properties "$MODULE_TEMPLATE/gradle.properties"
cp ./src/gradle/template-settings.gradle "$MODULE_TEMPLATE/settings.gradle"
cp ./src/gradle/template-build.gradle "$MODULE_TEMPLATE/build.gradle"
cp ./src/gradle/template-module.gradle "$MODULE_TEMPLATE/module/build.gradle"
bash ../gradlew -p .. :app:exportReleaseDex -Pxposed=false
cp -rfv ./src/cpp/* $MODULE_TEMPLATE/module/src/main/cpp/
cp -rfv "$MODULE_GRALDE_FILE" $MODULE_TEMPLATE/module.gradle
cp -rfv "./src/gradle/fingerprint.gradle" $MODULE_TEMPLATE/
if [ -f "../local.properties" ]; then cp -rfv ../local.properties $MODULE_TEMPLATE/; fi
if [ "$PLUGIN_TYPE_NAME" == "Zygisk" ]; then
  echo "ZYGISK_MODULE_LIB_NAME=\"$MODULE_LIB_NAME\"" > $MODULE_TEMPLATE/template/magisk_module/customize.sh
  cat ./src/zygisk/customize.sh >> $MODULE_TEMPLATE/template/magisk_module/customize.sh
  python3 -c 'import pathlib,sys; pathlib.Path(sys.argv[1]).unlink(missing_ok=True)' "$MODULE_TEMPLATE/template/magisk_module/riru.sh"
else
  cat ./src/magisk/customize.sh >> $MODULE_TEMPLATE/template/magisk_module/customize.sh
fi
echo "rm -f \"/data/local/tmp/lib$MODULE_LIB_NAME.dex\" || true" >> $MODULE_TEMPLATE/template/magisk_module/uninstall.sh
perl -0777 -i -pe  's/(forkAndSpecializePre[\W\w]+?{[\W\w]+?)}/$1    fingerprintPre(env, appDataDir, niceName);\n}/'  $MODULE_TEMPLATE/module/src/main/cpp/main.cpp
perl -0777 -i -pe  's/(forkAndSpecializePost[\W\w]+?{[\W\w]*?)}/$1    fingerprintPost(env, MAGISK_MODULE_TYPE_RIRU);\n    }/'  $MODULE_TEMPLATE/module/src/main/cpp/main.cpp
perl -0777 -i -pe  's/(specializeAppProcessPre[\W\w]+?{[\W\w]+?)}/$1    fingerprintPre(env, appDataDir, niceName);\n}/'  $MODULE_TEMPLATE/module/src/main/cpp/main.cpp
perl -0777 -i -pe  's/(specializeAppProcessPost[\W\w]+?{[\W\w]+?)}/$1    fingerprintPost(env, MAGISK_MODULE_TYPE_RIRU);\n}/'  $MODULE_TEMPLATE/module/src/main/cpp/main.cpp
perl -0777 -i -pe  's/^/#include "fingerprint.h"\n/'  $MODULE_TEMPLATE/module/src/main/cpp/main.cpp
perl -i -pe  's/(main\.cpp)/$1 fingerprint.cpp zygisk_main.cpp resource_extractor.cpp crc32.cpp/g'  $MODULE_TEMPLATE/module/src/main/cpp/CMakeLists.txt
perl -0777 -i -pe 's/find_program\(CRC32_COMMAND crc32\)[\W\w]+?add_definitions\(-DRESOURCE_CRC32=0x\$\{RESOURCE_CRC32\}\)/include("\${CMAKE_CURRENT_SOURCE_DIR}\/resource_crc32.cmake")/' "$MODULE_TEMPLATE/module/src/main/cpp/CMakeLists.txt"
$MODULE_TEMPLATE/gradlew -p $MODULE_TEMPLATE clean \
  -PVERSION_NAME=$VERSION_NAME \
  -PVERSION_CODE=$VERSION_CODE \
  -PPLUGIN_TYPE_NAME=$PLUGIN_TYPE_NAME \
  -PMODULE_LIB_NAME=$MODULE_LIB_NAME \

$MODULE_TEMPLATE/gradlew -p $MODULE_TEMPLATE $MODULE_GRALDE_TASK \
  -PVERSION_NAME=$VERSION_NAME \
  -PVERSION_CODE=$VERSION_CODE \
  -PPLUGIN_TYPE_NAME=$PLUGIN_TYPE_NAME \
  -PMODULE_LIB_NAME=$MODULE_LIB_NAME \

if [ ! -d "./build/release" ]; then mkdir -p "./build/release"; fi
find $MODULE_TEMPLATE/out -name "*.zip" | xargs -I{} bash -c "cp -fv {} ./build/release/\$(basename {})"
ZIPNAME=$(ls $MODULE_TEMPLATE/out/ | grep -E "\.zip$" | head -n1 | sed  -E 's/-[A-Za-z]+-v/-all-v/g')
CURRENT_DIR="$PWD"
cd "$MODULE_TEMPLATE/out"
zip -u "$CURRENT_DIR/build/release/$ZIPNAME" *.zip
cd "$CURRENT_DIR/src/installer"
zip -ur "$CURRENT_DIR/build/release/$ZIPNAME" * || true
cd "$CURRENT_DIR"
bash ./reset.sh

