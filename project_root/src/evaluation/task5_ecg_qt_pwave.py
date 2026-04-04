from __future__ import annotations

import numpy as np
import neurokit2 as nk

from src.evaluation.channel_utils import find_channel_by_keywords
from src.preprocessing.ecg import detect_rpeaks_adaptive, preprocess_ecg, rpeak_sqi


def run_task5(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 10,
    max_duration_sec: float = 180.0,
) -> dict:
    total = 0
    rejected = 0
    delineated = 0
    sqi_values = []

    for dataset_name in dataset_names:
        records = loader_registry.list_records(dataset_name)[:max_records_per_dataset]
        for record_id in records:
            try:
                payload = loader_registry.load_record(dataset_name, record_id)
                signals = payload["signals"]
                fs_map = payload["fs"]
                if not signals:
                    continue
                ecg_ch = find_channel_by_keywords(signals, ["ecg", "mlii", "ii", "v1"]) or next(iter(signals.keys()))
                ecg = np.asarray(signals[ecg_ch], dtype=float)
                fs = float(fs_map.get(ecg_ch, next(iter(fs_map.values()), 250.0)))
                if ecg.size < fs * 20:
                    continue
                max_len = int(fs * max_duration_sec)
                if max_len > 0 and ecg.size > max_len:
                    ecg = ecg[:max_len]
                total += 1
                ecg_clean = preprocess_ecg(ecg, fs)
                rpeaks = detect_rpeaks_adaptive(ecg_clean, fs)
                sqi = rpeak_sqi(rpeaks, fs)
                sqi_values.append(sqi)
                if sqi < 0.45:
                    rejected += 1
                    continue
                try:
                    _, info = nk.ecg_delineate(
                        ecg_clean,
                        rpeaks=rpeaks,
                        sampling_rate=fs,
                        method="dwt",
                        show=False,
                    )
                    if info:
                        delineated += 1
                except Exception:  # noqa: BLE001
                    continue
            except Exception:  # noqa: BLE001
                continue

    if total == 0:
        return {
            "status": "skipped",
            "reason": "no usable QT/P-wave records",
        }
    return {
        "status": "ok",
        "evaluated_records": total,
        "rejected_low_quality": rejected,
        "delineation_success_count": delineated,
        "delineation_success_rate": float(delineated / max(total - rejected, 1)),
        "mean_sqi": float(np.mean(sqi_values)) if sqi_values else float("nan"),
        "note": "Reference point-level GT mismatch not unified across datasets; MAE fields reserved for next iteration.",
    }
