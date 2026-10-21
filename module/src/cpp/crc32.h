#ifndef CRC32_H
#define CRC32_H

#include <stdint.h>
#include <stddef.h>

class CRC32 {
public:
    // 获取文件大小，文件不存在返回-1
    static size_t getFileSize(const char* filename);

    // 计算文件的CRC32值
    static uint32_t calculateFileCRC32(const char* filename);

    // 验证文件CRC32是否匹配预期值
    static bool verifyFileCRC32(const char* filename, uint32_t expected_crc);

    // 验证文件大小和CRC32是否匹配预期值
    static bool verifyFileSizeAndCRC32(const char* filename, size_t expected_size, uint32_t expected_crc);


};

#endif // CRC32_H