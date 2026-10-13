#include <jni.h>
#include <sys/system_properties.h>
#include <string>

namespace {
void copyPropertyValue(void *cookie, const char *, const char *value, uint32_t) {
    *static_cast<std::string *>(cookie) = value;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_cyclone_cloak_probe_ProbeActivity_nativeProperty(JNIEnv *env, jclass, jstring key) {
    const char *name = env->GetStringUTFChars(key, nullptr);
    if (!name) return nullptr;
    char value[PROP_VALUE_MAX] = {};
    __system_property_get(name, value);
    env->ReleaseStringUTFChars(key, name);
    return env->NewStringUTF(value);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_cyclone_cloak_probe_ProbeActivity_nativePropertyCallback(JNIEnv *env, jclass, jstring key) {
    const char *name = env->GetStringUTFChars(key, nullptr);
    if (!name) return nullptr;
    const prop_info *info = __system_property_find(name);
    env->ReleaseStringUTFChars(key, name);
    if (!info) return env->NewStringUTF("");
    std::string value;
    __system_property_read_callback(info, copyPropertyValue, &value);
    return env->NewStringUTF(value.c_str());
}
