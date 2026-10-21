#pragma once
#include <jni.h>
#include <stddef.h>
#include "dex_payload.h"
#ifdef __cplusplus
extern "C" {
#endif
jobject memoryDexLoader(JNIEnv* env, const DexBytes* dex, size_t count, jobject parent);
void* artSymbol(const char* name);
#ifdef __cplusplus
}
#endif
