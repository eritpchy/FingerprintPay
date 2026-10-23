#include "memory_dex.h"
#include "debug_payload.h"
#include <sys/system_properties.h>
#include <dlfcn.h>
#include <memory>
#include <string>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <android/log.h>
#ifdef FP_DEBUG_FILE_DEX
#include <unistd.h>
static std::vector<unsigned char> debugProbePayload;
extern "C" JNIEXPORT jobject JNICALL
Java_com_surcumference_fingerprint_runtime_MemoryDexProbe_loadDebug(JNIEnv* env, jclass, jstring appData) {
    const char* path = env->GetStringUTFChars(appData, nullptr);
    if (!path) return nullptr;
    DebugPayloadResult result = loadDebugPayload(path, debugProbePayload);
    env->ReleaseStringUTFChars(appData, path);
    if (result != DebugPayloadResult::Loaded) return nullptr;
    std::vector<DexBytes> dex;
    if (!parseDexPayload(debugProbePayload.data(), debugProbePayload.size(), dex)) return nullptr;
    jclass cls = env->FindClass("java/lang/ClassLoader");
    jobject parent = env->CallStaticObjectMethod(cls, env->GetStaticMethodID(cls, "getSystemClassLoader", "()Ljava/lang/ClassLoader;"));
    return memoryDexLoader(env, dex.data(), dex.size(), parent);
}
#endif

static int sdk() { char value[PROP_VALUE_MAX]{}; __system_property_get("ro.build.version.sdk", value); return atoi(value); }
static jobject emptyLoader(JNIEnv* env, jobject parent) {
    jclass cls = env->FindClass("dalvik/system/PathClassLoader");
    if (!cls || env->ExceptionCheck()) return nullptr;
    jmethodID constructor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    if (!constructor || env->ExceptionCheck()) return nullptr;
    return env->NewObject(cls, constructor, env->NewStringUTF(""), parent);
}
// ART 6/7 return an owning pointer; retaining it keeps the native DexFile alive with its cookie.
struct ArtDexFile;
struct RetainDex { void operator()(const ArtDexFile*) const {} };
using ArtPtr = std::unique_ptr<const ArtDexFile, RetainDex>;
static jobject legacyDex(JNIEnv* env, const DexBytes& dex, int api, size_t index) {
    void* art = dlopen("libart.so", RTLD_NOW);
    using Open = ArtPtr (*)(const unsigned char*, size_t, const std::string&, uint32_t, void*, const void*, std::string*);
    const char* symbol = sizeof(void*) == 8
        ? "_ZN3art7DexFile10OpenMemoryEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"
        : "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_";
    auto open = reinterpret_cast<Open>(art ? dlsym(art, symbol) : nullptr);
    if (!open) open = reinterpret_cast<Open>(artSymbol(symbol));
    if (!open) { if (art) dlclose(art); __android_log_print(ANDROID_LOG_ERROR, "FingerprintPay", "ART memory symbol unavailable"); return nullptr; }
    uint32_t checksum; memcpy(&checksum, dex.data + 8, sizeof(checksum));
    std::string error, location = "fingerprintpay-memory-" + std::to_string(index);
    ArtPtr file = open(dex.data, dex.size, location, checksum, nullptr, nullptr, &error);
    if (!file) { if (art) dlclose(art); __android_log_print(ANDROID_LOG_ERROR, "FingerprintPay", "ART memory open failed: %s", error.c_str()); return nullptr; }
    jclass cls = env->FindClass("dalvik/system/DexFile");
    if (!cls || env->ExceptionCheck()) return nullptr;
    jobject result = env->AllocObject(cls);
    if (!result || env->ExceptionCheck()) return nullptr;
    jfieldID cookie = env->GetFieldID(cls, "mCookie", "Ljava/lang/Object;");
    if (!cookie || env->ExceptionCheck()) return nullptr;
    jlong pointers[2] = {0, reinterpret_cast<jlong>(file.release())};
    jlongArray array = env->NewLongArray(api == 23 ? 1 : 2);
    if (!array || env->ExceptionCheck()) return nullptr;
    env->SetLongArrayRegion(array, 0, api == 23 ? 1 : 2, pointers + (api == 23 ? 1 : 0));
    env->SetObjectField(result, cookie, array);
    if (api >= 24) {
        jfieldID internal = env->GetFieldID(cls, "mInternalCookie", "Ljava/lang/Object;");
        if (!internal || env->ExceptionCheck()) return nullptr;
        env->SetObjectField(result, internal, array);
    }
    jfieldID fileName = env->GetFieldID(cls, "mFileName", "Ljava/lang/String;");
    if (!fileName || env->ExceptionCheck()) return nullptr;
    env->SetObjectField(result, fileName, env->NewStringUTF(location.c_str()));
    return result;
}
jobject memoryDexLoader(JNIEnv* env, const DexBytes* dex, size_t count, jobject parent) {
    if (!dex || !count || count > 64 || env->ExceptionCheck()) return nullptr;
    int api = sdk();
    jclass buffer = env->FindClass("java/nio/ByteBuffer");
    if (!buffer || env->ExceptionCheck()) return nullptr;
    jobjectArray buffers = env->NewObjectArray(count, buffer, nullptr);
    if (!buffers || env->ExceptionCheck()) return nullptr;
    for (size_t i = 0; i < count; ++i) {
        if (!dex[i].data || dex[i].size < 112 || memcmp(dex[i].data, "dex\n", 4)) return nullptr;
        jobject bytes = env->NewDirectByteBuffer(const_cast<unsigned char*>(dex[i].data), dex[i].size);
        if (!bytes || env->ExceptionCheck()) return nullptr;
        env->SetObjectArrayElement(buffers, i, bytes); env->DeleteLocalRef(bytes);
    }
    if (api >= 27) {
        jclass cls = env->FindClass("dalvik/system/InMemoryDexClassLoader");
        if (!cls || env->ExceptionCheck()) return nullptr;
        jmethodID constructor = env->GetMethodID(cls, "<init>", "([Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
        if (!constructor || env->ExceptionCheck()) return nullptr;
        return env->NewObject(cls, constructor, buffers, parent);
    }
    if (api < 23) return nullptr;
    jobject loader = emptyLoader(env, parent);
    if (!loader || env->ExceptionCheck()) return nullptr;
    jclass base = env->FindClass("dalvik/system/BaseDexClassLoader");
    if (!base || env->ExceptionCheck()) return nullptr;
    jfieldID listField = env->GetFieldID(base, "pathList", "Ldalvik/system/DexPathList;");
    if (!listField || env->ExceptionCheck()) return nullptr;
    jobject list = env->GetObjectField(loader, listField);
    if (!list || env->ExceptionCheck()) return nullptr;
    jclass listClass = env->FindClass("dalvik/system/DexPathList");
    if (!listClass || env->ExceptionCheck()) return nullptr;
    jfieldID elementsField = env->GetFieldID(listClass, "dexElements", "[Ldalvik/system/DexPathList$Element;");
    if (!elementsField || env->ExceptionCheck()) return nullptr;
    jclass elementClass = env->FindClass("dalvik/system/DexPathList$Element");
    if (!elementClass || env->ExceptionCheck()) return nullptr;
    jobjectArray elements = env->NewObjectArray(count, elementClass, nullptr);
    if (!elements || env->ExceptionCheck()) return nullptr;
    for (size_t i = 0; i < count; ++i) {
        jobject element = nullptr;
        if (api == 26) {
            jclass cls = env->FindClass("dalvik/system/InMemoryDexClassLoader");
            if (!cls || env->ExceptionCheck()) return nullptr;
            jmethodID constructor = env->GetMethodID(cls, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
            if (!constructor || env->ExceptionCheck()) return nullptr;
            jobject single = env->NewObject(cls, constructor, env->GetObjectArrayElement(buffers, i), parent);
            if (!single || env->ExceptionCheck()) return nullptr;
            jobject singleList = env->GetObjectField(single, listField);
            if (!singleList || env->ExceptionCheck()) return nullptr;
            auto singleElements = static_cast<jobjectArray>(env->GetObjectField(singleList, elementsField));
            if (!singleElements || env->ExceptionCheck() || env->GetArrayLength(singleElements) != 1) return nullptr;
            element = env->GetObjectArrayElement(singleElements, 0);
        } else {
            jobject file = legacyDex(env, dex[i], api, i);
            if (!file || env->ExceptionCheck()) return nullptr;
            element = env->AllocObject(elementClass);
            if (!element || env->ExceptionCheck()) return nullptr;
            jfieldID field = env->GetFieldID(elementClass, "dexFile", "Ldalvik/system/DexFile;");
            if (!field || env->ExceptionCheck()) return nullptr;
            env->SetObjectField(element, field, file);
        }
        if (env->ExceptionCheck() || !element) return nullptr;
        env->SetObjectArrayElement(elements, i, element);
    }
    env->SetObjectField(list, elementsField, elements);
    return loader;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_surcumference_fingerprint_runtime_MemoryDexProbe_load(JNIEnv* env, jclass, jobjectArray buffers) {
    std::vector<DexBytes> dex;
    for (jsize i = 0; i < env->GetArrayLength(buffers); ++i) {
        jobject buffer = env->GetObjectArrayElement(buffers, i);
        auto* data = static_cast<unsigned char*>(env->GetDirectBufferAddress(buffer));
        jlong size = env->GetDirectBufferCapacity(buffer);
        if (!data || size < 112) return nullptr;
        dex.push_back({data, static_cast<size_t>(size)});
    }
    jclass cls = env->FindClass("java/lang/ClassLoader");
    jobject parent = env->CallStaticObjectMethod(cls, env->GetStaticMethodID(cls, "getSystemClassLoader", "()Ljava/lang/ClassLoader;"));
    return memoryDexLoader(env, dex.data(), dex.size(), parent);
}
