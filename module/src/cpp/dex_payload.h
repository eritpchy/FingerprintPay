#pragma once
#include <stddef.h>
typedef struct { const unsigned char* data; size_t size; } DexBytes;
#ifdef __cplusplus
#include <cstdint>
#include <cstring>
#include <vector>
inline bool parseDexPayload(const unsigned char* data, size_t size, std::vector<DexBytes>& dex) {
    dex.clear();
    if (!data || size < 12 || std::memcmp(data, "FPDEX001", 8)) return false;
    uint32_t count; std::memcpy(&count, data + 8, 4);
    if (!count || count > 64) return false;
    size_t offset = 12;
    for (uint32_t i = 0; i < count; ++i) {
        if (size - offset < 4) return false;
        uint32_t length; std::memcpy(&length, data + offset, 4); offset += 4;
        if (length < 112 || length > size - offset || std::memcmp(data + offset, "dex\n", 4)) return false;
        dex.push_back({data + offset, length}); offset += length;
    }
    return offset == size;
}
#endif
