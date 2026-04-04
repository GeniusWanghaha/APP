# Real-Time BLE Waveform Viewer

This viewer connects to the ESP32-S3 ECG+PPG terminal over BLE, sends the
`start streaming` command, reassembles segmented notifications, validates the
CRC, and plots ECG / PPG Red / PPG IR waveforms in real time.

## Install

```powershell
cd e:\OPD\tools\realtime_viewer
pip install -r requirements.txt
```

## Run

```powershell
cd e:\OPD\tools\realtime_viewer
python viewer.py
```

Or double-click:

```text
e:\OPD\tools\realtime_viewer\run_viewer.bat
```

## Typical Flow

1. Click `Scan`.
2. Select `ECG-PPG-Terminal`.
3. Click `Connect`.
4. Click `Start Stream`.
5. Put your finger on the PPG window and attach ECG electrodes.

## Notes

- The viewer uses the embedded protocol from the ESP-IDF firmware in this
  workspace.
- `ppg_phase_us` is adjustable from the UI for visual alignment.
- The status panel reflects the firmware's `Status Notify` characteristic.
