#pragma once
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <sstream>
#include <stdexcept>
#include <string>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

namespace fp {
struct RootProfile { bool excluded; bool granted; };

inline RootProfile apatch_profile(const std::string& data, uint32_t uid) {
    RootProfile result{false, false};
    std::istringstream rows(data); std::string line;
    bool found = false;
    while (std::getline(rows, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty()) continue;
        std::istringstream row(line); std::string pkg, excluded, allowed, owner;
        if (!std::getline(row, pkg, ',') || !std::getline(row, excluded, ',')
                || !std::getline(row, allowed, ',') || !std::getline(row, owner, ','))
            throw std::runtime_error("Invalid APatch profile");
        auto trim = [](std::string& value) {
            auto begin = value.find_first_not_of(" \t\r\n");
            value = begin == std::string::npos ? "" : value.substr(begin, value.find_last_not_of(" \t\r\n") - begin + 1);
        };
        trim(excluded); trim(allowed); trim(owner);
        if (excluded == "exclude" && allowed == "allow" && owner == "uid") continue;
        if ((excluded != "0" && excluded != "1") || (allowed != "0" && allowed != "1")
                || owner.empty() || owner.size() > 10 || owner.find_first_not_of("0123456789") != std::string::npos)
            throw std::runtime_error("Invalid APatch profile");
        unsigned long value = std::stoul(owner);
        if (value > UINT32_MAX) throw std::runtime_error("Invalid APatch UID");
        if (value != uid) continue;
        RootProfile next{excluded == "1", allowed == "1"};
        if (found && (result.excluded != next.excluded || result.granted != next.granted))
            throw std::runtime_error("Conflicting APatch profiles");
        result = next; found = true;
    }
    return result;
}

inline RootProfile kernelsu_profile(uint32_t uid) {
    int driver = -1;
    if (DIR* entries = opendir("/proc/self/fd")) {
        while (dirent* entry = readdir(entries)) {
            char* end = nullptr; long fd = strtol(entry->d_name, &end, 10);
            if (!*entry->d_name || *end || fd < 0 || fd > INT32_MAX) continue;
            std::string path = std::string("/proc/self/fd/") + entry->d_name;
            char target[128]{}; ssize_t size = readlink(path.c_str(), target, sizeof(target) - 1);
            if (size > 0 && (std::string(target, size) == "anon_inode:[ksu_driver]"
                    || std::string(target, size) == "anon_inode:[ksu_driver_su]")) {
                driver = fcntl(int(fd), F_DUPFD_CLOEXEC, 0); break;
            }
        }
        closedir(entries);
    }
    // Official v3 ioctl access and legacy prctl both report the effective per-UID policy.
    if (driver < 0) syscall(SYS_reboot, 0xDEADBEEF, 0xCAFEBABE, 0, &driver);
    if (driver >= 0) {
        struct Query { uint32_t uid; uint8_t value; } excluded{uid, 0}, granted{uid, 0};
        bool ok = ioctl(driver, _IOC(_IOC_READ | _IOC_WRITE, 'K', 9, 0), &excluded) == 0
                && ioctl(driver, _IOC(_IOC_READ | _IOC_WRITE, 'K', 8, 0), &granted) == 0;
        close(driver);
        if (ok && excluded.value <= 1 && granted.value <= 1) return {excluded.value != 0, granted.value != 0};
    }
    bool excluded = false, granted = false; uint32_t reply = 0;
    prctl(0xDEADBEEF, 13, uid, &excluded, &reply);
    if (reply != 0xDEADBEEF) throw std::runtime_error("KernelSU profile unavailable");
    reply = 0;
    prctl(0xDEADBEEF, 12, uid, &granted, &reply);
    if (reply != 0xDEADBEEF) throw std::runtime_error("KernelSU grant unavailable");
    return {excluded, granted};
}
}
