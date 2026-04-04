#include "app_tasks.h"

#include "esp_log.h"
#include "nvs_flash.h"
#include "state_monitor.h"
#include "timebase.h"

static const char *TAG = "app_main";

void app_main(void)
{
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    ESP_ERROR_CHECK(state_monitor_init());
    ESP_ERROR_CHECK(timebase_init());
    state_monitor_note_self_test(STATE_MON_SELF_TEST_TIMEBASE, timebase_self_test());

    ESP_ERROR_CHECK(app_tasks_init());
    ESP_LOGI(TAG, "System initialized; waiting for BLE control command to start streaming");
}
