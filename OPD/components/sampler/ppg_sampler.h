#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/gpio.h"
#include "esp_err.h"
#include "freertos/ringbuf.h"
#include "max30102.h"

typedef struct ppg_sampler_dev *ppg_sampler_handle_t;

typedef struct {
    uint64_t timestamp_us;
    uint32_t red;
    uint32_t ir;
} ppg_sample_t;

typedef struct {
    max30102_handle_t sensor;
    gpio_num_t int_gpio;
    RingbufHandle_t output_ringbuf;
} ppg_sampler_config_t;

esp_err_t ppg_sampler_init(const ppg_sampler_config_t *config, ppg_sampler_handle_t *out_handle);
esp_err_t ppg_sampler_start(ppg_sampler_handle_t handle);
esp_err_t ppg_sampler_stop(ppg_sampler_handle_t handle);
esp_err_t ppg_sampler_apply_settings(ppg_sampler_handle_t handle,
                                      uint8_t red_pa,
                                      uint8_t ir_pa,
                                      bool temp_enabled,
                                      bool use_int_mode,
                                      int32_t ppg_latency_us);
void ppg_sampler_flush(ppg_sampler_handle_t handle);
