/*
 * Cyclone Cloak 0.1 - Zygisk module.
 *
 * preAppSpecialize runs while the freshly forked process is still root: we resolve the package
 * from its data dir, look it up in the companion's published state directory (see
 * docs/STATE_LAYOUT.md) and stash the bound profile's props. postAppSpecialize rewrites
 * android.os.Build statics and hooks android.os.SystemProperties' native methods so every read
 * inside the scoped app returns the bound profile, including telephony and display fields.
 * Unscoped apps are untouched.
 */

#include <sys/stat.h>
#include <sys/utsname.h>
#include <sys/socket.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/syscall.h>
#include <cstdarg>
#include <cstdlib>
#include "zygisk.hpp"
#include "runtime_scope.hpp"
#include "module_load_record.hpp"

#include <android/log.h>
#include <android/api-level.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iterator>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include <dlfcn.h>

#include "json.hpp"

// Hook bridge assets generated at build time by tools/gen_embedded.py.
extern const unsigned char CLOAK_HOOKS_DEX[];
extern const size_t CLOAK_HOOKS_DEX_SIZE;
extern const unsigned char CLOAK_PINE_LIB[];
extern const size_t CLOAK_PINE_LIB_SIZE;
extern const unsigned char CLOAK_SHADOWHOOK_LIB[];
extern const size_t CLOAK_SHADOWHOOK_LIB_SIZE;

using json = nlohmann::json;
using zygisk::Api;
using zygisk::AppSpecializeArgs;

// Per-fork read-only snapshot, delivered only after the existing user/package binding lookup.
static std::string g_runtime_profile;
static bool g_pif_applied = false;

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CloakModule", __VA_ARGS__)

namespace {

// profile device key -> system property; all values are scalars in the profile.
const std::pair<const char *, const char *> kDeviceProps[] = {
    {"manufacturer", "ro.product.manufacturer"},
    {"brand", "ro.product.brand"},
    {"model", "ro.product.model"},
    {"product", "ro.product.name"},
    {"device", "ro.product.device"},
    {"hardware", "ro.hardware"},
    {"fingerprint", "ro.build.fingerprint"},
    {"version_release", "ro.build.version.release"},
    {"security_patch", "ro.build.version.security_patch"},
    {"build_id", "ro.build.id"},
    {"version_incremental", "ro.build.version.incremental"},
    {"sdk_int", "ro.build.version.sdk"},
    {"first_api_level", "ro.build.version.first_api_level"},
    {"build_date_utc", "ro.build.date.utc"},
    {"bootloader", "ro.bootloader"},
    {"baseband", "gsm.version.baseband"},
};

// telephony key -> system property; the operator numeric is assembled from
// the profile's mcc and mnc in load_profile_for.
const std::pair<const char *, const char *> kTelephonyProps[] = {
    {"carrier_name", "gsm.operator.alpha"},
    {"carrier_name", "gsm.sim.operator.alpha"},
    {"network_type", "gsm.network.type"},
};

// display key -> system property. Width/height/refresh feed the Java-side
// Display and Resources rewrites through cloak.display.*; density keeps the
// real property key because getprop readers also consult it.
const std::pair<const char *, const char *> kDisplayProps[] = {
    {"density", "ro.sf.lcd_density"},
    {"width", "cloak.display.width"},
    {"height", "cloak.display.height"},
    {"refresh_rate_hz", "cloak.display.refresh"},
};

// identifier key -> system property. serial is listed twice: Build.getSerial()
// and getprop readers hit ro.serialno, while firmware-level reads use
// ro.boot.serialno. The cloak.* keys expose every identifier through the same
// hooked SystemProperties surface so later Java-level callbacks and the
// companion diagnostics read identical values.
const std::pair<const char *, const char *> kIdentifierProps[] = {
    {"serial", "ro.serialno"},
    {"serial", "ro.boot.serialno"},
    {"android_id", "cloak.android_id"},
    {"advertising_id", "cloak.advertising_id"},
    {"app_set_id", "cloak.app_set_id"},
    {"mac", "cloak.wifi_mac"},
    {"bt_mac", "cloak.bt_mac"},
    {"imei_primary", "cloak.imei0"},
    {"imei_secondary", "cloak.imei1"},
    {"sim_serial", "cloak.sim_serial0"},
    {"gsf_id", "cloak.gsf_id"},
    {"widevine_id", "cloak.widevine_id"},
};

// locale key -> system property; language/country also feed the Java-side
// default-locale rewrite in apply_profile.
const std::pair<const char *, const char *> kLocaleProps[] = {
    {"timezone", "persist.sys.timezone"},
    {"language", "persist.sys.language"},
    {"country", "persist.sys.country"},
};

std::unordered_map<std::string, std::string> g_props;
std::string g_active_package;
std::string g_active_key;
bool g_active = false;
// ART belongs to the host OS, even when Build.VERSION is profile-backed.
int g_runtime_sdk = 0;
int g_runtime_flags = 0;
// Native trampolines retain code addresses, not Java GC roots. Keep their DEX
// loader alive for the whole scoped process, including partially installed hooks.
jobject g_hook_loader = nullptr;
std::string g_app_data_dir;
bool g_java_callbacks = true;
bool g_native_hook_enabled = true;
bool cloak_prop_for(const char *name, char *value, int host_len);

// Filesystem reality layer and root-trace concealment (D1 core).
std::unordered_map<std::string, std::string> g_file_views;
bool g_file_views_ready = false;
std::string read_all_and_filter(int fd);
bool is_root_trace_path(const std::string &path);
bool is_root_trace_path(const char *path);

// Native property surface: ShadowHook-intercepted __system_property_get.
using PropertyGetFn = int (*)(const char *, char *);
PropertyGetFn g_orig_property_get = nullptr;
void *g_shadowhook_handle = nullptr;
bool g_shadowhook_ready = false;

// Modern callers may use the prop_info handle API instead of
// __system_property_get. The handle carries only an opaque pointer, so the
// original call supplies the name and the callback wrapper substitutes the
// bound value before handing it to the real consumer callback.
using PropertyReadCallbackFn = void (*)(void *, const char *, const char *, uint32_t);
using PropertyReadCallbackApiFn = void (*)(const prop_info *,
                                           PropertyReadCallbackFn, void *);
PropertyReadCallbackApiFn g_orig_property_read_callback = nullptr;

struct PropertyReadContext {
    PropertyReadCallbackFn callback;
    void *cookie;
};

void hooked_property_read_callback(void *cookie, const char *name,
                                   const char *value, uint32_t serial) {
    auto *context = static_cast<PropertyReadContext *>(cookie);
    char replacement[PROP_VALUE_MAX] = {0};
    if (cloak_prop_for(name, replacement, PROP_VALUE_MAX)) {
        context->callback(context->cookie, name, replacement, serial);
        return;
    }
    context->callback(context->cookie, name, value, serial);
}

void hooked_property_read_callback_api(const prop_info *info,
                                       PropertyReadCallbackFn callback,
                                       void *cookie) {
    if (!g_orig_property_read_callback || !info || !callback) {
        return;
    }
    PropertyReadContext context{callback, cookie};
    g_orig_property_read_callback(info, hooked_property_read_callback, &context);
}

// Overwrite policy for a hooked __system_property_get call. Returns false to
// keep the value produced by the real libc implementation. The buffer is
// PROP_VALUE_MAX bytes; never write at or beyond that boundary.
bool cloak_prop_for(const char *name, char *value, int host_len) {
    if (!g_active || name == nullptr || value == nullptr || host_len < 0) {
        return false;
    }
    auto it = g_props.find(name);
    if (it == g_props.end() || it->second.empty()) {
        return false;
    }
    const std::string &replacement = it->second;
    if (replacement.size() >= PROP_VALUE_MAX) {
        // Callers allocate PROP_VALUE_MAX bytes; a longer value would not be
        // representable through this surface regardless of who writes it.
        return false;
    }
    memcpy(value, replacement.c_str(), replacement.size() + 1);
    return true;
}

int hooked_property_get(const char *name, char *value) {
    int host_len = g_orig_property_get ? g_orig_property_get(name, value) : 0;
    if (cloak_prop_for(name, value, host_len)) {
        return static_cast<int>(g_props.at(name).size());
    }
    return host_len;
}

// Stage the embedded ShadowHook library into the app's own data directory
// (same ownership pattern as libpine.so) and hook libc's property read.
void install_native_property_hook() {
    if (!g_native_hook_enabled || g_shadowhook_ready) {
        return;
    }
    std::string path = g_app_data_dir + "/libshadowhook.so";
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) {
        LOGI("native hook: cannot write %s", path.c_str());
        return;
    }
    out.write(reinterpret_cast<const char *>(CLOAK_SHADOWHOOK_LIB),
              static_cast<std::streamsize>(CLOAK_SHADOWHOOK_LIB_SIZE));
    out.close();
    g_shadowhook_handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!g_shadowhook_handle) {
        LOGI("native hook: dlopen failed");
        return;
    }
    using InitFn = int (*)(int, bool);
    using HookFn = void *(*)(const char *, const char *, void *, void **);
    auto init = reinterpret_cast<InitFn>(dlsym(g_shadowhook_handle, "shadowhook_init"));
    auto hook = reinterpret_cast<HookFn>(dlsym(g_shadowhook_handle, "shadowhook_hook_sym_name"));
    if (!init || !hook) {
        LOGI("native hook: symbols missing");
        return;
    }
    // SHADOWHOOK_MODE_UNIQUE = 1: single owner of the hooked entry point.
    if (init(1, false) != 0) {
        LOGI("native hook: init failed");
        return;
    }
    // Initialization is intentionally not retried: a partial hook install is
    // safer than repeatedly hooking already-patched entries.
    g_shadowhook_ready = true;
    void *orig = nullptr;
    void *record = hook("libc.so", "__system_property_get",
                        reinterpret_cast<void *>(hooked_property_get), &orig);
    if (!record || !orig) {
        LOGI("native hook: hook failed");
        return;
    }
    g_orig_property_get = reinterpret_cast<PropertyGetFn>(orig);
    LOGI("native property hook installed");

    orig = nullptr;
    record = hook("libc.so", "__system_property_read_callback",
                  reinterpret_cast<void *>(hooked_property_read_callback_api), &orig);
    if (!record || !orig) {
        LOGI("native property callback hook failed");
        return;
    }
    g_orig_property_read_callback =
        reinterpret_cast<PropertyReadCallbackApiFn>(orig);
    LOGI("native property callback hook installed");
}
// D1: file-surface hooks. Identity files get profile-backed content; maps and
// mounts get filtered snapshots; root-trace paths read as absent. Everything
// else passes through untouched.
using OpenatFn = int (*)(int, const char *, int, ...);
using OpenFn = int (*)(const char *, int, ...);
using AccessFn = int (*)(const char *, int);
using FaccessatFn = int (*)(int, const char *, int, int);
using FstatatFn = int (*)(int, const char *, struct stat *, int);
OpenatFn g_orig_openat = nullptr;
OpenFn g_orig_open = nullptr;
AccessFn g_orig_access = nullptr;
FaccessatFn g_orig_faccessat = nullptr;
FstatatFn g_orig_fstatat = nullptr;

bool file_view_for(const char *path, std::string &content) {
    if (path == nullptr || !g_active) return false;
    auto it = g_file_views.find(path);
    if (it != g_file_views.end()) {
        content = it->second;
        return true;
    }
    static const char *filtered[] = {
        "/proc/self/maps", "/proc/self/mountinfo", "/proc/mounts",
    };
    for (const char *candidate : filtered) {
        if (std::strcmp(path, candidate) == 0) {
            int fd = g_orig_openat ? g_orig_openat(AT_FDCWD, path, O_RDONLY, 0) : -1;
            if (fd < 0) return false;
            content = read_all_and_filter(fd);
            close(fd);
            return true;
        }
    }
    return false;
}

int serve_synthetic(const char *path, int flags) {
    if (!g_active || path == nullptr) return -2;
    if ((flags & O_ACCMODE) != O_RDONLY) return -2;
    std::string content;
    if (!file_view_for(path, content)) return -2;
    int fd = static_cast<int>(syscall(SYS_memfd_create, "cloak_view", 1 /* MFD_CLOEXEC */));
    if (fd < 0) return -2;
    size_t written = 0;
    while (written < content.size()) {
        ssize_t count = write(fd, content.data() + written, content.size() - written);
        if (count <= 0) {
            close(fd);
            return -2;
        }
        written += static_cast<size_t>(count);
    }
    if (lseek(fd, 0, SEEK_SET) < 0) {
        close(fd);
        return -2;
    }
    return fd;
}

int hooked_openat(int dirfd, const char *pathname, int flags, ...) {
    int served = serve_synthetic(pathname, flags);
    if (served >= 0) return served;
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }
    return g_orig_openat ? g_orig_openat(dirfd, pathname, flags, mode) : -1;
}

int hooked_open(const char *pathname, int flags, ...) {
    int served = serve_synthetic(pathname, flags);
    if (served >= 0) return served;
    mode_t mode = 0;
    if (flags & (O_CREAT | O_TMPFILE)) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }
    return g_orig_open ? g_orig_open(pathname, flags, mode) : -1;
}

int hooked_access(const char *path, int mode) {
    if (g_active && path && is_root_trace_path(path)) {
        errno = ENOENT;
        return -1;
    }
    return g_orig_access ? g_orig_access(path, mode) : -1;
}

int hooked_faccessat(int dirfd, const char *path, int mode, int flags) {
    if (g_active && path && is_root_trace_path(path)) {
        errno = ENOENT;
        return -1;
    }
    return g_orig_faccessat ? g_orig_faccessat(dirfd, path, mode, flags) : -1;
}

int hooked_fstatat(int dirfd, const char *path, struct stat *buf, int flags) {
    if (g_active && path && is_root_trace_path(path)) {
        errno = ENOENT;
        return -1;
    }
    return g_orig_fstatat ? g_orig_fstatat(dirfd, path, buf, flags) : -1;
}

void install_native_file_hooks() {
    if (!g_native_hook_enabled || !g_shadowhook_ready || !g_shadowhook_handle) {
        return;
    }
    using HookFn = void *(*)(const char *, const char *, void *, void **);
    auto hook = reinterpret_cast<HookFn>(
        dlsym(g_shadowhook_handle, "shadowhook_hook_sym_name"));
    if (!hook) return;
    auto apply = [&](const char *symbol, void *replacement, void *storage) {
        void *orig = nullptr;
        void *record = hook("libc.so", symbol, replacement, &orig);
        if (record && orig) {
            *reinterpret_cast<void **>(storage) = orig;
        }
    };
    apply("openat", reinterpret_cast<void *>(hooked_openat), &g_orig_openat);
    apply("open", reinterpret_cast<void *>(hooked_open), &g_orig_open);
    apply("access", reinterpret_cast<void *>(hooked_access), &g_orig_access);
    apply("faccessat", reinterpret_cast<void *>(hooked_faccessat), &g_orig_faccessat);
    apply("fstatat", reinterpret_cast<void *>(hooked_fstatat), &g_orig_fstatat);
    apply("fstatat64", reinterpret_cast<void *>(hooked_fstatat), &g_orig_fstatat);
    LOGI("native file hooks installed");
}

void set_prop(const std::string &key, const std::string &value) {
    if (!value.empty()) {
        g_props[key] = value;
    }
}

std::string read_prop(const char *key) {
    char value[PROP_VALUE_MAX] = {0};
    int length = __system_property_get(key, value);
    return length > 0 ? std::string(value, static_cast<size_t>(length)) : std::string();
}

std::string lookup_prop(const char *key) {
    auto it = g_props.find(key);
    if (it != g_props.end()) {
        return it->second;
    }
    return read_prop(key);
}

std::string string_arg(JNIEnv *env, jstring value) {
    if (!value) {
        return std::string();
    }
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

// Resolve a property: override map first, then the real property store.
std::string resolve(JNIEnv *env, jstring keyJ) {
    std::string key = string_arg(env, keyJ);
    if (key.empty()) {
        return std::string();
    }
    return lookup_prop(key.c_str());
}
// --- D1: filesystem reality and concealment core --------------------------

std::string kernel_for_sdk(int sdk) {
    if (sdk >= 35) return "6.1.75-android15-8";
    if (sdk == 34) return "5.15.94-android14-6";
    if (sdk >= 33) return "5.10.157-android13-8";
    if (sdk >= 31) return "5.10.101-android12-9";
    return "5.4.186-android11-6";
}

std::string render_build_prop() {
    static const char *keys[] = {
        "ro.product.manufacturer", "ro.product.brand", "ro.product.model",
        "ro.product.name", "ro.product.device", "ro.hardware",
        "ro.build.fingerprint", "ro.build.id", "ro.build.version.incremental",
        "ro.build.version.sdk", "ro.build.version.release",
        "ro.build.version.security_patch", "ro.build.date.utc",
        "ro.bootloader", "gsm.version.baseband", "ro.serialno",
    };
    std::string out;
    for (const char *key : keys) {
        std::string value = lookup_prop(key);
        if (!value.empty()) {
            out += key;
            out += '=';
            out += value;
            out += '\n';
        }
    }
    return out;
}

std::string render_proc_version() {
    std::string buildType = lookup_prop("ro.build.type");
    std::string buildTags = lookup_prop("ro.build.tags");
    std::string fingerprint = lookup_prop("ro.build.fingerprint");
    // Measured kernel release; the SDK-derived string is only a fallback.
    std::string kernel;
    struct utsname uts;
    if (uname(&uts) == 0 && uts.release[0] != 0) {
        kernel = uts.release;
    } else {
        int sdk = 33;
        std::string sdkStr = lookup_prop("ro.build.version.sdk");
        if (!sdkStr.empty()) sdk = std::atoi(sdkStr.c_str());
        kernel = kernel_for_sdk(sdk);
    }
    return "Linux version " + kernel +
           " (build@android-build) (clang version 17.0.2) #1 SMP PREEMPT " +
           buildType + " " + buildTags + " " + fingerprint + "\n";
}

std::string render_cpuinfo() {
    std::string hardware = lookup_prop("ro.hardware");
    if (hardware.empty()) hardware = "qcom";
    // Measured host core count; guard against absurd values before rendering.
    long coreCount = sysconf(_SC_NPROCESSORS_ONLN);
    if (coreCount <= 0 || coreCount > 64) coreCount = 8;
    std::string out;
    for (int i = 0; i < static_cast<int>(coreCount); ++i) {
        out += "processor\t: " + std::to_string(i) + "\n";
        out += "BogoMIPS\t: 38.40\n";
        out += "Features\t: fp asimd aes sha1 sha2 crc32\n";
        out += "CPU implementer\t: 0x41\n";
        out += "CPU architecture: 8\n";
        out += "Hardware\t: " + hardware + "\n\n";
    }
    return out;
}

// Drop lines that reveal the host root/module layer from maps and mounts.
std::string filter_root_traces(const std::string &content) {
    static const char *needles[] = {
        "magisk", "zygisk", "libhook", "cyclone_cloak", "/data/adb",
        "shadowhook",
    };
    std::string out;
    size_t start = 0;
    while (start < content.size()) {
        size_t end = content.find('\n', start);
        if (end == std::string::npos) end = content.size();
        std::string line = content.substr(start, end - start);
        bool hide = false;
        for (const char *needle : needles) {
            if (line.find(needle) != std::string::npos) {
                hide = true;
                break;
            }
        }
        if (!hide) {
            out += line;
            out.push_back('\n');
        }
        start = end + 1;
    }
    return out;
}

bool is_root_trace_path(const std::string &path) {
    static const char *roots[] = {
        "/data/adb", "/sbin/.magisk", "/sbin/su", "/system/bin/su",
        "/system/xbin/su", "/proc/magisk", "/system/bin/.ext/.su",
    };
    for (const char *candidate : roots) {
        std::string prefix = candidate;
        if (path == prefix) return true;
        if (path.size() > prefix.size() &&
            path.compare(0, prefix.size(), prefix) == 0 &&
            path[prefix.size()] == '/') {
            return true;
        }
    }
    return false;
}

bool is_root_trace_path(const char *path) {
    return path && is_root_trace_path(std::string(path));
}
void build_file_views() {
    if (g_file_views_ready) return;
    g_file_views_ready = true;
    std::string buildProp = render_build_prop();
    if (!buildProp.empty()) {
        g_file_views["/system/build.prop"] = buildProp;
        g_file_views["/vendor/build.prop"] = buildProp;
    }
    std::string version = render_proc_version();
    if (!version.empty()) g_file_views["/proc/version"] = version;
    std::string cpuinfo = render_cpuinfo();
    if (!cpuinfo.empty()) g_file_views["/proc/cpuinfo"] = cpuinfo;
}

std::string read_all_and_filter(int fd) {
    std::string content;
    char buffer[4096];
    ssize_t count;
    while ((count = read(fd, buffer, sizeof(buffer))) > 0) {
        content.append(buffer, static_cast<size_t>(count));
    }
    return filter_root_traces(content);
}

constexpr uint32_t kStateRequestMagic = 0x434C4B31;  // CLK1
constexpr uint32_t kLoadRecordRequestMagic = 0x434C4B32;  // CLK2; private companion IPC only
constexpr size_t kMaxProfileBytes = 1024 * 1024;

struct StateRequest {
    uint32_t magic;
    char user[16];
    char packageName[256];
};

bool read_exact(int fd, void *buffer, size_t size) {
    auto *cursor = static_cast<unsigned char *>(buffer);
    while (size > 0) {
        ssize_t count = read(fd, cursor, size);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return false;
        cursor += count;
        size -= static_cast<size_t>(count);
    }
    return true;
}

bool write_exact(int fd, const void *buffer, size_t size) {
    const auto *cursor = static_cast<const unsigned char *>(buffer);
    while (size > 0) {
        ssize_t count = send(fd, cursor, size, MSG_NOSIGNAL);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return false;
        cursor += count;
        size -= static_cast<size_t>(count);
    }
    return true;
}

std::string bounded_string(const char *value, size_t capacity) {
    const void *end = memchr(value, '\0', capacity);
    if (!end) return {};
    return std::string(value, static_cast<const char *>(end) - value);
}

bool valid_request(const std::string &user, const std::string &pkg) {
    if (user.empty() || user.size() >= 16 || pkg.empty() || pkg.size() >= 256 ||
        pkg.find('.') == std::string::npos) {
        return false;
    }
    if (user.find_first_not_of("0123456789") != std::string::npos) return false;
    return pkg.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.") ==
           std::string::npos;
}

// Optional per-profile denylist integration. When the profile enables it,
// the root companion writes the bound package path into Magisk's denylist
// config so Shamiko (or stock MagiskDenyList enforcement) hides root from
// that process at the kernel layer. The in-process needle filter stays as
// a fallback for KernelSU or when the write fails.
constexpr uint32_t kDenyListRequestMagic = 0x434C4B33;  // CLK3; companion IPC only
constexpr size_t kDenyListMax = 8;

struct DenyListRequest {
    uint32_t magic;
    char user[16];
    char packageName[256];
};

// Called from the root companion with an already-validated request.
// Returns 1 when the denylist entry exists after the call, 0 when skipped.
bool ensure_denylist_entry(const std::string &user, const std::string &pkg) {
    // Magisk denylist config: one path per line under
    // /data/adb/magisk/<classic denylist>/... The canonical location is
    // managed by the magisk binary itself, so the safest write is a marker
    // file the companion shell can apply with `magisk --denylist add`.
    // We execute it from the companion (root) context once per profile+pkg.
    std::string command = "magisk --denylist add " + pkg;
    const int rc = system(command.c_str());
    return rc == 0;
}

bool write_denylist_entry(const std::string &user, const std::string &pkg) {
    if (user.empty() || pkg.empty()) return false;
    // Only run when Magisk is present; on KernelSU or plain, skip silently.
    if (access("/proc/self/ns/mnt", F_OK) != 0 || access("/data/adb/magisk", F_OK) != 0) {
        return false;
    }
    return ensure_denylist_entry(user, pkg);
}

std::string read_profile_state(const std::string &user, const std::string &pkg) {
    const std::string base = "/data/adb/cyclone_cloak/state-v1";
    std::string stateKey;
    {
        std::ifstream indexFile(base + "/index.json");
        if (!indexFile.good()) {
            return {};
        }
        json index = json::parse(indexFile, nullptr, false);
        if (index.is_discarded() || !index.is_object()) {
            LOGI("index unreadable");
            return {};
        }
        auto entries = index.find("entries");
        if (entries == index.end() || !entries->is_object()) {
            return {};
        }
        auto found = entries->find(user + "/" + pkg);
        if (found == entries->end() || !found->is_object()) {
            return {};
        }
        auto keyIt = found->find("key");
        if (keyIt == found->end() || !keyIt->is_string()) {
            return {};
        }
        stateKey = keyIt->get<std::string>();
    }
    if (stateKey.size() != 64 ||
        stateKey.find_first_not_of("0123456789abcdef") != std::string::npos) {
        return {};
    }
    std::ifstream profileFile(base + "/" + stateKey + "/profile.json");
    if (!profileFile.good()) {
        return {};
    }
    std::string profileJson((std::istreambuf_iterator<char>(profileFile)), std::istreambuf_iterator<char>());
    if (profileJson.empty() || profileJson.size() > kMaxProfileBytes) {
        return {};
    }
    json profile = json::parse(profileJson, nullptr, false);
    if (profile.is_discarded() || !profile.is_object() ||
        !profile.contains("device") || !profile["device"].is_object()) {
        return {};
    }
    return profileJson;
}

void companion_handler(int socket) {
    StateRequest request{};
    if (!read_exact(socket, &request, sizeof(request))) return;
    if (request.magic == kLoadRecordRequestMagic) {
        // Existing root companion owns the write; zygote never opens the log.
        struct timeval timeout{3, 0};
        setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        const auto user = bounded_string(request.user, sizeof(request.user));
        const auto pkg = bounded_string(request.packageName, sizeof(request.packageName));
        uint32_t size = 0;
        auto result = cloak_load_evidence::Result::INVALID;
        if (valid_request(user, pkg) && read_exact(socket, &size, sizeof(size)) &&
            size > 0 && size <= cloak_load_evidence::kMaxRecord) {
            std::string payload(size, '\0');
            if (read_exact(socket, payload.data(), payload.size()) && !read_profile_state(user, pkg).empty()) {
                const int directory = open("/data/adb/modules/cyclone_cloak",
                                           O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
                result = cloak_load_evidence::persist(directory, payload, pkg, user);
                if (directory >= 0) close(directory);
            }
        }
        const uint32_t reply = static_cast<uint32_t>(result);
        if (result != cloak_load_evidence::Result::OK) LOGI("companion load record status=%u", reply);
        write_exact(socket, &reply, sizeof(reply));  // ack only after fdatasync
        return;
    }
    if (request.magic != kStateRequestMagic) {
        const uint32_t empty = 0;
        write_exact(socket, &empty, sizeof(empty));
        return;
    }
    const std::string user = bounded_string(request.user, sizeof(request.user));
    const std::string pkg = bounded_string(request.packageName, sizeof(request.packageName));
    std::string profileJson;
    if (valid_request(user, pkg)) {
        profileJson = read_profile_state(user, pkg);
        // Denylist integration: only after a valid profile is loaded and
        // only when the profile requests it via cloak.denylist_enabled.
        if (!profileJson.empty()) {
            json parsed = json::parse(profileJson, nullptr, false);
            if (!parsed.is_discarded() && parsed.is_object()) {
                auto cloakObj = parsed.find("cloak");
                if (cloakObj != parsed.end() && cloakObj->is_object() &&
                    cloakObj->value("denylist_enabled", false) == true) {
                    write_denylist_entry(user, pkg);
                }
            }
        }
    }
    const uint32_t size = profileJson.size() <= kMaxProfileBytes
        ? static_cast<uint32_t>(profileJson.size())
        : 0;
    if (!write_exact(socket, &size, sizeof(size))) return;
    if (size > 0) write_exact(socket, profileJson.data(), size);
}

cloak_load_evidence::Result send_load_record(Api *api, const std::string &user,
                                           const std::string &pkg, const std::string &payload) {
    if (!api || !valid_request(user, pkg) || payload.empty() ||
        payload.size() > cloak_load_evidence::kMaxRecord) return cloak_load_evidence::Result::INVALID;
    const int socket = api->connectCompanion();
    if (socket < 0) return cloak_load_evidence::Result::TRANSPORT;
    struct timeval timeout{3, 0};
    setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(socket, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    StateRequest request{};
    request.magic = kLoadRecordRequestMagic;
    memcpy(request.user, user.c_str(), user.size() + 1);
    memcpy(request.packageName, pkg.c_str(), pkg.size() + 1);
    const uint32_t size = payload.size();
    uint32_t reply = static_cast<uint32_t>(cloak_load_evidence::Result::TRANSPORT);
    const bool received = write_exact(socket, &request, sizeof(request)) &&
        write_exact(socket, &size, sizeof(size)) && write_exact(socket, payload.data(), payload.size()) &&
        read_exact(socket, &reply, sizeof(reply));
    close(socket);
    if (!received || reply > static_cast<uint32_t>(cloak_load_evidence::Result::TRANSPORT))
        return cloak_load_evidence::Result::TRANSPORT;
    return static_cast<cloak_load_evidence::Result>(reply);
}

bool load_profile_for(Api *api, const std::string &user, const std::string &pkg) {
    g_props.clear();
    g_runtime_profile.clear();
    g_java_callbacks = true;
    int sim_slots = 1;
    if (!api || !valid_request(user, pkg)) {
        return false;
    }
    StateRequest request{};
    request.magic = kStateRequestMagic;
    memcpy(request.user, user.c_str(), user.size() + 1);
    memcpy(request.packageName, pkg.c_str(), pkg.size() + 1);
    const int socket = api->connectCompanion();
    if (socket < 0) {
        LOGI("root companion connection failed");
        return false;
    }
    uint32_t size = 0;
    if (!write_exact(socket, &request, sizeof(request)) || !read_exact(socket, &size, sizeof(size)) ||
        size == 0 || size > kMaxProfileBytes) {
        close(socket);
        return false;
    }
    std::string profileJson(size, '\0');
    const bool received = read_exact(socket, profileJson.data(), size);
    close(socket);
    if (!received) return false;

    json profile = json::parse(profileJson, nullptr, false);
    if (profile.is_discarded() || !profile.is_object()) {
        LOGI("profile unreadable");
        return false;
    }
    auto device = profile.find("device");
    if (device == profile.end() || !device->is_object()) {
        return false;
    }
    // No new reader format or publication path. Only the saved profile supplies the envelope.
    json envelope = json::object();
for (const char *key : {"schema_version", "reality", "display", "telephony", "ua", "pif"}) {
        if (profile.contains(key)) envelope[key] = profile[key];
    }
    g_runtime_profile = envelope.dump(-1, ' ', true, json::error_handler_t::replace);
    for (const auto &[key, prop] : kDeviceProps) {
        auto value = device->find(key);
        if (value == device->end()) {
            continue;
        }
        if (value->is_string()) {
            set_prop(prop, value->get<std::string>());
        } else if (value->is_number_integer()) {
            set_prop(prop, std::to_string(value->get<long long>()));
        }
    }

    // Build type and tags come from the fingerprint tail: ...:user/release-keys.
    auto fingerprint = g_props.find("ro.build.fingerprint");
    if (fingerprint != g_props.end()) {
        const std::string &fp = fingerprint->second;
        auto colon = fp.rfind(':');
        if (colon != std::string::npos) {
            std::string tail = fp.substr(colon + 1);
            auto slash = tail.find('/');
            if (slash != std::string::npos) {
                set_prop("ro.build.type", tail.substr(0, slash));
                set_prop("ro.build.tags", tail.substr(slash + 1));
            }
        }
    }
    // Telephony identity: carrier alpha plus the combined MCC+MNC numeric for
    // both the registered network and the SIM.
    auto telephony = profile.find("telephony");
    if (telephony != profile.end() && telephony->is_object()) {
        for (const auto &entry : kTelephonyProps) {
            auto value = telephony->find(entry.first);
            if (value != telephony->end() && value->is_string()) {
                set_prop(entry.second, value->get<std::string>());
            }
        }
        auto mcc = telephony->find("mcc");
        auto mnc = telephony->find("mnc");
        if (mcc != telephony->end() && mcc->is_string() &&
            mnc != telephony->end() && mnc->is_string()) {
            const std::string numeric = mcc->get<std::string>() + mnc->get<std::string>();
            set_prop("gsm.operator.numeric", numeric);
            set_prop("gsm.sim.operator.numeric", numeric);
        }
        auto slots = telephony->find("sim_slot_count");
        if (slots != telephony->end() && slots->is_number_integer()) {
            int count = slots->get<int>();
            if (count >= 1 && count <= 2) {
                sim_slots = count;
                set_prop("cloak.sim_slot_count", std::to_string(count));
            }
        }
    }

    // Display identity: the profile's density reads straight through.
    auto display = profile.find("display");
    if (display != profile.end() && display->is_object()) {
        for (const auto &entry : kDisplayProps) {
            auto value = display->find(entry.first);
            if (value != display->end() && value->is_number_integer()) {
                set_prop(entry.second, std::to_string(value->get<long long>()));
            }
        }
    }

    // Identifier vault: serial gets the real serialno props; every other value
    // renders under its stable cloak.* key.
    auto identifiers = profile.find("identifiers");
    if (identifiers != profile.end() && identifiers->is_object()) {
        for (const auto &entry : kIdentifierProps) {
            // Single-SIM profiles keep their secondary IMEI vault-only, so the
            // scoped app sees exactly one identity slot.
            if (entry.first == std::string("imei_secondary") && sim_slots < 2) {
                continue;
            }
            auto value = identifiers->find(entry.first);
            if (value != identifiers->end() && value->is_string()) {
                set_prop(entry.second, value->get<std::string>());
            }
        }
    }

    // Locale and timezone: native props here, Java-side defaults in apply_profile.
    auto locale = profile.find("locale");
    if (locale != profile.end() && locale->is_object()) {
        for (const auto &entry : kLocaleProps) {
            auto value = locale->find(entry.first);
            if (value != locale->end() && value->is_string()) {
                set_prop(entry.second, value->get<std::string>());
                set_prop(std::string("cloak.locale.") + entry.first, value->get<std::string>());
            }
        }
    // Camera: publish per-camera identity specs as cloak.camera.* props; the
    // Java callback rewrites supported-level and pixel-array reads by facing.
    auto camera = profile.find("camera");
    if (camera != profile.end() && camera->is_object()) {
        auto cams = camera->find("cameras");
        if (cams != camera->end() && cams->is_array()) {
            int count = 0;
            for (const auto &cam : *cams) {
                if (!cam.is_object() || count >= 6) {
                    break;
                }
                auto field = [&cam](const char *key) -> std::string {
                    auto value = cam.find(key);
                    return value != cam.end() && value->is_number()
                               ? std::to_string(value->get<int>())
                               : std::string();
                };
                std::string prefix = "cloak.camera." + std::to_string(count) + ".";
                std::string facing = field("facing");
                std::string level = field("hardware_level");
                std::string width = field("pixel_width");
                std::string height = field("pixel_height");
                if (!facing.empty() && !level.empty() && !width.empty() && !height.empty()) {
                    set_prop(prefix + "facing", facing);
                    set_prop(prefix + "level", level);
                    set_prop(prefix + "w", width);
                    set_prop(prefix + "h", height);
                    ++count;
                }
            }
            set_prop("cloak.camera.count", std::to_string(count));
        }
    }
        auto lang = locale->find("language");
        auto country = locale->find("country");
        if (lang != locale->end() && lang->is_string() &&
            country != locale->end() && country->is_string()) {
            set_prop("persist.sys.locale",
                     lang->get<std::string>() + "-" + country->get<std::string>());
        }
    }

    // User agent: the WebView UA reads as one prop; HookBridge serves it
    // through WebSettings.getUserAgentString().
    auto ua = profile.find("ua");
    if (ua != profile.end() && ua->is_object()) {
        auto value = ua->find("value");
        if (value != ua->end() && value->is_string()) {
            set_prop("cloak.ua", value->get<std::string>());
        }
    }

    // Java callback gate: profiles older than the engine block default to on.
    auto engine = profile.find("engine");
    if (engine != profile.end() && engine->is_object()) {
        auto flag = engine->find("java_callbacks");
        if (flag != engine->end() && flag->is_boolean()) {
            g_java_callbacks = flag->get<bool>();
        }
    }

    // Android names 5G networks NR in gsm.network.type; profiles say 5G.
    if (auto net = g_props.find("gsm.network.type"); net != g_props.end() && net->second == "5G") {
        net->second = "NR";
    }
    return !g_props.empty();
}

jstring make_string(JNIEnv *env, const std::string &value) {
    return env->NewStringUTF(value.c_str());
}

jstring native_get1(JNIEnv *env, jclass, jstring keyJ) {
    return make_string(env, resolve(env, keyJ));
}

jstring native_get2(JNIEnv *env, jclass, jstring keyJ, jstring defJ) {
    std::string value = resolve(env, keyJ);
    if (value.empty()) {
        value = string_arg(env, defJ);
    }
    return make_string(env, value);
}

jint native_get_int(JNIEnv *env, jclass, jstring keyJ, jint def) {
    std::string value = resolve(env, keyJ);
    if (value.empty()) {
        return def;
    }
    char *end = nullptr;
    errno = 0;
    long parsed = strtol(value.c_str(), &end, 10);
    if (errno != 0 || end == nullptr || *end != '\0') {
        return def;
    }
    return static_cast<jint>(parsed);
}

jlong native_get_long(JNIEnv *env, jclass, jstring keyJ, jlong def) {
    std::string value = resolve(env, keyJ);
    if (value.empty()) {
        return def;
    }
    char *end = nullptr;
    errno = 0;
    long long parsed = strtoll(value.c_str(), &end, 10);
    if (errno != 0 || end == nullptr || *end != '\0') {
        return def;
    }
    return static_cast<jlong>(parsed);
}

jboolean native_get_boolean(JNIEnv *env, jclass, jstring keyJ, jboolean def) {
    std::string value = resolve(env, keyJ);
    if (value.empty()) {
        return def;
    }
    for (char &c : value) {
        c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    if (value == "1" || value == "true" || value == "y" || value == "yes" || value == "on") {
        return JNI_TRUE;
    }
    if (value == "0" || value == "false" || value == "n" || value == "no" || value == "off") {
        return JNI_FALSE;
    }
    return def;
}

void apply_profile(JNIEnv *env) {
    // 1. Rewrite the Build statics the app sees.
    jclass build = env->FindClass("android/os/Build");
    if (!build) {
        env->ExceptionClear();
        return;
    }
    struct FieldSpec {
        const char *field;
        const char *prop;
        bool numeric;
    };
    static const FieldSpec fields[] = {
        {"MANUFACTURER", "ro.product.manufacturer", false},
        {"BRAND", "ro.product.brand", false},
        {"MODEL", "ro.product.model", false},
        {"SERIAL", "ro.serialno", false},
        {"DEVICE", "ro.product.device", false},
        {"PRODUCT", "ro.product.name", false},
        {"HARDWARE", "ro.hardware", false},
        {"FINGERPRINT", "ro.build.fingerprint", false},
        {"ID", "ro.build.id", false},
        {"BOOTLOADER", "ro.bootloader", false},
        {"RADIO", "gsm.version.baseband", false},
        {"TYPE", "ro.build.type", false},
        {"TAGS", "ro.build.tags", false},
        {"TIME", "ro.build.date.utc", true},
    };
    for (const auto &spec : fields) {
        auto it = g_props.find(spec.prop);
        if (it == g_props.end()) {
            continue;
        }
        jfieldID field = env->GetStaticFieldID(build, spec.field,
                                               spec.numeric ? "J" : "Ljava/lang/String;");
        if (!field) {
            env->ExceptionClear();
            continue;
        }
        if (spec.numeric) {
            env->SetStaticLongField(
                build, field,
                static_cast<jlong>(strtoll(it->second.c_str(), nullptr, 10)) * 1000);
        } else {
            jstring value = env->NewStringUTF(it->second.c_str());
            env->SetStaticObjectField(build, field, value);
            env->DeleteLocalRef(value);
        }
    }
    // Android reports the API level from a nested class, so it needs its own pass.
    if (jclass version = env->FindClass("android/os/Build$VERSION")) {
        if (auto it = g_props.find("ro.build.version.incremental"); it != g_props.end()) {
            if (jfieldID field = env->GetStaticFieldID(version, "INCREMENTAL", "Ljava/lang/String;")) {
                jstring value = env->NewStringUTF(it->second.c_str());
                env->SetStaticObjectField(version, field, value);
                env->DeleteLocalRef(value);
            } else {
                env->ExceptionClear();
            }
        }
        if (auto it = g_props.find("ro.build.version.sdk"); it != g_props.end()) {
            if (auto field = env->GetStaticFieldID(version, "SDK_INT", "I")) {
                env->SetStaticIntField(version, field, static_cast<jint>(strtol(it->second.c_str(), nullptr, 10)));
            }
        }
        if (auto it = g_props.find("ro.build.version.release"); it != g_props.end()) {
            if (auto field = env->GetStaticFieldID(version, "RELEASE", "Ljava/lang/String;")) {
                auto value = env->NewStringUTF(it->second.c_str());
                env->SetStaticObjectField(version, field, value);
                env->DeleteLocalRef(value);
            }
        }
        if (auto it = g_props.find("ro.build.version.security_patch"); it != g_props.end()) {
            if (auto field = env->GetStaticFieldID(version, "SECURITY_PATCH", "Ljava/lang/String;")) {
                auto value = env->NewStringUTF(it->second.c_str());
                env->SetStaticObjectField(version, field, value);
                env->DeleteLocalRef(value);
            }
        }
        env->DeleteLocalRef(version);
    } else {
        env->ExceptionClear();
    }

    // 1b. Per-profile PIF configuration. When the bound process is GMS and
    // the profile carries a pif section, materialize its fields as extra
    // prop overrides before the profile is applied so GMS-side verification
    // sees them in the same pass as the general identity fields. This is
    // the hook point that reuses PIF verdict mechanics inside our own
    // per-process envelope instead of a device-global Magisk module.
    if (!g_pif_applied && !g_runtime_profile.empty()) {
        json runtimeEnvelope = json::parse(g_runtime_profile, nullptr, false);
        if (!runtimeEnvelope.is_discarded() && runtimeEnvelope.is_object()) {
            auto pif = runtimeEnvelope.find("pif");
            if (pif != runtimeEnvelope.end() && pif->is_object() &&
                pif->value("enabled", false) == true) {
                auto applyString = [](const char *prop, const json &container, const char *key) {
                    auto it = container.find(key);
                    if (it != container.end() && it->is_string() && !it->get_ref<const std::string &>().empty()) {
                        set_prop(prop, it->get<std::string>());
                    }
                };
                applyString("ro.build.fingerprint.pif", *pif, "fingerprint");
                applyString("ro.build.version.security_patch.pif", *pif, "security_patch");
                applyString("ro.product.model.pif", *pif, "model");
                if (auto level = pif->find("first_api_level"); level != pif->end() && level->is_number()) {
                    set_prop("ro.product.first_api_level", std::to_string(level->get<int>()));
                }
                if (auto vb = pif->find("verified_boot"); vb != pif->end() && vb->is_boolean()) {
                    set_prop("ro.boot.vbmeta.device_state",
                             vb->get<bool>() ? "verified" : "unlocked");
                }
            }
        }
        g_pif_applied = true;
    }

    // 2. Point the process Java-side defaults at the profile so
    // Locale.getDefault() and TimeZone.getDefault() agree with the props.
    const std::string language = lookup_prop("cloak.locale.language");
    const std::string country = lookup_prop("cloak.locale.country");
    if (!language.empty() || !country.empty()) {
        if (jclass system = env->FindClass("java/lang/System")) {
            if (auto setProperty = env->GetStaticMethodID(system, "setProperty",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
                auto put = [&](const char *key, const std::string &value) {
                    if (value.empty()) return;
                    jstring keyStr = env->NewStringUTF(key);
                    jstring valStr = env->NewStringUTF(value.c_str());
                    env->CallStaticObjectMethod(system, setProperty, keyStr, valStr);
                    env->DeleteLocalRef(keyStr);
                    env->DeleteLocalRef(valStr);
                };
                put("user.language", language);
                put("user.country", country);
            } else {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(system);
        } else {
            env->ExceptionClear();
        }
        if (jclass localeClass = env->FindClass("java/util/Locale")) {
            jmethodID ctor = env->GetMethodID(localeClass, "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V");
            jmethodID setDefault = env->GetStaticMethodID(localeClass, "setDefault",
                "(Ljava/util/Locale;)V");
            if (ctor && setDefault) {
                jstring langStr = env->NewStringUTF(language.c_str());
                jstring countryStr = env->NewStringUTF(country.c_str());
                jobject locale = env->NewObject(localeClass, ctor, langStr, countryStr);
                if (locale) {
                    env->CallStaticVoidMethod(localeClass, setDefault, locale);
                    env->DeleteLocalRef(locale);
                } else {
                    env->ExceptionClear();
                }
                env->DeleteLocalRef(langStr);
                env->DeleteLocalRef(countryStr);
            } else {
                env->ExceptionClear();
            }
            env->DeleteLocalRef(localeClass);
        } else {
            env->ExceptionClear();
        }
    }

    // 3. Hook SystemProperties reads for everything that follows process start.
    jclass sysprops = env->FindClass("android/os/SystemProperties");
    if (!sysprops) {
        env->ExceptionClear();
        return;
    }
    auto add = [&](const char *name, const char *sig, void *fn) {
        jmethodID method = env->GetStaticMethodID(sysprops, name, sig);
        if (!method) {
            env->ExceptionClear();
            return;
        }
        // Modern Android retains native_get(String) as a Java forwarding
        // wrapper. Registering it as native rejects the entire batch.
        jobject reflected = env->ToReflectedMethod(sysprops, method, JNI_TRUE);
        jclass methodClass = env->FindClass("java/lang/reflect/Method");
        jmethodID modifiers = methodClass ? env->GetMethodID(methodClass, "getModifiers", "()I") : nullptr;
        bool isNative = reflected && modifiers &&
            (env->CallIntMethod(reflected, modifiers) & 0x0100) != 0;
        if (env->ExceptionCheck()) { env->ExceptionClear(); isNative = false; }
        if (reflected) env->DeleteLocalRef(reflected);
        if (methodClass) env->DeleteLocalRef(methodClass);
        if (!isNative) return;
        JNINativeMethod replacement{const_cast<char *>(name), const_cast<char *>(sig), fn};
        if (env->RegisterNatives(sysprops, &replacement, 1) < 0) {
            env->ExceptionClear();
            LOGI("RegisterNatives failed for %s%s", name, sig);
        }
    };
    add("native_get", "(Ljava/lang/String;)Ljava/lang/String;",
        reinterpret_cast<void *>(native_get1));
    add("native_get", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        reinterpret_cast<void *>(native_get2));
    add("native_get_int", "(Ljava/lang/String;I)I", reinterpret_cast<void *>(native_get_int));
    add("native_get_long", "(Ljava/lang/String;J)J", reinterpret_cast<void *>(native_get_long));
    add("native_get_boolean", "(Ljava/lang/String;Z)Z", reinterpret_cast<void *>(native_get_boolean));
    env->DeleteLocalRef(sysprops);
}


// Load the embedded hook bridge (android/module/src/hook/java) and let it
// install the Java framework callbacks. Fail-open: any failure only logs and
// leaves the prop-level coverage in place.
void init_java_hooks(JNIEnv *env, const std::string &app_data_dir) {
    if (!g_active || !g_java_callbacks || g_hook_loader != nullptr) {
        return;
    }
    jobject dexBuffer = env->NewDirectByteBuffer(
        const_cast<unsigned char *>(CLOAK_HOOKS_DEX),
        static_cast<jlong>(CLOAK_HOOKS_DEX_SIZE));
    if (!dexBuffer) {
        env->ExceptionClear();
        return;
    }
    jclass loaderClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (!loaderClass) {
        env->ExceptionClear();
        env->DeleteLocalRef(dexBuffer);
        return;
    }
    jmethodID loaderCtor = env->GetMethodID(loaderClass, "<init>",
        "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    if (!loaderCtor) {
        env->ExceptionClear();
        env->DeleteLocalRef(loaderClass);
        env->DeleteLocalRef(dexBuffer);
        return;
    }
    jobject loader = env->NewObject(loaderClass, loaderCtor, dexBuffer,
                                    (jobject) nullptr);
    env->DeleteLocalRef(dexBuffer);
    if (!loader) {
        env->ExceptionClear();
        LOGI("hook bridge classloader failed");
        env->DeleteLocalRef(loaderClass);
        return;
    }
    jmethodID loadClass = env->GetMethodID(loaderClass, "loadClass",
        "(Ljava/lang/String;)Ljava/lang/Class;");
    env->DeleteLocalRef(loaderClass);
    if (!loadClass) {
        env->ExceptionClear();
        env->DeleteLocalRef(loader);
        return;
    }
    jstring bridgeName = env->NewStringUTF("dev.cyclone.cloak.hook.HookBridge");
    jclass bridge = bridgeName
        ? (jclass) env->CallObjectMethod(loader, loadClass, bridgeName)
        : nullptr;
    if (bridgeName) {
        env->DeleteLocalRef(bridgeName);
    }
    if (!bridge) {
        env->ExceptionClear();
        LOGI("hook bridge not loadable");
        env->DeleteLocalRef(loader);
        return;
    }
    jmethodID init = env->GetStaticMethodID(bridge, "init",
        "(Ljava/lang/String;[BII)V");
    if (!init) {
        env->ExceptionClear();
        env->DeleteLocalRef(bridge);
        env->DeleteLocalRef(loader);
        return;
    }
    jbyteArray pine = env->NewByteArray(static_cast<jsize>(CLOAK_PINE_LIB_SIZE));
    if (!pine) {
        env->ExceptionClear();
        env->DeleteLocalRef(bridge);
        env->DeleteLocalRef(loader);
        return;
    }
    env->SetByteArrayRegion(pine, 0, static_cast<jsize>(CLOAK_PINE_LIB_SIZE),
        reinterpret_cast<const jbyte *>(CLOAK_PINE_LIB));
    jstring dataDir = env->NewStringUTF(app_data_dir.c_str());
    if (!dataDir) {
        env->ExceptionClear();
        env->DeleteLocalRef(pine);
        env->DeleteLocalRef(bridge);
        env->DeleteLocalRef(loader);
        return;
    }
    // Pin before installing even the first callback. Static references inside
    // this loader are a self-contained cycle, not roots; deleting the local
    // references otherwise lets ART unload methods still used by trampolines.
    g_hook_loader = env->NewGlobalRef(loader);
    if (!g_hook_loader) {
        env->ExceptionClear();
        LOGI("hook bridge lifetime root unavailable");
        env->DeleteLocalRef(dataDir);
        env->DeleteLocalRef(pine);
        env->DeleteLocalRef(bridge);
        env->DeleteLocalRef(loader);
        return;
    }
    jmethodID configure = env->GetStaticMethodID(bridge, "configureEnvelope", "(Ljava/lang/String;)V");
    jstring snapshot = env->NewStringUTF(g_runtime_profile.c_str());
    if (configure && snapshot && !env->ExceptionCheck()) {
        env->CallStaticVoidMethod(bridge, configure, snapshot);
    }
    if (snapshot) env->DeleteLocalRef(snapshot);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGI("envelope setup unavailable");
    }
    env->CallStaticVoidMethod(bridge, init, dataDir, pine, g_runtime_sdk, g_runtime_flags);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGI("hook bridge init reported an error");
    }
    env->DeleteLocalRef(dataDir);
    env->DeleteLocalRef(pine);
    env->DeleteLocalRef(bridge);
    env->DeleteLocalRef(loader);
}

class CloakModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        // Read before property hooks or apply_profile can expose a synthetic SDK.
        g_runtime_sdk = android_get_device_api_level();
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        // Observe the process's real ART flags; never change them.
        g_runtime_flags = args->runtime_flags;
        // app_data_dir looks like /data/user/0/<package>; the last segment is the package.
        g_app_data_dir = string_arg(env, args->app_data_dir);
        g_active_package = g_app_data_dir;
        std::string userId = "0";
        auto slash = g_active_package.find_last_of('/');
        if (slash != std::string::npos) {
            auto userSlash = slash > 0 ? g_active_package.find_last_of('/', slash - 1) : std::string::npos;
            if (userSlash != std::string::npos) {
                std::string candidate = g_active_package.substr(userSlash + 1, slash - userSlash - 1);
                if (!candidate.empty() && candidate.find_first_not_of("0123456789") == std::string::npos) {
                    userId = candidate;
                }
            }
            g_active_package = g_active_package.substr(slash + 1);
        }
        g_active_key = userId + ":" + g_active_package;
        // Ignore even an existing self-binding; do not rewrite the saved or published records.
        if (!runtime_package_allowed(g_active_package)) {
            g_active = false;
            return;
        }
        // Owner kill switch: an empty marker file in the module directory
        // keeps future native hooking off for every process.
        g_native_hook_enabled =
            access("/data/adb/modules/cyclone_cloak/disable_native_hook", F_OK) != 0;
        g_active = load_profile_for(api, userId, g_active_package);
        g_pif_applied = false;
        if (g_active) {
            const int module_directory = api->getModuleDir();
            const auto payload = cloak_load_evidence::assemble(module_directory, g_active_package, userId, g_app_data_dir);
            if (module_directory >= 0) close(module_directory);
            const auto result = send_load_record(api, userId, g_active_package, payload);
            if (result != cloak_load_evidence::Result::OK)
                LOGI("load record status=%u", static_cast<uint32_t>(result));
            LOGI("cloaking %s (%zu props)", g_active_key.c_str(), g_props.size());
        }
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (g_active) {
            install_native_property_hook();
            build_file_views();
            install_native_file_hooks();
            apply_profile(env);
            init_java_hooks(env, g_app_data_dir);
        }
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
};

}  // namespace

REGISTER_ZYGISK_MODULE(CloakModule)
REGISTER_ZYGISK_COMPANION(companion_handler)
