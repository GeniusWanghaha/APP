#include "max30102.h"
#include "max30102_regs.h"

#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"

struct max30102_dev {
    max30102_config_t config;
    SemaphoreHandle_t lock;
};

static const char *TAG = "max30102";

static esp_err_t max30102_write_locked(max30102_handle_t handle, uint8_t reg, const uint8_t *data, size_t length)
{
    uint8_t buffer[1 + 8] = {0};
    if (length > 8U) {
        return ESP_ERR_INVALID_SIZE;
    }

    buffer[0] = reg;
    memcpy(&buffer[1], data, length);
    return i2c_master_write_to_device(handle->config.i2c_port,
                                      handle->config.i2c_address,
                                      buffer,
                                      length + 1U,
                                      pdMS_TO_TICKS(100));
}

static esp_err_t max30102_read_locked(max30102_handle_t handle, uint8_t reg, uint8_t *data, size_t length)
{
    return i2c_master_write_read_device(handle->config.i2c_port,
                                        handle->config.i2c_address,
                                        &reg,
                                        1,
                                        data,
                                        length,
                                        pdMS_TO_TICKS(100));
}

static esp_err_t max30102_write_reg_locked(max30102_handle_t handle, uint8_t reg, uint8_t value)
{
    return max30102_write_locked(handle, reg, &value, 1);
}

static esp_err_t max30102_read_reg_locked(max30102_handle_t handle, uint8_t reg, uint8_t *value)
{
    return max30102_read_locked(handle, reg, value, 1);
}

esp_err_t max30102_init(const max30102_config_t *config, max30102_handle_t *out_handle)
{
    if ((config == NULL) || (out_handle == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    max30102_handle_t handle = calloc(1, sizeof(*handle));
    if (handle == NULL) {
        return ESP_ERR_NO_MEM;
    }

    handle->config = *config;
    handle->lock = xSemaphoreCreateMutex();
    if (handle->lock == NULL) {
        free(handle);
        return ESP_ERR_NO_MEM;
    }

    i2c_config_t i2c_cfg = {
        .mode = I2C_MODE_MASTER,
        .sda_io_num = config->sda_gpio,
        .scl_io_num = config->scl_gpio,
        .sda_pullup_en = GPIO_PULLUP_DISABLE,
        .scl_pullup_en = GPIO_PULLUP_DISABLE,
        .master.clk_speed = config->i2c_clk_hz,
        .clk_flags = 0,
    };

    esp_err_t ret = i2c_param_config(config->i2c_port, &i2c_cfg);
    if (ret != ESP_OK) {
        vSemaphoreDelete(handle->lock);
        free(handle);
        return ret;
    }

    ret = i2c_driver_install(config->i2c_port, I2C_MODE_MASTER, 0, 0, 0);
    if ((ret != ESP_OK) && (ret != ESP_ERR_INVALID_STATE)) {
        vSemaphoreDelete(handle->lock);
        free(handle);
        return ret;
    }

    *out_handle = handle;
    ESP_LOGI(TAG, "MAX30102 I2C init on port %d addr 0x%02X", config->i2c_port, config->i2c_address);
    return ESP_OK;
}

esp_err_t max30102_read_register(max30102_handle_t handle, uint8_t reg, uint8_t *value)
{
    if ((handle == NULL) || (value == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    const esp_err_t ret = max30102_read_reg_locked(handle, reg, value);
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_write_register(max30102_handle_t handle, uint8_t reg, uint8_t value)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    const esp_err_t ret = max30102_write_reg_locked(handle, reg, value);
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_read_part_info(max30102_handle_t handle, uint8_t *part_id, uint8_t *rev_id)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = ESP_OK;
    if (rev_id != NULL) {
        ret = max30102_read_reg_locked(handle, MAX30102_REG_REV_ID, rev_id);
    }
    if ((ret == ESP_OK) && (part_id != NULL)) {
        ret = max30102_read_reg_locked(handle, MAX30102_REG_PART_ID, part_id);
    }
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_reset(max30102_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = max30102_write_reg_locked(handle, MAX30102_REG_MODE_CONFIG, MAX30102_MODE_RESET);
    if (ret != ESP_OK) {
        xSemaphoreGive(handle->lock);
        return ret;
    }

    uint8_t mode = MAX30102_MODE_RESET;
    for (uint32_t i = 0; i < 50U; ++i) {
        vTaskDelay(pdMS_TO_TICKS(2));
        ret = max30102_read_reg_locked(handle, MAX30102_REG_MODE_CONFIG, &mode);
        if (ret != ESP_OK) {
            xSemaphoreGive(handle->lock);
            return ret;
        }
        if ((mode & MAX30102_MODE_RESET) == 0U) {
            xSemaphoreGive(handle->lock);
            return ESP_OK;
        }
    }

    xSemaphoreGive(handle->lock);
    return ESP_ERR_TIMEOUT;
}

esp_err_t max30102_shutdown(max30102_handle_t handle, bool shutdown)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    uint8_t mode = 0;
    esp_err_t ret = max30102_read_reg_locked(handle, MAX30102_REG_MODE_CONFIG, &mode);
    if (ret == ESP_OK) {
        if (shutdown) {
            mode |= MAX30102_MODE_SHDN;
        } else {
            mode &= (uint8_t)~MAX30102_MODE_SHDN;
        }
        ret = max30102_write_reg_locked(handle, MAX30102_REG_MODE_CONFIG, mode);
    }
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_clear_fifo(max30102_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = max30102_write_reg_locked(handle, MAX30102_REG_FIFO_WR_PTR, 0x00);
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_OVF_COUNTER, 0x00);
    }
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_FIFO_RD_PTR, 0x00);
    }
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_set_led_currents(max30102_handle_t handle, uint8_t red_pa, uint8_t ir_pa)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = max30102_write_reg_locked(handle, MAX30102_REG_LED1_PA, red_pa);
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_LED2_PA, ir_pa);
    }
    if (ret == ESP_OK) {
        handle->config.red_led_pa = red_pa;
        handle->config.ir_led_pa = ir_pa;
    }
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_set_temperature_enabled(max30102_handle_t handle, bool enabled)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    uint8_t value = enabled ? MAX30102_INT_DIE_TEMP_RDY : 0x00;
    esp_err_t ret = max30102_write_reg_locked(handle, MAX30102_REG_INTR_ENABLE_2, value);
    if (ret == ESP_OK) {
        handle->config.enable_temperature_interrupt = enabled;
    }
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_config_default(max30102_handle_t handle)
{
    if (handle == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    esp_err_t ret = max30102_shutdown(handle, false);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = max30102_reset(handle);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = max30102_clear_fifo(handle);
    if (ret != ESP_OK) {
        return ret;
    }

    const uint8_t fifo_cfg = (uint8_t)((handle->config.sample_average << MAX30102_FIFO_SMP_AVE_SHIFT) |
                                       (handle->config.fifo_a_full & MAX30102_FIFO_A_FULL_MASK));
    const uint8_t spo2_cfg = (uint8_t)((handle->config.adc_range << MAX30102_SPO2_ADC_RGE_SHIFT) |
                                       (handle->config.sample_rate << MAX30102_SPO2_SR_SHIFT) |
                                       (handle->config.pulse_width & MAX30102_SPO2_LED_PW_MASK));
    const uint8_t intr1 = (uint8_t)(MAX30102_INT_A_FULL | MAX30102_INT_ALC_OVF);
    const uint8_t intr2 = handle->config.enable_temperature_interrupt ? MAX30102_INT_DIE_TEMP_RDY : 0x00;

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    ret = max30102_write_reg_locked(handle, MAX30102_REG_INTR_ENABLE_1, intr1);
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_INTR_ENABLE_2, intr2);
    }
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_FIFO_CONFIG, fifo_cfg);
    }
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_SPO2_CONFIG, spo2_cfg);
    }
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_LED1_PA, handle->config.red_led_pa);
    }
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_LED2_PA, handle->config.ir_led_pa);
    }
    if (ret == ESP_OK) {
        ret = max30102_write_reg_locked(handle, MAX30102_REG_MODE_CONFIG, MAX30102_MODE_SPO2);
    }
    if (ret == ESP_OK) {
        uint8_t dummy[2] = {0};
        ret = max30102_read_locked(handle, MAX30102_REG_INTR_STATUS_1, dummy, sizeof(dummy));
    }
    xSemaphoreGive(handle->lock);
    return ret;
}

esp_err_t max30102_get_interrupt_status(max30102_handle_t handle, max30102_interrupt_status_t *status)
{
    if ((handle == NULL) || (status == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    uint8_t raw[2] = {0};
    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = max30102_read_locked(handle, MAX30102_REG_INTR_STATUS_1, raw, sizeof(raw));
    xSemaphoreGive(handle->lock);
    if (ret != ESP_OK) {
        return ret;
    }

    status->status1 = raw[0];
    status->status2 = raw[1];
    return ESP_OK;
}

esp_err_t max30102_get_fifo_state(max30102_handle_t handle, max30102_fifo_state_t *state)
{
    if ((handle == NULL) || (state == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    uint8_t raw[3] = {0};
    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = max30102_read_locked(handle, MAX30102_REG_FIFO_WR_PTR, raw, sizeof(raw));
    xSemaphoreGive(handle->lock);
    if (ret != ESP_OK) {
        return ret;
    }

    state->wr_ptr = raw[0] & 0x1FU;
    state->ovf_counter = raw[1] & 0x1FU;
    state->rd_ptr = raw[2] & 0x1FU;
    state->unread_samples = (uint8_t)((state->wr_ptr - state->rd_ptr) & 0x1FU);
    return ESP_OK;
}

esp_err_t max30102_read_fifo_samples(max30102_handle_t handle,
                                     max30102_fifo_sample_t *samples,
                                     size_t max_samples,
                                     size_t *samples_read,
                                     max30102_fifo_state_t *state_after)
{
    if ((handle == NULL) || (samples == NULL) || (samples_read == NULL) || (max_samples == 0U)) {
        return ESP_ERR_INVALID_ARG;
    }

    max30102_fifo_state_t state = {0};
    esp_err_t ret = max30102_get_fifo_state(handle, &state);
    if (ret != ESP_OK) {
        return ret;
    }

    size_t to_read = state.unread_samples;
    if (to_read > max_samples) {
        to_read = max_samples;
    }
    if (to_read == 0U) {
        *samples_read = 0;
        if (state_after != NULL) {
            *state_after = state;
        }
        return ESP_OK;
    }

    const size_t bytes_to_read = to_read * 6U;
    uint8_t fifo_bytes[6U * 8U] = {0};
    if (bytes_to_read > sizeof(fifo_bytes)) {
        return ESP_ERR_INVALID_SIZE;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    ret = max30102_read_locked(handle, MAX30102_REG_FIFO_DATA, fifo_bytes, bytes_to_read);
    xSemaphoreGive(handle->lock);
    if (ret != ESP_OK) {
        return ret;
    }

    for (size_t i = 0; i < to_read; ++i) {
        const size_t base = i * 6U;
        samples[i].red = ((((uint32_t)fifo_bytes[base + 0U]) << 16) |
                          (((uint32_t)fifo_bytes[base + 1U]) << 8) |
                          ((uint32_t)fifo_bytes[base + 2U])) & 0x3FFFFU;
        samples[i].ir = ((((uint32_t)fifo_bytes[base + 3U]) << 16) |
                         (((uint32_t)fifo_bytes[base + 4U]) << 8) |
                         ((uint32_t)fifo_bytes[base + 5U])) & 0x3FFFFU;
    }

    *samples_read = to_read;
    if (state_after != NULL) {
        ret = max30102_get_fifo_state(handle, state_after);
        if (ret != ESP_OK) {
            return ret;
        }
    }
    return ESP_OK;
}

esp_err_t max30102_read_temperature_optional(max30102_handle_t handle, float *temp_c)
{
    if ((handle == NULL) || (temp_c == NULL)) {
        return ESP_ERR_INVALID_ARG;
    }

    xSemaphoreTake(handle->lock, portMAX_DELAY);
    esp_err_t ret = max30102_write_reg_locked(handle, MAX30102_REG_TEMP_CONFIG, MAX30102_TEMP_EN);
    xSemaphoreGive(handle->lock);
    if (ret != ESP_OK) {
        return ret;
    }

    uint8_t temp_cfg = MAX30102_TEMP_EN;
    for (uint32_t i = 0; i < 20U; ++i) {
        vTaskDelay(pdMS_TO_TICKS(5));
        xSemaphoreTake(handle->lock, portMAX_DELAY);
        ret = max30102_read_reg_locked(handle, MAX30102_REG_TEMP_CONFIG, &temp_cfg);
        xSemaphoreGive(handle->lock);
        if (ret != ESP_OK) {
            return ret;
        }
        if ((temp_cfg & MAX30102_TEMP_EN) == 0U) {
            break;
        }
    }

    uint8_t raw[2] = {0};
    xSemaphoreTake(handle->lock, portMAX_DELAY);
    ret = max30102_read_locked(handle, MAX30102_REG_TEMP_INTEGER, raw, sizeof(raw));
    xSemaphoreGive(handle->lock);
    if (ret != ESP_OK) {
        return ret;
    }

    const int8_t integer = (int8_t)raw[0];
    const float fraction = (float)(raw[1] & 0x0FU) * 0.0625f;
    *temp_c = (float)integer + fraction;
    return ESP_OK;
}
