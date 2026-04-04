#include "ecg_sampler.h"

#include <stdlib.h>

#include "driver/gptimer.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "state_monitor.h"
#include "timebase.h"

#define ECG_SAMPLE_PERIOD_US 4000ULL

struct ecg_sampler_dev {
    ad8232_handle_t ad8232;
    RingbufHandle_t output_ringbuf;
    QueueHandle_t trigger_queue;
    TaskHandle_t task_handle;
    gptimer_handle_t timer;
    volatile bool running;
    uint64_t next_timestamp_us;
};

static const char *TAG = "ecg_sampler";

static bool ecg_sampler_on_alarm(gptimer_handle_t timer,
                                 const gptimer_alarm_event_data_t *event_data,
                                 void *user_ctx)
{
    (void)timer;
    (void)event_data;
    ecg_sampler_handle_t handle = (ecg_sampler_handle_t)user_ctx;
    uint8_t token = 1U;
    BaseType_t high_task_wakeup = pdFALSE;
    xQueueSendFromISR(handle->trigger_queue, &token, &high_task_wakeup);
    return high_task_wakeup == pdTRUE;
}

static void ecg_sampler_task(void *arg)
{
    ecg_sampler_handle_t handle = (ecg_sampler_handle_t)arg;
    uint8_t token = 0;

    while (true) {
        if (xQueueReceive(handle->trigger_queue, &token, portMAX_DELAY) != pdTRUE) {
            continue;
        }
        if (!handle->running) {
            continue;
        }

        ad8232_sample_t sample = {0};
        const esp_err_t ret = ad8232_sample_once(handle->ad8232, &sample);
        if (ret != ESP_OK) {
            ESP_LOGW(TAG, "ADC sample failed: %s", esp_err_to_name(ret));
            continue;
        }

        if (handle->next_timestamp_us == 0U) {
            handle->next_timestamp_us = timebase_get_time_us();
        } else {
            handle->next_timestamp_us += ECG_SAMPLE_PERIOD_US;
        }

        ecg_sample_t ring_sample = {
            .timestamp_us = handle->next_timestamp_us,
            .raw = sample.raw,
            .saturated = sample.saturated ? 1U : 0U,
            .lo_plus = sample.lead_off.lo_plus ? 1U : 0U,
            .lo_minus = sample.lead_off.lo_minus ? 1U : 0U,
        };

        state_monitor_update_lead_off(sample.lead_off.lo_plus, sample.lead_off.lo_minus);
        if (sample.saturated) {
            state_monitor_note_adc_saturation();
        }

        if (xRingbufferSend(handle->output_ringbuf, &ring_sample, sizeof(ring_sample), 0) != pdTRUE) {
            state_monitor_note_ecg_ring_drop();
        }
    }
}

esp_err_t ecg_sampler_init(const ecg_sampler_config_t *config, ecg_sampler_handle_t *out_handle)
{
    if ((config == NULL) || (out_handle == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    ecg_sampler_handle_t handle = calloc(1, sizeof(*handle));
    if (handle == NULL) {
        return ESP_ERR_NO_MEM;
    }

    handle->ad8232 = config->ad8232;
    handle->output_ringbuf = config->output_ringbuf;
    handle->trigger_queue = xQueueCreate(32, sizeof(uint8_t));
    if (handle->trigger_queue == NULL) {
        free(handle);
        return ESP_ERR_NO_MEM;
    }

    gptimer_config_t timer_cfg = {
        .clk_src = GPTIMER_CLK_SRC_DEFAULT,
        .direction = GPTIMER_COUNT_UP,
        .resolution_hz = 1000000,
    };
    esp_err_t ret = gptimer_new_timer(&timer_cfg, &handle->timer);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    gptimer_event_callbacks_t callbacks = {
        .on_alarm = ecg_sampler_on_alarm,
    };
    ret = gptimer_register_event_callbacks(handle->timer, &callbacks, handle);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    gptimer_alarm_config_t alarm_cfg = {
        .alarm_count = ECG_SAMPLE_PERIOD_US,
        .reload_count = 0,
        .flags.auto_reload_on_alarm = true,
    };
    ret = gptimer_set_alarm_action(handle->timer, &alarm_cfg);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    ret = gptimer_enable(handle->timer);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    BaseType_t task_ok = xTaskCreate(ecg_sampler_task,
                                     "ecg_sampling_task",
                                     4096,
                                     handle,
                                     18,
                                     &handle->task_handle);
    if (task_ok != pdPASS) {
        free(handle);
        return ESP_ERR_NO_MEM;
    }

    *out_handle = handle;
    return ESP_OK;
}

esp_err_t ecg_sampler_start(ecg_sampler_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    ecg_sampler_flush(handle);
    xQueueReset(handle->trigger_queue);
    handle->next_timestamp_us = 0U;

    esp_err_t ret = ad8232_enable(handle->ad8232);
    if (ret != ESP_OK) {
        return ret;
    }

    vTaskDelay(pdMS_TO_TICKS(10));
    handle->running = true;
    return gptimer_start(handle->timer);
}

esp_err_t ecg_sampler_stop(ecg_sampler_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    handle->running = false;
    (void)gptimer_stop(handle->timer);
    return ad8232_disable(handle->ad8232);
}

void ecg_sampler_flush(ecg_sampler_handle_t handle)
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
