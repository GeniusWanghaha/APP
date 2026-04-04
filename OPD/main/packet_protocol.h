#pragma once

#include <stdint.h>

#include "app_config.h"

typedef enum {
    PACKET_MSG_TYPE_DATA_SEGMENT = 0xA1,
} packet_message_type_t;

typedef struct __attribute__((packed)) {
    uint16_t frame_id;
    uint64_t t_base_us;
    uint8_t ecg_count;
    uint8_t ppg_count;
    uint16_t state_flags;
    uint16_t ecg_raw[APP_ECG_SAMPLES_PER_FRAME];
    uint8_t ppg_red[APP_PPG_SAMPLES_PER_FRAME][3];
    uint8_t ppg_ir[APP_PPG_SAMPLES_PER_FRAME][3];
    uint16_t crc16;
} packet_protocol_frame_t;

typedef struct __attribute__((packed)) {
    uint8_t protocol_version;
    uint8_t message_type;
    uint16_t frame_id;
    uint8_t segment_index;
    uint8_t segment_count;
    uint16_t payload_len;
} packet_segment_header_t;

static inline void packet_write_u24(uint8_t out[3], uint32_t value)
{
    out[0] = (uint8_t)((value >> 16) & 0xFFU);
    out[1] = (uint8_t)((value >> 8) & 0xFFU);
    out[2] = (uint8_t)(value & 0xFFU);
}

static inline uint32_t packet_read_u24(const uint8_t in[3])
{
    return (((uint32_t)in[0]) << 16) | (((uint32_t)in[1]) << 8) | ((uint32_t)in[2]);
}

_Static_assert(sizeof(packet_protocol_frame_t) == APP_MAX_BLE_FRAME_BYTES,
               "Unexpected packed frame size");
_Static_assert(sizeof(packet_segment_header_t) == APP_MAX_BLE_SEGMENT_HEADER_BYTES,
               "Unexpected segment header size");
