#include "ble_service.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "host/ble_gap.h"
#include "host/ble_gatt.h"
#include "host/ble_hs.h"
#include "host/ble_hs_mbuf.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "os/os_mbuf.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
#include "store/config/ble_store_config.h"

#define BLE_SERVICE_CHAR_DATA    0
#define BLE_SERVICE_CHAR_CONTROL 1
#define BLE_SERVICE_CHAR_STATUS  2

static const char *TAG = "ble_service";

static const ble_uuid128_t g_service_uuid =
    BLE_UUID128_INIT(0xF0, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12,
                     0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12);
static const ble_uuid128_t g_data_char_uuid =
    BLE_UUID128_INIT(0xF1, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12,
                     0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12);
static const ble_uuid128_t g_control_char_uuid =
    BLE_UUID128_INIT(0xF2, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12,
                     0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12);
static const ble_uuid128_t g_status_char_uuid =
    BLE_UUID128_INIT(0xF3, 0xDE, 0xBC, 0x9A, 0x78, 0x56, 0x34, 0x12,
                     0x78, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12);

static ble_service_config_t s_config;
static uint16_t s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
static uint16_t s_data_val_handle;
static uint16_t s_control_val_handle;
static uint16_t s_status_val_handle;
static uint8_t s_own_addr_type;
static bool s_data_notify_enabled;
static bool s_status_notify_enabled;

static int ble_service_gap_event(struct ble_gap_event *event, void *arg);
static void ble_service_advertise(void);
void ble_store_config_init(void);

static int ble_service_handle_data_access(struct ble_gatt_access_ctxt *ctxt)
{
    (void)ctxt;
    return BLE_ATT_ERR_READ_NOT_PERMITTED;
}

static int ble_service_handle_control_read(struct ble_gatt_access_ctxt *ctxt)
{
    if (s_config.fill_control_snapshot == NULL) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    ble_control_snapshot_t snapshot = {0};
    if (s_config.fill_control_snapshot(&snapshot) != ESP_OK) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    if (os_mbuf_append(ctxt->om, &snapshot, sizeof(snapshot)) != 0) {
        return BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    return 0;
}

static int ble_service_handle_status_read(struct ble_gatt_access_ctxt *ctxt)
{
    if (s_config.fill_status_snapshot == NULL) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    ble_status_snapshot_t snapshot = {0};
    if (s_config.fill_status_snapshot(&snapshot) != ESP_OK) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    if (os_mbuf_append(ctxt->om, &snapshot, sizeof(snapshot)) != 0) {
        return BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    return 0;
}

static int ble_service_handle_control_write(struct ble_gatt_access_ctxt *ctxt)
{
    if (s_config.control_queue == NULL) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    const uint16_t len = OS_MBUF_PKTLEN(ctxt->om);
    uint8_t buffer[8] = {0};
    uint16_t out_len = 0;
    if ((len == 0U) || (len > sizeof(buffer))) {
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }
    if (ble_hs_mbuf_to_flat(ctxt->om, buffer, sizeof(buffer), &out_len) != 0) {
        return BLE_ATT_ERR_UNLIKELY;
    }

    ble_control_event_t event = {0};
    event.opcode = (ble_control_opcode_t)buffer[0];

    switch (event.opcode) {
        case BLE_CONTROL_OP_START_STREAM:
        case BLE_CONTROL_OP_STOP_STREAM:
        case BLE_CONTROL_OP_GET_INFO:
        case BLE_CONTROL_OP_SELF_TEST:
        case BLE_CONTROL_OP_SYNC_MARK:
            if (out_len != 1U) {
                return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            }
            break;
        case BLE_CONTROL_OP_SET_LED_PA:
            if (out_len != 3U) {
                return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            }
            event.value.led.red_led_pa = buffer[1];
            event.value.led.ir_led_pa = buffer[2];
            break;
        case BLE_CONTROL_OP_SET_PPG_PHASE:
        case BLE_CONTROL_OP_SET_PPG_LATENCY:
            if (out_len != 5U) {
                return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            }
            event.value.i32 = (int32_t)((uint32_t)buffer[1] |
                                        ((uint32_t)buffer[2] << 8) |
                                        ((uint32_t)buffer[3] << 16) |
                                        ((uint32_t)buffer[4] << 24));
            break;
        case BLE_CONTROL_OP_SET_TEMP_EN:
        case BLE_CONTROL_OP_SET_PPG_MODE:
            if (out_len != 2U) {
                return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            }
            event.value.boolean = (buffer[1] != 0U);
            break;
        case BLE_CONTROL_OP_SET_LOG_LEVEL:
            if (out_len != 2U) {
                return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
            }
            event.value.u8 = buffer[1];
            break;
        default:
            return BLE_ATT_ERR_UNLIKELY;
    }

    if (xQueueSend(s_config.control_queue, &event, 0) != pdTRUE) {
        return BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    return 0;
}

static int ble_service_access_cb(uint16_t conn_handle,
                                 uint16_t attr_handle,
                                 struct ble_gatt_access_ctxt *ctxt,
                                 void *arg)
{
    (void)conn_handle;
    (void)attr_handle;
    const intptr_t which = (intptr_t)arg;

    switch (which) {
        case BLE_SERVICE_CHAR_DATA:
            return ble_service_handle_data_access(ctxt);
        case BLE_SERVICE_CHAR_CONTROL:
            if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
                return ble_service_handle_control_read(ctxt);
            }
            if (ctxt->op == BLE_GATT_ACCESS_OP_WRITE_CHR) {
                return ble_service_handle_control_write(ctxt);
            }
            break;
        case BLE_SERVICE_CHAR_STATUS:
            if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
                return ble_service_handle_status_read(ctxt);
            }
            break;
        default:
            break;
    }

    return BLE_ATT_ERR_UNLIKELY;
}

static const struct ble_gatt_svc_def gatt_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &g_service_uuid.u,
        .characteristics = (struct ble_gatt_chr_def[]) {
            {
                .uuid = &g_data_char_uuid.u,
                .access_cb = ble_service_access_cb,
                .arg = (void *)(intptr_t)BLE_SERVICE_CHAR_DATA,
                .val_handle = &s_data_val_handle,
                .flags = BLE_GATT_CHR_F_NOTIFY,
            },
            {
                .uuid = &g_control_char_uuid.u,
                .access_cb = ble_service_access_cb,
                .arg = (void *)(intptr_t)BLE_SERVICE_CHAR_CONTROL,
                .val_handle = &s_control_val_handle,
                .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_WRITE | BLE_GATT_CHR_F_WRITE_NO_RSP,
            },
            {
                .uuid = &g_status_char_uuid.u,
                .access_cb = ble_service_access_cb,
                .arg = (void *)(intptr_t)BLE_SERVICE_CHAR_STATUS,
                .val_handle = &s_status_val_handle,
                .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
            },
            { 0 }
        },
    },
    { 0 }
};

static void ble_service_host_task(void *param)
{
    (void)param;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

static void ble_service_on_sync(void)
{
    if (ble_hs_id_infer_auto(0, &s_own_addr_type) == 0) {
        ble_service_advertise();
    }
}

static void ble_service_on_reset(int reason)
{
    ESP_LOGW(TAG, "NimBLE reset reason=%d", reason);
}

static void ble_service_advertise(void)
{
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.tx_pwr_lvl_is_present = 1;
    fields.tx_pwr_lvl = BLE_HS_ADV_TX_PWR_LVL_AUTO;
    fields.uuids128 = (ble_uuid128_t *)&g_service_uuid;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    int rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0) {
        ESP_LOGW(TAG, "adv_set_fields rc=%d", rc);
    }
    struct ble_hs_adv_fields rsp_fields = {0};
    rsp_fields.name = (uint8_t *)CONFIG_APP_BLE_DEVICE_NAME;
    rsp_fields.name_len = (uint8_t)strlen(CONFIG_APP_BLE_DEVICE_NAME);
    rsp_fields.name_is_complete = 1;
    rc = ble_gap_adv_rsp_set_fields(&rsp_fields);
    if (rc != 0) {
        ESP_LOGW(TAG, "adv_rsp_set_fields rc=%d", rc);
    }

    struct ble_gap_adv_params adv_params = {
        .conn_mode = BLE_GAP_CONN_MODE_UND,
        .disc_mode = BLE_GAP_DISC_MODE_GEN,
    };

    rc = ble_gap_adv_start(s_own_addr_type, NULL, BLE_HS_FOREVER, &adv_params, ble_service_gap_event, NULL);
    if (rc != 0) {
        ESP_LOGW(TAG, "adv_start rc=%d", rc);
    }
}

static int ble_service_gap_event(struct ble_gap_event *event, void *arg)
{
    (void)arg;

    switch (event->type) {
        case BLE_GAP_EVENT_CONNECT:
            if (event->connect.status == 0) {
                s_conn_handle = event->connect.conn_handle;
                if (s_config.connection_cb != NULL) {
                    s_config.connection_cb(true, (uint16_t)ble_att_mtu(s_conn_handle));
                }
                ESP_LOGI(TAG, "BLE connected, mtu=%u", (unsigned)ble_att_mtu(s_conn_handle));
            } else {
                ble_service_advertise();
            }
            return 0;
        case BLE_GAP_EVENT_DISCONNECT: {
            s_conn_handle = BLE_HS_CONN_HANDLE_NONE;
            s_data_notify_enabled = false;
            s_status_notify_enabled = false;
            if (s_config.connection_cb != NULL) {
                s_config.connection_cb(false, 0);
            }
            ble_control_event_t stop_evt = {.opcode = BLE_CONTROL_OP_STOP_STREAM};
            if (s_config.control_queue != NULL) {
                (void)xQueueSend(s_config.control_queue, &stop_evt, 0);
            }
            ble_service_advertise();
            return 0;
        }
        case BLE_GAP_EVENT_SUBSCRIBE:
            if (event->subscribe.attr_handle == s_data_val_handle) {
                s_data_notify_enabled = event->subscribe.cur_notify != 0;
            }
            if (event->subscribe.attr_handle == s_status_val_handle) {
                s_status_notify_enabled = event->subscribe.cur_notify != 0;
            }
            return 0;
        case BLE_GAP_EVENT_MTU:
            if (s_config.connection_cb != NULL) {
                s_config.connection_cb(true, event->mtu.value);
            }
            return 0;
        case BLE_GAP_EVENT_ADV_COMPLETE:
            ble_service_advertise();
            return 0;
        default:
            return 0;
    }
}

esp_err_t ble_service_init(const ble_service_config_t *config)
{
    if (config == NULL) {
        return ESP_ERR_INVALID_ARG;
    }

    s_config = *config;

    esp_err_t ret = nimble_port_init();
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "nimble_port_init failed: %s", esp_err_to_name(ret));
        return ret;
    }
    ble_hs_cfg.reset_cb = ble_service_on_reset;
    ble_hs_cfg.sync_cb = ble_service_on_sync;
    ble_att_set_preferred_mtu(CONFIG_APP_BLE_PREFERRED_MTU);

    ble_svc_gap_init();
    ble_svc_gatt_init();
    ble_svc_gap_device_name_set(CONFIG_APP_BLE_DEVICE_NAME);
    ble_store_config_init();

    int rc = ble_gatts_count_cfg(gatt_svcs);
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_gatts_count_cfg rc=%d", rc);
        return ESP_FAIL;
    }
    rc = ble_gatts_add_svcs(gatt_svcs);
    if (rc != 0) {
        ESP_LOGE(TAG, "ble_gatts_add_svcs rc=%d", rc);
        return ESP_FAIL;
    }

    nimble_port_freertos_init(ble_service_host_task);
    return ESP_OK;
}

bool ble_service_is_connected(void)
{
    return s_conn_handle != BLE_HS_CONN_HANDLE_NONE;
}

bool ble_service_is_data_notify_enabled(void)
{
    return s_data_notify_enabled;
}

bool ble_service_is_status_notify_enabled(void)
{
    return s_status_notify_enabled;
}

uint16_t ble_service_get_att_payload_mtu(void)
{
    const uint16_t mtu = ble_service_is_connected() ? (uint16_t)ble_att_mtu(s_conn_handle) : CONFIG_APP_BLE_PREFERRED_MTU;
    return (mtu > 3U) ? (uint16_t)(mtu - 3U) : 20U;
}

static esp_err_t ble_service_notify_flat(uint16_t value_handle, const void *data, size_t length)
{
    if (!ble_service_is_connected()) {
        return ESP_ERR_INVALID_STATE;
    }

    struct os_mbuf *om = ble_hs_mbuf_from_flat(data, (uint16_t)length);
    if (om == NULL) {
        return ESP_ERR_NO_MEM;
    }

    const int rc = ble_gatts_notify_custom(s_conn_handle, value_handle, om);
    if (rc != 0) {
        os_mbuf_free_chain(om);
    }
    return (rc == 0) ? ESP_OK : ESP_FAIL;
}

esp_err_t ble_service_notify_data_segment(const uint8_t *data, size_t length)
{
    if ((data == NULL) || (length == 0U)) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!s_data_notify_enabled) {
        return ESP_ERR_INVALID_STATE;
    }
    return ble_service_notify_flat(s_data_val_handle, data, length);
}

esp_err_t ble_service_notify_status(const ble_status_snapshot_t *status)
{
    if (status == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    if (!s_status_notify_enabled) {
        return ESP_ERR_INVALID_STATE;
    }
    return ble_service_notify_flat(s_status_val_handle, status, sizeof(*status));
}




