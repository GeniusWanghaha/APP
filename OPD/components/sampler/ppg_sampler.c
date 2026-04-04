#include "ppg_sampler.h"

#include <stdlib.h>

#include "driver/gpio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "max30102_regs.h"
#include "sdkconfig.h"
#include "state_monitor.h"
#include "timebase.h"

#define PPG_SAMPLE_PERIOD_US 2500ULL

#ifndef CONFIG_APP_MAX30102_POLL_PERIOD_MS
#define CONFIG_APP_MAX30102_POLL_PERIOD_MS 20
#endif

struct ppg_sampler_dev {
    max30102_handle_t sensor;
    gpio_num_t int_gpio;
    RingbufHandle_t output_ringbuf;
    QueueHandle_t trigger_queue;
    TaskHandle_t task_handle;
    volatile bool running;
    bool use_int_mode;
    bool temp_enabled;
    uint8_t red_pa;
    uint8_t ir_pa;
    int32_t ppg_latency_us;
    uint64_t last_sample_timestamp_us;
    TickType_t last_temp_tick;
    float last_temp_c;
    uint32_t int_missing_ms_accum;
};

static const char *TAG = "ppg_sampler";

static void IRAM_ATTR ppg_sampler_int_isr(void *arg)
{
    ppg_sampler_handle_t handle = (ppg_sampler_handle_t)arg;
    uint8_t token = 1U;
    BaseType_t high_task_wakeup = pdFALSE;
    xQueueSendFromISR(handle->trigger_queue, &token, &high_task_wakeup);
    if (high_task_wakeup == pdTRUE) {
        portYIELD_FROM_ISR();
    }
}

static uint64_t ppg_sampler_estimate_oldest_timestamp(ppg_sampler_handle_t handle,
                                                       uint64_t event_time_us,
                                                       size_t sample_count)
{
    const uint64_t batch_span_us = (sample_count > 0U) ? ((uint64_t)(sample_count - 1U) * PPG_SAMPLE_PERIOD_US) : 0U;
    const uint64_t latency_us = (handle->ppg_latency_us > 0) ? (uint64_t)handle->ppg_latency_us : 0U;
    const uint64_t event_based = (event_time_us > (latency_us + batch_span_us))
                                     ? (event_time_us - latency_us - batch_span_us)
                                     : 0U;

    if (handle->last_sample_timestamp_us == 0U) {
        return event_based;
    }

    const uint64_t predicted = handle->last_sample_timestamp_us + PPG_SAMPLE_PERIOD_US;
    const uint64_t delta = (predicted > event_based) ? (predicted - event_based) : (event_based - predicted);
    return (delta > (2U * PPG_SAMPLE_PERIOD_US)) ? event_based : predicted;
}

static esp_err_t ppg_sampler_service_fifo(ppg_sampler_handle_t handle, uint64_t event_time_us)
{
    max30102_interrupt_status_t intr = {0};
    esp_err_t ret = max30102_get_interrupt_status(handle->sensor, &intr);
    if (ret != ESP_OK) {
        state_monitor_note_i2c_error();
        return ret;
    }

    max30102_fifo_state_t fifo_state = {0};
    ret = max30102_get_fifo_state(handle->sensor, &fifo_state);
    if (ret != ESP_OK) {
        state_monitor_note_i2c_error();
        return ret;
    }

    if (fifo_state.ovf_counter > 0U) {
        state_monitor_note_ppg_fifo_overflow(fifo_state.ovf_counter);
    }

    while (fifo_state.unread_samples > 0U) {
        max30102_fifo_sample_t fifo_samples[8] = {0};
        size_t read_count = 0;
        const size_t request = (fifo_state.unread_samples > 8U) ? 8U : fifo_state.unread_samples;

        ret = max30102_read_fifo_samples(handle->sensor,
                                         fifo_samples,
                                         request,
                                         &read_count,
                                         &fifo_state);
        if (ret != ESP_OK) {
            state_monitor_note_i2c_error();
            return ret;
        }
        if (read_count == 0U) {
            break;
        }

        const uint64_t oldest_timestamp_us = ppg_sampler_estimate_oldest_timestamp(handle, event_time_us, read_count);
        bool finger_detected = false;

        for (size_t i = 0; i < read_count; ++i) {
            ppg_sample_t sample = {
                .timestamp_us = oldest_timestamp_us + ((uint64_t)i * PPG_SAMPLE_PERIOD_US),
                .red = fifo_samples[i].red,
                .ir = fifo_samples[i].ir,
            };
            finger_detected = finger_detected || (sample.ir >= (uint32_t)CONFIG_APP_PPG_FINGER_IR_THRESHOLD);

            if (xRingbufferSend(handle->output_ringbuf, &sample, sizeof(sample), 0) != pdTRUE) {
                state_monitor_note_ppg_ring_drop();
            }
        }

        handle->last_sample_timestamp_us = oldest_timestamp_us + ((uint64_t)(read_count - 1U) * PPG_SAMPLE_PERIOD_US);
        state_monitor_update_finger_detected(finger_detected);
    }

    if ((intr.status1 & MAX30102_INT_ALC_OVF) != 0U) {
        ESP_LOGW(TAG, "MAX30102 ambient light cancellation overflow");
    }
    return ESP_OK;
}

static void ppg_sampler_task(void *arg)
{
    ppg_sampler_handle_t handle = (ppg_sampler_handle_t)arg;
    uint8_t token = 0;

    while (true) {
        if (!handle->running) {
            xQueueReceive(handle->trigger_queue, &token, pdMS_TO_TICKS(100));
            continue;
        }

        const TickType_t poll_ticks = pdMS_TO_TICKS(CONFIG_APP_MAX30102_POLL_PERIOD_MS);
        bool got_interrupt = false;
        if (handle->use_int_mode) {
            got_interrupt = (xQueueReceive(handle->trigger_queue, &token, poll_ticks) == pdTRUE);
            if (got_interrupt) {
                handle->int_missing_ms_accum = 0U;
            } else {
                handle->int_missing_ms_accum += CONFIG_APP_MAX30102_POLL_PERIOD_MS;
                if (handle->int_missing_ms_accum >= (uint32_t)CONFIG_APP_MAX30102_INT_TIMEOUT_MS) {
                    state_monitor_note_ppg_int_timeout();
                    handle->int_missing_ms_accum = 0U;
                }
            }
        } else {
            vTaskDelay(poll_ticks);
        }

        const uint64_t event_time_us = timebase_get_time_us();
        (void)ppg_sampler_service_fifo(handle, event_time_us);

        if (handle->temp_enabled && ((xTaskGetTickCount() - handle->last_temp_tick) >= pdMS_TO_TICKS(1000))) {
            float temp_c = 0.0f;
            if (max30102_read_temperature_optional(handle->sensor, &temp_c) == ESP_OK) {
                handle->last_temp_c = temp_c;
            } else {
                state_monitor_note_i2c_error();
            }
            handle->last_temp_tick = xTaskGetTickCount();
        }
    }
}

esp_err_t ppg_sampler_init(const ppg_sampler_config_t *config, ppg_sampler_handle_t *out_handle)
{
    if ((config == NULL) || (out_handle == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    ppg_sampler_handle_t handle = calloc(1, sizeof(*handle));
    if (handle == NULL) {
        return ESP_ERR_NO_MEM;
    }

    handle->sensor = config->sensor;
    handle->int_gpio = config->int_gpio;
    handle->output_ringbuf = config->output_ringbuf;
    handle->trigger_queue = xQueueCreate(32, sizeof(uint8_t));
    if (handle->trigger_queue == NULL) {
        free(handle);
        return ESP_ERR_NO_MEM;
    }

    gpio_config_t int_cfg = {
        .pin_bit_mask = (1ULL << config->int_gpio),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_NEGEDGE,
    };
    esp_err_t ret = gpio_config(&int_cfg);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    ret = gpio_install_isr_service(0);
    if ((ret != ESP_OK) && (ret != ESP_ERR_INVALID_STATE)) {
        free(handle);
        return ret;
    }

    ret = gpio_isr_handler_add(config->int_gpio, ppg_sampler_int_isr, handle);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    BaseType_t task_ok = xTaskCreate(ppg_sampler_task,
                                     "ppg_sampling_task",
                                     4608,
                                     handle,
                                     17,
                                     &handle->task_handle);
    if (task_ok != pdPASS) {
        free(handle);
        return ESP_ERR_NO_MEM;
    }

    *out_handle = handle;
    return ESP_OK;
}

esp_err_t ppg_sampler_start(ppg_sampler_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    ppg_sampler_flush(handle);
    xQueueReset(handle->trigger_queue);
    handle->last_sample_timestamp_us = 0U;
    handle->last_temp_tick = 0U;
    handle->int_missing_ms_accum = 0U;

    esp_err_t ret = max30102_shutdown(handle->sensor, false);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = max30102_config_default(handle->sensor);
    if (ret != ESP_OK) {
        return ret;
    }

    ESP_LOGI(TAG,
             "PPG sampler started: mode=%s, poll_period=%d ms, int_timeout=%d ms",
             handle->use_int_mode ? "INT+poll-fallback" : "poll",
             CONFIG_APP_MAX30102_POLL_PERIOD_MS,
             CONFIG_APP_MAX30102_INT_TIMEOUT_MS);
    handle->running = true;
    return ESP_OK;
}

esp_err_t ppg_sampler_stop(ppg_sampler_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    handle->running = false;
    return max30102_shutdown(handle->sensor, true);
}

esp_err_t ppg_sampler_apply_settings(ppg_sampler_handle_t handle,
                                      uint8_t red_pa,
                                      uint8_t ir_pa,
                                      bool temp_enabled,
                                      bool use_int_mode,
                                      int32_t ppg_latency_us)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    handle->red_pa = red_pa;
    handle->ir_pa = ir_pa;
    handle->temp_enabled = temp_enabled;
    handle->use_int_mode = use_int_mode;
    handle->ppg_latency_us = ppg_latency_us;

    esp_err_t ret = max30102_set_led_currents(handle->sensor, red_pa, ir_pa);
    if (ret != ESP_OK) {
        return ret;
    }

    return max30102_set_temperature_enabled(handle->sensor, temp_enabled);
}

void ppg_sampler_flush(ppg_sampler_handle_t handle)
{
    if (handle == NULL) {
        return;
    }

    size_t item_size = 0;
    void *item = NULL;
    do {
        item = xRingbufferReceive(handle->output_ringbuf, &item_size, 0);
        if (item != NULL) {
            vRingbufferReturnItem(handle->output_ringbuf, item);
        }
    } while (item != NULL);
}
