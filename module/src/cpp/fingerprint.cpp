#include "fingerprint.h"
#include "memory_dex.h"
#include "debug_payload.h"
#include "config_socket.h"
#include "log.h"
#include <cstring>
#include <string>
#include <vector>

extern "C" const unsigned char fp_payload_start[], fp_payload_end[];
static const char* packages[] = {"com.tencent.mm", "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.tencent.mobileqq", "com.unionpay"};
static const char* entries[] = {"WeChatPlugin", "AlipayPlugin", "TaobaoPlugin", "QQPlugin", "UnionPayPlugin"};
static int target = -1;
static std::string process;
#ifdef FP_DEBUG_FILE_DEX
static std::string appData;
// ART 6/7 keep native pointers into these bytes for the lifetime of the process.
static std::vector<unsigned char> debugPayload;
#endif
void fingerprintDisable() { target = -1; }
bool fingerprintEnabled() { return target >= 0; }
void fingerprintPre(JNIEnv* env, jstring* appDataDir, jstring* niceName, int uid, int companion) {
    target = -1; process.clear();
    if (!niceName || !*niceName) return;
    const char* value = env->GetStringUTFChars(*niceName, nullptr);
    if (!value) return;
    process = value; env->ReleaseStringUTFChars(*niceName, value);
    for (int i = 0; i < 5; ++i) if (process == packages[i] || (i == 3 && process == "com.tencent.mobileqq:tool")) { target = i; break; }
    if (target < 0) return;
#ifdef FP_DEBUG_FILE_DEX
    appData.clear();
    if (appDataDir && *appDataDir) {
        const char* directory = env->GetStringUTFChars(*appDataDir, nullptr);
        if (directory) { appData = directory; env->ReleaseStringUTFChars(*appDataDir, directory); }
    }
#endif
    LOGI("Checking target %s user %d", process.c_str(), uid / 100000);
    std::string query = "{\"op\":\"scope\",\"package\":\"" + std::string(packages[target])
            + "\",\"user\":" + std::to_string(uid / 100000) + "}";
    if (companion >= 0) {
        if (!fp::frame(companion, query, true) || !fp::frame(companion, query, false)) { LOGE("Scope companion transport failed: %d", errno); target = -1; return; }
    } else query = fp::request(query);
    if (query != "1") { LOGW("Injection skipped: scope reply %s", query == "0" ? "disabled" : query.empty() ? "unavailable" : "invalid"); target = -1; return; }
}
void fingerprintPost(JNIEnv* env, const char* type) {
    if (target < 0) return;
    const unsigned char* payload = fp_payload_start;
    size_t payloadSize = fp_payload_end - fp_payload_start;
#ifdef FP_DEBUG_FILE_DEX
    DebugPayloadResult debug = loadDebugPayload(appData.c_str(), debugPayload);
    if (debug == DebugPayloadResult::Invalid) { LOGE("Invalid debug DEX deployment for %s", process.c_str()); return; }
    if (debug == DebugPayloadResult::Loaded) {
        payload = debugPayload.data(); payloadSize = debugPayload.size();
        LOGI("Loading local debug DEX for %s", process.c_str());
    }
#endif
    if (env->PushLocalFrame(256) != JNI_OK) return;
    bool ok = false;
    do {
        std::vector<DexBytes> dex;
        if (!parseDexPayload(payload, payloadSize, dex)) break;
        jclass base = env->FindClass("java/lang/ClassLoader");
        if (!base || env->ExceptionCheck()) break;
        jmethodID systemLoader = env->GetStaticMethodID(base, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
        if (!systemLoader || env->ExceptionCheck()) break;
        jobject parent = env->CallStaticObjectMethod(base, systemLoader);
        if (!parent || env->ExceptionCheck()) break;
        jobject loader = memoryDexLoader(env, dex.data(), dex.size(), parent);
        if (!loader || env->ExceptionCheck()) break;
        jmethodID load = env->GetMethodID(base, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        if (!load || env->ExceptionCheck()) break;
        auto find = [&](const char* name) { return static_cast<jclass>(env->CallObjectMethod(loader, load, env->NewStringUTF(name))); };
        std::string entry = std::string("com.surcumference.fingerprint.plugin.magisk.") + entries[target];
        jclass plugin = find(entry.c_str());
        if (!plugin || env->ExceptionCheck()) break;
        jmethodID main = env->GetStaticMethodID(plugin, "main", "(Ljava/lang/String;Ljava/lang/String;)V");
        if (!main || env->ExceptionCheck()) break;
        env->CallStaticVoidMethod(plugin, main, env->NewStringUTF(process.c_str()), env->NewStringUTF(type));
        ok = !env->ExceptionCheck();
    } while (false);
    if (ok) LOGI("In-memory module initialized for %s", process.c_str());
    if (!ok) { if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); } LOGE("In-memory module initialization failed"); }
    env->PopLocalFrame(nullptr);
}
