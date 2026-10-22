#include "config_socket.h"
#include "json.hpp"
#include <sodium.h>
#include <sys/file.h>
#include <poll.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <fstream>
#include <sstream>
#include <iostream>
#include <array>
#include <vector>
#include <map>
#include <memory>
#include <ctime>
#include <algorithm>

using json = nlohmann::json;
#ifdef FP_CONFIG_TEST
static const std::string root = "/data/local/tmp/fp-config-regression";
static const char* package_list = "/data/local/tmp/fp-config-regression/packages.list";
#else
static const std::string root = "/data/adb/fingerprintpay";
static const char* package_list = "/data/system/packages.list";
#endif
static const std::array<std::string, 5> packages = {"com.tencent.mm", "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.tencent.mobileqq", "com.unionpay"};
static const std::array<std::string, 5> options = {"scope", "switch_on1", "fingerprint_icon", "biometric_api", "volume_down_monitor_enabled"};
static std::array<unsigned char, 32> public_key{}, private_key{};
static bool global_key(const std::string& k) { return k == "license_agree" || k == "auto_update" || k == "skip_xposed" || k == "skip_zygisk"; }
static std::string hex(const unsigned char* p, size_t n) { std::string out(n * 2 + 1, '\0'); sodium_bin2hex(&out[0], out.size(), p, n); out.resize(n * 2); return out; }
static std::vector<unsigned char> unhex(const std::string& value) {
    if (value.size() % 2 || value.size() > 8192) throw std::runtime_error("Invalid encrypted value");
    std::vector<unsigned char> result(value.size() / 2); size_t size = 0;
    if (sodium_hex2bin(result.data(), result.size(), value.data(), value.size(), nullptr, &size, nullptr) || size != result.size()) throw std::runtime_error("Invalid encrypted value");
    return result;
}
static std::string read_file(const std::string& path) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) { if (errno == ENOENT) return ""; throw std::runtime_error("Cannot read configuration"); }
    struct stat st{};
    if (fstat(fd, &st) || !S_ISREG(st.st_mode) || st.st_size > 1024 * 1024) { close(fd); throw std::runtime_error("Invalid configuration file"); }
    std::string data(st.st_size, '\0'); size_t offset = 0;
    while (offset < data.size()) { ssize_t n = read(fd, &data[offset], data.size() - offset); if (n < 0 && errno == EINTR) continue; if (n <= 0) { close(fd); throw std::runtime_error("Truncated configuration"); } offset += n; }
    close(fd); return data;
}
static void atomic_write(const std::string& path, const std::string& data) {
    std::string temp = path + ".new";
    int fd = open(temp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) throw std::runtime_error("Cannot save configuration");
    size_t offset = 0; bool ok = fchmod(fd, 0600) == 0;
    while (ok && offset < data.size()) { ssize_t n = write(fd, data.data() + offset, data.size() - offset); if (n < 0 && errno == EINTR) continue; if (n <= 0) ok = false; else offset += n; }
    if (fsync(fd)) ok = false;
    close(fd);
    if (!ok || rename(temp.c_str(), path.c_str())) { unlink(temp.c_str()); throw std::runtime_error("Cannot commit configuration"); }
    int dir = open(root.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC); if (dir >= 0) { fsync(dir); close(dir); }
}
static void init_keys() { crypto_box_keypair(public_key.data(), private_key.data()); }
static std::array<unsigned char, crypto_secretbox_KEYBYTES> pending_key{};
static uint64_t monotonic_ms() { timespec now{}; clock_gettime(CLOCK_BOOTTIME, &now); return uint64_t(now.tv_sec) * 1000 + now.tv_nsec / 1000000; }
struct PendingPassword {
    std::string id, status = "waiting";
    uint64_t expires, version;
    std::vector<unsigned char> envelope;
    ~PendingPassword() { sodium_memzero(envelope.data(), envelope.size()); }
    void finish(const std::string& state) { sodium_memzero(envelope.data(), envelope.size()); envelope.clear(); status = state; }
};
static std::map<std::string, std::unique_ptr<PendingPassword>> pending;
static json pending_status(const std::string& target, const std::string& id = "") {
    auto it = pending.find(target);
    if (it == pending.end() || (!id.empty() && it->second->id != id)) return {{"status", "superseded"}};
    auto& entry = *it->second;
    if ((entry.status == "waiting" || entry.status == "authenticating") && monotonic_ms() >= entry.expires) entry.finish("expired");
    return {{"id", entry.id}, {"status", entry.status}};
}
static PendingPassword& active_pending(const std::string& target, const std::string& id, uint64_t version) {
    auto state = pending_status(target, id);
    if (id.size() != 32 || (state.at("status") != "waiting" && state.at("status") != "authenticating") || pending.at(target)->id != id || pending.at(target)->version != version) throw std::runtime_error("Password request expired or changed");
    return *pending.at(target);
}
static void begin_password(const std::string& target, const std::string& password, uint64_t version) {
    if (!pending.count(target) && pending.size() >= 64) {
        for (auto it = pending.begin(); it != pending.end();) {
            auto state = pending_status(it->first).at("status");
            if (state != "waiting" && state != "authenticating") it = pending.erase(it);
            else ++it;
        }
    }
    if (!pending.count(target) && pending.size() >= 64) throw std::runtime_error("Too many pending requests");
    auto entry = std::make_unique<PendingPassword>();
    std::array<unsigned char, 16> id{}; randombytes_buf(id.data(), id.size()); entry->id = hex(id.data(), id.size());
    entry->version = version; entry->expires = monotonic_ms() + 120000;
    entry->envelope.resize(crypto_secretbox_NONCEBYTES + crypto_secretbox_MACBYTES + password.size());
    randombytes_buf(entry->envelope.data(), crypto_secretbox_NONCEBYTES);
    crypto_secretbox_easy(entry->envelope.data() + crypto_secretbox_NONCEBYTES, reinterpret_cast<const unsigned char*>(password.data()), password.size(), entry->envelope.data(), pending_key.data());
    pending[target] = std::move(entry);
}
static void validate_record(const json& record, int user, const std::string& pkg, const std::string& id) {
    auto iv = unhex(record.at("iv")), data = unhex(record.at("data"));
    std::string prefix = record.at("format") == "tee-aes-gcm-v2" ? "fingerprintpay.tee.v2.Zygisk."
            : record.at("format") == "software-aes-gcm-v1" ? "fingerprintpay.software.v1.Zygisk." : "";
    if (prefix.empty() || id.size() != 32 || id.find_first_not_of("0123456789abcdef") != std::string::npos
            || record.at("keyAlias") != prefix + std::to_string(user) + "." + pkg + "." + id
            || iv.size() != 12 || data.size() < 17 || data.size() > 1040) throw std::runtime_error("Invalid password record");
}
static json defaults() { return {{"scope", true}, {"switch_on1", false}, {"fingerprint_icon", false}, {"biometric_api", false}, {"volume_down_monitor_enabled", true}, {"credentialVersion", 0}, {"hasPassword", false}}; }
static bool belongs(uid_t uid, const std::string& pkg) {
    std::ifstream list(package_list); std::string line, name; unsigned app_id;
    while (std::getline(list, line)) { std::istringstream row(line); if (row >> name >> app_id && name == pkg && app_id == uid % 100000) return true; }
    return false;
}
static json handle(const json& req, uid_t uid) {
    std::string op = req.at("op"), pkg = req.at("package");
    int user = req.value("user", int(uid / 100000));
    if (std::find(packages.begin(), packages.end(), pkg) == packages.end() || user < 0 || user > 21474) throw std::runtime_error("Unknown application or user");
    if (uid != 0 && (user != int(uid / 100000) || !belongs(uid, pkg))) throw std::runtime_error("Application access denied");
    if (op == "publicKey") { if (uid) throw std::runtime_error("Management access denied"); return {{"ok", true}, {"publicKey", hex(public_key.data(), public_key.size())}}; }
    std::string target = std::to_string(user) + ":" + pkg;
    std::string path = root + "/user-" + std::to_string(user) + ".json";
    std::string saved = read_file(path);
    if (saved.empty() && access(path.c_str(), F_OK) == 0) throw std::runtime_error("Empty configuration file");
    json doc = saved.empty() ? json{{"schema", 2}, {"revision", 0}, {"apps", json::object()}, {"globals", {{"auto_update", true}, {"license_agree", false}}}} : json::parse(saved);
    if (doc.at("schema") == 1) {
        for (auto& app : doc["apps"].items()) if (app.value().contains("secret")) {
            auto& settings = app.value(); settings["legacySecret"] = settings["secret"]; settings.erase("secret");
            if (settings.contains("nonce")) { settings["legacyNonce"] = settings["nonce"]; settings.erase("nonce"); }
            settings["hasPassword"] = false; settings["switch_on1"] = false; settings["requiresReentry"] = true;
            settings["credentialVersion"] = settings.value("credentialVersion", uint64_t(0)) + 1;
        }
        doc["schema"] = 2; doc["revision"] = doc.at("revision").get<uint64_t>() + 1; atomic_write(path, doc.dump());
    }
    if (doc.at("schema") != 2) throw std::runtime_error("Unsupported configuration schema");
    auto& apps = doc["apps"]; auto& globals = doc["globals"];
    json settings = apps.contains(pkg) ? apps[pkg] : defaults();
    if (!apps.contains(pkg)) {
        std::string scopes = read_file(root + "/scope-defaults.json");
        if (!scopes.empty()) settings["scope"] = json::parse(scopes).value(pkg, true);
    }
    uint64_t revision = doc.at("revision");
    if (op == "scope") return settings.value("scope", false);
    uint64_t version = settings.value("credentialVersion", uint64_t(0));
    if (op == "reveal") throw std::runtime_error("Passwords require host TEE decryption");
    if (op == "passwordStatus") return {{"ok", true}, {"pending", pending_status(target, req.at("id"))}};
    if (op == "cancelPassword") {
        auto state = pending_status(target, req.at("id"));
        if (state.contains("id") && (state.at("status") == "waiting" || state.at("status") == "authenticating")) pending.at(target)->finish("canceled");
        return {{"ok", true}};
    }
    if (op == "pendingPassword" || op == "claimPassword" || op == "commitPassword" || op == "credential") {
        if (!uid) throw std::runtime_error("Target application required");
    }
    if (op == "pendingPassword") {
        json state = pending_status(target);
        if (state.at("status") == "waiting") pending.at(target)->status = "authenticating";
        else state = json::object();
        return {{"ok", true}, {"pending", state}};
    }
    if (op == "claimPassword") {
        auto& entry = active_pending(target, req.at("id"), version);
        std::vector<unsigned char> plain(entry.envelope.size() - crypto_secretbox_NONCEBYTES - crypto_secretbox_MACBYTES);
        if (crypto_secretbox_open_easy(plain.data(), entry.envelope.data() + crypto_secretbox_NONCEBYTES, entry.envelope.size() - crypto_secretbox_NONCEBYTES, entry.envelope.data(), pending_key.data())) throw std::runtime_error("Invalid pending password");
        json result{{"ok", true}, {"password", std::string(reinterpret_cast<char*>(plain.data()), plain.size())}};
        sodium_memzero(plain.data(), plain.size()); return result;
    }
    if (op == "credential") {
        if (!settings.value("scope", false) || !settings.value("switch_on1", false) || !globals.value("license_agree", false)
                || !settings.contains("secret") || req.at("credentialVersion") != version) throw std::runtime_error("Password changed or payment disabled");
        return {{"ok", true}, {"record", settings.at("secret")}};
    }
    if (op != "read") {
        if (req.at("revision") != revision) throw std::runtime_error("Configuration changed; refresh and retry");
        if (op == "patch" || op == "installScope") {
            if (op == "installScope") {
                if (uid || req.at("values").size() != 1 || !req.at("values").contains("scope") || !req.at("values").at("scope").is_boolean()) throw std::runtime_error("Management access denied");
                std::string prior = read_file(root + "/scope-defaults.json");
                json scopes = prior.empty() ? json::object() : json::parse(prior);
                scopes[pkg] = req.at("values").at("scope");
                atomic_write(root + "/scope-defaults.json", scopes.dump());
            }
            for (auto it = req.at("values").begin(); it != req.at("values").end(); ++it) {
                const auto& k = it.key(); const auto& v = it.value();
                if (!global_key(k) && std::find(options.begin(), options.end(), k) == options.end()) throw std::runtime_error("Unknown option");
                if (k.rfind("skip_", 0) == 0) { if (!v.is_string() || v.get<std::string>().size() > 128) throw std::runtime_error("Invalid version"); }
                else if (!v.is_boolean()) throw std::runtime_error("Expected boolean");
                if (global_key(k)) globals[k] = v; else settings[k] = v;
            }
            if (settings.value("biometric_api", false)) settings["volume_down_monitor_enabled"] = false;
            if (settings.value("switch_on1", false) && (!settings.contains("secret") || !globals.value("license_agree", false))) throw std::runtime_error("Accept the license and set a password first");
        } else if (op == "password") {
            std::string password;
            struct Wipe { std::string& value; ~Wipe() { sodium_memzero(&value[0], value.size()); } } wipe{password};
            if (req.contains("sealed")) {
                if (uid) throw std::runtime_error("Management access denied");
                auto sealed = unhex(req.at("sealed"));
                if (sealed.size() < crypto_box_SEALBYTES) throw std::runtime_error("Invalid password envelope");
                std::vector<unsigned char> plain(sealed.size() - crypto_box_SEALBYTES);
                if (crypto_box_seal_open(plain.data(), sealed.data(), sealed.size(), public_key.data(), private_key.data())) throw std::runtime_error("Expired password envelope; retry");
                password.assign(reinterpret_cast<char*>(plain.data()), plain.size()); sodium_memzero(plain.data(), plain.size());
            } else { password = req.at("password"); if (!uid && !password.empty()) throw std::runtime_error("Management password must be encrypted"); }
            if (password.size() > 1024) throw std::runtime_error("Password too long");
            if (!password.empty()) {
                if (!settings.value("scope", false)) throw std::runtime_error("Enable this application first");
                begin_password(target, password, version); if (uid) pending.at(target)->status = "authenticating"; sodium_memzero(&password[0], password.size());
                settings.erase("secret"); settings.erase("legacySecret"); settings.erase("legacyNonce");
                return {{"ok", true}, {"passwordProtection", "tee-aes-gcm-v2"}, {"revision", revision}, {"settings", settings}, {"globals", globals}, {"pending", pending_status(target)}};
            }
            auto state = pending_status(target);
            if (state.contains("id")) pending.at(target)->finish("canceled");
            settings.erase("secret"); settings.erase("legacySecret"); settings.erase("legacyNonce");
            settings["switch_on1"] = false; settings["hasPassword"] = false; settings["requiresReentry"] = false; settings["credentialVersion"] = version + 1;
        } else if (op == "commitPassword") {
            std::string id = req.at("id"); active_pending(target, id, version);
            if (!settings.value("scope", false)) throw std::runtime_error("Application disabled");
            validate_record(req.at("record"), user, pkg, id);
            settings["secret"] = req.at("record"); settings["hasPassword"] = true; settings["requiresReentry"] = false; settings["credentialVersion"] = version + 1;
            settings.erase("legacySecret"); settings.erase("legacyNonce");
        } else throw std::runtime_error("Unknown operation");
        apps[pkg] = settings; doc["revision"] = ++revision;
        atomic_write(path, doc.dump());
        if (op == "commitPassword") pending.at(target)->finish("done");
    }
    settings.erase("secret"); settings.erase("nonce"); settings.erase("legacySecret"); settings.erase("legacyNonce");
    return {{"ok", true}, {"passwordProtection", "tee-aes-gcm-v2"}, {"revision", revision}, {"settings", settings}, {"globals", globals}, {"pending", pending_status(target)}};
}
static int open_resource(const json& request, uid_t uid) {
    if (!uid) throw std::runtime_error("Host access required");
    auto scope = request; scope["op"] = "scope";
    if (!handle(scope, uid).get<bool>()) throw std::runtime_error("Application disabled");
#ifdef FP_CONFIG_TEST
    std::string path = root + "/resources.apk";
#else
    std::string path = "/data/adb/modules/" MODULE_NAME "/resources.apk";
#endif
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) throw std::runtime_error("Resources unavailable");
    try {
        struct stat info{};
        if (fstat(fd, &info) || !S_ISREG(info.st_mode) || info.st_uid != 0 || info.st_size < 4 || info.st_size > 32 * 1024 * 1024)
            throw std::runtime_error("Invalid resources");
        crypto_hash_sha256_state state; crypto_hash_sha256_init(&state);
        unsigned char buffer[65536], hash[crypto_hash_sha256_BYTES]; ssize_t n;
        while ((n = read(fd, buffer, sizeof(buffer))) != 0) {
            if (n < 0) { if (errno == EINTR) continue; throw std::runtime_error("Cannot read resources"); }
            crypto_hash_sha256_update(&state, buffer, n);
        }
        crypto_hash_sha256_final(&state, hash);
        if (request.at("sha256") != hex(hash, sizeof(hash)) || lseek(fd, 0, SEEK_SET) < 0)
            throw std::runtime_error("Resource version mismatch");
    } catch (...) { close(fd); throw; }
    return fd;
}
int main(int argc, char** argv) {
    if (getuid() != 0) { std::cerr << "FP_CONFIG_ERROR:ROOT_REQUIRED" << std::endl; return 1; }
    if (sodium_init() < 0) return 1;
    try {
        if (argc == 3 && std::string(argv[1]) == "request") {
            std::string encoded(argv[2]); if (encoded.size() > 90000) return 1;
            std::vector<unsigned char> bytes(encoded.size()); size_t length;
            if (sodium_base642bin(bytes.data(), bytes.size(), encoded.data(), encoded.size(), nullptr, &length, nullptr, sodium_base64_VARIANT_ORIGINAL)) return 1;
            int error = 0;
            std::string response = fp::request(std::string(reinterpret_cast<char*>(bytes.data()), length), &error);
            if (response.empty()) { std::cerr << "FP_CONFIG_ERROR:CONNECT:" << error << std::endl; return 1; }
            std::cout << response << std::endl; return 0;
        }
        if (argc == 3 && std::string(argv[1]) == "apply-scope") {
            std::ifstream input(argv[2]); if (!input) return 1;
            const std::array<std::string, 5> names = {"wechat", "alipay", "taobao", "qq", "unionpay"};
            std::string line;
            for (int tries = 0; tries < 30; ++tries) { int fd = fp::connect_store(); if (fd >= 0) { close(fd); break; } usleep(100000); }
            while (std::getline(input, line)) {
                auto at = line.find('='); if (at == std::string::npos) return 1;
                auto found = std::find(names.begin(), names.end(), line.substr(0, at)); if (found == names.end()) return 1;
                std::string value = line.substr(at + 1); if (value == "keep") continue;
                if (value != "true" && value != "false") return 1;
                std::string pkg = packages[found - names.begin()];
                json base{{"op", "read"}, {"package", pkg}, {"user", 0}};
                json current = json::parse(fp::request(base.dump())); if (!current.value("ok", false)) return 1;
                base["op"] = "installScope"; base["revision"] = current.at("revision"); base["values"] = {{"scope", value == "true"}};
                if (!json::parse(fp::request(base.dump())).value("ok", false)) return 1;
            }
            return 0;
        }
        if (argc != 2 || std::string(argv[1]) != "serve") return 1;
        umask(0077); if (mkdir(root.c_str(), 0700) && errno != EEXIST) return 1;
        int lock = open((root + "/lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600);
        if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB)) return 0;
        init_keys(); randombytes_buf(pending_key.data(), pending_key.size());
        int server = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
        sockaddr_un address{}; address.sun_family = AF_UNIX; memcpy(address.sun_path + 1, fp::socket_name, strlen(fp::socket_name));
        if (bind(server, reinterpret_cast<sockaddr*>(&address), offsetof(sockaddr_un, sun_path) + 1 + strlen(fp::socket_name)) || listen(server, 16)) return 1;
        for (;;) {
            pollfd waiting{server, POLLIN, 0}; int ready = poll(&waiting, 1, 1000);
            for (const auto& entry : pending) pending_status(entry.first);
            if (ready <= 0) { if (ready < 0 && errno != EINTR) return 1; continue; }
            int client = accept4(server, nullptr, nullptr, SOCK_CLOEXEC); if (client < 0) { if (errno == EINTR) continue; return 1; }
            timeval timeout{1, 500000}; setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)); setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
            ucred peer{}; socklen_t size = sizeof(peer); std::string data;
            bool trusted = !getsockopt(client, SOL_SOCKET, SO_PEERCRED, &peer, &size);
            if (trusted && peer.uid) trusted = std::any_of(packages.begin(), packages.end(), [&](const std::string& pkg) { return belongs(peer.uid, pkg); });
            if (trusted && fp::frame(client, data, false)) {
                bool resource = false;
                try {
                    json request = json::parse(data); resource = request.value("op", "") == "resource";
                    if (resource) {
                        int fd = open_resource(request, peer.uid); fp::send_resource(client, fd); close(fd);
                    } else {
                        json result = handle(request, peer.uid);
                        data = request.value("op", "") == "scope" ? (result.get<bool>() ? "1" : "0") : result.dump();
                    }
                } catch (const std::exception& error) {
                    if (resource) fp::send_resource(client, -1);
                    else data = json{{"ok", false}, {"error", error.what()}}.dump();
                }
                if (!resource) fp::frame(client, data, true);
                sodium_memzero(&data[0], data.size());
            }
            close(client);
        }
    } catch (const std::exception&) { std::cerr << "Configuration service unavailable" << std::endl; return 1; }
}
