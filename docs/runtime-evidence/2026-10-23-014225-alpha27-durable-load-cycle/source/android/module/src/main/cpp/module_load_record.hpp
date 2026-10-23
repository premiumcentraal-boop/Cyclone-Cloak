#pragma once

#include <cerrno>
#include <fcntl.h>
#include <string>
#include <sys/file.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include "json.hpp"

namespace cloak_load_evidence {
constexpr off_t kLimit = 64 * 1024;
constexpr size_t kMaxRecord = 1024;

// The digest is packaging metadata, independently checked against the ZIP's ABI
// library by the evidence runner. It is not an in-memory attestation or ZIP hash.
inline std::string packaged_digest(int directory) {
    const int fd = openat(directory, "artifacts.sha256", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return {};
    char bytes[512];
    const ssize_t count = read(fd, bytes, sizeof(bytes));
    close(fd);
    if (count <= 0 || count == sizeof(bytes)) return {};
#if defined(__aarch64__)
    const std::string name = "zygisk/arm64-v8a.so";
#elif defined(__arm__)
    const std::string name = "zygisk/armeabi-v7a.so";
#else
    return {};
#endif
#if defined(__aarch64__) || defined(__arm__)
    const std::string manifest(bytes, static_cast<size_t>(count));
    std::string digest;
    for (size_t start = 0; start < manifest.size();) {
        const size_t end = manifest.find('\n', start);
        const std::string line = manifest.substr(start, end == std::string::npos ? end : end - start);
        if (line.size() == 66 + name.size() && line.substr(64) == "  " + name) {
            if (!digest.empty()) return {};  // ambiguous manifests never yield evidence
            digest = line.substr(0, 64);
            if (digest.find_first_not_of("0123456789abcdef") != std::string::npos) return {};
        }
        if (end == std::string::npos) break;
        start = end + 1;
    }
    return digest;
#endif
}

inline bool append(int directory, const std::string &record) {
    if (record.empty() || record.size() > kMaxRecord || record.back() != '\n') return false;
    const int fd = openat(directory, "load-records.jsonl",
                          O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) return false;
    int locked;
    do { locked = flock(fd, LOCK_EX); } while (locked < 0 && errno == EINTR);
    bool ok = false;
    struct stat state{};
    if (locked == 0 && fstat(fd, &state) == 0 && S_ISREG(state.st_mode) && state.st_nlink == 1) {
        bool ready = true;
        if (state.st_size > kLimit - static_cast<off_t>(record.size())) {
            ready = ftruncate(fd, 0) == 0;
            state.st_size = 0;
        }
        if (ready) {
            // One write under the inode lock: concurrent forked processes cannot
            // interleave JSON lines or race the size cap/truncation. Never unlink.
            const ssize_t written = write(fd, record.data(), record.size());
            if (written == static_cast<ssize_t>(record.size())) {
                ok = fdatasync(fd) == 0;
            } else {
                if (ftruncate(fd, state.st_size) != 0) ok = false;
            }
        }
    }
    close(fd);  // releases flock on all paths
    return ok;
}

inline bool record(int directory, const std::string &package, const std::string &user,
                   const std::string &app_directory) noexcept {
    if (directory < 0) return false;
    try {
        const auto digest = packaged_digest(directory);
        struct timespec now{};
        if (digest.empty() || clock_gettime(CLOCK_REALTIME, &now) != 0) return false;
        const auto line = nlohmann::json{
            {"format", 1}, {"stage", "preAppSpecialize_bound"},
            {"timestamp_ms", static_cast<int64_t>(now.tv_sec) * 1000 + now.tv_nsec / 1000000},
            {"target_package", package}, {"android_user_id", std::stoi(user)},
            {"pid", getpid()}, {"module_artifact_sha256", digest},
            {"digest_source", "packaged_abi_manifest"},
            {"hook_library_path", app_directory + "/code_cache/cloak_hooks/libpine.so"},
            {"hook_path_state", "planned_before_postAppSpecialize"}
        }.dump() + "\n";
        return append(directory, line);
    } catch (...) {
        return false;  // evidence failure never changes profile/hook decisions
    }
}
}  // namespace cloak_load_evidence
