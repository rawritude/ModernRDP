/**
 * ModernRDP JNI Bridge — connects Kotlin/Compose UI to libfreerdp.
 *
 * This implements the same patterns as FreeRDP's official android_freerdp.c
 * but with our own class paths (com.modernrdp.rdp.FreeRdpBridge) and
 * a cleaner interface designed for our Compose-based rendering pipeline.
 *
 * Licensed under Apache 2.0 (same as FreeRDP).
 */

#include "modernrdp_jni.h"
#include "modernrdp_callback.h"

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <freerdp/freerdp.h>
#include <freerdp/client.h>
#include <freerdp/client/cmdline.h>
#include <freerdp/channels/channels.h>
#include <freerdp/gdi/gdi.h>
#include <freerdp/gdi/gfx.h>
#include <freerdp/settings.h>
#include <freerdp/codec/bitmap.h>
#include <freerdp/event.h>
#include <freerdp/version.h>
#include <freerdp/buildflags.h>

#include <winpr/crt.h>
#include <winpr/synch.h>
#include <winpr/thread.h>

#define TAG "ModernRDP-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

/* ================================================================
 * FreeRDP Callbacks — called by the FreeRDP core library
 * ================================================================ */

static BOOL mrdp_begin_paint(rdpContext* context)
{
    rdpGdi* gdi = context->gdi;
    if (!gdi || !gdi->primary || !gdi->primary->hdc)
        return FALSE;
    gdi->primary->hdc->hwnd->invalid->null = TRUE;
    gdi->primary->hdc->hwnd->ninvalid = 0;
    return TRUE;
}

static BOOL mrdp_end_paint(rdpContext* context)
{
    rdpGdi* gdi = context->gdi;
    if (!gdi || !gdi->primary || !gdi->primary->hdc)
        return FALSE;

    HGDI_WND hwnd = gdi->primary->hdc->hwnd;
    if (hwnd->invalid->null || hwnd->ninvalid == 0)
        return TRUE;

    /* Compute the bounding rectangle of all dirty regions */
    INT32 x1 = hwnd->cinvalid[0].x;
    INT32 y1 = hwnd->cinvalid[0].y;
    INT32 x2 = hwnd->cinvalid[0].x + hwnd->cinvalid[0].w;
    INT32 y2 = hwnd->cinvalid[0].y + hwnd->cinvalid[0].h;

    for (int i = 1; i < hwnd->ninvalid; i++)
    {
        INT32 cx1 = hwnd->cinvalid[i].x;
        INT32 cy1 = hwnd->cinvalid[i].y;
        INT32 cx2 = cx1 + hwnd->cinvalid[i].w;
        INT32 cy2 = cy1 + hwnd->cinvalid[i].h;

        if (cx1 < x1) x1 = cx1;
        if (cy1 < y1) y1 = cy1;
        if (cx2 > x2) x2 = cx2;
        if (cy2 > y2) y2 = cy2;
    }

    /* Notify Java side about the dirty region */
    mrdp_callback("onNativeGraphicsUpdate", "(JIIII)V",
                  (jlong)context->instance, (jint)x1, (jint)y1,
                  (jint)(x2 - x1), (jint)(y2 - y1));

    hwnd->invalid->null = TRUE;
    hwnd->ninvalid = 0;
    return TRUE;
}

static BOOL mrdp_desktop_resize(rdpContext* context)
{
    rdpGdi* gdi = context->gdi;
    rdpSettings* settings = context->settings;

    UINT32 width = freerdp_settings_get_uint32(settings, FreeRDP_DesktopWidth);
    UINT32 height = freerdp_settings_get_uint32(settings, FreeRDP_DesktopHeight);
    UINT32 bpp = freerdp_settings_get_uint32(settings, FreeRDP_ColorDepth);

    LOGI("Desktop resize: %ux%u @%ubpp", width, height, bpp);

    if (!gdi_resize(gdi, width, height))
        return FALSE;

    /* Notify Java to reallocate the bitmap */
    mrdp_callback("onNativeGraphicsResize", "(JIII)V",
                  (jlong)context->instance, (jint)width, (jint)height, (jint)bpp);

    return TRUE;
}

/* ================================================================
 * Pre/Post Connect — FreeRDP session lifecycle hooks
 * ================================================================ */

static BOOL mrdp_pre_connect(freerdp* instance)
{
    rdpSettings* settings = instance->context->settings;

    LOGI("Pre-connect: %s:%u",
         freerdp_settings_get_string(settings, FreeRDP_ServerHostname),
         freerdp_settings_get_uint32(settings, FreeRDP_ServerPort));

    /* Always use software GDI on Android */
    if (!freerdp_settings_set_bool(settings, FreeRDP_SoftwareGdi, TRUE))
        return FALSE;

    /* Enable unicode keyboard mapping for better Android input handling */
    if (!freerdp_settings_set_bool(settings, FreeRDP_UnicodeInput, TRUE))
        return FALSE;

    mrdp_callback("onNativePreConnect", "(J)V", (jlong)instance);
    return TRUE;
}

static BOOL mrdp_post_connect(freerdp* instance)
{
    rdpContext* context = instance->context;
    rdpSettings* settings = context->settings;
    rdpUpdate* update = context->update;

    /* Initialize GDI with RGBX32 pixel format (matches Android ARGB_8888) */
    if (!gdi_init(instance, PIXEL_FORMAT_RGBX32))
    {
        LOGE("gdi_init failed");
        return FALSE;
    }

    /* Register our paint callbacks */
    update->BeginPaint = mrdp_begin_paint;
    update->EndPaint = mrdp_end_paint;
    update->DesktopResize = mrdp_desktop_resize;

    UINT32 width = freerdp_settings_get_uint32(settings, FreeRDP_DesktopWidth);
    UINT32 height = freerdp_settings_get_uint32(settings, FreeRDP_DesktopHeight);
    UINT32 bpp = freerdp_settings_get_uint32(settings, FreeRDP_ColorDepth);

    LOGI("Post-connect: session %ux%u @%ubpp", width, height, bpp);

    /* Notify Java about final negotiated resolution */
    mrdp_callback("onNativeSettingsChanged", "(JIII)V",
                  (jlong)instance, (jint)width, (jint)height, (jint)bpp);

    /* Notify connection success */
    mrdp_callback("onNativeConnected", "(J)V", (jlong)instance);

    return TRUE;
}

static void mrdp_post_disconnect(freerdp* instance)
{
    LOGI("Post-disconnect");
    mrdp_callback("onNativeDisconnecting", "(J)V", (jlong)instance);

    if (instance->context)
        gdi_free(instance);
}

/* ================================================================
 * Input Event Processing — drain the queue on the FreeRDP thread
 * ================================================================ */

static BOOL mrdp_process_event(freerdp* instance, MRDP_EVENT* event)
{
    if (!event)
        return TRUE;

    rdpInput* input = instance->context->input;

    switch (event->type)
    {
        case MRDP_EVENT_TYPE_KEY:
        {
            MRDP_EVENT_KEY* key_event = (MRDP_EVENT_KEY*)event;
            freerdp_input_send_keyboard_event(input, key_event->flags, key_event->scancode);
            break;
        }
        case MRDP_EVENT_TYPE_UNICODE_KEY:
        {
            MRDP_EVENT_UNICODE_KEY* unicode_event = (MRDP_EVENT_UNICODE_KEY*)event;
            freerdp_input_send_unicode_keyboard_event(input, unicode_event->flags, unicode_event->code);
            break;
        }
        case MRDP_EVENT_TYPE_CURSOR:
        {
            MRDP_EVENT_CURSOR* cursor_event = (MRDP_EVENT_CURSOR*)event;
            freerdp_input_send_mouse_event(input, cursor_event->flags,
                                            cursor_event->x, cursor_event->y);
            break;
        }
        case MRDP_EVENT_TYPE_DISCONNECT:
            return FALSE; /* Signal disconnect */

        case MRDP_EVENT_TYPE_CLIPBOARD:
        {
            /* Clipboard events handled via channel if cliprdr is active */
            break;
        }
        default:
            LOGW("Unknown event type: %d", event->type);
            break;
    }

    return TRUE;
}

static BOOL mrdp_check_input_events(freerdp* instance)
{
    mrdpContext* ctx = (mrdpContext*)instance->context;
    if (!ctx || !ctx->event_queue)
        return TRUE;

    MRDP_EVENT* event;
    while ((event = mrdp_event_queue_pop(ctx->event_queue)) != NULL)
    {
        BOOL result = mrdp_process_event(instance, event);
        mrdp_event_free(event);
        if (!result)
            return FALSE;
    }

    return TRUE;
}

/* ================================================================
 * Main RDP Thread — runs the FreeRDP event loop
 * ================================================================ */

static DWORD WINAPI mrdp_thread_func(LPVOID param)
{
    freerdp* instance = (freerdp*)param;
    mrdpContext* ctx = (mrdpContext*)instance->context;

    LOGI("RDP thread started");

    /* Connect */
    BOOL connected = freerdp_connect(instance);
    if (!connected)
    {
        LOGE("freerdp_connect failed");
        mrdp_callback("onNativeConnectionFailed", "(J)V", (jlong)instance);
        return 1;
    }

    ctx->is_connected = TRUE;

    /* Main event loop */
    while (!freerdp_shall_disconnect_context(instance->context))
    {
        HANDLE handles[64];
        DWORD nhandles = 0;

        /* FreeRDP event handles (network I/O, channels) */
        nhandles = freerdp_get_event_handles(instance->context, handles, ARRAYSIZE(handles));
        if (nhandles == 0)
        {
            LOGE("freerdp_get_event_handles failed");
            break;
        }

        /* Our input event queue handle */
        HANDLE input_handle = mrdp_event_queue_handle(ctx->event_queue);
        if (input_handle)
            handles[nhandles++] = input_handle;

        /* Wait for any event */
        DWORD wait_status = WaitForMultipleObjects(nhandles, handles, FALSE, 100);
        if (wait_status == WAIT_FAILED)
        {
            LOGE("WaitForMultipleObjects failed");
            break;
        }

        /* Process FreeRDP protocol events */
        if (!freerdp_check_event_handles(instance->context))
        {
            if (freerdp_get_last_error(instance->context) == FREERDP_ERROR_SUCCESS)
                LOGI("Session ended gracefully");
            else
                LOGE("freerdp_check_event_handles failed, error: 0x%08x",
                     (unsigned)freerdp_get_last_error(instance->context));
            break;
        }

        /* Process our input events */
        if (!mrdp_check_input_events(instance))
        {
            LOGI("Disconnect requested by user");
            break;
        }
    }

    /* Clean up */
    ctx->is_connected = FALSE;
    freerdp_disconnect(instance);

    LOGI("RDP thread exiting");
    mrdp_callback("onNativeDisconnected", "(J)V", (jlong)instance);

    return 0;
}

/* ================================================================
 * Client Entry — FreeRDP client plugin registration
 * ================================================================ */

static BOOL mrdp_client_new(freerdp* instance, rdpContext* context)
{
    mrdpContext* ctx = (mrdpContext*)context;

    instance->PreConnect = mrdp_pre_connect;
    instance->PostConnect = mrdp_post_connect;
    instance->PostDisconnect = mrdp_post_disconnect;

    ctx->event_queue = mrdp_event_queue_new();
    if (!ctx->event_queue)
        return FALSE;

    return TRUE;
}

static void mrdp_client_free(freerdp* instance, rdpContext* context)
{
    mrdpContext* ctx = (mrdpContext*)context;
    if (ctx)
    {
        mrdp_event_queue_free(ctx->event_queue);
        ctx->event_queue = NULL;
    }
}

static int mrdp_client_start(rdpContext* context)
{
    return 0; /* Thread is started externally */
}

static int mrdp_client_stop(rdpContext* context)
{
    return 0;
}

static int RdpClientEntry(RDP_CLIENT_ENTRY_POINTS* entry_points)
{
    ZeroMemory(entry_points, sizeof(RDP_CLIENT_ENTRY_POINTS));
    entry_points->Version = RDP_CLIENT_INTERFACE_VERSION;
    entry_points->Size = sizeof(RDP_CLIENT_ENTRY_POINTS);
    entry_points->ContextSize = sizeof(mrdpContext);
    entry_points->ClientNew = mrdp_client_new;
    entry_points->ClientFree = mrdp_client_free;
    entry_points->ClientStart = mrdp_client_start;
    entry_points->ClientStop = mrdp_client_stop;
    return 0;
}

/* ================================================================
 * JNI_OnLoad — initialize callback environment
 * ================================================================ */

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    if (mrdp_callback_init(vm, env) != 0)
        return JNI_ERR;

    LOGI("ModernRDP JNI loaded (FreeRDP %s)", freerdp_get_version_string());
    return JNI_VERSION_1_6;
}

/* ================================================================
 * JNI Exported Methods — called from Kotlin FreeRdpBridge
 * ================================================================ */

/*
 * Create a new FreeRDP instance. Returns a handle (long) to the instance.
 */
JNIEXPORT jlong JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeNew(JNIEnv* env, jobject thiz, jobject context)
{
    /* Set HOME directory for FreeRDP cert cache etc. */
    jclass context_class = (*env)->GetObjectClass(env, context);
    jmethodID get_files_dir = (*env)->GetMethodID(env, context_class, "getFilesDir", "()Ljava/io/File;");
    jobject files_dir = (*env)->CallObjectMethod(env, context, get_files_dir);

    jclass file_class = (*env)->GetObjectClass(env, files_dir);
    jmethodID get_path = (*env)->GetMethodID(env, file_class, "getAbsolutePath", "()Ljava/lang/String;");
    jstring path = (jstring)(*env)->CallObjectMethod(env, files_dir, get_path);

    const char* home_path = (*env)->GetStringUTFChars(env, path, NULL);
    setenv("HOME", home_path, 1);
    LOGI("HOME set to: %s", home_path);
    (*env)->ReleaseStringUTFChars(env, path, home_path);

    /* Create FreeRDP client context */
    RDP_CLIENT_ENTRY_POINTS entry_points;
    RdpClientEntry(&entry_points);

    rdpContext* ctx = freerdp_client_context_new(&entry_points);
    if (!ctx)
    {
        LOGE("freerdp_client_context_new failed");
        return 0;
    }

    return (jlong)ctx->instance;
}

/*
 * Free a FreeRDP instance and all associated resources.
 */
JNIEXPORT void JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeFree(JNIEnv* env, jobject thiz, jlong instance)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return;

    freerdp_client_context_free(inst->context);
}

/*
 * Parse connection arguments (FreeRDP command-line format).
 * args is a String[] like: ["/v:host", "/port:3389", "/u:user", "/size:1920x1080", ...]
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeParseArguments(JNIEnv* env, jobject thiz,
                                                           jlong instance, jobjectArray args)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst || !args)
        return JNI_FALSE;

    int argc = (*env)->GetArrayLength(env, args);
    if (argc <= 0)
        return JNI_FALSE;

    /* Convert Java String[] to C char*[] */
    char** argv = calloc(argc, sizeof(char*));
    if (!argv)
        return JNI_FALSE;

    for (int i = 0; i < argc; i++)
    {
        jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        const char* str = (*env)->GetStringUTFChars(env, jstr, NULL);
        argv[i] = strdup(str);
        (*env)->ReleaseStringUTFChars(env, jstr, str);
        (*env)->DeleteLocalRef(env, jstr);
    }

    /* Parse the command-line into FreeRDP settings */
    int status = freerdp_client_settings_parse_command_line(inst->context->settings,
                                                            argc, argv, FALSE);

    for (int i = 0; i < argc; i++)
        free(argv[i]);
    free(argv);

    return (status == 0) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Start the RDP connection on a background thread.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeConnect(JNIEnv* env, jobject thiz, jlong instance)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return JNI_FALSE;

    mrdpContext* ctx = (mrdpContext*)inst->context;

    /* Launch connection thread */
    ctx->thread = CreateThread(NULL, 0, mrdp_thread_func, inst, 0, NULL);
    if (!ctx->thread)
    {
        LOGE("Failed to create RDP thread");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/*
 * Request disconnection. Pushes a disconnect event to the queue and
 * signals the connection to abort.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeDisconnect(JNIEnv* env, jobject thiz, jlong instance)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return JNI_FALSE;

    mrdpContext* ctx = (mrdpContext*)inst->context;

    /* Push disconnect event */
    MRDP_EVENT* event = mrdp_event_disconnect_new();
    if (event)
        mrdp_event_queue_push(ctx->event_queue, event);

    /* Also signal FreeRDP to abort the connection */
    freerdp_abort_connect_context(inst->context);

    /* Wait for thread to finish */
    if (ctx->thread)
    {
        WaitForSingleObject(ctx->thread, 5000);
        CloseHandle(ctx->thread);
        ctx->thread = NULL;
    }

    return JNI_TRUE;
}

/*
 * Blit pixels from FreeRDP's GDI buffer into an Android Bitmap.
 * Called from Kotlin after receiving an onNativeGraphicsUpdate callback.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeUpdateGraphics(JNIEnv* env, jobject thiz,
                                                           jlong instance, jobject bitmap,
                                                           jint x, jint y, jint width, jint height)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst || !bitmap)
        return JNI_FALSE;

    rdpGdi* gdi = inst->context->gdi;
    if (!gdi || !gdi->primary_buffer)
        return JNI_FALSE;

    AndroidBitmapInfo info;
    void* pixels;

    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS)
        return JNI_FALSE;

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS)
        return JNI_FALSE;

    /* Determine destination pixel format from Android bitmap format */
    UINT32 dst_format;
    switch (info.format)
    {
        case ANDROID_BITMAP_FORMAT_RGBA_8888:
            dst_format = PIXEL_FORMAT_RGBX32;
            break;
        case ANDROID_BITMAP_FORMAT_RGB_565:
            dst_format = PIXEL_FORMAT_RGB16;
            break;
        default:
            AndroidBitmap_unlockPixels(env, bitmap);
            return JNI_FALSE;
    }

    /* Copy the dirty region from GDI buffer to Android bitmap */
    BOOL result = freerdp_image_copy(
        pixels, dst_format, info.stride, x, y,
        width, height,
        gdi->primary_buffer, gdi->dstFormat, gdi->stride,
        x, y, &gdi->palette, FREERDP_FLIP_NONE);

    AndroidBitmap_unlockPixels(env, bitmap);
    return result ? JNI_TRUE : JNI_FALSE;
}

/*
 * Send a mouse/touch event. Flags follow FreeRDP PTR_FLAGS constants.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeSendCursorEvent(JNIEnv* env, jobject thiz,
                                                            jlong instance,
                                                            jint x, jint y, jint flags)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return JNI_FALSE;

    mrdpContext* ctx = (mrdpContext*)inst->context;
    MRDP_EVENT_CURSOR* event = mrdp_event_cursor_new((UINT16)flags, (UINT16)x, (UINT16)y);
    if (!event)
        return JNI_FALSE;

    return mrdp_event_queue_push(ctx->event_queue, (MRDP_EVENT*)event) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Send a keyboard scancode event.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeSendKeyEvent(JNIEnv* env, jobject thiz,
                                                         jlong instance,
                                                         jint keycode, jboolean down)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return JNI_FALSE;

    mrdpContext* ctx = (mrdpContext*)inst->context;
    int flags = down ? KBD_FLAGS_DOWN : KBD_FLAGS_RELEASE;
    MRDP_EVENT_KEY* event = mrdp_event_key_new(flags, (UINT16)keycode);
    if (!event)
        return JNI_FALSE;

    return mrdp_event_queue_push(ctx->event_queue, (MRDP_EVENT*)event) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Send a Unicode character event.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeSendUnicodeKeyEvent(JNIEnv* env, jobject thiz,
                                                                jlong instance,
                                                                jint code, jboolean down)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return JNI_FALSE;

    mrdpContext* ctx = (mrdpContext*)inst->context;
    UINT16 flags = down ? 0 : KBD_FLAGS_RELEASE;
    MRDP_EVENT_UNICODE_KEY* event = mrdp_event_unicode_key_new(flags, (UINT16)code);
    if (!event)
        return JNI_FALSE;

    return mrdp_event_queue_push(ctx->event_queue, (MRDP_EVENT*)event) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Send clipboard text to the remote session.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeSendClipboardData(JNIEnv* env, jobject thiz,
                                                              jlong instance, jstring jdata)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst || !jdata)
        return JNI_FALSE;

    mrdpContext* ctx = (mrdpContext*)inst->context;
    const char* data = (*env)->GetStringUTFChars(env, jdata, NULL);
    size_t length = strlen(data);

    MRDP_EVENT_CLIPBOARD* event = mrdp_event_clipboard_new(data, length);
    (*env)->ReleaseStringUTFChars(env, jdata, data);

    if (!event)
        return JNI_FALSE;

    return mrdp_event_queue_push(ctx->event_queue, (MRDP_EVENT*)event) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Get FreeRDP version string.
 */
JNIEXPORT jstring JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeGetVersion(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, freerdp_get_version_string());
}

/*
 * Get FreeRDP build configuration info.
 */
JNIEXPORT jstring JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeGetBuildConfig(JNIEnv* env, jobject thiz)
{
    return (*env)->NewStringUTF(env, freerdp_get_build_config());
}

/*
 * Check if H.264 codec support is available.
 */
JNIEXPORT jboolean JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeHasH264(JNIEnv* env, jobject thiz)
{
    return freerdp_settings_get_bool(NULL, FreeRDP_SupportGraphicsPipeline) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Get the last error as a human-readable string.
 */
JNIEXPORT jstring JNICALL
Java_com_modernrdp_rdp_FreeRdpBridge_nativeGetLastError(JNIEnv* env, jobject thiz, jlong instance)
{
    freerdp* inst = (freerdp*)instance;
    if (!inst)
        return (*env)->NewStringUTF(env, "Invalid instance");

    UINT32 error = freerdp_get_last_error(inst->context);
    const char* error_str = freerdp_get_last_error_string(error);
    return (*env)->NewStringUTF(env, error_str ? error_str : "Unknown error");
}
