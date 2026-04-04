#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"

typedef enum {
    BLE_CONTROL_OP_START_STREAM    = 0x01,
    BLE_CONTROL_OP_STOP_STREAM     = 0x02,
    BLE_CONTROL_OP_SET_LED_PA      = 0x10,
    BLE_CONTROL_OP_SET_PPG_PHASE   = 0x11,
    BLE_CONTROL_OP_SET_PPG_LATENCY = 0x12,
    BLE_CONTROL_OP_SET_TEMP_EN     = 0x13,
    BLE_CONTROL_OP_SET_LOG_LEVEL   = 0x14,
    BLE_CONTROL_OP_SET_PPG_MODE    = 0x15,
    BLE_CONTROL_OP_GET_INFO        = 0x20,
    BLE_CONTROL_OP_SELF_TEST       = 0x21,
    BLE_CONTROL_OP_SYNC_MARK       = 0x22,
} ble_control_opcode_t;

typedef struct __attribute__((packed)) {
    uint8_t protocol_version;
    uint8_t streaming_enabled;
    uint8_t red_led_pa;
    uint8_t ir_led_pa;
    int32_t ppg_phase_us;
    int32_t ppg_latency_us;
    uint8_t temperature_enabled;
    uint8_t log_level;
    uint8_t ppg_int_mode;
} ble_control_snapshot_t;

typedef struct __attribute__((packed)) {
    uint8_t protocol_version;
    uint8_t streaming_enabled;
    uint16_t state_flags;
    uint32_t self_test_pass_bitmap;
    uint32_t self_test_fail_bitmap;
    uint32_t ppg_fifo_overflow_count;
    uint32_t ppg_int_timeout_count;
    uint32_t ble_backpressure_count;
    uint32_t ble_dropped_frame_count;
    uint32_t i2c_error_count;
    uint32_t adc_saturation_count;
    uint32_t ecg_ring_drop_count;
    uint32_t ppg_ring_drop_count;
    uint32_t generated_frame_count;
    uint32_t transmitted_frame_count;
    uint32_t frame_sequence_error_count;
    uint16_t ecg_ring_items;
    uint16_t ppg_ring_items;
    uint16_t ble_queue_items;
    uint16_t ecg_ring_high_watermark;
    uint16_t ppg_ring_high_watermark;
    uint16_t ble_queue_high_watermark;
    uint16_t mtu;
    uint8_t red_led_pa;
    uint8_t ir_led_pa;
    int32_t ppg_phase_us;
    int32_t ppg_latency_us;
    uint8_t temperature_enabled;
    uint8_t log_level;
    uint8_t sensor_ready;
    uint8_t finger_detected;
} ble_status_snapshot_t;

typedef struct {
    ble_control_opcode_t opcode;
    union {
        struct {
            uint8_t red_led_pa;
            uint8_t ir_led_pa;
        } led;
        int32_t i32;
        uint8_t u8;
        bool boolean;
    } value;
} ble_control_event_t;

typedef esp_err_t (*ble_service_fill_control_cb_t)(ble_control_snapshot_t *out_snapshot);
typedef esp_err_t (*ble_service_fill_status_cb_t)(ble_status_snapshot_t *out_snapshot);
typedef void (*ble_service_connection_cb_t)(bool connected, uint16_t mtu);

typedef struct {
    QueueHandle_t control_queue;
    ble_service_fill_control_cb_t fill_control_snapshot;
    ble_service_fill_status_cb_t fill_status_snapshot;
    ble_service_connection_cb_t connection_cb;
} ble_service_config_t;

esp_err_t ble_service_init(const ble_service_config_t *config);
bool ble_service_is_connected(void);
bool ble_service_is_data_notify_enabled(void);
bool ble_service_is_status_notify_enabled(void);
uint16_t ble_service_get_att_payload_mtu(void);
esp_err_t ble_service_notify_data_segment(const uint8_t *data, size_t length);
esp_err_t ble_service_notify_status(const ble_status_snapshot_t *status);
