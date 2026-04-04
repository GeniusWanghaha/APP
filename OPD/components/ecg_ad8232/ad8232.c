#include "ad8232.h"

#include <stdlib.h>
#include <string.h>

#include "esp_log.h"

struct ad8232_dev {
    ad8232_config_t config;
    adc_oneshot_unit_handle_t adc_handle;
    uint8_t lo_plus_debounce;
    uint8_t lo_minus_debounce;
    bool enabled;
};

static const char *TAG = "ad8232";

#if CONFIG_APP_AD8232_SDN_ACTIVE_HIGH
#define AD8232_SDN_ASSERT_LEVEL 1
#define AD8232_SDN_RELEASE_LEVEL 0
#else
#define AD8232_SDN_ASSERT_LEVEL 0
#define AD8232_SDN_RELEASE_LEVEL 1
#endif

static esp_err_t ad8232_update_lead_off(ad8232_handle_t handle, ad8232_lead_off_state_t *state)
{
    const int lo_plus_raw = gpio_get_level(handle->config.lo_plus_gpio);
    const int lo_minus_raw = gpio_get_level(handle->config.lo_minus_gpio);

    handle->lo_plus_debounce = lo_plus_raw ? (uint8_t)(handle->lo_plus_debounce + 1U) : 0U;
    handle->lo_minus_debounce = lo_minus_raw ? (uint8_t)(handle->lo_minus_debounce + 1U) : 0U;

    if (handle->lo_plus_debounce > handle->config.lead_off_debounce_samples) {
        handle->lo_plus_debounce = handle->config.lead_off_debounce_samples;
    }
    if (handle->lo_minus_debounce > handle->config.lead_off_debounce_samples) {
        handle->lo_minus_debounce = handle->config.lead_off_debounce_samples;
    }

    state->lo_plus = (handle->lo_plus_debounce >= handle->config.lead_off_debounce_samples);
    state->lo_minus = (handle->lo_minus_debounce >= handle->config.lead_off_debounce_samples);
    state->any_off = state->lo_plus || state->lo_minus;
    return ESP_OK;
}

esp_err_t ad8232_init(const ad8232_config_t *config, ad8232_handle_t *out_handle)
{
    if ((config == NULL) || (out_handle == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    ad8232_handle_t handle = calloc(1, sizeof(*handle));
    if (handle == NULL) {
        return ESP_ERR_NO_MEM;
    }

    handle->config = *config;

    adc_oneshot_unit_init_cfg_t unit_cfg = {
        .unit_id = config->adc_unit,
        .ulp_mode = ADC_ULP_MODE_DISABLE,
    };
    esp_err_t ret = adc_oneshot_new_unit(&unit_cfg, &handle->adc_handle);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    adc_oneshot_chan_cfg_t chan_cfg = {
        .atten = config->adc_atten,
        .bitwidth = ADC_BITWIDTH_12,
    };
    ret = adc_oneshot_config_channel(handle->adc_handle, config->adc_channel, &chan_cfg);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    gpio_config_t input_cfg = {
        .pin_bit_mask = (1ULL << config->lo_plus_gpio) | (1ULL << config->lo_minus_gpio),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    ret = gpio_config(&input_cfg);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    gpio_config_t sdn_cfg = {
        .pin_bit_mask = (1ULL << config->sdn_gpio),
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    ret = gpio_config(&sdn_cfg);
    if (ret != ESP_OK) {
        free(handle);
        return ret;
    }

    gpio_set_level(config->sdn_gpio, AD8232_SDN_ASSERT_LEVEL);
    handle->enabled = false;
    *out_handle = handle;
    ESP_LOGI(TAG, "AD8232 initialized on GPIO%d / ADC1 channel %d", config->output_gpio, config->adc_channel);
    return ESP_OK;
}

esp_err_t ad8232_enable(ad8232_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    gpio_set_level(handle->config.sdn_gpio, AD8232_SDN_RELEASE_LEVEL);
    handle->enabled = true;
    return ESP_OK;
}

esp_err_t ad8232_disable(ad8232_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    gpio_set_level(handle->config.sdn_gpio, AD8232_SDN_ASSERT_LEVEL);
    handle->enabled = false;
    return ESP_OK;
}

esp_err_t ad8232_get_lead_off_state(ad8232_handle_t handle, ad8232_lead_off_state_t *state)
{
    if ((handle == NULL) || (state == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    return ad8232_update_lead_off(handle, state);
}

esp_err_t ad8232_sample_once(ad8232_handle_t handle, ad8232_sample_t *sample)
{
    if ((handle == NULL) || (sample == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    int raw = 0;
    esp_err_t ret = adc_oneshot_read(handle->adc_handle, handle->config.adc_channel, &raw);
    if (ret != ESP_OK) {
        return ret;
    }

    memset(sample, 0, sizeof(*sample));
    sample->raw = (uint16_t)raw;
    sample->saturated = (raw <= handle->config.saturation_low_threshold) ||
                        (raw >= handle->config.saturation_high_threshold);
    ad8232_update_lead_off(handle, &sample->lead_off);
    return ESP_OK;
}
