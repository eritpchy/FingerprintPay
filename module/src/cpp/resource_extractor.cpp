#include <cerrno>
#include <cstdio>
#include <cstring>
#include <string>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>
#include "log.h"
#include "crc32.h"

extern "C" {
    extern const uint8_t _binary_payload_apk_start[];
    extern const uint8_t _binary_payload_apk_end[];
}

extern "C" bool extractDexResource(const char* outputPath) {
    const size_t size = _binary_payload_apk_end - _binary_payload_apk_start;
    std::string lockPath = std::string(outputPath) + ".lock";
    int lock = open(lockPath.c_str(), O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock < 0) return false;
    if (flock(lock, LOCK_EX) != 0) { close(lock); return false; }
    bool success = false;
    struct stat status{};
    if (lstat(outputPath, &status) == 0 && S_ISREG(status.st_mode)
            && (status.st_mode & 0222) == 0
            && CRC32::verifyFileSizeAndCRC32(outputPath, size, RESOURCE_CRC32)) {
        success = true;
    } else {
        std::string temporary = std::string(outputPath) + ".XXXXXX";
        int file = mkstemp(&temporary[0]);
        if (file >= 0) {
            bool written = fchmod(file, 0444) == 0;
            size_t offset = 0;
            while (written && offset < size) {
                ssize_t count = write(file, _binary_payload_apk_start + offset, size - offset);
                if (count < 0 && errno == EINTR) continue;
                if (count <= 0) { written = false; break; }
                offset += static_cast<size_t>(count);
            }
            written = written && fsync(file) == 0;
            if (close(file) != 0) written = false;
            if (written && CRC32::verifyFileSizeAndCRC32(temporary.c_str(), size, RESOURCE_CRC32)) {
                success = rename(temporary.c_str(), outputPath) == 0;
            }
            if (!success) unlink(temporary.c_str());
        }
    }
    flock(lock, LOCK_UN);
    close(lock);
    if (!success) LOGE("Module payload extraction failed: %s (%s)", outputPath, strerror(errno));
    return success;
}
