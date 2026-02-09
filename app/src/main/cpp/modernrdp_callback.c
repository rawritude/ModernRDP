#include "modernrdp_callback.h"
#include "modernrdp_jni.h"

#include <android/log.h>
#include <stdarg.h>

#define TAG "ModernRDP-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM* g_jvm = NULL;
static jclass g_bridge_class = NULL;

int mrdp_callback_init(JavaVM* vm, JNIEnv* env)
{
    g_jvm = vm;

    jclass local_class = (*env)->FindClass(env, MRDP_JNI_CLASS);
    if (!local_class)
    {
        LOGE("Failed to find class %s", MRDP_JNI_CLASS);
        return -1;
    }

    g_bridge_class = (*env)->NewGlobalRef(env, local_class);
    (*env)->DeleteLocalRef(env, local_class);

    if (!g_bridge_class)
    {
        LOGE("Failed to create global ref for %s", MRDP_JNI_CLASS);
        return -1;
    }

    LOGI("JNI callback environment initialized");
    return 0;
}

/**
 * Get JNIEnv for the current thread, attaching if necessary.
 * Returns TRUE if the thread was attached (and needs detaching after).
 */
static BOOL get_env(JNIEnv** env, BOOL* attached)
{
    *attached = FALSE;

    if (!g_jvm)
        return FALSE;

    int status = (*g_jvm)->GetEnv(g_jvm, (void**)env, JNI_VERSION_1_6);
    if (status == JNI_OK)
        return TRUE;

    if (status == JNI_EDETACHED)
    {
        JavaVMAttachArgs args = {
            .version = JNI_VERSION_1_6,
            .name = "ModernRDP-Native",
            .group = NULL
        };
        if ((*g_jvm)->AttachCurrentThread(g_jvm, env, &args) == JNI_OK)
        {
            *attached = TRUE;
            return TRUE;
        }
    }

    return FALSE;
}

static void release_env(BOOL attached)
{
    if (attached && g_jvm)
        (*g_jvm)->DetachCurrentThread(g_jvm);
}

void mrdp_callback(const char* method_name, const char* signature, ...)
{
    JNIEnv* env;
    BOOL attached;

    if (!get_env(&env, &attached))
    {
        LOGE("mrdp_callback: failed to get JNIEnv for %s", method_name);
        return;
    }

    jmethodID method = (*env)->GetStaticMethodID(env, g_bridge_class, method_name, signature);
    if (!method)
    {
        LOGE("mrdp_callback: method not found: %s %s", method_name, signature);
        release_env(attached);
        return;
    }

    va_list args;
    va_start(args, signature);
    (*env)->CallStaticVoidMethodV(env, g_bridge_class, method, args);
    va_end(args);

    if ((*env)->ExceptionCheck(env))
    {
        LOGE("mrdp_callback: exception in %s", method_name);
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    release_env(attached);
}

jboolean mrdp_callback_bool(const char* method_name, const char* signature, ...)
{
    JNIEnv* env;
    BOOL attached;
    jboolean result = JNI_FALSE;

    if (!get_env(&env, &attached))
    {
        LOGE("mrdp_callback_bool: failed to get JNIEnv for %s", method_name);
        return JNI_FALSE;
    }

    jmethodID method = (*env)->GetStaticMethodID(env, g_bridge_class, method_name, signature);
    if (!method)
    {
        LOGE("mrdp_callback_bool: method not found: %s %s", method_name, signature);
        release_env(attached);
        return JNI_FALSE;
    }

    va_list args;
    va_start(args, signature);
    result = (*env)->CallStaticBooleanMethodV(env, g_bridge_class, method, args);
    va_end(args);

    if ((*env)->ExceptionCheck(env))
    {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        result = JNI_FALSE;
    }

    release_env(attached);
    return result;
}

jint mrdp_callback_int(const char* method_name, const char* signature, ...)
{
    JNIEnv* env;
    BOOL attached;
    jint result = 0;

    if (!get_env(&env, &attached))
    {
        LOGE("mrdp_callback_int: failed to get JNIEnv for %s", method_name);
        return 0;
    }

    jmethodID method = (*env)->GetStaticMethodID(env, g_bridge_class, method_name, signature);
    if (!method)
    {
        LOGE("mrdp_callback_int: method not found: %s %s", method_name, signature);
        release_env(attached);
        return 0;
    }

    va_list args;
    va_start(args, signature);
    result = (*env)->CallStaticIntMethodV(env, g_bridge_class, method, args);
    va_end(args);

    if ((*env)->ExceptionCheck(env))
    {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        result = 0;
    }

    release_env(attached);
    return result;
}

JNIEnv* mrdp_get_env(BOOL* attached)
{
    JNIEnv* env;
    if (!get_env(&env, attached))
        return NULL;
    return env;
}

void mrdp_release_env(BOOL attached)
{
    release_env(attached);
}

jclass mrdp_get_bridge_class(void)
{
    return g_bridge_class;
}
