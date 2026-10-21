#include "debug_payload.h"
#ifdef FP_DEBUG_FILE_DEX
#include "dex_payload.h"
#include <cerrno>
#include <fcntl.h>
#include <string>
#include <sys/stat.h>
#include <unistd.h>

namespace {
struct File {
    int fd;
    explicit File(int value) : fd(value) {}
    ~File() { if (fd >= 0) close(fd); }
};
bool readableFile(int fd, off_t limit, struct stat& info) {
    return fd >= 0 && !fstat(fd, &info) && S_ISREG(info.st_mode) && info.st_uid == geteuid()
            && !(info.st_mode & 0222) && info.st_size > 0 && info.st_size <= limit;
}
bool readAll(int fd, unsigned char* data, size_t size) {
    while (size) {
        ssize_t count = read(fd, data, size);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return false;
        data += count; size -= count;
    }
    return true;
}
}
DebugPayloadResult loadDebugPayload(const char* appData, std::vector<unsigned char>& payload, int& resources) {
    resources = -1;
    if (!appData || !*appData) return DebugPayloadResult::Invalid;
    std::string path = std::string(appData) + "/files/" MODULE_NAME ".debug";
    File root(open(path.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW));
    if (root.fd < 0) return errno == ENOENT ? DebugPayloadResult::Missing : DebugPayloadResult::Invalid;
    File current(openat(root.fd, "current", O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (current.fd < 0) return errno == ENOENT ? DebugPayloadResult::Missing : DebugPayloadResult::Invalid;
    struct stat info{};
    if (!readableFile(current.fd, 64, info) || info.st_size != 64) return DebugPayloadResult::Invalid;
    std::string generation(64, '\0');
    if (!readAll(current.fd, reinterpret_cast<unsigned char*>(&generation[0]), generation.size())
            || generation.find_first_not_of("0123456789abcdef") != std::string::npos) return DebugPayloadResult::Invalid;
    File directory(openat(root.fd, generation.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW));
    if (directory.fd < 0) return DebugPayloadResult::Invalid;
    File dex(openat(directory.fd, "payload.dex", O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (!readableFile(dex.fd, 128 * 1024 * 1024, info)) return DebugPayloadResult::Invalid;
    std::vector<unsigned char> bytes(static_cast<size_t>(info.st_size));
    std::vector<DexBytes> parsed;
    if (!readAll(dex.fd, bytes.data(), bytes.size()) || !parseDexPayload(bytes.data(), bytes.size(), parsed))
        return DebugPayloadResult::Invalid;
    File resource(openat(directory.fd, "resources.apk", O_RDONLY | O_CLOEXEC | O_NOFOLLOW));
    if (!readableFile(resource.fd, 128 * 1024 * 1024, info)) return DebugPayloadResult::Invalid;
    unsigned char magic[4];
    if (!readAll(resource.fd, magic, sizeof(magic)) || std::memcmp(magic, "PK\003\004", 4)
            || lseek(resource.fd, 0, SEEK_SET) < 0) return DebugPayloadResult::Invalid;
    payload.swap(bytes);
    resources = resource.fd; resource.fd = -1;
    return DebugPayloadResult::Loaded;
}
#endif
