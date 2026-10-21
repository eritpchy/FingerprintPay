#include "fingerprint.h"
#include "memory_dex.h"
#include "debug_payload.h"
#include "config_socket.h"
#include "log.h"
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <string>
#include <vector>

extern "C" const unsigned char fp_payload_start[], fp_payload_end[];
static const char* packages[] = {"com.tencent.mm", "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.tencent.mobileqq", "com.unionpay"};
static const char* entries[] = {"WeChatPlugin", "AlipayPlugin", "TaobaoPlugin", "QQPlugin", "UnionPayPlugin"};
static int target = -1, resourceFd = -1;
static std::string process;
static int androidUser;
#ifdef FP_DEBUG_FILE_DEX
static std::string appData;
// ART 6/7 keep native pointers into these bytes for the lifetime of the process.
static std::vector<unsigned char> debugPayload;
#endif
void fingerprintDeferResource() { if (resourceFd >= 0) close(resourceFd); resourceFd = -1; }
void fingerprintDisable() { target = -1; if (resourceFd >= 0) { close(resourceFd); resourceFd = -1; } }
bool fingerprintEnabled() { return target >= 0; }
int fingerprintResourceFd() { return resourceFd; }
void fingerprintPre(JNIEnv* env, jstring* appDataDir, jstring* niceName, int uid, int companion, int moduleDir) {
    target = -1; process.clear();
    if (resourceFd >= 0) { close(resourceFd); resourceFd = -1; }
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
    androidUser = uid / 100000;
    LOGI("Checking target %s user %d", process.c_str(), androidUser);
    std::string query = "{\"op\":\"scope\",\"package\":\"" + std::string(packages[target])
            + "\",\"user\":" + std::to_string(uid / 100000) + "}";
    if (companion >= 0) {
        if (!fp::frame(companion, query, true) || !fp::frame(companion, query, false)) { LOGE("Scope companion transport failed: %d", errno); target = -1; return; }
    } else query = fp::request(query);
    if (query != "1") { LOGW("Injection skipped: scope reply %s", query == "0" ? "disabled" : query.empty() ? "unavailable" : "invalid"); target = -1; return; }
    resourceFd = moduleDir >= 0 ? openat(moduleDir, "resources.apk", O_RDONLY | O_CLOEXEC | O_NOFOLLOW)
            : open("/data/adb/modules/" MODULE_NAME "/resources.apk", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (resourceFd < 0) LOGW("Deferring resource open: %d (module directory %d)", errno, moduleDir);
    else LOGI("Target resource descriptor ready");
}
void fingerprintPost(JNIEnv* env, const char* type) {
    if (target < 0) return;
    const unsigned char* payload = fp_payload_start;
    size_t payloadSize = fp_payload_end - fp_payload_start;
#ifdef FP_DEBUG_FILE_DEX
    int debugResources = -1;
    DebugPayloadResult debug = loadDebugPayload(appData.c_str(), debugPayload, debugResources);
    if (debug == DebugPayloadResult::Invalid) { fingerprintDeferResource(); LOGE("Invalid debug DEX deployment for %s", process.c_str()); return; }
    if (debug == DebugPayloadResult::Loaded) {
        fingerprintDeferResource(); resourceFd = debugResources;
        payload = debugPayload.data(); payloadSize = debugPayload.size();
        LOGI("Loading local debug DEX for %s", process.c_str());
    }
#endif
    if (resourceFd < 0) {
        std::string query = "{\"op\":\"resource\",\"package\":\"" + std::string(packages[target])
                + "\",\"user\":" + std::to_string(androidUser) + ",\"sha256\":\"" FP_RESOURCE_SHA256 "\"}";
        resourceFd = fp::resource_request(query);
        if (resourceFd < 0) { LOGE("Resource descriptor service failed: %d", errno); return; }
        LOGI("Resource descriptor received after specialization");
    }
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
        std::string path = "/proc/self/fd/" + std::to_string(resourceFd);
        jclass resourceParent = find("com.surcumference.fingerprint.util.MemoryResourceLoader");
        if (!resourceParent || env->ExceptionCheck()) break;
        jmethodID resourceConstructor = env->GetMethodID(resourceParent, "<init>", "(Ljava/lang/ClassLoader;Ljava/lang/String;)V");
        if (!resourceConstructor || env->ExceptionCheck()) break;
        jobject resources = env->NewObject(resourceParent, resourceConstructor, parent, env->NewStringUTF(path.c_str()));
        if (!resources || env->ExceptionCheck()) break;
        jfieldID parentField = env->GetFieldID(base, "parent", "Ljava/lang/ClassLoader;");
        if (!parentField || env->ExceptionCheck()) break;
        env->SetObjectField(loader, parentField, resources);
        if (env->ExceptionCheck()) break;
        jclass resourceContext = find("com.surcumference.fingerprint.util.ModuleResources");
        if (!resourceContext || env->ExceptionCheck()) break;
        jmethodID setPath = env->GetStaticMethodID(resourceContext, "setPayloadPath", "(Ljava/lang/String;)V");
        if (!setPath || env->ExceptionCheck()) break;
        env->CallStaticVoidMethod(resourceContext, setPath, env->NewStringUTF(path.c_str()));
        if (env->ExceptionCheck()) break;
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
