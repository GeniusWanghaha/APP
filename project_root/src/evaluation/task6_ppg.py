from __future__ import annotations

import numpy as np

from src.evaluation.channel_utils import find_channel_by_keywords
from src.preprocessing.ppg import detect_ppg_foots, detect_ppg_peaks, estimate_ppg_sqi, preprocess_ppg


def _hr_from_peaks(peaks: np.ndarray, fs: float) -> float:
    if len(peaks) < 2:
        return float("nan")
    rr = np.diff(peaks) / fs
    rr = rr[(rr > 0.3) & (rr < 2.0)]
    if rr.size == 0:
        return float("nan")
    return float(60.0 / np.mean(rr))


def run_task6(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 12,
    max_duration_sec: float = 180.0,
) -> dict:
    rows = []
    skipped = 0
    for dataset_name in dataset_names:
        records = loader_registry.list_records(dataset_name)[:max_records_per_dataset]
        for record_id in records:
            try:
                payload = loader_registry.load_record(dataset_name, record_id)
                signals = payload["signals"]
                fs_map = payload["fs"]
                if not signals or "features" in signals:
                    skipped += 1
                    continue
                ppg_ch = find_channel_by_keywords(signals, ["ppg", "pleth", "ir", "red"]) or next(iter(signals.keys()))
                ppg = np.asarray(signals[ppg_ch], dtype=float)
                fs = float(fs_map.get(ppg_ch, next(iter(fs_map.values()), 100.0)))
                if ppg.size < fs * 20:
                    skipped += 1
                    continue
                max_len = int(fs * max_duration_sec)
                if max_len > 0 and ppg.size > max_len:
                    ppg = ppg[:max_len]

                ppg_clean = preprocess_ppg(ppg, fs)
                peaks = detect_ppg_peaks(ppg_clean, fs)
                feet = detect_ppg_foots(ppg_clean, fs)
                sqi = estimate_ppg_sqi(ppg_clean, peaks, fs)
                hr = _hr_from_peaks(peaks, fs)
                # No universal GT HR for these datasets in this unified loader.
                # We use a self-consistency proxy: compare HR from peaks vs feet.
                hr_from_feet = _hr_from_peaks(feet, fs)
                hr_err = float(abs(hr - hr_from_feet)) if np.isfinite(hr) and np.isfinite(hr_from_feet) else float("nan")

                rows.append(
                    {
                        "dataset_name": dataset_name,
                        "record_id": record_id,
                        "hr_bpm": hr,
                        "hr_proxy_error_bpm": hr_err,
                        "peak_count": int(len(peaks)),
                        "foot_count": int(len(feet)),
                        "sqi": float(sqi),
                    }
                )
            except Exception:  # noqa: BLE001
                skipped += 1
                continue

    if not rows:
        return {
            "status": "skipped",
            "reason": "no usable ppg waveform records",
            "record_count": 0,
            "skipped_records": skipped,
        }

    hr_errors = [r["hr_proxy_error_bpm"] for r in rows if np.isfinite(r["hr_proxy_error_bpm"])]
    sqi_values = [r["sqi"] for r in rows]
    return {
        "status": "ok",
        "record_count": len(rows),
        "skipped_records": skipped,
        "hr_proxy_mae_bpm": float(np.mean(np.abs(hr_errors))) if hr_errors else float("nan"),
        "sqi_mean": float(np.mean(sqi_values)) if sqi_values else float("nan"),
        "peak_foot_success_rate": float(np.mean([1.0 if (r["peak_count"] > 1 and r["foot_count"] > 1) else 0.0 for r in rows])),
        "sqi_values": sqi_values[:1000],
        "hr_proxy_errors_bpm": hr_errors[:1000],
    }
