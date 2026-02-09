#ifndef MODERNRDP_JNI_H
#define MODERNRDP_JNI_H

#include <jni.h>
#include <freerdp/freerdp.h>
#include <freerdp/client/rdpei.h>
#include <freerdp/client/cliprdr.h>
#include <freerdp/gdi/gdi.h>
#include <winpr/synch.h>
#include <winpr/thread.h>
#include <winpr/collections.h>

/* Event types for the input queue */
#define MRDP_EVENT_TYPE_KEY          1
#define MRDP_EVENT_TYPE_CURSOR       2
#define MRDP_EVENT_TYPE_DISCONNECT   3
#define MRDP_EVENT_TYPE_UNICODE_KEY  4
#define MRDP_EVENT_TYPE_CLIPBOARD    5

/* Base event */
typedef struct {
    int type;
} MRDP_EVENT;

/* Key event */
typedef struct {
    int type;
    int flags;
    UINT16 scancode;
} MRDP_EVENT_KEY;

/* Unicode key event */
typedef struct {
    int type;
    UINT16 flags;
    UINT16 code;
} MRDP_EVENT_UNICODE_KEY;

/* Cursor/mouse event */
typedef struct {
    int type;
    UINT16 flags;
    UINT16 x;
    UINT16 y;
} MRDP_EVENT_CURSOR;

/* Clipboard event */
typedef struct {
    int type;
    char* data;
    size_t length;
} MRDP_EVENT_CLIPBOARD;

/* Event queue */
typedef struct {
    int count;
    int capacity;
    MRDP_EVENT** events;
    HANDLE event_handle;
    CRITICAL_SECTION lock;
} MRDP_EVENT_QUEUE;

/* Extended Android context — extends rdpClientContext */
typedef struct {
    rdpClientContext common;

    MRDP_EVENT_QUEUE* event_queue;
    HANDLE thread;

    BOOL is_connected;

    /* Clipboard state */
    BOOL clipboard_sync;
    wClipboard* clipboard;
    UINT32 num_server_formats;
    UINT32 requested_format_id;
    HANDLE clipboard_request_event;
    CLIPRDR_FORMAT* server_formats;
    CliprdrClientContext* cliprdr;
    UINT32 clipboard_capabilities;
} mrdpContext;

/* JNI class path for callbacks */
#define MRDP_JNI_CLASS "com/modernrdp/rdp/FreeRdpBridge"

/* Event queue functions */
MRDP_EVENT_QUEUE* mrdp_event_queue_new(void);
void mrdp_event_queue_free(MRDP_EVENT_QUEUE* queue);
BOOL mrdp_event_queue_push(MRDP_EVENT_QUEUE* queue, MRDP_EVENT* event);
MRDP_EVENT* mrdp_event_queue_pop(MRDP_EVENT_QUEUE* queue);
HANDLE mrdp_event_queue_handle(MRDP_EVENT_QUEUE* queue);

/* Event constructors */
MRDP_EVENT_KEY* mrdp_event_key_new(int flags, UINT16 scancode);
MRDP_EVENT_UNICODE_KEY* mrdp_event_unicode_key_new(UINT16 flags, UINT16 code);
MRDP_EVENT_CURSOR* mrdp_event_cursor_new(UINT16 flags, UINT16 x, UINT16 y);
MRDP_EVENT* mrdp_event_disconnect_new(void);
MRDP_EVENT_CLIPBOARD* mrdp_event_clipboard_new(const char* data, size_t length);
void mrdp_event_free(MRDP_EVENT* event);

#endif /* MODERNRDP_JNI_H */
