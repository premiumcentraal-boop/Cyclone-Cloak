#include <jni.h>
#include <sys/system_properties.h>

extern "C" JNIEXPORT jstring JNICALL
Java_dev_cyclone_cloak_probe_ProbeActivity_nativeProperty(JNIEnv *env, jclass, jstring key) {
    const char *name = env->GetStringUTFChars(key, nullptr);
    if (!name) return nullptr;
    char value[PROP_VALUE_MAX] = {};
    __system_property_get(name, value);
    env->ReleaseStringUTFChars(key, name);
    return env->NewStringUTF(value);
}
