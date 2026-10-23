#pragma once
#include <jni.h>
#include <stdbool.h>
#ifdef __cplusplus
extern "C" {
#endif
#define MAGISK_MODULE_TYPE_ZYGISK "Zygisk"
void fingerprintPre(JNIEnv* env, jstring* appDataDir, jstring* niceName, int uid, int companion);
void fingerprintPost(JNIEnv* env, const char* type);
bool fingerprintEnabled();
void fingerprintDisable();

#ifdef __cplusplus
}
#endif
