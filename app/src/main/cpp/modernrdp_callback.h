#ifndef MODERNRDP_CALLBACK_H
#define MODERNRDP_CALLBACK_H

#include <jni.h>

/**
 * Initialize the JNI callback environment. Called from JNI_OnLoad.
 * Caches the JavaVM pointer and FreeRdpBridge class reference.
 */
int mrdp_callback_init(JavaVM* vm, JNIEnv* env);

/**
 * Fire a void callback on the FreeRdpBridge Java class.
 * The method name and JNI signature are used to look up the static method.
 *
 * Example: mrdp_callback("onNativeConnected", "(J)V", (jlong)instance);
 */
void mrdp_callback(const char* method_name, const char* signature, ...);

/**
 * Fire a callback that returns a boolean result.
 */
jboolean mrdp_callback_bool(const char* method_name, const char* signature, ...);

/**
 * Fire a callback that returns an int result.
 */
jint mrdp_callback_int(const char* method_name, const char* signature, ...);

/**
 * Get a JNIEnv for the current thread, attaching if necessary.
 * The 'attached' flag must be passed to mrdp_release_env() after use.
 */
JNIEnv* mrdp_get_env(BOOL* attached);

/**
 * Release a JNIEnv obtained via mrdp_get_env().
 */
void mrdp_release_env(BOOL attached);

/**
 * Get the cached FreeRdpBridge class reference.
 */
jclass mrdp_get_bridge_class(void);

#endif /* MODERNRDP_CALLBACK_H */
