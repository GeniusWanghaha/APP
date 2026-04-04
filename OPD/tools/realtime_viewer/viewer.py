import asyncio
import struct
import sys
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Any, Optional

import numpy as np
import pyqtgraph as pg
from bleak import BleakClient, BleakScanner
from PySide6 import QtCore, QtGui, QtWidgets

try:
    from bleak.backends.winrt.util import allow_sta
except Exception:  # pragma: no cover - only needed on Windows GUI setups
    allow_sta = None


APP_PROTOCOL_VERSION = 0x01
PACKET_MSG_TYPE_DATA_SEGMENT = 0xA1
PREFERRED_DEVICE_NAME = "ECG-PPG-Terminal"

ECG_SAMPLE_PERIOD_US = 4000
PPG_SAMPLE_PERIOD_US = 2500
ECG_SAMPLES_PER_FRAME = 10
PPG_SAMPLES_PER_FRAME = 16

FRAME_STRUCT = struct.Struct("<HQBBH10H48s48sH")
SEGMENT_HEADER_STRUCT = struct.Struct("<BBHBBH")
STATUS_STRUCT = struct.Struct("<BBH13I7HBBiiBBBB")

MAX_FRAME_AGE_SECONDS = 2.0
PLOT_WINDOW_SECONDS = 12.0


def crc16_ccitt_false(data: bytes, init: int = 0xFFFF) -> int:
    crc = init
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & 0xFFFF
            else:
                crc = (crc << 1) & 0xFFFF
    return crc


def read_u24(chunk: bytes) -> int:
    return (chunk[0] << 16) | (chunk[1] << 8) | chunk[2]


@dataclass
class ParsedFrame:
    frame_id: int
    t_base_us: int
    state_flags: int
    ecg_values: list[int]
    ppg_red_values: list[int]
    ppg_ir_values: list[int]


class BleBackend(QtCore.QObject):
    devices_updated = QtCore.Signal(object)
    frame_ready = QtCore.Signal(object)
    status_ready = QtCore.Signal(object)
    stats_ready = QtCore.Signal(object)
    connection_changed = QtCore.Signal(bool, str)
    log_ready = QtCore.Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self._loop = asyncio.new_event_loop()
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

        self._client: Optional[BleakClient] = None
        self._device_cache: dict[str, str] = {}
        self._data_char: Optional[str] = None
        self._control_char: Optional[str] = None
        self._status_char: Optional[str] = None
        self._partial_frames: dict[int, dict[str, Any]] = {}

        self._frames_ok = 0
        self._frames_crc_error = 0
        self._segments_seen = 0
        self._last_frame_monotonic: Optional[float] = None

    def _run_loop(self) -> None:
        asyncio.set_event_loop(self._loop)
        self._loop.run_forever()

    def _submit(self, coro: Any) -> None:
        future = asyncio.run_coroutine_threadsafe(coro, self._loop)
        future.add_done_callback(self._report_future_exception)

    def _report_future_exception(self, future: Any) -> None:
        try:
            future.result()
        except Exception as exc:
            self.log_ready.emit(f"BLE task error: {exc}")

    def shutdown(self) -> None:
        self._submit(self._shutdown_async())

    async def _shutdown_async(self) -> None:
        await self._disconnect_async(log_message=False)
        self._loop.call_soon_threadsafe(self._loop.stop)

    def scan(self) -> None:
        self._submit(self._scan_async())

    async def _scan_async(self) -> None:
        self.log_ready.emit("Scanning BLE devices...")
        devices = await BleakScanner.discover(timeout=4.0)
        results: list[tuple[str, str]] = []
        self._device_cache.clear()
        for device in devices:
            name = device.name or "Unknown"
            display = f"{name} [{device.address}]"
            results.append((display, device.address))
            self._device_cache[display] = device.address
        results.sort(key=lambda item: item[0].lower())
        self.devices_updated.emit(results)
        self.log_ready.emit(f"Scan complete: {len(results)} device(s)")

    def connect(self, address: str) -> None:
        self._submit(self._connect_async(address))

    async def _connect_async(self, address: str) -> None:
        await self._disconnect_async(log_message=False)
        self.log_ready.emit(f"Connecting to {address}...")
        client = BleakClient(address, disconnected_callback=self._handle_disconnect)
        await client.connect()
        services = client.services or await client.get_services()

        chars = self._find_target_characteristics(services)
        if chars is None:
            await client.disconnect()
            raise RuntimeError("Could not locate the custom ECG/PPG BLE service")

        self._client = client
        self._data_char = chars["data"]
        self._control_char = chars["control"]
        self._status_char = chars["status"]
        self._partial_frames.clear()

        await client.start_notify(self._data_char, self._handle_data_notification)
        if self._status_char is not None:
            await client.start_notify(self._status_char, self._handle_status_notification)
            try:
                payload = await client.read_gatt_char(self._status_char)
                self._parse_status(bytes(payload))
            except Exception as exc:  # pragma: no cover - depends on BLE stack behavior
                self.log_ready.emit(f"Status read skipped: {exc}")

        self.connection_changed.emit(True, f"Connected to {address}")
        self.log_ready.emit("BLE connected and notifications enabled")
        self._emit_stats()

    def disconnect(self) -> None:
        self._submit(self._disconnect_async())

    async def _disconnect_async(self, log_message: bool = True) -> None:
        client = self._client
        self._client = None
        self._data_char = None
        self._control_char = None
        self._status_char = None
        self._partial_frames.clear()

        if client is not None:
            try:
                if client.is_connected:
                    await client.disconnect()
            except Exception as exc:  # pragma: no cover - depends on BLE stack behavior
                if log_message:
                    self.log_ready.emit(f"Disconnect warning: {exc}")

        self.connection_changed.emit(False, "Disconnected")
        if log_message:
            self.log_ready.emit("BLE disconnected")
        self._emit_stats()

    def start_stream(self) -> None:
        self._submit(self._write_control_async(bytes([0x01]), "Start stream"))

    def stop_stream(self) -> None:
        self._submit(self._write_control_async(bytes([0x02]), "Stop stream"))

    async def _write_control_async(self, payload: bytes, description: str) -> None:
        if self._client is None or self._control_char is None or not self._client.is_connected:
            self.log_ready.emit(f"{description} failed: not connected")
            return
        await self._client.write_gatt_char(self._control_char, payload, response=False)
        self.log_ready.emit(f"{description} command sent")

    def _handle_disconnect(self, _client: BleakClient) -> None:
        self._client = None
        self._data_char = None
        self._control_char = None
        self._status_char = None
        self.connection_changed.emit(False, "Disconnected")
        self.log_ready.emit("Device disconnected")
        self._emit_stats()

    def _handle_data_notification(self, _char: Any, payload: bytearray) -> None:
        try:
            packet = bytes(payload)
            if len(packet) < SEGMENT_HEADER_STRUCT.size:
                return

            proto, msg_type, frame_id, seg_index, seg_count, payload_len = SEGMENT_HEADER_STRUCT.unpack_from(packet, 0)
            if proto != APP_PROTOCOL_VERSION or msg_type != PACKET_MSG_TYPE_DATA_SEGMENT:
                return

            data = packet[SEGMENT_HEADER_STRUCT.size: SEGMENT_HEADER_STRUCT.size + payload_len]
            if len(data) != payload_len:
                return

            self._segments_seen += 1
            bucket = self._partial_frames.setdefault(
                frame_id,
                {
                    "segment_count": seg_count,
                    "segments": {},
                    "created_at": time.monotonic(),
                },
            )
            if bucket["segment_count"] != seg_count:
                bucket["segment_count"] = seg_count
                bucket["segments"] = {}
                bucket["created_at"] = time.monotonic()
            bucket["segments"][seg_index] = data
            self._drop_stale_frames()

            if len(bucket["segments"]) != seg_count:
                self._emit_stats()
                return

            full_frame = b"".join(bucket["segments"][index] for index in range(seg_count))
            del self._partial_frames[frame_id]
            parsed = self._parse_frame(full_frame)
            if parsed is None:
                self._frames_crc_error += 1
                self.log_ready.emit(f"CRC error on frame {frame_id}")
            else:
                self._frames_ok += 1
                self._last_frame_monotonic = time.monotonic()
                self.frame_ready.emit(parsed)
            self._emit_stats()
        except Exception as exc:  # pragma: no cover - depends on live BLE traffic
            self.log_ready.emit(f"Data parse error: {exc}")

    def _handle_status_notification(self, _char: Any, payload: bytearray) -> None:
        try:
            self._parse_status(bytes(payload))
        except Exception as exc:  # pragma: no cover - depends on live BLE traffic
            self.log_ready.emit(f"Status parse error: {exc}")

    def _parse_frame(self, payload: bytes) -> Optional[ParsedFrame]:
        if len(payload) != FRAME_STRUCT.size:
            self.log_ready.emit(f"Unexpected frame size: {len(payload)}")
            return None

        crc_expected = struct.unpack_from("<H", payload, FRAME_STRUCT.size - 2)[0]
        crc_actual = crc16_ccitt_false(payload[:-2])
        if crc_actual != crc_expected:
            return None

        unpacked = FRAME_STRUCT.unpack(payload)
        frame_id = unpacked[0]
        t_base_us = unpacked[1]
        ecg_count = unpacked[2]
        ppg_count = unpacked[3]
        state_flags = unpacked[4]
        ecg_values = list(unpacked[5:15])
        ppg_red_raw = unpacked[15]
        ppg_ir_raw = unpacked[16]

        if ecg_count != ECG_SAMPLES_PER_FRAME or ppg_count != PPG_SAMPLES_PER_FRAME:
            self.log_ready.emit(
                f"Unexpected sample counts: ECG={ecg_count}, PPG={ppg_count}"
            )

        ppg_red_values = [read_u24(ppg_red_raw[i:i + 3]) for i in range(0, len(ppg_red_raw), 3)]
        ppg_ir_values = [read_u24(ppg_ir_raw[i:i + 3]) for i in range(0, len(ppg_ir_raw), 3)]
        return ParsedFrame(
            frame_id=frame_id,
            t_base_us=t_base_us,
            state_flags=state_flags,
            ecg_values=ecg_values,
            ppg_red_values=ppg_red_values,
            ppg_ir_values=ppg_ir_values,
        )

    def _parse_status(self, payload: bytes) -> None:
        if len(payload) != STATUS_STRUCT.size:
            self.log_ready.emit(f"Unexpected status size: {len(payload)}")
            return

        values = STATUS_STRUCT.unpack(payload)
        status = {
            "protocol_version": values[0],
            "streaming_enabled": values[1],
            "state_flags": values[2],
            "self_test_pass_bitmap": values[3],
            "self_test_fail_bitmap": values[4],
            "ppg_fifo_overflow_count": values[5],
            "ppg_int_timeout_count": values[6],
            "ble_backpressure_count": values[7],
            "ble_dropped_frame_count": values[8],
            "i2c_error_count": values[9],
            "adc_saturation_count": values[10],
            "ecg_ring_drop_count": values[11],
            "ppg_ring_drop_count": values[12],
            "generated_frame_count": values[13],
            "transmitted_frame_count": values[14],
            "frame_sequence_error_count": values[15],
            "ecg_ring_items": values[16],
            "ppg_ring_items": values[17],
            "ble_queue_items": values[18],
            "ecg_ring_high_watermark": values[19],
            "ppg_ring_high_watermark": values[20],
            "ble_queue_high_watermark": values[21],
            "mtu": values[22],
            "red_led_pa": values[23],
            "ir_led_pa": values[24],
            "ppg_phase_us": values[25],
            "ppg_latency_us": values[26],
            "temperature_enabled": values[27],
            "log_level": values[28],
            "sensor_ready": values[29],
            "finger_detected": values[30],
        }
        self.status_ready.emit(status)

    def _emit_stats(self) -> None:
        frame_rate = 0.0
        if self._last_frame_monotonic is not None:
            delta = time.monotonic() - self._last_frame_monotonic
            if delta < 1.0:
                frame_rate = 1.0 / max(delta, 1e-6)
        self.stats_ready.emit(
            {
                "frames_ok": self._frames_ok,
                "crc_errors": self._frames_crc_error,
                "segments_seen": self._segments_seen,
                "pending_frames": len(self._partial_frames),
                "frame_rate_est": frame_rate,
            }
        )

    def _drop_stale_frames(self) -> None:
        now = time.monotonic()
        stale = [
            frame_id
            for frame_id, bucket in self._partial_frames.items()
            if now - bucket["created_at"] > MAX_FRAME_AGE_SECONDS
        ]
        for frame_id in stale:
            del self._partial_frames[frame_id]

    @staticmethod
    def _find_target_characteristics(services: Any) -> Optional[dict[str, str]]:
        service_iter = services.services.values() if hasattr(services, "services") else services
        for service in service_iter:
            data_uuid: Optional[str] = None
            control_uuid: Optional[str] = None
            status_uuid: Optional[str] = None

            for char in service.characteristics:
                props = {prop.lower().replace("_", "-") for prop in char.properties}
                has_write = "write" in props or "write-without-response" in props
                has_read = "read" in props
                has_notify = "notify" in props

                if has_notify and not has_read and not has_write:
                    data_uuid = char.uuid
                elif has_read and has_write:
                    control_uuid = char.uuid
                elif has_notify and has_read and not has_write:
                    status_uuid = char.uuid

            if data_uuid and control_uuid and status_uuid:
                return {
                    "data": data_uuid,
                    "control": control_uuid,
                    "status": status_uuid,
                }
        return None


class MainWindow(QtWidgets.QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("ECG + PPG Real-Time Viewer")
        self.resize(1400, 960)

        self.backend = BleBackend()
        self.backend.devices_updated.connect(self._on_devices_updated)
        self.backend.frame_ready.connect(self._on_frame_ready)
        self.backend.status_ready.connect(self._on_status_ready)
        self.backend.stats_ready.connect(self._on_stats_ready)
        self.backend.connection_changed.connect(self._on_connection_changed)
        self.backend.log_ready.connect(self._append_log)

        self._time_origin_us: Optional[int] = None
        self._connected = False

        self._ecg_x: deque[float] = deque(maxlen=int(PLOT_WINDOW_SECONDS * 250))
        self._ecg_y: deque[int] = deque(maxlen=int(PLOT_WINDOW_SECONDS * 250))
        self._red_x: deque[float] = deque(maxlen=int(PLOT_WINDOW_SECONDS * 400))
        self._red_y: deque[int] = deque(maxlen=int(PLOT_WINDOW_SECONDS * 400))
        self._ir_x: deque[float] = deque(maxlen=int(PLOT_WINDOW_SECONDS * 400))
        self._ir_y: deque[int] = deque(maxlen=int(PLOT_WINDOW_SECONDS * 400))

        self._build_ui()

        self._plot_timer = QtCore.QTimer(self)
        self._plot_timer.timeout.connect(self._refresh_plots)
        self._plot_timer.start(50)
        QtCore.QTimer.singleShot(500, self.backend.scan)

    def _build_ui(self) -> None:
        central = QtWidgets.QWidget()
        root = QtWidgets.QVBoxLayout(central)
        root.setSpacing(8)
        self.setCentralWidget(central)

        controls = QtWidgets.QHBoxLayout()
        self.scan_button = QtWidgets.QPushButton("Scan")
        self.connect_button = QtWidgets.QPushButton("Connect")
        self.disconnect_button = QtWidgets.QPushButton("Disconnect")
        self.start_button = QtWidgets.QPushButton("Start Stream")
        self.stop_button = QtWidgets.QPushButton("Stop Stream")
        self.clear_button = QtWidgets.QPushButton("Clear")
        self.device_combo = QtWidgets.QComboBox()
        self.device_combo.setMinimumWidth(420)
        self.phase_spin = QtWidgets.QSpinBox()
        self.phase_spin.setRange(-20000, 20000)
        self.phase_spin.setValue(1250)
        self.phase_spin.setSuffix(" us")

        controls.addWidget(QtWidgets.QLabel("Device"))
        controls.addWidget(self.device_combo, 1)
        controls.addWidget(self.scan_button)
        controls.addWidget(self.connect_button)
        controls.addWidget(self.disconnect_button)
        controls.addSpacing(16)
        controls.addWidget(QtWidgets.QLabel("PPG Phase"))
        controls.addWidget(self.phase_spin)
        controls.addSpacing(16)
        controls.addWidget(self.start_button)
        controls.addWidget(self.stop_button)
        controls.addWidget(self.clear_button)
        root.addLayout(controls)

        status_row = QtWidgets.QGridLayout()
        self.connection_label = QtWidgets.QLabel("Disconnected")
        self.frames_label = QtWidgets.QLabel("Frames: 0")
        self.errors_label = QtWidgets.QLabel("CRC Errors: 0")
        self.rate_label = QtWidgets.QLabel("Frame Rate: 0.0 fps")
        self.state_label = QtWidgets.QLabel("State Flags: 0x0000")
        self.sensor_label = QtWidgets.QLabel("Sensor Ready: -")
        self.finger_label = QtWidgets.QLabel("Finger Detected: -")
        self.queue_label = QtWidgets.QLabel("BLE Queue: -")

        status_row.addWidget(QtWidgets.QLabel("Connection"), 0, 0)
        status_row.addWidget(self.connection_label, 0, 1)
        status_row.addWidget(self.frames_label, 0, 2)
        status_row.addWidget(self.errors_label, 0, 3)
        status_row.addWidget(self.rate_label, 0, 4)
        status_row.addWidget(self.state_label, 1, 0, 1, 2)
        status_row.addWidget(self.sensor_label, 1, 2)
        status_row.addWidget(self.finger_label, 1, 3)
        status_row.addWidget(self.queue_label, 1, 4)
        root.addLayout(status_row)

        self.ecg_plot = pg.PlotWidget(title="ECG Raw (ADC)")
        self.red_plot = pg.PlotWidget(title="PPG Red (18-bit)")
        self.ir_plot = pg.PlotWidget(title="PPG IR (18-bit)")
        for plot in (self.ecg_plot, self.red_plot, self.ir_plot):
            plot.showGrid(x=True, y=True, alpha=0.25)
            plot.setLabel("bottom", "Time", units="s")
            plot.enableAutoRange(axis="y", enable=True)
            root.addWidget(plot, 1)

        self.ecg_curve = self.ecg_plot.plot(pen=pg.mkPen("#ff5d5d", width=1.8))
        self.red_curve = self.red_plot.plot(pen=pg.mkPen("#ff8c42", width=1.8))
        self.ir_curve = self.ir_plot.plot(pen=pg.mkPen("#39a0ed", width=1.8))

        self.log_box = QtWidgets.QPlainTextEdit()
        self.log_box.setReadOnly(True)
        self.log_box.setMaximumBlockCount(300)
        self.log_box.setPlaceholderText("Runtime log...")
        root.addWidget(self.log_box, 1)

        self.scan_button.clicked.connect(self.backend.scan)
        self.connect_button.clicked.connect(self._connect_selected_device)
        self.disconnect_button.clicked.connect(self.backend.disconnect)
        self.start_button.clicked.connect(self.backend.start_stream)
        self.stop_button.clicked.connect(self.backend.stop_stream)
        self.clear_button.clicked.connect(self._clear_data)

        self.disconnect_button.setEnabled(False)
        self.start_button.setEnabled(False)
        self.stop_button.setEnabled(False)

    def _connect_selected_device(self) -> None:
        address = self.device_combo.currentData()
        if address:
            self.backend.connect(str(address))
        else:
            self._append_log("No device selected")

    def _on_devices_updated(self, devices: object) -> None:
        self.device_combo.clear()
        preferred_index = -1
        index = 0
        for display, address in devices:
            self.device_combo.addItem(display, address)
            if display.startswith(PREFERRED_DEVICE_NAME):
                preferred_index = index
            index += 1
        if not devices:
            self._append_log("No BLE devices found")
            return

        if preferred_index >= 0:
            self.device_combo.setCurrentIndex(preferred_index)
            self._append_log(f"Preferred device selected: {self.device_combo.currentText()}")
            if not self._connected:
                QtCore.QTimer.singleShot(300, self._connect_selected_device)

    def _on_connection_changed(self, connected: bool, message: str) -> None:
        self._connected = connected
        self.connection_label.setText(message)
        self.connect_button.setEnabled(not connected)
        self.disconnect_button.setEnabled(connected)
        self.start_button.setEnabled(connected)
        self.stop_button.setEnabled(connected)
        if connected:
            QtCore.QTimer.singleShot(300, self.backend.start_stream)

    def _on_frame_ready(self, frame: object) -> None:
        parsed: ParsedFrame = frame
        if self._time_origin_us is None:
            self._time_origin_us = parsed.t_base_us

        phase_us = int(self.phase_spin.value())
        for index, value in enumerate(parsed.ecg_values):
            timestamp = parsed.t_base_us + index * ECG_SAMPLE_PERIOD_US
            self._ecg_x.append((timestamp - self._time_origin_us) / 1_000_000.0)
            self._ecg_y.append(value)

        for index, value in enumerate(parsed.ppg_red_values):
            timestamp = parsed.t_base_us + phase_us + index * PPG_SAMPLE_PERIOD_US
            self._red_x.append((timestamp - self._time_origin_us) / 1_000_000.0)
            self._red_y.append(value)

        for index, value in enumerate(parsed.ppg_ir_values):
            timestamp = parsed.t_base_us + phase_us + index * PPG_SAMPLE_PERIOD_US
            self._ir_x.append((timestamp - self._time_origin_us) / 1_000_000.0)
            self._ir_y.append(value)

        self.state_label.setText(f"State Flags: 0x{parsed.state_flags:04X}")

    def _on_status_ready(self, status: object) -> None:
        self.phase_spin.blockSignals(True)
        self.phase_spin.setValue(int(status["ppg_phase_us"]))
        self.phase_spin.blockSignals(False)

        self.sensor_label.setText(f"Sensor Ready: {status['sensor_ready']}")
        self.finger_label.setText(f"Finger Detected: {status['finger_detected']}")
        self.queue_label.setText(
            f"BLE Queue: {status['ble_queue_items']} / MTU {status['mtu']}"
        )
        self.state_label.setText(f"State Flags: 0x{status['state_flags']:04X}")

    def _on_stats_ready(self, stats: object) -> None:
        self.frames_label.setText(f"Frames: {stats['frames_ok']}")
        self.errors_label.setText(f"CRC Errors: {stats['crc_errors']}")
        self.rate_label.setText(f"Frame Rate: {stats['frame_rate_est']:.1f} fps")

    def _refresh_plots(self) -> None:
        self._set_curve_data(self.ecg_curve, self._ecg_x, self._ecg_y)
        self._set_curve_data(self.red_curve, self._red_x, self._red_y)
        self._set_curve_data(self.ir_curve, self._ir_x, self._ir_y)

    @staticmethod
    def _set_curve_data(curve: Any, x_values: deque[float], y_values: deque[int]) -> None:
        if not x_values:
            curve.setData([], [])
            return
        curve.setData(np.fromiter(x_values, dtype=np.float64), np.fromiter(y_values, dtype=np.float64))

    def _clear_data(self) -> None:
        self._time_origin_us = None
        self._ecg_x.clear()
        self._ecg_y.clear()
        self._red_x.clear()
        self._red_y.clear()
        self._ir_x.clear()
        self._ir_y.clear()
        self._refresh_plots()
        self._append_log("Plot buffers cleared")

    def _append_log(self, message: str) -> None:
        timestamp = time.strftime("%H:%M:%S")
        self.log_box.appendPlainText(f"[{timestamp}] {message}")

    def closeEvent(self, event: QtGui.QCloseEvent) -> None:
        self.backend.shutdown()
        super().closeEvent(event)


def main() -> int:
    if allow_sta is not None:
        try:
            allow_sta()
        except Exception:
            pass

    pg.setConfigOptions(antialias=True, background="#0b1020", foreground="#d7dbe6")
    app = QtWidgets.QApplication(sys.argv)
    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
