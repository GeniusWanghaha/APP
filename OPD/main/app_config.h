#pragma once

#include <stdbool.h>
#include <stdint.h>

#include "sdkconfig.h"

#define APP_PROTOCOL_VERSION                0x01U
#define APP_TIMEBASE_RESOLUTION_HZ          1000000ULL

#define APP_ECG_SAMPLE_RATE_HZ              250U
#define APP_ECG_SAMPLE_PERIOD_US            4000ULL
#define APP_ECG_SAMPLES_PER_FRAME           10U

#define APP_PPG_SAMPLE_RATE_HZ              400U
#define APP_PPG_SAMPLE_PERIOD_US            2500ULL
#define APP_PPG_SAMPLES_PER_FRAME           16U

#define APP_FRAME_PERIOD_US                 40000ULL
#define APP_CRC16_INIT_CCITT_FALSE          0xFFFFU
#define APP_CRC16_POLY_CCITT_FALSE          0x1021U

#define APP_ECG_RINGBUF_BYTES               ((CONFIG_APP_ECG_RING_CAPACITY) * 32U)
#define APP_PPG_RINGBUF_BYTES               ((CONFIG_APP_PPG_RING_CAPACITY) * 32U)

#define APP_MAX_BLE_FRAME_BYTES             132U
#define APP_MAX_BLE_SEGMENT_HEADER_BYTES    8U
#define APP_MAX_BLE_SEGMENT_BYTES           (APP_MAX_BLE_FRAME_BYTES + APP_MAX_BLE_SEGMENT_HEADER_BYTES)

#if defined(CONFIG_APP_MAX30102_ENABLE_TEMP)
#define APP_CONFIG_TEMP_ENABLED_DEFAULT true
#else
#define APP_CONFIG_TEMP_ENABLED_DEFAULT false
#endif
typedef struct {
    uint8_t red_led_pa;
    uint8_t ir_led_pa;
    int32_t ppg_phase_us;
    int32_t ppg_latency_us;
    bool temperature_enabled;
    uint8_t log_level;
    bool ppg_int_mode;
} app_runtime_config_t;

static inline app_runtime_config_t app_default_runtime_config(void)
{
    const app_runtime_config_t cfg = {
        .red_led_pa = (uint8_t)CONFIG_APP_MAX30102_RED_LED_PA,
        .ir_led_pa = (uint8_t)CONFIG_APP_MAX30102_IR_LED_PA,
        .ppg_phase_us = CONFIG_APP_PPG_PHASE_US,
        .ppg_latency_us = CONFIG_APP_PPG_LATENCY_US,
        .temperature_enabled = APP_CONFIG_TEMP_ENABLED_DEFAULT,
        .log_level = (uint8_t)CONFIG_APP_LOG_LEVEL_DEFAULT,
        .ppg_int_mode = CONFIG_APP_MAX30102_USE_INT,
    };
    return cfg;
}
