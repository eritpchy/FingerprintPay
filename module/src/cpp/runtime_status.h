#pragma once
#include "json.hpp"

namespace fp {
inline const nlohmann::json& status_field(const nlohmann::json& object, const char* key) {
    static const nlohmann::json missing;
    if (!object.is_object()) return missing;
    auto it = object.find(key);
    return it == object.end() ? missing : *it;
}
inline int status_number(const nlohmann::json& value) {
    if (value.is_boolean()) return value.get<bool>() ? 1 : 0;
    for (int number = 0; number <= 3; ++number) {
        if ((value.is_number_integer() && value == number)
                || (value.is_string() && value == std::to_string(number))) return number;
    }
    return -1;
}
inline const char* rezygisk_status(const std::string& data) {
    using json = nlohmann::json;
    auto status = json::parse(data, nullptr, false);
    if (!status.is_object()) return "unknown";
    const auto& monitor_state = status_field(status_field(status, "monitor"), "state");
    int monitor = monitor_state.is_boolean() ? -1 : status_number(monitor_state);
    if (monitor == 1 || monitor == 2) return "disabled";
    if (monitor == 3) return "failed";
    bool active = false, unloaded = false, failed = false;
    for (const char* abi : {"64", "32"}) {
        const auto& daemon = status_field(status_field(status, "rezygiskd"), abi);
        const auto& modules = status_field(daemon, "modules");
        int running = status_number(status_field(daemon, "state"));
        int zygote = status_number(status_field(status_field(status, "zygote"), abi));
        if (running == 0) { failed = true; continue; }
        if (zygote == 0) { unloaded = true; continue; }
        if (!modules.is_null() && !modules.is_array()) continue;
        bool loaded = false;
        for (const auto& module : modules) {
            if (module == "zygisk-module-xfingerprint-pay-all") loaded = true;
        }
        if (modules.is_array() && !loaded) { unloaded = true; continue; }
        active |= zygote == 1 || (running == 1 && loaded);
    }
    // A working ABI remains usable even when an optional secondary ABI is unavailable.
    return active ? "active" : failed ? "failed" : unloaded ? "unloaded" : "unknown";
}
}
