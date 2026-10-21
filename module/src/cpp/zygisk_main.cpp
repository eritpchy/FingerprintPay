#include <sys/types.h>
#include "zygisk.hpp"
#include "fingerprint.h"
#include "config_socket.h"
#include "log.h"
#include <unistd.h>

class ZygiskModule : public zygisk::ModuleBase {
    zygisk::Api* api = nullptr;
    JNIEnv* env = nullptr;
public:
    void onLoad(zygisk::Api* value, JNIEnv* jni) override { api = value; env = jni; }
    void preAppSpecialize(zygisk::AppSpecializeArgs* args) override {
        int fd = api->connectCompanion();
        int directory = api->getModuleDir();
        fingerprintPre(env, &args->app_data_dir, &args->nice_name, args->uid, fd, directory);
        if (directory >= 0) close(directory);
        if (fd >= 0) close(fd);
        if (!fingerprintEnabled()) api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        else if (fingerprintResourceFd() >= 0 && !api->exemptFd(fingerprintResourceFd())) {
            LOGI("Resource descriptor exemption unavailable; deferring to configuration service");
            fingerprintDeferResource();
        }
    }
    void postAppSpecialize(const zygisk::AppSpecializeArgs*) override { fingerprintPost(env, MAGISK_MODULE_TYPE_ZYGISK); }
    void preServerSpecialize(zygisk::ServerSpecializeArgs*) override { api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY); }
};
REGISTER_ZYGISK_MODULE(ZygiskModule)
static void companion(int fd) {
    std::string request;
    if (fp::frame(fd, request, false)) { request = fp::request(request); if (!request.empty()) fp::frame(fd, request, true); }
}
REGISTER_ZYGISK_COMPANION(companion)
