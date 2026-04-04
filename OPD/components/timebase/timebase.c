#include "timebase.h"

#include "driver/gptimer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

typedef struct {
    gptimer_handle_t timer;
    bool ready;
} timebase_ctx_t;

static timebase_ctx_t s_ctx;

esp_err_t timebase_init(void)
{
    gptimer_config_t config = {
        .clk_src = GPTIMER_CLK_SRC_DEFAULT,
        .direction = GPTIMER_COUNT_UP,
        .resolution_hz = 1000000,
    };

    esp_err_t ret = gptimer_new_timer(&config, &s_ctx.timer);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = gptimer_enable(s_ctx.timer);
    if (ret != ESP_OK) {
        return ret;
    }

    ret = gptimer_start(s_ctx.timer);
    if (ret != ESP_OK) {
        return ret;
    }

    s_ctx.ready = true;
    return ESP_OK;
}

uint64_t timebase_get_time_us(void)
{
    uint64_t count = 0;
    if (!s_ctx.ready) {
        return 0;
    }

    (void)gptimer_get_raw_count(s_ctx.timer, &count);
    return count;
}

bool timebase_is_ready(void)
{
    return s_ctx.ready;
}

bool timebase_self_test(void)
{
    const uint64_t t0 = timebase_get_time_us();
    vTaskDelay(pdMS_TO_TICKS(2));
    return timebase_get_time_us() > t0;
}
