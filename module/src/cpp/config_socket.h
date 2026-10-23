#pragma once
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <cerrno>
#include <cstddef>
#include <cstring>
#include <fcntl.h>
#include <string>

namespace fp {
constexpr const char* socket_name = "fingerprintpay.config.v1";
inline bool transfer(int fd, void* data, size_t size, bool write_data) {
    auto* bytes = static_cast<char*>(data);
    while (size) {
        ssize_t n = write_data ? send(fd, bytes, size, MSG_NOSIGNAL) : read(fd, bytes, size);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) { if (!n) errno = ECONNRESET; return false; }
        bytes += n; size -= n;
    }
    return true;
}
inline bool frame(int fd, std::string& value, bool send_data) {
    uint32_t size = send_data ? htonl(value.size()) : 0;
    if (!transfer(fd, &size, sizeof(size), send_data)) return false;
    size = ntohl(size);
    if (!size || size > 65536) { errno = EMSGSIZE; return false; }
    if (!send_data) value.resize(size);
    return transfer(fd, &value[0], size, send_data);
}
inline int connect_store() {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    timeval timeout{1, 500000};
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    sockaddr_un addr{}; addr.sun_family = AF_UNIX;
    memcpy(addr.sun_path + 1, socket_name, strlen(socket_name));
    if (connect(fd, reinterpret_cast<sockaddr*>(&addr), offsetof(sockaddr_un, sun_path) + 1 + strlen(socket_name))) { int error = errno; close(fd); errno = error; return -1; }
    ucred peer{}; socklen_t size = sizeof(peer);
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &peer, &size)) { int error = errno; close(fd); errno = error; return -1; }
    if (peer.uid != 0) { close(fd); errno = EPERM; return -1; }
    return fd;
}
inline std::string request(std::string value, int* error = nullptr) {
    int fd = connect_store();
    if (fd < 0) { if (error) *error = errno; return ""; }
    bool ok = frame(fd, value, true) && frame(fd, value, false);
    if (!ok && error) *error = errno ? errno : ECONNRESET;
    close(fd); return ok ? value : "";
}
}
