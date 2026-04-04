#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "driver/gpio.h"
#include "driver/i2c.h"
#include "esp_err.h"

typedef struct max30102_dev *max30102_handle_t;

typedef struct {
    i2c_port_t i2c_port;
    gpio_num_t scl_gpio;
    gpio_num_t sda_gpio;
    gpio_num_t int_gpio;
    uint8_t i2c_address;
    uint32_t i2c_clk_hz;
    uint8_t fifo_a_full;
    uint8_t sample_average;
    uint8_t adc_range;
    uint8_t sample_rate;
    uint8_t pulse_width;
    uint8_t red_led_pa;
    uint8_t ir_led_pa;
    bool enable_temperature_interrupt;
} max30102_config_t;

typedef struct {
    uint8_t status1;
    uint8_t status2;
} max30102_interrupt_status_t;

typedef struct {
    uint8_t wr_ptr;
    uint8_t ovf_counter;
    uint8_t rd_ptr;
    uint8_t unread_samples;
} max30102_fifo_state_t;

typedef struct {
    uint32_t red;
    uint32_t ir;
} max30102_fifo_sample_t;

esp_err_t max30102_init(const max30102_config_t *config, max30102_handle_t *out_handle);
esp_err_t max30102_read_register(max30102_handle_t handle, uint8_t reg, uint8_t *value);
esp_err_t max30102_write_register(max30102_handle_t handle, uint8_t reg, uint8_t value);
esp_err_t max30102_read_part_info(max30102_handle_t handle, uint8_t *part_id, uint8_t *rev_id);
esp_err_t max30102_reset(max30102_handle_t handle);
esp_err_t max30102_shutdown(max30102_handle_t handle, bool shutdown);
esp_err_t max30102_clear_fifo(max30102_handle_t handle);
esp_err_t max30102_config_default(max30102_handle_t handle);
esp_err_t max30102_set_led_currents(max30102_handle_t handle, uint8_t red_pa, uint8_t ir_pa);
esp_err_t max30102_set_temperature_enabled(max30102_handle_t handle, bool enabled);
esp_err_t max30102_get_interrupt_status(max30102_handle_t handle, max30102_interrupt_status_t *status);
esp_err_t max30102_get_fifo_state(max30102_handle_t handle, max30102_fifo_state_t *state);
esp_err_t max30102_read_fifo_samples(max30102_handle_t handle,
                                     max30102_fifo_sample_t *samples,
                                     size_t max_samples,
                                     size_t *samples_read,
                                     max30102_fifo_state_t *state_after);
esp_err_t max30102_read_temperature_optional(max30102_handle_t handle, float *temp_c);
