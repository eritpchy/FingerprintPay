#include <sys/types.h>
#include "zygisk.hpp"
#include "fingerprint.h"
#include "config_socket.h"
#include "log.h"
#include <fcntl.h>
#include <pthread.h>
#include <memory>
#include <string>
#include <unistd.h>

struct AppStart { JavaVM* vm; std::string name; };
static void* startApp(void* value) {
    std::unique_ptr<AppStart> app(static_cast<AppStart*>(value));
    // argv0 changes after the fork callback and Zygisk's runtime cleanup return.
    for (int attempt = 0; attempt < 1000; ++attempt) {
        char name[256]{};
        int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
        ssize_t size = fd < 0 ? -1 : read(fd, name, sizeof(name) - 1);
        if (fd >= 0) close(fd);
        if (size > 0 && app->name == name) {
            JNIEnv* env = nullptr;
            if (app->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
            fingerprintPost(env, MAGISK_MODULE_TYPE_ZYGISK);
            app->vm->DetachCurrentThread();
            return nullptr;
        }
        usleep(10000);
    }
    LOGE("Application process startup timed out");
    return nullptr;
}

class ZygiskModule : public zygisk::ModuleBase {
    zygisk::Api* api = nullptr;
    JNIEnv* env = nullptr;
public:
    void onLoad(zygisk::Api* value, JNIEnv* jni) override { api = value; env = jni; }
    void preAppSpecialize(zygisk::AppSpecializeArgs* args) override {
        int fd = api->connectCompanion();
        fingerprintPre(env, &args->app_data_dir, &args->nice_name, args->uid, fd);
        if (fd >= 0) close(fd);
        if (!fingerprintEnabled()) api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }
    void postAppSpecialize(const zygisk::AppSpecializeArgs* args) override {
        if (!fingerprintEnabled()) return;
        JavaVM* vm = nullptr;
        if (env->GetJavaVM(&vm) != JNI_OK) return;
        const char* name = env->GetStringUTFChars(args->nice_name, nullptr);
        if (!name) return;
        auto* app = new AppStart{vm, name};
        env->ReleaseStringUTFChars(args->nice_name, name);
        pthread_t thread;
        if (pthread_create(&thread, nullptr, startApp, app) != 0) {
            delete app;
            LOGE("Application initialization thread could not start");
        } else pthread_detach(thread);
    }
    void preServerSpecialize(zygisk::ServerSpecializeArgs*) override { api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY); }
};
REGISTER_ZYGISK_MODULE(ZygiskModule)
static void companion(int fd) {
    std::string request;
    if (fp::frame(fd, request, false)) { request = fp::request(request); if (!request.empty()) fp::frame(fd, request, true); }
}
REGISTER_ZYGISK_COMPANION(companion)
