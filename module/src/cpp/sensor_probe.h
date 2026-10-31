#pragma once
#include "dex_payload.h"
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <csignal>
#include <cstdlib>
#include <fcntl.h>
#include <iterator>
#include <string>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

namespace fp {
inline bool embeddedDex(const unsigned char* data, size_t size, std::vector<DexBytes>& dex) {
    dex.clear();
    const unsigned char magic[] = {'F', 'P', 'D', 'E', 'X', '0', '0', '1'};
    const auto* end = data + size;
    for (auto* at = data; at < end;) {
        at = std::search(at, end, std::begin(magic), std::end(magic));
        if (end - at < 12) break;
        uint32_t count; std::memcpy(&count, at + 8, 4);
        size_t length = 12, available = end - at;
        if (count && count <= 64) {
            for (uint32_t i = 0; i < count; ++i) {
                if (available - length < 4) { length = 0; break; }
                uint32_t bytes; std::memcpy(&bytes, at + length, 4); length += 4;
                if (bytes > available - length) { length = 0; break; }
                length += bytes;
            }
            std::vector<DexBytes> candidate;
            if (length && parseDexPayload(at, length, candidate)) {
                if (!dex.empty()) { dex.clear(); return false; }
                dex = std::move(candidate);
                at += length;
                continue;
            }
        }
        ++at;
    }
    return !dex.empty();
}

struct SensorProbeFiles {
    std::string directory;
    std::vector<std::string> paths;
    ~SensorProbeFiles() {
        for (const auto& path : paths) unlink(path.c_str());
        if (!directory.empty()) rmdir(directory.c_str());
    }
};

inline int sensorDefaults(const char* library, const std::string& scratch) {
    int fd = open(library, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return 1;
    struct stat info{};
    if (fstat(fd, &info) || !S_ISREG(info.st_mode) || info.st_uid != 0
            || (info.st_mode & 0022) || info.st_size < 12 || info.st_size > 128 * 1024 * 1024) {
        close(fd); return 1;
    }
    void* mapping = mmap(nullptr, info.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);
    if (mapping == MAP_FAILED) return 1;
    std::vector<DexBytes> dex;
    bool valid = embeddedDex(static_cast<const unsigned char*>(mapping), info.st_size, dex);
    SensorProbeFiles files;
    std::string pattern = scratch + "/sensor-XXXXXX", classpath;
    char* directory = valid ? mkdtemp(&pattern[0]) : nullptr;
    if (directory) {
        files.directory = directory;
        for (size_t i = 0; valid && i < dex.size(); ++i) {
            std::string path = files.directory + "/classes" + std::to_string(i) + ".dex";
            fd = open(path.c_str(), O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
            if (fd < 0) { valid = false; break; }
            files.paths.push_back(path);
            size_t offset = 0;
            while (offset < dex[i].size) {
                ssize_t n = write(fd, dex[i].data + offset, dex[i].size - offset);
                if (n < 0 && errno == EINTR) continue;
                if (n <= 0) { valid = false; break; }
                offset += n;
            }
            // ART requires dynamically loaded code to be read-only on recent Android versions.
            if (fchmod(fd, 0444) || fsync(fd)) valid = false;
            close(fd);
            if (!classpath.empty()) classpath += ':';
            classpath += path;
        }
    } else valid = false;
    munmap(mapping, info.st_size);
    if (!valid) return 1;
    pid_t child = fork();
    if (child < 0) return 1;
    if (!child) {
        if (setenv("CLASSPATH", classpath.c_str(), 1)) _exit(1);
        execl("/system/bin/app_process", "app_process", "/system/bin",
                "com.surcumference.fingerprint.sensor.SensorDefaults", static_cast<char*>(nullptr));
        _exit(1);
    }
    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(15);
    for (;;) {
        int status = 0;
        pid_t done = waitpid(child, &status, WNOHANG);
        if (done == child) return WIFEXITED(status) ? WEXITSTATUS(status) : 1;
        if ((done < 0 && errno != EINTR) || std::chrono::steady_clock::now() >= deadline) {
            kill(child, SIGKILL);
            while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
            return 1;
        }
        usleep(50000);
    }
}
}
