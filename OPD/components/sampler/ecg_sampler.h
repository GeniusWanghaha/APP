#pragma once

#include <stdint.h>

#include "ad8232.h"
#include "esp_err.h"
#include "freertos/ringbuf.h"

typedef struct ecg_sampler_dev *ecg_sampler_handle_t;

typedef struct {
    uint64_t timestamp_us;
    uint16_t raw;
    uint8_t saturated;
    uint8_t lo_plus;
    uint8_t lo_minus;
} ecg_sample_t;

typedef struct {
    ad8232_handle_t ad8232;
    RingbufHandle_t output_ringbuf;
} ecg_sampler_config_t;

esp_err_t ecg_sampler_init(const ecg_sampler_config_t *config, ecg_sampler_handle_t *out_handle);
esp_err_t ecg_sampler_start(ecg_sampler_handle_t handle);
esp_err_t ecg_sampler_stop(ecg_sampler_handle_t handle);
void ecg_sampler_flush(ecg_sampler_handle_t handle);
