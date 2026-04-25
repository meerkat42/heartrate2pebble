/*
 * Copyright (C) 2026 meerkat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 *
 * bpm_display.c
 *
 * Receives a BPM integer from an Android app via AppMessage and displays it
 * as large as possible on the watch face, with "BPM" in smaller dark-grey
 * text. Background is black; number is light red.
 * A status label at the bottom shows "Connected" / "Disconnected" depending
 * on whether a BPM message has arrived within the label timeout.  A separate
 * vibration timer fires independently when the vibration timeout elapses.
 * Both timeouts are configurable from the Android app and are persisted across
 * restarts.
 *
 * Build target: all platforms (Aplite, Basalt, Chalk, Diorite, Emery, Gabbro)
 */

#include <pebble.h>

/* ── Persistent-storage keys ────────────────────────────────────────────── */
#define PERSIST_KEY_LABEL_TIMEOUT   1
#define PERSIST_KEY_VIBRATE_TIMEOUT 2

/* ── Colors ────────────────────────────────────────────────────────────── */
#define COLOR_BACKGROUND          PBL_IF_COLOR_ELSE(GColorBlack, GColorWhite)
#define COLOR_BPM_NUMBER          PBL_IF_COLOR_ELSE(GColorRed, GColorBlack)    
#define COLOR_BPM_LABEL           PBL_IF_COLOR_ELSE(GColorDarkGray, GColorBlack)
#define COLOR_STATUS_CONNECTED    PBL_IF_COLOR_ELSE(GColorGreen, GColorBlack)
#define COLOR_STATUS_DISCONNECTED PBL_IF_COLOR_ELSE(GColorDarkGray, GColorBlack)

/* ═══════════════════════════════════════════════════════════════════════
 * PADDING — edit CONTENT_PADDING_PX to add space around the number and
 * "BPM" label as a group.  This single value is the inset from each edge
 * of the display (top, bottom, left, right) in pixels.
 *
 *   0  = text fills the whole screen (maximum size)
 *   8  = comfortable breathing room  (default)
 *   16 = noticeable border
 *   20 = generous frame
 *
 * On Chalk (round display) this inset is ADDED on top of the extra
 * circular-edge inset the layout already applies.
 * ═══════════════════════════════════════════════════════════════════════ */
#define CONTENT_PADDING_PX     8
#define NUMBERS_FONT           RESOURCE_ID_FONT_ROBOTO_BLACK_70
#define HEIGHT_OF_HEARTRATE    70
#define HEIGHT_OF_BPM_LABEL    18
#define HEIGHT_OF_STATUS_LABEL 18

/* ── Default timeouts (milliseconds) ────────────────────────────────────── */
#define DEFAULT_LABEL_TIMEOUT_MS   5000
#define DEFAULT_VIBRATE_TIMEOUT_MS 20000

static GFont s_font_number;
static GFont s_font_label;

/* ── Layers ─────────────────────────────────────────────────────────────── */
static Window    *s_window;
static TextLayer *s_number_layer;
static TextLayer *s_label_layer;
static TextLayer *s_status_layer;

/* ── State ─────────────────────────────────────────────────────────────── */
static char      s_bpm_buffer[8];   /* "---" on startup, up to "999\0" */
static int       s_current_bpm  = -1;
static bool      s_is_connected = false;

/* ── Configurable timeouts (loaded from storage, updated via AppMessage) ── */
static uint32_t  s_label_timeout_ms   = DEFAULT_LABEL_TIMEOUT_MS;
static uint32_t  s_vibrate_timeout_ms = DEFAULT_VIBRATE_TIMEOUT_MS;

/* ── Independent disconnect timers ──────────────────────────────────────── */
static AppTimer *s_label_timer   = NULL;   /* fires → change label to Disconnected */
static AppTimer *s_vibrate_timer = NULL;   /* fires → vibrate the watch            */

/* ────────────────────────────────────────────────────────────────────────
 * Settings persistence
 * ──────────────────────────────────────────────────────────────────────── */
static void prv_load_settings(void) {
    if (persist_exists(PERSIST_KEY_LABEL_TIMEOUT)) {
        s_label_timeout_ms = (uint32_t)persist_read_int(PERSIST_KEY_LABEL_TIMEOUT);
    }
    if (persist_exists(PERSIST_KEY_VIBRATE_TIMEOUT)) {
        s_vibrate_timeout_ms = (uint32_t)persist_read_int(PERSIST_KEY_VIBRATE_TIMEOUT);
    }
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_load_settings: label=%ums vibrate=%ums",
            (unsigned)s_label_timeout_ms, (unsigned)s_vibrate_timeout_ms);
}

static void prv_save_settings(void) {
    persist_write_int(PERSIST_KEY_LABEL_TIMEOUT,   (int32_t)s_label_timeout_ms);
    persist_write_int(PERSIST_KEY_VIBRATE_TIMEOUT, (int32_t)s_vibrate_timeout_ms);
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_save_settings: label=%ums vibrate=%ums",
            (unsigned)s_label_timeout_ms, (unsigned)s_vibrate_timeout_ms);
}

/* ────────────────────────────────────────────────────────────────────────
 * Timer callbacks and reset helper
 * ──────────────────────────────────────────────────────────────────────── */

/* Fires when no BPM message has arrived within s_label_timeout_ms. */
static void prv_label_timer_callback(void *context) {
    s_label_timer   = NULL;
    s_is_connected  = false;
    text_layer_set_text(s_status_layer, "Disconnected");
    text_layer_set_text_color(s_status_layer, COLOR_STATUS_DISCONNECTED);
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_label_timer_callback: label set to Disconnected");
}

/* Fires when no BPM message has arrived within s_vibrate_timeout_ms. */
static void prv_vibrate_timer_callback(void *context) {
    s_vibrate_timer = NULL;
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_vibrate_timer_callback: vibrating");
    static const uint32_t segments[] = { 200, 100, 200, 100, 200 };
    VibePattern pat = {
        .durations    = segments,
        .num_segments = ARRAY_LENGTH(segments),
    };
    vibes_enqueue_custom_pattern(pat);
}

/*
 * Called on every incoming BPM message.
 * Cancels any running timers, arms fresh ones, and flips the label back to
 * "Connected" if it was showing "Disconnected".
 */
static void prv_reset_timers(void) {
    if (s_label_timer)   { app_timer_cancel(s_label_timer);   s_label_timer   = NULL; }
    if (s_vibrate_timer) { app_timer_cancel(s_vibrate_timer); s_vibrate_timer = NULL; }

    s_label_timer   = app_timer_register(s_label_timeout_ms,   prv_label_timer_callback,   NULL);
    s_vibrate_timer = app_timer_register(s_vibrate_timeout_ms, prv_vibrate_timer_callback, NULL);

    if (!s_is_connected) {
        s_is_connected = true;
        text_layer_set_text(s_status_layer, "Connected");
        text_layer_set_text_color(s_status_layer, COLOR_STATUS_CONNECTED);
        APP_LOG(APP_LOG_LEVEL_INFO, "prv_reset_timers: label set to Connected");
    }
}

/* ────────────────────────────────────────────────────────────────────────
 * Layout helper — called once on load.
 * Applies CONTENT_PADDING_PX around the combined content area so you
 * can tune the buffer without touching anything else.
 * ──────────────────────────────────────────────────────────────────────── */
static void prv_layout_layers(GRect bounds) {
    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_layout_layers: begin");

    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_layout_layers: display bounds %dx%d",
            bounds.size.w, bounds.size.h);

    /* ── Per-platform font and base inset ─────────────────────────────── */
    int edge_inset = CONTENT_PADDING_PX;
    #if defined(PBL_PLATFORM_CHALK)
        /*
        * Chalk: 180 × 180 round display.
        * Apply a fixed 12 px circular-edge inset first, then add the
        * user-configurable CONTENT_PADDING_PX on top of it.
        */
        APP_LOG(APP_LOG_LEVEL_INFO, "prv_layout_layers: platform=Chalk (round), edge_inset=%d",
                12 + CONTENT_PADDING_PX);
        edge_inset = 12 + edge_inset;
    #endif

    GRect usable = grect_inset(bounds, GEdgeInsets(edge_inset));
    s_font_number = fonts_load_custom_font(resource_get_handle(NUMBERS_FONT));
    s_font_label  = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);

    /*
     * Heartrate is centered, "BPM" label is at the top, status at bottom.
     */

    /* Number layer — vertically centered */
    GRect number_frame = GRect(
        usable.origin.x/2,
        usable.origin.y/2 + HEIGHT_OF_HEARTRATE/2,
        usable.size.w,
        HEIGHT_OF_HEARTRATE
    );
    text_layer_set_font(s_number_layer, s_font_number);
    layer_set_frame(text_layer_get_layer(s_number_layer), number_frame);

    /* "BPM" label layer — top of usable area */
    GRect label_frame = GRect(
        usable.origin.x,
        usable.origin.y,
        usable.size.w,
        HEIGHT_OF_BPM_LABEL
    );
    text_layer_set_font(s_label_layer, s_font_label);
    layer_set_frame(text_layer_get_layer(s_label_layer), label_frame);

    /* Connection status label — anchored to bottom of usable area */
    GRect status_frame = GRect(
        usable.origin.x,
        usable.origin.y + usable.size.h - HEIGHT_OF_STATUS_LABEL,
        usable.size.w,
        HEIGHT_OF_STATUS_LABEL
    );
    text_layer_set_font(s_status_layer, s_font_label);
    layer_set_frame(text_layer_get_layer(s_status_layer), status_frame);

    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_layout_layers: done");
}

/* ────────────────────────────────────────────────────────────────────────
 * Window lifecycle
 * ──────────────────────────────────────────────────────────────────────── */
static void prv_window_load(Window *window) {
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_window_load: begin");
    Layer *root = window_get_root_layer(window);
    GRect bounds = layer_get_bounds(root);

    window_set_background_color(window, COLOR_BACKGROUND);

    /* Number text layer (temporarily full size; layout() refines it) */
    s_number_layer = text_layer_create(bounds);
    text_layer_set_background_color(s_number_layer, GColorClear);
    text_layer_set_text_color(s_number_layer, COLOR_BPM_NUMBER);
    text_layer_set_text_alignment(s_number_layer, GTextAlignmentCenter);
    text_layer_set_overflow_mode(s_number_layer, GTextOverflowModeWordWrap);
    snprintf(s_bpm_buffer, sizeof(s_bpm_buffer), "---");
    text_layer_set_text(s_number_layer, s_bpm_buffer);
    layer_add_child(root, text_layer_get_layer(s_number_layer));
    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_window_load: number layer created, initial text=\"---\"");

    /* "BPM" label text layer */
    s_label_layer = text_layer_create(bounds);
    text_layer_set_background_color(s_label_layer, GColorClear);
    text_layer_set_text_color(s_label_layer, COLOR_BPM_LABEL);
    text_layer_set_text_alignment(s_label_layer, GTextAlignmentCenter);
    text_layer_set_text(s_label_layer, "BPM");
    layer_add_child(root, text_layer_get_layer(s_label_layer));
    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_window_load: BPM label layer created");

    /* Connection status label — starts Disconnected (no vibration on boot) */
    s_status_layer = text_layer_create(bounds);
    text_layer_set_background_color(s_status_layer, GColorClear);
    text_layer_set_text_color(s_status_layer, COLOR_STATUS_DISCONNECTED);
    text_layer_set_text_alignment(s_status_layer, GTextAlignmentCenter);
    text_layer_set_text(s_status_layer, "Disconnected");
    layer_add_child(root, text_layer_get_layer(s_status_layer));
    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_window_load: status label layer created");

    /* Apply platform-aware sizing and padding */
    prv_layout_layers(bounds);
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_window_load: done");
}

static void prv_window_unload(Window *window) {
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_window_unload: destroying layers");
    if (s_label_timer)   { app_timer_cancel(s_label_timer);   s_label_timer   = NULL; }
    if (s_vibrate_timer) { app_timer_cancel(s_vibrate_timer); s_vibrate_timer = NULL; }
    text_layer_destroy(s_number_layer);
    fonts_unload_custom_font(s_font_number);
    text_layer_destroy(s_label_layer);
    text_layer_destroy(s_status_layer);
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_window_unload: done");
}

/* ────────────────────────────────────────────────────────────────────────
 * AppMessage callbacks
 * ──────────────────────────────────────────────────────────────────────── */
static void prv_inbox_received(DictionaryIterator *iter, void *context) {
    APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_inbox_received: message arrived");

    /* ── Configuration keys (sent from the Android config UI) ─────────── */
    bool settings_changed = false;

    Tuple *label_t = dict_find(iter, MESSAGE_KEY_Disconnected_Label_Timeout);
    if (label_t && label_t->value->int32 > 0) {
        s_label_timeout_ms = (uint32_t)label_t->value->int32;
        settings_changed = true;
        APP_LOG(APP_LOG_LEVEL_INFO, "prv_inbox_received: label_timeout=%ums",
                (unsigned)s_label_timeout_ms);
    }

    Tuple *vibrate_t = dict_find(iter, MESSAGE_KEY_Disconnected_Vibrate_Timeout);
    if (vibrate_t && vibrate_t->value->int32 > 0) {
        s_vibrate_timeout_ms = (uint32_t)vibrate_t->value->int32;
        settings_changed = true;
        APP_LOG(APP_LOG_LEVEL_INFO, "prv_inbox_received: vibrate_timeout=%ums",
                (unsigned)s_vibrate_timeout_ms);
    }

    if (settings_changed) {
        prv_save_settings();
    }

    /* ── BPM data ──────────────────────────────────────────────────────── */
    Tuple *bpm_tuple = dict_find(iter, MESSAGE_KEY_BPM);
    if (bpm_tuple) {
        int bpm = (int)bpm_tuple->value->int32;
        APP_LOG(APP_LOG_LEVEL_INFO, "prv_inbox_received: BPM=%d (previous=%d)", bpm, s_current_bpm);

        /* Mark as connected and reset both disconnect timers */
        prv_reset_timers();

        if (bpm != s_current_bpm) {
            s_current_bpm = bpm;
            snprintf(s_bpm_buffer, sizeof(s_bpm_buffer), "%d", bpm);
            text_layer_set_text(s_number_layer, s_bpm_buffer);
            APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_inbox_received: display updated to \"%s\"", s_bpm_buffer);
        } else {
            APP_LOG(APP_LOG_LEVEL_DEBUG, "prv_inbox_received: BPM unchanged, skipping update");
        }
    } else if (!settings_changed) {
        APP_LOG(APP_LOG_LEVEL_WARNING, "prv_inbox_received: no recognised keys in message");
    }
}

/* ────────────────────────────────────────────────────────────────────────
 * App entry point
 * ──────────────────────────────────────────────────────────────────────── */
static void prv_init(void) {
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_init: begin");
    prv_load_settings();
    s_window = window_create();
    window_set_window_handlers(s_window, (WindowHandlers){
        .load   = prv_window_load,
        .unload = prv_window_unload,
    });
    window_stack_push(s_window, true /* animated */);
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_init: window pushed, init done");
}

static void prv_deinit(void) {
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_deinit: destroying window");
    window_destroy(s_window);
    APP_LOG(APP_LOG_LEVEL_INFO, "prv_deinit: done");
}

int main(void) {
    APP_LOG(APP_LOG_LEVEL_INFO, "main: app starting");

    prv_init();
    app_message_open(128, 64);
    app_message_register_inbox_received(prv_inbox_received);
    APP_LOG(APP_LOG_LEVEL_DEBUG, "main: AppMessage opened (inbox=128, outbox=64)");

    app_event_loop();
    prv_deinit();
    APP_LOG(APP_LOG_LEVEL_INFO, "main: app exiting");
}
