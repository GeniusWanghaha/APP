#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_err.h"

typedef struct ad8232_dev *ad8232_handle_t;

typedef struct {
    adc_unit_t adc_unit;
    adc_channel_t adc_channel;
    adc_atten_t adc_atten;
    gpio_num_t output_gpio;
    gpio_num_t lo_plus_gpio;
    gpio_num_t lo_minus_gpio;
    gpio_num_t sdn_gpio;
    uint16_t saturation_low_threshold;
    uint16_t saturation_high_threshold;
    uint8_t lead_off_debounce_samples;
} ad8232_config_t;

typedef struct {
    bool lo_plus;
    bool lo_minus;
    bool any_off;
} ad8232_lead_off_state_t;

typedef struct {
    uint16_t raw;
    bool saturated;
    ad8232_lead_off_state_t lead_off;
} ad8232_sample_t;

esp_err_t ad8232_init(const ad8232_config_t *config, ad8232_handle_t *out_handle);
esp_err_t ad8232_enable(ad8232_handle_t handle);
esp_err_t ad8232_disable(ad8232_handle_t handle);
esp_err_t ad8232_get_lead_off_state(ad8232_handle_t handle, ad8232_lead_off_state_t *state);
esp_err_t ad8232_sample_once(ad8232_handle_t handle, ad8232_sample_t *sample);
