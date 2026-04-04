#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "esp_bit_defs.h"
#include "esp_err.h"

typedef enum {
    STATE_MON_FLAG_ECG_LEADS_OFF_ANY = BIT0,
    STATE_MON_FLAG_ECG_LO_PLUS       = BIT1,
    STATE_MON_FLAG_ECG_LO_MINUS      = BIT2,
    STATE_MON_FLAG_PPG_FIFO_OVERFLOW = BIT3,
    STATE_MON_FLAG_PPG_INT_TIMEOUT   = BIT4,
    STATE_MON_FLAG_ADC_SATURATED     = BIT5,
    STATE_MON_FLAG_BLE_BACKPRESSURE  = BIT6,
    STATE_MON_FLAG_SENSOR_READY      = BIT7,
    STATE_MON_FLAG_FINGER_DETECTED   = BIT8,
    STATE_MON_FLAG_SYNC_MARK         = BIT9,
} state_monitor_flag_t;

typedef enum {
    STATE_MON_SELF_TEST_TIMEBASE        = BIT0,
    STATE_MON_SELF_TEST_AD8232_GPIO     = BIT1,
    STATE_MON_SELF_TEST_MAX30102_I2C    = BIT2,
    STATE_MON_SELF_TEST_MAX30102_CONFIG = BIT3,
    STATE_MON_SELF_TEST_BLE_STACK       = BIT4,
} state_monitor_self_test_bit_t;

typedef struct {
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
    bool streaming;
} state_monitor_snapshot_t;

esp_err_t state_monitor_init(void);
void state_monitor_reset_stream_counters(void);
void state_monitor_note_self_test(uint32_t bit, bool pass);
void state_monitor_update_lead_off(bool lo_plus, bool lo_minus);
void state_monitor_update_sensor_ready(bool ready);
void state_monitor_update_finger_detected(bool detected);
void state_monitor_note_sync_mark(void);
void state_monitor_set_streaming(bool streaming);
void state_monitor_note_ppg_fifo_overflow(uint32_t dropped_samples);
void state_monitor_note_ppg_int_timeout(void);
void state_monitor_note_i2c_error(void);
void state_monitor_note_ble_backpressure(void);
void state_monitor_note_ble_frame_dropped(void);
void state_monitor_note_adc_saturation(void);
void state_monitor_note_ecg_ring_drop(void);
void state_monitor_note_ppg_ring_drop(void);
void state_monitor_note_frame_generated(uint16_t frame_id);
void state_monitor_note_frame_transmitted(uint16_t frame_id);
void state_monitor_update_buffer_levels(uint16_t ecg_items, uint16_t ppg_items, uint16_t ble_items);
uint16_t state_monitor_take_frame_flags(void);
void state_monitor_get_snapshot(state_monitor_snapshot_t *out_snapshot);
