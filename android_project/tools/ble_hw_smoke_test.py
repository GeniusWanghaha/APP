#!/usr/bin/env python3
"""BLE hardware smoke test for ECG/PPG firmware.

This script validates a connected ESP32-S3 device end-to-end by:
1. Scanning and connecting via BLE.
2. Reading control/status snapshots.
3. Sending START/STOP and SET_CFG opcodes.
4. Verifying status counters/fields update as expected.
5. Restoring original runtime config before exit.

Usage:
    python tools/ble_hw_smoke_test.py
"""

from __future__ import annotations

import argparse
import asyncio
import struct
import sys
import time
from dataclasses import dataclass

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
DATA_CHAR_UUID = "12345678-1234-5678-1234-56789abcdef1"
CONTROL_CHAR_UUID = "12345678-1234-5678-1234-56789abcdef2"
STATUS_CHAR_UUID = "12345678-1234-5678-1234-56789abcdef3"

OP_START = 0x01
OP_STOP = 0x02
OP_SET_LED_PA = 0x10
OP_SET_PPG_PHASE = 0x11
OP_SET_PPG_LATENCY = 0x12
OP_SET_TEMP_EN = 0x13
OP_SET_LOG_LEVEL = 0x14
OP_SET_PPG_MODE = 0x15
OP_GET_INFO = 0x20
OP_SELF_TEST = 0x21
OP_SYNC_MARK = 0x22

STATUS_FMT = "<BBH" + ("I" * 13) + ("H" * 7) + "BBiiBBBB"
CONTROL_FMT = "<BBBBiiBBB"
STATUS_SIZE = struct.calcsize(STATUS_FMT)
CONTROL_SIZE = struct.calcsize(CONTROL_FMT)
SEGMENT_HEADER_SIZE = 8
FRAME_SYNC_MARK_BIT = 0x0200


@dataclass(frozen=True)
class StatusSnapshot:
    protocol_version: int
    streaming_enabled: bool
    state_flags: int
    self_test_pass_bitmap: int
    self_test_fail_bitmap: int
    ppg_fifo_overflow_count: int
    ppg_int_timeout_count: int
    ble_backpressure_count: int
    ble_dropped_frame_count: int
    i2c_error_count: int
    adc_saturation_count: int
    ecg_ring_drop_count: int
    ppg_ring_drop_count: int
    generated_frame_count: int
    transmitted_frame_count: int
    frame_sequence_error_count: int
    ecg_ring_items: int
    ppg_ring_items: int
    ble_queue_items: int
    ecg_ring_high_watermark: int
    ppg_ring_high_watermark: int
    ble_queue_high_watermark: int
    mtu: int
    red_led_pa: int
    ir_led_pa: int
    ppg_phase_us: int
    ppg_latency_us: int
    temperature_enabled: bool
    log_level: int
    sensor_ready: bool
    finger_detected: bool


@dataclass(frozen=True)
class ControlSnapshot:
    protocol_version: int
    streaming_enabled: bool
    red_led_pa: int
    ir_led_pa: int
    ppg_phase_us: int
    ppg_latency_us: int
    temperature_enabled: bool
    log_level: int
    ppg_int_mode: bool


def parse_status(payload: bytes) -> StatusSnapshot:
    if len(payload) != STATUS_SIZE:
        raise ValueError(f"Unexpected status payload size: {len(payload)} != {STATUS_SIZE}")
    fields = struct.unpack(STATUS_FMT, payload)
    return StatusSnapshot(
        protocol_version=fields[0],
        streaming_enabled=bool(fields[1]),
        state_flags=fields[2],
        self_test_pass_bitmap=fields[3],
        self_test_fail_bitmap=fields[4],
        ppg_fifo_overflow_count=fields[5],
        ppg_int_timeout_count=fields[6],
        ble_backpressure_count=fields[7],
        ble_dropped_frame_count=fields[8],
        i2c_error_count=fields[9],
        adc_saturation_count=fields[10],
        ecg_ring_drop_count=fields[11],
        ppg_ring_drop_count=fields[12],
        generated_frame_count=fields[13],
        transmitted_frame_count=fields[14],
        frame_sequence_error_count=fields[15],
        ecg_ring_items=fields[16],
        ppg_ring_items=fields[17],
        ble_queue_items=fields[18],
        ecg_ring_high_watermark=fields[19],
        ppg_ring_high_watermark=fields[20],
        ble_queue_high_watermark=fields[21],
        mtu=fields[22],
        red_led_pa=fields[23],
        ir_led_pa=fields[24],
        ppg_phase_us=fields[25],
        ppg_latency_us=fields[26],
        temperature_enabled=bool(fields[27]),
        log_level=fields[28],
        sensor_ready=bool(fields[29]),
        finger_detected=bool(fields[30]),
    )


def parse_control(payload: bytes) -> ControlSnapshot:
    if len(payload) != CONTROL_SIZE:
        raise ValueError(f"Unexpected control payload size: {len(payload)} != {CONTROL_SIZE}")
    fields = struct.unpack(CONTROL_FMT, payload)
    return ControlSnapshot(
        protocol_version=fields[0],
        streaming_enabled=bool(fields[1]),
        red_led_pa=fields[2],
        ir_led_pa=fields[3],
        ppg_phase_us=fields[4],
        ppg_latency_us=fields[5],
        temperature_enabled=bool(fields[6]),
        log_level=fields[7],
        ppg_int_mode=bool(fields[8]),
    )


async def find_device(name_prefix: str, timeout: float):
    deadline = time.time() + timeout
    while time.time() < deadline:
        devices = await BleakScanner.discover(timeout=4.0)
        for dev in devices:
            if dev.name and dev.name.startswith(name_prefix):
                return dev
    return None


async def run_smoke(name_prefix: str, timeout: float, demo_seconds: float) -> int:
    print(f"[INFO] scanning BLE devices by prefix: {name_prefix}")
    device = await find_device(name_prefix=name_prefix, timeout=timeout)
    if device is None:
        print("[FAIL] target BLE device not found")
        return 2

    print(f"[INFO] found: name={device.name} address={device.address}")
    data_packets = 0
    status_packets = 0
    sync_mark_seen = False
    frame_segments: dict[int, dict[int, bytes]] = {}
    frame_segment_count: dict[int, int] = {}

    def on_data(_: int, data: bytearray):
        nonlocal data_packets, sync_mark_seen
        data_packets += 1
        raw = bytes(data)
        if len(raw) < SEGMENT_HEADER_SIZE:
            return
        if raw[1] != 0xA1:
            return
        frame_id = int.from_bytes(raw[2:4], "little")
        seg_idx = raw[4]
        seg_count = raw[5]
        payload_len = int.from_bytes(raw[6:8], "little")
        segment_payload = raw[8 : 8 + payload_len]
        if len(segment_payload) != payload_len:
            return
        frame_segments.setdefault(frame_id, {})[seg_idx] = segment_payload
        frame_segment_count[frame_id] = seg_count
        if len(frame_segments[frame_id]) == seg_count:
            payload = b"".join(frame_segments[frame_id][i] for i in range(seg_count))
            if len(payload) >= 14:
                state_flags = int.from_bytes(payload[12:14], "little")
                if (state_flags & FRAME_SYNC_MARK_BIT) != 0:
                    sync_mark_seen = True
            frame_segments.pop(frame_id, None)
            frame_segment_count.pop(frame_id, None)

    def on_status(_: int, data: bytearray):
        nonlocal status_packets
        status_packets += 1

    async with BleakClient(device.address, timeout=20.0) as client:
        print(f"[INFO] connected={client.is_connected}")
        svcs = client.services
        if SERVICE_UUID not in [s.uuid for s in svcs]:
            print("[FAIL] required service UUID not found")
            return 3

        await client.start_notify(DATA_CHAR_UUID, on_data)
        await client.start_notify(STATUS_CHAR_UUID, on_status)

        control_before = parse_control(await client.read_gatt_char(CONTROL_CHAR_UUID))
        status_before = parse_status(await client.read_gatt_char(STATUS_CHAR_UUID))
        print(
            "[INFO] before: "
            f"stream={status_before.streaming_enabled}, "
            f"phase={status_before.ppg_phase_us}, latency={status_before.ppg_latency_us}, "
            f"temp={status_before.temperature_enabled}, log={status_before.log_level}"
        )

        await client.write_gatt_char(CONTROL_CHAR_UUID, bytes([OP_START]), response=False)
        await asyncio.sleep(demo_seconds)
        status_after_start = parse_status(await client.read_gatt_char(STATUS_CHAR_UUID))

        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_PPG_PHASE]) + struct.pack("<i", 2200),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_PPG_LATENCY]) + struct.pack("<i", 6100),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_LED_PA, 0x28, 0x2A]),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_TEMP_EN, 0x01]),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_LOG_LEVEL, 0x03]),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_PPG_MODE, 0x01]),
            response=False,
        )
        await asyncio.sleep(1.5)

        control_after_cfg = parse_control(await client.read_gatt_char(CONTROL_CHAR_UUID))
        status_after_cfg = parse_status(await client.read_gatt_char(STATUS_CHAR_UUID))

        status_before_get_info = status_packets
        await client.write_gatt_char(CONTROL_CHAR_UUID, bytes([OP_GET_INFO]), response=False)
        await asyncio.sleep(1.0)
        status_after_get_info = status_packets

        await client.write_gatt_char(CONTROL_CHAR_UUID, bytes([OP_SELF_TEST]), response=False)
        await asyncio.sleep(1.0)
        status_after_self_test = parse_status(await client.read_gatt_char(STATUS_CHAR_UUID))

        # SELF_TEST currently stops streaming in firmware for safety, restart it for SYNC_MARK check.
        await client.write_gatt_char(CONTROL_CHAR_UUID, bytes([OP_START]), response=False)
        await asyncio.sleep(1.0)
        sync_mark_seen = False
        await client.write_gatt_char(CONTROL_CHAR_UUID, bytes([OP_SYNC_MARK]), response=False)
        await asyncio.sleep(1.5)
        status_after_sync_mark = parse_status(await client.read_gatt_char(STATUS_CHAR_UUID))

        # Restore original runtime configuration to avoid leaving device in a modified state.
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_PPG_PHASE]) + struct.pack("<i", control_before.ppg_phase_us),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_PPG_LATENCY]) + struct.pack("<i", control_before.ppg_latency_us),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_LED_PA, control_before.red_led_pa, control_before.ir_led_pa]),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_TEMP_EN, 0x01 if control_before.temperature_enabled else 0x00]),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_LOG_LEVEL, control_before.log_level & 0xFF]),
            response=False,
        )
        await client.write_gatt_char(
            CONTROL_CHAR_UUID,
            bytes([OP_SET_PPG_MODE, 0x01 if control_before.ppg_int_mode else 0x00]),
            response=False,
        )
        await asyncio.sleep(1.0)

        await client.write_gatt_char(CONTROL_CHAR_UUID, bytes([OP_STOP]), response=False)
        await asyncio.sleep(1.5)
        status_after_stop = parse_status(await client.read_gatt_char(STATUS_CHAR_UUID))

        await client.stop_notify(DATA_CHAR_UUID)
        await client.stop_notify(STATUS_CHAR_UUID)

    failures = []
    if not status_after_start.streaming_enabled:
        failures.append("START 后 streaming_enabled 未变为 true")
    if data_packets <= 0:
        failures.append("START 后未收到数据通知")
    if status_after_cfg.ppg_phase_us != 2200:
        failures.append("SET_PPG_PHASE 未生效")
    if status_after_cfg.ppg_latency_us != 6100:
        failures.append("SET_PPG_LATENCY 未生效")
    if status_after_cfg.red_led_pa != 0x28 or status_after_cfg.ir_led_pa != 0x2A:
        failures.append("SET_LED_PA 未生效")
    if not status_after_cfg.temperature_enabled:
        failures.append("SET_TEMP_EN 未生效")
    if status_after_cfg.log_level != 0x03:
        failures.append("SET_LOG_LEVEL 未生效")
    if not control_after_cfg.ppg_int_mode:
        failures.append("SET_PPG_MODE 未生效")
    if status_after_get_info <= status_before_get_info:
        failures.append("GET_INFO 后未触发状态通知")
    if status_after_self_test.self_test_fail_bitmap != 0:
        failures.append(f"SELF_TEST 后存在失败位: 0x{status_after_self_test.self_test_fail_bitmap:08X}")
    if not sync_mark_seen:
        failures.append("SYNC_MARK 后未在数据帧中观察到 bit9 标记")
    if status_after_stop.streaming_enabled:
        failures.append("STOP 后 streaming_enabled 仍为 true")
    if status_packets <= 0:
        failures.append("未收到状态通知")
    if status_after_start.generated_frame_count == 0:
        failures.append("START 后 generated_frame_count 仍为 0")
    if status_after_start.transmitted_frame_count == 0:
        failures.append("START 后 transmitted_frame_count 仍为 0")
    if status_after_start.self_test_fail_bitmap != 0:
        failures.append(f"自检失败位非 0: 0x{status_after_start.self_test_fail_bitmap:08X}")

    print(
        "[INFO] counters: "
        f"data_packets={data_packets}, status_packets={status_packets}, "
        f"generated={status_after_start.generated_frame_count}, "
        f"transmitted={status_after_start.transmitted_frame_count}"
    )
    print(
        "[INFO] after stop: "
        f"stream={status_after_stop.streaming_enabled}, "
        f"fail_bitmap=0x{status_after_stop.self_test_fail_bitmap:08X}, "
        f"state_flags=0x{status_after_stop.state_flags:04X}"
    )
    print(
        "[INFO] control before/after: "
        f"phase {control_before.ppg_phase_us}->{control_after_cfg.ppg_phase_us}, "
        f"latency {control_before.ppg_latency_us}->{control_after_cfg.ppg_latency_us}"
    )
    print(
        "[INFO] command validation: "
        f"get_info_notify_delta={status_after_get_info - status_before_get_info}, "
        f"sync_mark_seen_in_frame={sync_mark_seen}, "
        f"sync_state_flags=0x{status_after_sync_mark.state_flags:04X}"
    )

    if failures:
        print("[FAIL] smoke test failed:")
        for item in failures:
            print(f"  - {item}")
        return 1

    print("[PASS] BLE hardware smoke test passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="ESP32-S3 BLE hardware smoke test")
    parser.add_argument("--name-prefix", default="ECG-PPG-Terminal", help="BLE device name prefix")
    parser.add_argument("--scan-timeout", type=float, default=30.0, help="scan timeout seconds")
    parser.add_argument(
        "--demo-seconds",
        type=float,
        default=3.0,
        help="streaming duration before STOP (recommended 30~60 for demo validation)",
    )
    args = parser.parse_args()
    try:
        return asyncio.run(run_smoke(args.name_prefix, args.scan_timeout, args.demo_seconds))
    except KeyboardInterrupt:
        print("[INFO] interrupted")
        return 130
    except Exception as exc:  # pragma: no cover - runtime guard
        print(f"[FAIL] unexpected error: {exc}")
        return 99


if __name__ == "__main__":
    sys.exit(main())
