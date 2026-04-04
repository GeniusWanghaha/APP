#include "state_monitor.h"

#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#define STATE_MON_TRANSIENT_FLAGS \
    (STATE_MON_FLAG_PPG_FIFO_OVERFLOW | STATE_MON_FLAG_PPG_INT_TIMEOUT | \
     STATE_MON_FLAG_ADC_SATURATED | STATE_MON_FLAG_BLE_BACKPRESSURE | \
     STATE_MON_FLAG_SYNC_MARK)

typedef struct {
    SemaphoreHandle_t mutex;
    state_monitor_snapshot_t snapshot;
    bool last_frame_valid;
    uint16_t last_frame_id;
} state_monitor_ctx_t;

static state_monitor_ctx_t s_ctx;

static void state_monitor_lock(void)
{
    xSemaphoreTake(s_ctx.mutex, portMAX_DELAY);
}

static void state_monitor_unlock(void)
{
    xSemaphoreGive(s_ctx.mutex);
}

esp_err_t state_monitor_init(void)
{
    memset(&s_ctx, 0, sizeof(s_ctx));
    s_ctx.mutex = xSemaphoreCreateMutex();
    return (s_ctx.mutex != NULL) ? ESP_OK : ESP_ERR_NO_MEM;
}

void state_monitor_reset_stream_counters(void)
{
    state_monitor_lock();
    s_ctx.snapshot.ppg_fifo_overflow_count = 0;
    s_ctx.snapshot.ppg_int_timeout_count = 0;
    s_ctx.snapshot.ble_backpressure_count = 0;
    s_ctx.snapshot.ble_dropped_frame_count = 0;
    s_ctx.snapshot.i2c_error_count = 0;
    s_ctx.snapshot.adc_saturation_count = 0;
    s_ctx.snapshot.ecg_ring_drop_count = 0;
    s_ctx.snapshot.ppg_ring_drop_count = 0;
    s_ctx.snapshot.generated_frame_count = 0;
    s_ctx.snapshot.transmitted_frame_count = 0;
    s_ctx.snapshot.frame_sequence_error_count = 0;
    s_ctx.snapshot.ecg_ring_items = 0;
    s_ctx.snapshot.ppg_ring_items = 0;
    s_ctx.snapshot.ble_queue_items = 0;
    s_ctx.snapshot.ecg_ring_high_watermark = 0;
    s_ctx.snapshot.ppg_ring_high_watermark = 0;
    s_ctx.snapshot.ble_queue_high_watermark = 0;
    s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_TRANSIENT_FLAGS;
    s_ctx.last_frame_valid = false;
    state_monitor_unlock();
}

void state_monitor_note_self_test(uint32_t bit, bool pass)
{
    state_monitor_lock();
    if (pass) {
        s_ctx.snapshot.self_test_pass_bitmap |= bit;
        s_ctx.snapshot.self_test_fail_bitmap &= ~bit;
    } else {
        s_ctx.snapshot.self_test_fail_bitmap |= bit;
        s_ctx.snapshot.self_test_pass_bitmap &= ~bit;
    }
    state_monitor_unlock();
}

void state_monitor_update_lead_off(bool lo_plus, bool lo_minus)
{
    state_monitor_lock();
    if (lo_plus) {
        s_ctx.snapshot.state_flags |= STATE_MON_FLAG_ECG_LO_PLUS;
    } else {
        s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_FLAG_ECG_LO_PLUS;
    }

    if (lo_minus) {
        s_ctx.snapshot.state_flags |= STATE_MON_FLAG_ECG_LO_MINUS;
    } else {
        s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_FLAG_ECG_LO_MINUS;
    }

    if (lo_plus || lo_minus) {
        s_ctx.snapshot.state_flags |= STATE_MON_FLAG_ECG_LEADS_OFF_ANY;
    } else {
        s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_FLAG_ECG_LEADS_OFF_ANY;
    }
    state_monitor_unlock();
}

void state_monitor_update_sensor_ready(bool ready)
{
    state_monitor_lock();
    if (ready) {
        s_ctx.snapshot.state_flags |= STATE_MON_FLAG_SENSOR_READY;
    } else {
        s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_FLAG_SENSOR_READY;
    }
    state_monitor_unlock();
}

void state_monitor_update_finger_detected(bool detected)
{
    state_monitor_lock();
    if (detected) {
        s_ctx.snapshot.state_flags |= STATE_MON_FLAG_FINGER_DETECTED;
    } else {
        s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_FLAG_FINGER_DETECTED;
    }
    state_monitor_unlock();
}

void state_monitor_note_sync_mark(void)
{
    state_monitor_lock();
    s_ctx.snapshot.state_flags |= STATE_MON_FLAG_SYNC_MARK;
    state_monitor_unlock();
}

void state_monitor_set_streaming(bool streaming)
{
    state_monitor_lock();
    s_ctx.snapshot.streaming = streaming;
    state_monitor_unlock();
}

void state_monitor_note_ppg_fifo_overflow(uint32_t dropped_samples)
{
    state_monitor_lock();
    s_ctx.snapshot.ppg_fifo_overflow_count += dropped_samples;
    s_ctx.snapshot.state_flags |= STATE_MON_FLAG_PPG_FIFO_OVERFLOW;
    state_monitor_unlock();
}

void state_monitor_note_ppg_int_timeout(void)
{
    state_monitor_lock();
    s_ctx.snapshot.ppg_int_timeout_count++;
    s_ctx.snapshot.state_flags |= STATE_MON_FLAG_PPG_INT_TIMEOUT;
    state_monitor_unlock();
}

void state_monitor_note_i2c_error(void)
{
    state_monitor_lock();
    s_ctx.snapshot.i2c_error_count++;
    state_monitor_unlock();
}

void state_monitor_note_ble_backpressure(void)
{
    state_monitor_lock();
    s_ctx.snapshot.ble_backpressure_count++;
    s_ctx.snapshot.state_flags |= STATE_MON_FLAG_BLE_BACKPRESSURE;
    state_monitor_unlock();
}

void state_monitor_note_ble_frame_dropped(void)
{
    state_monitor_lock();
    s_ctx.snapshot.ble_dropped_frame_count++;
    state_monitor_unlock();
}

void state_monitor_note_adc_saturation(void)
{
    state_monitor_lock();
    s_ctx.snapshot.adc_saturation_count++;
    s_ctx.snapshot.state_flags |= STATE_MON_FLAG_ADC_SATURATED;
    state_monitor_unlock();
}

void state_monitor_note_ecg_ring_drop(void)
{
    state_monitor_lock();
    s_ctx.snapshot.ecg_ring_drop_count++;
    state_monitor_unlock();
}

void state_monitor_note_ppg_ring_drop(void)
{
    state_monitor_lock();
    s_ctx.snapshot.ppg_ring_drop_count++;
    state_monitor_unlock();
}

void state_monitor_note_frame_generated(uint16_t frame_id)
{
    state_monitor_lock();
    if (s_ctx.last_frame_valid && frame_id != (uint16_t)(s_ctx.last_frame_id + 1U)) {
        s_ctx.snapshot.frame_sequence_error_count++;
    }
    s_ctx.last_frame_valid = true;
    s_ctx.last_frame_id = frame_id;
    s_ctx.snapshot.generated_frame_count++;
    state_monitor_unlock();
}

void state_monitor_note_frame_transmitted(uint16_t frame_id)
{
    (void)frame_id;
    state_monitor_lock();
    s_ctx.snapshot.transmitted_frame_count++;
    state_monitor_unlock();
}

void state_monitor_update_buffer_levels(uint16_t ecg_items, uint16_t ppg_items, uint16_t ble_items)
{
    state_monitor_lock();
    s_ctx.snapshot.ecg_ring_items = ecg_items;
    s_ctx.snapshot.ppg_ring_items = ppg_items;
    s_ctx.snapshot.ble_queue_items = ble_items;

    if (ecg_items > s_ctx.snapshot.ecg_ring_high_watermark) {
        s_ctx.snapshot.ecg_ring_high_watermark = ecg_items;
    }
    if (ppg_items > s_ctx.snapshot.ppg_ring_high_watermark) {
        s_ctx.snapshot.ppg_ring_high_watermark = ppg_items;
    }
    if (ble_items > s_ctx.snapshot.ble_queue_high_watermark) {
        s_ctx.snapshot.ble_queue_high_watermark = ble_items;
    }
    state_monitor_unlock();
}

uint16_t state_monitor_take_frame_flags(void)
{
    uint16_t flags;
    state_monitor_lock();
    flags = s_ctx.snapshot.state_flags;
    s_ctx.snapshot.state_flags &= (uint16_t)~STATE_MON_TRANSIENT_FLAGS;
    state_monitor_unlock();
    return flags;
}

void state_monitor_get_snapshot(state_monitor_snapshot_t *out_snapshot)
{
    if (out_snapshot == NULL) {
        return;
    }

    state_monitor_lock();
    *out_snapshot = s_ctx.snapshot;
    state_monitor_unlock();
}
