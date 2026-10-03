/*
 * Cyclone Cloak 0.1 - Zygisk module.
 *
 * preAppSpecialize runs while the freshly forked process is still root: we resolve the package
 * from its data dir, look it up in /data/adb/cyclone_cloak/config.json (written by the companion
 * app over su) and stash the bound profile's props. postAppSpecialize rewrites android.os.Build
 * statics and hooks android.os.SystemProperties' native methods so every read inside the scoped
 * app returns the bound profile. Unscoped apps are untouched.
 */

#include <sys/stat.h>
#include "zygisk.hpp"

#include <android/log.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <cctype>
#include <cerrno>
#include <cstring>
#include <fstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "json.hpp"

using json = nlohmann::json;
using zygisk::Api;
using zygisk::AppSpecializeArgs;

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
};

std::unordered_map<std::string, std::string> g_props;
std::string g_active_package;
std::string g_active_key;
bool g_active = false;

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

bool load_profile_for(const std::string &pkg) {
    g_props.clear();
    if (pkg.empty()) {
        return false;
    }
    std::ifstream file("/data/adb/cyclone_cloak/config.json");
    if (!file.good()) {
        return false;
    }
    json config = json::parse(file, nullptr, false);
    if (config.is_discarded() || !config.is_object()) {
        LOGI("config unreadable");
        return false;
    }
    auto bindings = config.find("bindings");
    if (bindings == config.end() || !bindings->is_object()) {
        return false;
    }
    auto colon = pkg.find(':');
    if (colon == std::string::npos) {
        return false;
    }
    auto entry = bindings->find(pkg);
    if (entry == bindings->end() || !entry->is_object()) {
        return false;
    }
    auto device = entry->find("device");
    if (device == entry->end() || !device->is_object()) {
        return false;
    }
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
        {"DEVICE", "ro.product.device", false},
        {"PRODUCT", "ro.product.name", false},
        {"HARDWARE", "ro.hardware", false},
        {"FINGERPRINT", "ro.build.fingerprint", false},
        {"ID", "ro.build.id", false},
        {"INCREMENTAL", "ro.build.version.incremental", false},
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

    // 2. Hook SystemProperties reads for everything that follows process start.
    jclass sysprops = env->FindClass("android/os/SystemProperties");
    if (!sysprops) {
        env->ExceptionClear();
        return;
    }
    std::vector<JNINativeMethod> methods;
    auto add = [&](const char *name, const char *sig, void *fn) {
        if (env->GetStaticMethodID(sysprops, name, sig) != nullptr) {
            methods.push_back({const_cast<char *>(name), const_cast<char *>(sig), fn});
        } else {
            env->ExceptionClear();
        }
    };
    add("native_get", "(Ljava/lang/String;)Ljava/lang/String;",
        reinterpret_cast<void *>(native_get1));
    add("native_get", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        reinterpret_cast<void *>(native_get2));
    add("native_get_int", "(Ljava/lang/String;I)I", reinterpret_cast<void *>(native_get_int));
    add("native_get_long", "(Ljava/lang/String;J)J", reinterpret_cast<void *>(native_get_long));
    add("native_get_boolean", "(Ljava/lang/String;Z)Z", reinterpret_cast<void *>(native_get_boolean));
    if (!methods.empty() &&
        env->RegisterNatives(sysprops, methods.data(), static_cast<jint>(methods.size())) < 0) {
        env->ExceptionClear();
        LOGI("RegisterNatives failed");
    }
    env->DeleteLocalRef(sysprops);
}

class CloakModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        // app_data_dir looks like /data/user/0/<package>; the last segment is the package.
        g_active_package = string_arg(env, args->app_data_dir);
        auto slash = g_active_package.find_last_of('/');
        if (slash != std::string::npos) {
            std::string userId = "0";
            auto userSlash = slash > 0 ? g_active_package.find_last_of('/', slash - 1) : std::string::npos;
            if (userSlash != std::string::npos) {
                std::string candidate = g_active_package.substr(userSlash + 1, slash - userSlash - 1);
                if (!candidate.empty() && candidate.find_first_not_of("0123456789") == std::string::npos) {
                    userId = candidate;
                }
            }
            g_active_package = g_active_package.substr(slash + 1);
            g_active_key = userId + ":" + g_active_package;
        }
        g_active = load_profile_for(g_active_key);
        if (g_active) {
            LOGI("cloaking %s (%zu props)", g_active_key.c_str(), g_props.size());
        }
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (g_active) {
            apply_profile(env);
        }
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
};

}  // namespace

REGISTER_ZYGISK_MODULE(CloakModule)
