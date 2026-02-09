#include "modernrdp_jni.h"

#include <stdlib.h>
#include <string.h>
#include <winpr/synch.h>

#define EVENT_QUEUE_INITIAL_CAPACITY 16

MRDP_EVENT_QUEUE* mrdp_event_queue_new(void)
{
    MRDP_EVENT_QUEUE* queue = calloc(1, sizeof(MRDP_EVENT_QUEUE));
    if (!queue)
        return NULL;

    queue->capacity = EVENT_QUEUE_INITIAL_CAPACITY;
    queue->events = calloc(queue->capacity, sizeof(MRDP_EVENT*));
    if (!queue->events)
    {
        free(queue);
        return NULL;
    }

    queue->event_handle = CreateEvent(NULL, TRUE, FALSE, NULL);
    if (!queue->event_handle)
    {
        free(queue->events);
        free(queue);
        return NULL;
    }

    InitializeCriticalSection(&queue->lock);
    return queue;
}

void mrdp_event_queue_free(MRDP_EVENT_QUEUE* queue)
{
    if (!queue)
        return;

    EnterCriticalSection(&queue->lock);
    for (int i = 0; i < queue->count; i++)
        mrdp_event_free(queue->events[i]);
    free(queue->events);
    LeaveCriticalSection(&queue->lock);

    CloseHandle(queue->event_handle);
    DeleteCriticalSection(&queue->lock);
    free(queue);
}

BOOL mrdp_event_queue_push(MRDP_EVENT_QUEUE* queue, MRDP_EVENT* event)
{
    if (!queue || !event)
        return FALSE;

    EnterCriticalSection(&queue->lock);

    if (queue->count >= queue->capacity)
    {
        int new_cap = queue->capacity * 2;
        MRDP_EVENT** new_events = realloc(queue->events, new_cap * sizeof(MRDP_EVENT*));
        if (!new_events)
        {
            LeaveCriticalSection(&queue->lock);
            return FALSE;
        }
        queue->events = new_events;
        queue->capacity = new_cap;
    }

    queue->events[queue->count++] = event;
    SetEvent(queue->event_handle);
    LeaveCriticalSection(&queue->lock);
    return TRUE;
}

MRDP_EVENT* mrdp_event_queue_pop(MRDP_EVENT_QUEUE* queue)
{
    MRDP_EVENT* event = NULL;

    if (!queue)
        return NULL;

    EnterCriticalSection(&queue->lock);

    if (queue->count > 0)
    {
        event = queue->events[0];
        queue->count--;
        if (queue->count > 0)
            memmove(queue->events, queue->events + 1, queue->count * sizeof(MRDP_EVENT*));
        else
            ResetEvent(queue->event_handle);
    }

    LeaveCriticalSection(&queue->lock);
    return event;
}

HANDLE mrdp_event_queue_handle(MRDP_EVENT_QUEUE* queue)
{
    if (!queue)
        return NULL;
    return queue->event_handle;
}

/* Event constructors */

MRDP_EVENT_KEY* mrdp_event_key_new(int flags, UINT16 scancode)
{
    MRDP_EVENT_KEY* event = calloc(1, sizeof(MRDP_EVENT_KEY));
    if (!event)
        return NULL;
    event->type = MRDP_EVENT_TYPE_KEY;
    event->flags = flags;
    event->scancode = scancode;
    return event;
}

MRDP_EVENT_UNICODE_KEY* mrdp_event_unicode_key_new(UINT16 flags, UINT16 code)
{
    MRDP_EVENT_UNICODE_KEY* event = calloc(1, sizeof(MRDP_EVENT_UNICODE_KEY));
    if (!event)
        return NULL;
    event->type = MRDP_EVENT_TYPE_UNICODE_KEY;
    event->flags = flags;
    event->code = code;
    return event;
}

MRDP_EVENT_CURSOR* mrdp_event_cursor_new(UINT16 flags, UINT16 x, UINT16 y)
{
    MRDP_EVENT_CURSOR* event = calloc(1, sizeof(MRDP_EVENT_CURSOR));
    if (!event)
        return NULL;
    event->type = MRDP_EVENT_TYPE_CURSOR;
    event->flags = flags;
    event->x = x;
    event->y = y;
    return event;
}

MRDP_EVENT* mrdp_event_disconnect_new(void)
{
    MRDP_EVENT* event = calloc(1, sizeof(MRDP_EVENT));
    if (!event)
        return NULL;
    event->type = MRDP_EVENT_TYPE_DISCONNECT;
    return event;
}

MRDP_EVENT_CLIPBOARD* mrdp_event_clipboard_new(const char* data, size_t length)
{
    MRDP_EVENT_CLIPBOARD* event = calloc(1, sizeof(MRDP_EVENT_CLIPBOARD));
    if (!event)
        return NULL;
    event->type = MRDP_EVENT_TYPE_CLIPBOARD;
    if (data && length > 0)
    {
        event->data = malloc(length + 1);
        if (!event->data)
        {
            free(event);
            return NULL;
        }
        memcpy(event->data, data, length);
        event->data[length] = '\0';
        event->length = length;
    }
    return event;
}

void mrdp_event_free(MRDP_EVENT* event)
{
    if (!event)
        return;

    if (event->type == MRDP_EVENT_TYPE_CLIPBOARD)
    {
        MRDP_EVENT_CLIPBOARD* clip = (MRDP_EVENT_CLIPBOARD*)event;
        free(clip->data);
    }

    free(event);
}
