#include <cstdio>
#include <array>
#include <sys/stat.h>
#include "crc32.h"

namespace {
const std::array<uint32_t, 256>& table() {
    static const std::array<uint32_t, 256> value = [] {
        std::array<uint32_t, 256> result{};
        for (unsigned i = 0; i < result.size(); ++i) {
            uint32_t crc = i;
            for (int bit = 0; bit < 8; ++bit) crc = (crc & 1) ? 0xEDB88320 ^ (crc >> 1) : crc >> 1;
            result[i] = crc;
        }
        return result;
    }();
    return value;
}
bool checksum(const char *path, uint32_t& value) {
    FILE *file = fopen(path, "rb");
    if (!file) return false;
    uint32_t crc = 0xFFFFFFFF;
    unsigned char buffer[8192];
    size_t count;
    const auto& lookup = table();
    while ((count = fread(buffer, 1, sizeof(buffer), file)) > 0) {
        for (size_t i = 0; i < count; ++i) crc = lookup[(crc ^ buffer[i]) & 255] ^ (crc >> 8);
    }
    bool success = !ferror(file);
    if (fclose(file) != 0) success = false;
    value = crc ^ 0xFFFFFFFF;
    return success;
}
}
size_t CRC32::getFileSize(const char *path) {
    struct stat status{};
    return stat(path, &status) == 0 && S_ISREG(status.st_mode) ? static_cast<size_t>(status.st_size) : static_cast<size_t>(-1);
}
uint32_t CRC32::calculateFileCRC32(const char *path) {
    uint32_t value = 0;
    checksum(path, value);
    return value;
}
bool CRC32::verifyFileCRC32(const char *path, uint32_t expected) {
    uint32_t value;
    return checksum(path, value) && value == expected;
}
bool CRC32::verifyFileSizeAndCRC32(const char *path, size_t size, uint32_t crc) {
    return getFileSize(path) == size && verifyFileCRC32(path, crc);
}
