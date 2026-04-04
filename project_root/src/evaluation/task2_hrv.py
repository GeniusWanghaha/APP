from __future__ import annotations

import numpy as np

from src.evaluation.channel_utils import find_channel_by_keywords
from src.features.hrv import hrv_features, remove_outlier_rr, rr_intervals_ms
from src.preprocessing.ecg import detect_rpeaks_adaptive, preprocess_ecg


def run_task2(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 12,
    max_duration_sec: float = 300.0,
) -> dict:
    per_record = []
    skipped = 0
    for dataset_name in dataset_names:
        records = loader_registry.list_records(dataset_name)[:max_records_per_dataset]
        for record_id in records:
            try:
                payload = loader_registry.load_record(dataset_name, record_id)
                signals = payload["signals"]
                fs_map = payload["fs"]
                if not signals:
                    skipped += 1
                    continue
                ecg_ch = find_channel_by_keywords(signals, ["ecg", "mlii", "v1", "ii"]) or next(iter(signals.keys()))
                ecg = np.asarray(signals[ecg_ch], dtype=float)
                fs = float(fs_map.get(ecg_ch, next(iter(fs_map.values()), 250.0)))
                if ecg.size < fs * 30:
                    skipped += 1
                    continue
                max_len = int(fs * max_duration_sec)
                if max_len > 0 and ecg.size > max_len:
                    ecg = ecg[:max_len]

                filtered = preprocess_ecg(ecg, fs)
                rpeaks = detect_rpeaks_adaptive(filtered, fs)
                rr_all = rr_intervals_ms(rpeaks, fs)
                rr_clean = remove_outlier_rr(rr_all)

                per_record.append(
                    {
                        "dataset_name": dataset_name,
                        "record_id": record_id,
                        "raw": hrv_features(rr_all),
                        "clean": hrv_features(rr_clean),
                    }
                )
            except Exception:  # noqa: BLE001
                skipped += 1
                continue

    if not per_record:
        return {
            "status": "skipped",
            "reason": "no usable records",
            "record_count": 0,
            "skipped_records": skipped,
        }

    def _aggregate(key: str, mode: str) -> float:
        values = [r[mode][key] for r in per_record if not np.isnan(r[mode][key])]
        return float(np.mean(values)) if values else float("nan")

    keys = ["mean_hr_bpm", "mean_rr_ms", "sdnn_ms", "rmssd_ms", "pnn50_percent"]
    summary = {
        "raw": {k: _aggregate(k, "raw") for k in keys},
        "clean": {k: _aggregate(k, "clean") for k in keys},
    }
    return {
        "status": "ok",
        "record_count": len(per_record),
        "skipped_records": skipped,
        "summary": summary,
    }
