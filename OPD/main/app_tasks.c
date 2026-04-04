#include "app_tasks.h"

#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "ad8232.h"
#include "app_config.h"
#include "ble_service.h"
#include "board_pins.h"
#include "crc16.h"
#include "ecg_sampler.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/queue.h"
#include "freertos/ringbuf.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "max30102.h"
#include "max30102_regs.h"
#include "packet_protocol.h"
#include "ppg_sampler.h"
#include "state_monitor.h"
#include "timebase.h"

#define APP_EVT_STREAMING                  BIT0
#define APP_BLE_TX_RETRY_COUNT             4
#define APP_BLE_TX_RETRY_DELAY_MS          10
#define APP_PACKETIZER_WAIT_MS             120

typedef struct {
    uint16_t frame_id;
    uint16_t length;
    uint8_t payload[APP_MAX_BLE_FRAME_BYTES];
} ble_tx_job_t;

typedef struct {
    SemaphoreHandle_t config_mutex;
    EventGroupHandle_t event_group;
    QueueHandle_t ble_tx_queue;
    QueueHandle_t control_queue;
    RingbufHandle_t ecg_ringbuf;
    RingbufHandle_t ppg_ringbuf;
    ad8232_handle_t ad8232;
    max30102_handle_t max30102;
    ecg_sampler_handle_t ecg_sampler;
    ppg_sampler_handle_t ppg_sampler;
    app_runtime_config_t runtime_config;
    uint16_t next_frame_id;
} app_ctx_t;

static app_ctx_t s_ctx;
static const char *TAG = "app_tasks";
static void app_run_self_tests(void);

static bool app_streaming_enabled(void)
{
    return (xEventGroupGetBits(s_ctx.event_group) & APP_EVT_STREAMING) != 0U;
}

static void app_get_runtime_config(app_runtime_config_t *out_config)
{
    xSemaphoreTake(s_ctx.config_mutex, portMAX_DELAY);
    *out_config = s_ctx.runtime_config;
    xSemaphoreGive(s_ctx.config_mutex);
}

static void app_set_runtime_config(const app_runtime_config_t *config)
{
    xSemaphoreTake(s_ctx.config_mutex, portMAX_DELAY);
    s_ctx.runtime_config = *config;
    xSemaphoreGive(s_ctx.config_mutex);
}

static void app_flush_ringbuffer(RingbufHandle_t ringbuf)
{
    size_t item_size = 0;
    void *item = NULL;
    do {
        item = xRingbufferReceive(ringbuf, &item_size, 0);
        if (item != NULL) {
            vRingbufferReturnItem(ringbuf, item);
        }
    } while (item != NULL);
}

static void app_flush_ble_tx_queue(void)
{
    ble_tx_job_t job = {0};
    while (xQueueReceive(s_ctx.ble_tx_queue, &job, 0) == pdTRUE) {
    }
}

static uint16_t app_estimate_ring_items(RingbufHandle_t ringbuf, size_t total_bytes, size_t item_size)
{
    const size_t free_bytes = xRingbufferGetCurFreeSize(ringbuf);
    const size_t used_bytes = (total_bytes > free_bytes) ? (total_bytes - free_bytes) : 0U;
    return (uint16_t)(used_bytes / item_size);
}

static bool app_pop_ecg_samples(ecg_sample_t *samples)
{
    for (size_t i = 0; i < APP_ECG_SAMPLES_PER_FRAME; ++i) {
        size_t item_size = 0;
        void *item = xRingbufferReceive(s_ctx.ecg_ringbuf, &item_size, pdMS_TO_TICKS(APP_PACKETIZER_WAIT_MS));
        if ((item == NULL) || (item_size != sizeof(ecg_sample_t))) {
            if (item != NULL) {
                vRingbufferReturnItem(s_ctx.ecg_ringbuf, item);
            }
            return false;
        }
        memcpy(&samples[i], item, sizeof(ecg_sample_t));
        vRingbufferReturnItem(s_ctx.ecg_ringbuf, item);
        if (!app_streaming_enabled()) {
            return false;
        }
    }
    return true;
}

static bool app_pop_ppg_samples(ppg_sample_t *samples)
{
    for (size_t i = 0; i < APP_PPG_SAMPLES_PER_FRAME; ++i) {
        size_t item_size = 0;
        void *item = xRingbufferReceive(s_ctx.ppg_ringbuf, &item_size, pdMS_TO_TICKS(APP_PACKETIZER_WAIT_MS));
        if ((item == NULL) || (item_size != sizeof(ppg_sample_t))) {
            if (item != NULL) {
                vRingbufferReturnItem(s_ctx.ppg_ringbuf, item);
            }
            return false;
        }
        memcpy(&samples[i], item, sizeof(ppg_sample_t));
        vRingbufferReturnItem(s_ctx.ppg_ringbuf, item);
        if (!app_streaming_enabled()) {
            return false;
        }
    }
    return true;
}

static esp_err_t app_fill_control_snapshot(ble_control_snapshot_t *out_snapshot)
{
    if (out_snapshot == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    app_runtime_config_t cfg = {0};
    app_get_runtime_config(&cfg);

    memset(out_snapshot, 0, sizeof(*out_snapshot));
    out_snapshot->protocol_version = APP_PROTOCOL_VERSION;
    out_snapshot->streaming_enabled = app_streaming_enabled() ? 1U : 0U;
    out_snapshot->red_led_pa = cfg.red_led_pa;
    out_snapshot->ir_led_pa = cfg.ir_led_pa;
    out_snapshot->ppg_phase_us = cfg.ppg_phase_us;
    out_snapshot->ppg_latency_us = cfg.ppg_latency_us;
    out_snapshot->temperature_enabled = cfg.temperature_enabled ? 1U : 0U;
    out_snapshot->log_level = cfg.log_level;
    out_snapshot->ppg_int_mode = cfg.ppg_int_mode ? 1U : 0U;
    return ESP_OK;
}

static esp_err_t app_fill_status_snapshot(ble_status_snapshot_t *out_snapshot)
{
    if (out_snapshot == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    app_runtime_config_t cfg = {0};
    state_monitor_snapshot_t state = {0};
    app_get_runtime_config(&cfg);
    state_monitor_get_snapshot(&state);

    memset(out_snapshot, 0, sizeof(*out_snapshot));
    out_snapshot->protocol_version = APP_PROTOCOL_VERSION;
    out_snapshot->streaming_enabled = state.streaming ? 1U : 0U;
    out_snapshot->state_flags = state.state_flags;
    out_snapshot->self_test_pass_bitmap = state.self_test_pass_bitmap;
    out_snapshot->self_test_fail_bitmap = state.self_test_fail_bitmap;
    out_snapshot->ppg_fifo_overflow_count = state.ppg_fifo_overflow_count;
    out_snapshot->ppg_int_timeout_count = state.ppg_int_timeout_count;
    out_snapshot->ble_backpressure_count = state.ble_backpressure_count;
    out_snapshot->ble_dropped_frame_count = state.ble_dropped_frame_count;
    out_snapshot->i2c_error_count = state.i2c_error_count;
    out_snapshot->adc_saturation_count = state.adc_saturation_count;
    out_snapshot->ecg_ring_drop_count = state.ecg_ring_drop_count;
    out_snapshot->ppg_ring_drop_count = state.ppg_ring_drop_count;
    out_snapshot->generated_frame_count = state.generated_frame_count;
    out_snapshot->transmitted_frame_count = state.transmitted_frame_count;
    out_snapshot->frame_sequence_error_count = state.frame_sequence_error_count;
    out_snapshot->ecg_ring_items = state.ecg_ring_items;
    out_snapshot->ppg_ring_items = state.ppg_ring_items;
    out_snapshot->ble_queue_items = state.ble_queue_items;
    out_snapshot->ecg_ring_high_watermark = state.ecg_ring_high_watermark;
    out_snapshot->ppg_ring_high_watermark = state.ppg_ring_high_watermark;
    out_snapshot->ble_queue_high_watermark = state.ble_queue_high_watermark;
    out_snapshot->mtu = (uint16_t)(ble_service_get_att_payload_mtu() + 3U);
    out_snapshot->red_led_pa = cfg.red_led_pa;
    out_snapshot->ir_led_pa = cfg.ir_led_pa;
    out_snapshot->ppg_phase_us = cfg.ppg_phase_us;
    out_snapshot->ppg_latency_us = cfg.ppg_latency_us;
    out_snapshot->temperature_enabled = cfg.temperature_enabled ? 1U : 0U;
    out_snapshot->log_level = cfg.log_level;
    out_snapshot->sensor_ready = ((state.state_flags & STATE_MON_FLAG_SENSOR_READY) != 0U) ? 1U : 0U;
    out_snapshot->finger_detected = ((state.state_flags & STATE_MON_FLAG_FINGER_DETECTED) != 0U) ? 1U : 0U;
    return ESP_OK;
}

static void app_ble_connection_changed(bool connected, uint16_t mtu)
{
    if (connected) {
        ESP_LOGI(TAG, "BLE connected, negotiated MTU=%u", (unsigned)mtu);
    } else {
        ESP_LOGI(TAG, "BLE disconnected");
    }
}

static bool app_required_self_tests_ok(void)
{
    state_monitor_snapshot_t state = {0};
    const uint32_t required = STATE_MON_SELF_TEST_TIMEBASE |
                              STATE_MON_SELF_TEST_AD8232_GPIO |
                              STATE_MON_SELF_TEST_MAX30102_I2C |
                              STATE_MON_SELF_TEST_MAX30102_CONFIG |
                              STATE_MON_SELF_TEST_BLE_STACK;
    state_monitor_get_snapshot(&state);
    return (state.self_test_pass_bitmap & required) == required;
}

static esp_err_t app_start_streaming(void)
{
    if (app_streaming_enabled()) {
        return ESP_OK;
    }
    if ((s_ctx.ecg_sampler == NULL) || (s_ctx.ppg_sampler == NULL) || !app_required_self_tests_ok()) {
        return ESP_ERR_INVALID_STATE;
    }

    app_runtime_config_t cfg = {0};
    app_get_runtime_config(&cfg);

    esp_err_t ret = ppg_sampler_apply_settings(s_ctx.ppg_sampler,
                                               cfg.red_led_pa,
                                               cfg.ir_led_pa,
                                               cfg.temperature_enabled,
                                               cfg.ppg_int_mode,
                                               cfg.ppg_latency_us);
    if (ret != ESP_OK) {
        return ret;
    }

    state_monitor_reset_stream_counters();
    app_flush_ringbuffer(s_ctx.ecg_ringbuf);
    app_flush_ringbuffer(s_ctx.ppg_ringbuf);
    app_flush_ble_tx_queue();
    s_ctx.next_frame_id = 0U;

    ret = ecg_sampler_start(s_ctx.ecg_sampler);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = ppg_sampler_start(s_ctx.ppg_sampler);
    if (ret != ESP_OK) {
        (void)ecg_sampler_stop(s_ctx.ecg_sampler);
        return ret;
    }

    state_monitor_set_streaming(true);
    xEventGroupSetBits(s_ctx.event_group, APP_EVT_STREAMING);
    return ESP_OK;
}

static void app_stop_streaming(void)
{
    if (!app_streaming_enabled()) {
        return;
    }

    xEventGroupClearBits(s_ctx.event_group, APP_EVT_STREAMING);
    state_monitor_set_streaming(false);
    if (s_ctx.ppg_sampler != NULL) {
        (void)ppg_sampler_stop(s_ctx.ppg_sampler);
    }
    if (s_ctx.ecg_sampler != NULL) {
        (void)ecg_sampler_stop(s_ctx.ecg_sampler);
    }
    app_flush_ble_tx_queue();
}

static void app_request_status_push(void)
{
    if (!ble_service_is_connected() || !ble_service_is_status_notify_enabled()) {
        return;
    }

    ble_status_snapshot_t snapshot = {0};
    if (app_fill_status_snapshot(&snapshot) == ESP_OK) {
        (void)ble_service_notify_status(&snapshot);
    }
}

static void packetizer_task(void *arg)
{
    (void)arg;
    ecg_sample_t ecg_samples[APP_ECG_SAMPLES_PER_FRAME] = {0};
    ppg_sample_t ppg_samples[APP_PPG_SAMPLES_PER_FRAME] = {0};

    while (true) {
        if (!app_streaming_enabled()) {
            vTaskDelay(pdMS_TO_TICKS(20));
            continue;
        }

        if (!app_pop_ecg_samples(ecg_samples) || !app_pop_ppg_samples(ppg_samples)) {
            continue;
        }

        packet_protocol_frame_t frame = {0};
        frame.frame_id = s_ctx.next_frame_id++;
        frame.t_base_us = ecg_samples[0].timestamp_us;
        frame.ecg_count = APP_ECG_SAMPLES_PER_FRAME;
        frame.ppg_count = APP_PPG_SAMPLES_PER_FRAME;
        frame.state_flags = state_monitor_take_frame_flags();

        for (size_t i = 0; i < APP_ECG_SAMPLES_PER_FRAME; ++i) {
            frame.ecg_raw[i] = ecg_samples[i].raw;
        }
        for (size_t i = 0; i < APP_PPG_SAMPLES_PER_FRAME; ++i) {
            packet_write_u24(frame.ppg_red[i], ppg_samples[i].red);
            packet_write_u24(frame.ppg_ir[i], ppg_samples[i].ir);
        }

        frame.crc16 = crc16_ccitt_false((const uint8_t *)&frame,
                                        offsetof(packet_protocol_frame_t, crc16),
                                        APP_CRC16_INIT_CCITT_FALSE);
        state_monitor_note_frame_generated(frame.frame_id);

        ble_tx_job_t job = {
            .frame_id = frame.frame_id,
            .length = sizeof(frame),
        };
        memcpy(job.payload, &frame, sizeof(frame));

        if (xQueueSend(s_ctx.ble_tx_queue, &job, 0) != pdTRUE) {
            state_monitor_note_ble_backpressure();
            state_monitor_note_ble_frame_dropped();
        }
    }
}

static void ble_tx_task(void *arg)
{
    (void)arg;
    ble_tx_job_t job = {0};

    while (true) {
        if (xQueueReceive(s_ctx.ble_tx_queue, &job, portMAX_DELAY) != pdTRUE) {
            continue;
        }

        if (!ble_service_is_connected() || !ble_service_is_data_notify_enabled()) {
            state_monitor_note_ble_frame_dropped();
            continue;
        }

        const uint16_t att_payload = ble_service_get_att_payload_mtu();
        const uint16_t chunk_capacity = (att_payload > sizeof(packet_segment_header_t))
                                            ? (uint16_t)(att_payload - sizeof(packet_segment_header_t))
                                            : 1U;
        const uint8_t segment_count = (uint8_t)((job.length + chunk_capacity - 1U) / chunk_capacity);
        size_t offset = 0U;
        bool failed = false;

        for (uint8_t segment_index = 0; segment_index < segment_count; ++segment_index) {
            const uint16_t chunk_len = (uint16_t)(((job.length - offset) > chunk_capacity) ? chunk_capacity : (job.length - offset));
            uint8_t tx_buffer[APP_MAX_BLE_SEGMENT_BYTES] = {0};

            packet_segment_header_t header = {
                .protocol_version = APP_PROTOCOL_VERSION,
                .message_type = PACKET_MSG_TYPE_DATA_SEGMENT,
                .frame_id = job.frame_id,
                .segment_index = segment_index,
                .segment_count = segment_count,
                .payload_len = chunk_len,
            };
            memcpy(tx_buffer, &header, sizeof(header));
            memcpy(&tx_buffer[sizeof(header)], &job.payload[offset], chunk_len);
            offset += chunk_len;

            esp_err_t ret = ESP_FAIL;
            for (uint8_t retry = 0; retry < APP_BLE_TX_RETRY_COUNT; ++retry) {
                ret = ble_service_notify_data_segment(tx_buffer, sizeof(header) + chunk_len);
                if (ret == ESP_OK) {
                    break;
                }
                state_monitor_note_ble_backpressure();
                vTaskDelay(pdMS_TO_TICKS(APP_BLE_TX_RETRY_DELAY_MS));
            }

            if (ret != ESP_OK) {
                failed = true;
                break;
            }
        }

        if (failed) {
            state_monitor_note_ble_frame_dropped();
        } else {
            state_monitor_note_frame_transmitted(job.frame_id);
        }
    }
}

static void control_task(void *arg)
{
    (void)arg;
    ble_control_event_t event = {0};

    while (true) {
        if (xQueueReceive(s_ctx.control_queue, &event, portMAX_DELAY) != pdTRUE) {
            continue;
        }

        app_runtime_config_t cfg = {0};
        app_get_runtime_config(&cfg);

        switch (event.opcode) {
            case BLE_CONTROL_OP_START_STREAM:
                if (app_start_streaming() != ESP_OK) {
                    ESP_LOGW(TAG, "Start stream rejected; self-test or sensor init incomplete");
                }
                break;
            case BLE_CONTROL_OP_STOP_STREAM:
                app_stop_streaming();
                break;
            case BLE_CONTROL_OP_SET_LED_PA:
                cfg.red_led_pa = event.value.led.red_led_pa;
                cfg.ir_led_pa = event.value.led.ir_led_pa;
                app_set_runtime_config(&cfg);
                if (s_ctx.ppg_sampler != NULL) {
                    (void)ppg_sampler_apply_settings(s_ctx.ppg_sampler,
                                                     cfg.red_led_pa,
                                                     cfg.ir_led_pa,
                                                     cfg.temperature_enabled,
                                                     cfg.ppg_int_mode,
                                                     cfg.ppg_latency_us);
                }
                break;
            case BLE_CONTROL_OP_SET_PPG_PHASE:
                cfg.ppg_phase_us = event.value.i32;
                app_set_runtime_config(&cfg);
                break;
            case BLE_CONTROL_OP_SET_PPG_LATENCY:
                cfg.ppg_latency_us = event.value.i32;
                app_set_runtime_config(&cfg);
                if (s_ctx.ppg_sampler != NULL) {
                    (void)ppg_sampler_apply_settings(s_ctx.ppg_sampler,
                                                     cfg.red_led_pa,
                                                     cfg.ir_led_pa,
                                                     cfg.temperature_enabled,
                                                     cfg.ppg_int_mode,
                                                     cfg.ppg_latency_us);
                }
                break;
            case BLE_CONTROL_OP_SET_TEMP_EN:
                cfg.temperature_enabled = event.value.boolean;
                app_set_runtime_config(&cfg);
                if (s_ctx.ppg_sampler != NULL) {
                    (void)ppg_sampler_apply_settings(s_ctx.ppg_sampler,
                                                     cfg.red_led_pa,
                                                     cfg.ir_led_pa,
                                                     cfg.temperature_enabled,
                                                     cfg.ppg_int_mode,
                                                     cfg.ppg_latency_us);
                }
                break;
            case BLE_CONTROL_OP_SET_LOG_LEVEL:
                cfg.log_level = (event.value.u8 > 5U) ? 5U : event.value.u8;
                app_set_runtime_config(&cfg);
                esp_log_level_set("*", (esp_log_level_t)cfg.log_level);
                break;
            case BLE_CONTROL_OP_SET_PPG_MODE:
                cfg.ppg_int_mode = event.value.boolean;
                app_set_runtime_config(&cfg);
                if (s_ctx.ppg_sampler != NULL) {
                    (void)ppg_sampler_apply_settings(s_ctx.ppg_sampler,
                                                     cfg.red_led_pa,
                                                     cfg.ir_led_pa,
                                                     cfg.temperature_enabled,
                                                     cfg.ppg_int_mode,
                                                     cfg.ppg_latency_us);
                }
                break;
            case BLE_CONTROL_OP_GET_INFO:
                app_request_status_push();
                break;
            case BLE_CONTROL_OP_SELF_TEST:
                if (app_streaming_enabled()) {
                    app_stop_streaming();
                }
                app_run_self_tests();
                state_monitor_update_sensor_ready(app_required_self_tests_ok());
                app_request_status_push();
                break;
            case BLE_CONTROL_OP_SYNC_MARK:
                state_monitor_note_sync_mark();
                app_request_status_push();
                break;
            default:
                break;
        }
    }
}

static void state_monitor_task(void *arg)
{
    (void)arg;

    while (true) {
        const uint16_t ecg_items = app_estimate_ring_items(s_ctx.ecg_ringbuf, APP_ECG_RINGBUF_BYTES, sizeof(ecg_sample_t));
        const uint16_t ppg_items = app_estimate_ring_items(s_ctx.ppg_ringbuf, APP_PPG_RINGBUF_BYTES, sizeof(ppg_sample_t));
        const uint16_t ble_items = (uint16_t)uxQueueMessagesWaiting(s_ctx.ble_tx_queue);
        state_monitor_update_buffer_levels(ecg_items, ppg_items, ble_items);

        if (ble_service_is_connected() && ble_service_is_status_notify_enabled()) {
            ble_status_snapshot_t snapshot = {0};
            if (app_fill_status_snapshot(&snapshot) == ESP_OK) {
                (void)ble_service_notify_status(&snapshot);
            }
        }
        vTaskDelay(pdMS_TO_TICKS(CONFIG_APP_STATUS_NOTIFY_PERIOD_MS));
    }
}

static void app_run_self_tests(void)
{
    if (s_ctx.ad8232 != NULL) {
        ad8232_lead_off_state_t lead_state = {0};
        ad8232_sample_t sample = {0};
        const bool ad8232_ok = (ad8232_enable(s_ctx.ad8232) == ESP_OK) &&
                               (ad8232_get_lead_off_state(s_ctx.ad8232, &lead_state) == ESP_OK) &&
                               (ad8232_sample_once(s_ctx.ad8232, &sample) == ESP_OK) &&
                               (ad8232_disable(s_ctx.ad8232) == ESP_OK);
        state_monitor_note_self_test(STATE_MON_SELF_TEST_AD8232_GPIO, ad8232_ok);
    } else {
        state_monitor_note_self_test(STATE_MON_SELF_TEST_AD8232_GPIO, false);
    }

    if (s_ctx.max30102 != NULL) {
        uint8_t part_id = 0;
        uint8_t rev_id = 0;
        const bool i2c_ok = (max30102_read_part_info(s_ctx.max30102, &part_id, &rev_id) == ESP_OK) &&
                            (part_id == MAX30102_EXPECTED_PART_ID);
        state_monitor_note_self_test(STATE_MON_SELF_TEST_MAX30102_I2C, i2c_ok);

        bool config_ok = false;
        if (i2c_ok && (max30102_config_default(s_ctx.max30102) == ESP_OK)) {
            uint8_t fifo_cfg = 0;
            uint8_t mode_cfg = 0;
            uint8_t spo2_cfg = 0;
            uint8_t led1 = 0;
            uint8_t led2 = 0;
            config_ok = (max30102_read_register(s_ctx.max30102, MAX30102_REG_FIFO_CONFIG, &fifo_cfg) == ESP_OK) &&
                        (max30102_read_register(s_ctx.max30102, MAX30102_REG_MODE_CONFIG, &mode_cfg) == ESP_OK) &&
                        (max30102_read_register(s_ctx.max30102, MAX30102_REG_SPO2_CONFIG, &spo2_cfg) == ESP_OK) &&
                        (max30102_read_register(s_ctx.max30102, MAX30102_REG_LED1_PA, &led1) == ESP_OK) &&
                        (max30102_read_register(s_ctx.max30102, MAX30102_REG_LED2_PA, &led2) == ESP_OK) &&
                        (fifo_cfg == 0x0F) &&
                        ((mode_cfg & 0x07U) == MAX30102_MODE_SPO2) &&
                        (spo2_cfg == (uint8_t)((MAX30102_SPO2_ADC_RGE_16384 << MAX30102_SPO2_ADC_RGE_SHIFT) |
                                               (MAX30102_SPO2_SR_400 << MAX30102_SPO2_SR_SHIFT) |
                                               MAX30102_LED_PW_215)) &&
                        (led1 == s_ctx.runtime_config.red_led_pa) &&
                        (led2 == s_ctx.runtime_config.ir_led_pa);
            (void)max30102_shutdown(s_ctx.max30102, true);
        }
        state_monitor_note_self_test(STATE_MON_SELF_TEST_MAX30102_CONFIG, config_ok);
    } else {
        state_monitor_note_self_test(STATE_MON_SELF_TEST_MAX30102_I2C, false);
        state_monitor_note_self_test(STATE_MON_SELF_TEST_MAX30102_CONFIG, false);
    }
}

esp_err_t app_tasks_init(void)
{
    memset(&s_ctx, 0, sizeof(s_ctx));
    s_ctx.runtime_config = app_default_runtime_config();

    s_ctx.config_mutex = xSemaphoreCreateMutex();
    s_ctx.event_group = xEventGroupCreate();
    s_ctx.ble_tx_queue = xQueueCreate(CONFIG_APP_BLE_TX_QUEUE_DEPTH, sizeof(ble_tx_job_t));
    s_ctx.control_queue = xQueueCreate(16, sizeof(ble_control_event_t));
    s_ctx.ecg_ringbuf = xRingbufferCreate(APP_ECG_RINGBUF_BYTES, RINGBUF_TYPE_NOSPLIT);
    s_ctx.ppg_ringbuf = xRingbufferCreate(APP_PPG_RINGBUF_BYTES, RINGBUF_TYPE_NOSPLIT);
    if ((s_ctx.config_mutex == NULL) || (s_ctx.event_group == NULL) || (s_ctx.ble_tx_queue == NULL) ||
        (s_ctx.control_queue == NULL) || (s_ctx.ecg_ringbuf == NULL) || (s_ctx.ppg_ringbuf == NULL)) {
        return ESP_ERR_NO_MEM;
    }

    esp_log_level_set("*", (esp_log_level_t)s_ctx.runtime_config.log_level);

    const ad8232_config_t ad8232_cfg = {
        .adc_unit = BOARD_AD8232_ADC_UNIT,
        .adc_channel = BOARD_AD8232_ADC_CHANNEL,
        .adc_atten = ADC_ATTEN_DB_12,
        .output_gpio = BOARD_AD8232_OUTPUT_GPIO,
        .lo_plus_gpio = BOARD_AD8232_LO_PLUS_GPIO,
        .lo_minus_gpio = BOARD_AD8232_LO_MINUS_GPIO,
        .sdn_gpio = BOARD_AD8232_SDN_GPIO,
        .saturation_low_threshold = CONFIG_APP_AD8232_ADC_SAT_LOW,
        .saturation_high_threshold = CONFIG_APP_AD8232_ADC_SAT_HIGH,
        .lead_off_debounce_samples = CONFIG_APP_AD8232_LEAD_OFF_DEBOUNCE_SAMPLES,
    };
    (void)ad8232_init(&ad8232_cfg, &s_ctx.ad8232);

    const max30102_config_t max_cfg = {
        .i2c_port = BOARD_MAX30102_I2C_PORT,
        .scl_gpio = BOARD_MAX30102_SCL_GPIO,
        .sda_gpio = BOARD_MAX30102_SDA_GPIO,
        .int_gpio = BOARD_MAX30102_INT_GPIO,
        .i2c_address = BOARD_MAX30102_I2C_ADDR,
        .i2c_clk_hz = 400000,
        .fifo_a_full = 0x0F,
        .sample_average = MAX30102_SMP_AVE_1,
        .adc_range = MAX30102_SPO2_ADC_RGE_16384,
        .sample_rate = MAX30102_SPO2_SR_400,
        .pulse_width = MAX30102_LED_PW_215,
        .red_led_pa = s_ctx.runtime_config.red_led_pa,
        .ir_led_pa = s_ctx.runtime_config.ir_led_pa,
        .enable_temperature_interrupt = s_ctx.runtime_config.temperature_enabled,
    };
    (void)max30102_init(&max_cfg, &s_ctx.max30102);

    app_run_self_tests();

    if (s_ctx.ad8232 != NULL) {
        ecg_sampler_config_t ecg_cfg = {
            .ad8232 = s_ctx.ad8232,
            .output_ringbuf = s_ctx.ecg_ringbuf,
        };
        ESP_ERROR_CHECK(ecg_sampler_init(&ecg_cfg, &s_ctx.ecg_sampler));
    }

    if (s_ctx.max30102 != NULL) {
        ppg_sampler_config_t ppg_cfg = {
            .sensor = s_ctx.max30102,
            .int_gpio = BOARD_MAX30102_INT_GPIO,
            .output_ringbuf = s_ctx.ppg_ringbuf,
        };
        ESP_ERROR_CHECK(ppg_sampler_init(&ppg_cfg, &s_ctx.ppg_sampler));
        (void)ppg_sampler_apply_settings(s_ctx.ppg_sampler,
                                         s_ctx.runtime_config.red_led_pa,
                                         s_ctx.runtime_config.ir_led_pa,
                                         s_ctx.runtime_config.temperature_enabled,
                                         s_ctx.runtime_config.ppg_int_mode,
                                         s_ctx.runtime_config.ppg_latency_us);
    }

    const ble_service_config_t ble_cfg = {
        .control_queue = s_ctx.control_queue,
        .fill_control_snapshot = app_fill_control_snapshot,
        .fill_status_snapshot = app_fill_status_snapshot,
        .connection_cb = app_ble_connection_changed,
    };
    const esp_err_t ble_ret = ble_service_init(&ble_cfg);
    state_monitor_note_self_test(STATE_MON_SELF_TEST_BLE_STACK, ble_ret == ESP_OK);
    if (ble_ret != ESP_OK) {
        return ble_ret;
    }

    state_monitor_update_sensor_ready(app_required_self_tests_ok());

    if (xTaskCreate(packetizer_task, "packetizer_task", 6144, NULL, 16, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(ble_tx_task, "ble_tx_task", 5120, NULL, 15, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(control_task, "control_task", 4096, NULL, 14, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }
    if (xTaskCreate(state_monitor_task, "state_monitor_task", 4096, NULL, 13, NULL) != pdPASS) {
        return ESP_ERR_NO_MEM;
    }

    return ESP_OK;
}

