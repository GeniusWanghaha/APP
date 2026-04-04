from __future__ import annotations

import numpy as np

from src.evaluation.channel_utils import find_channel_by_keywords
from src.features.ptt import pair_delays_ms_constrained
from src.preprocessing.ecg import detect_rpeaks_adaptive, preprocess_ecg
from src.preprocessing.ppg import detect_ppg_peaks, preprocess_ppg


def run_task8(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 10,
    max_duration_sec: float = 180.0,
) -> dict:
    all_r2f = []
    all_r2p = []
    evaluated = 0
    skipped = 0
    total_beats = 0
    valid_beats = 0
    invalid_reason_counts: dict[str, int] = {}
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
                ecg_ch = find_channel_by_keywords(signals, ["ecg", "mlii", "ii", "v1"])
                ppg_ch = find_channel_by_keywords(signals, ["ppg", "pleth", "ir", "red"])
                if ecg_ch is None or ppg_ch is None:
                    skipped += 1
                    continue
                ecg = np.asarray(signals[ecg_ch], dtype=float)
                ppg = np.asarray(signals[ppg_ch], dtype=float)
                fs_ecg = float(fs_map.get(ecg_ch, next(iter(fs_map.values()), 250.0)))
                fs_ppg = float(fs_map.get(ppg_ch, next(iter(fs_map.values()), 100.0)))
                if ecg.size < fs_ecg * 20 or ppg.size < fs_ppg * 20:
                    skipped += 1
                    continue
                max_len_ecg = int(fs_ecg * max_duration_sec)
                max_len_ppg = int(fs_ppg * max_duration_sec)
                if max_len_ecg > 0 and ecg.size > max_len_ecg:
                    ecg = ecg[:max_len_ecg]
                if max_len_ppg > 0 and ppg.size > max_len_ppg:
                    ppg = ppg[:max_len_ppg]

                ecg = preprocess_ecg(ecg, fs_ecg)
                ppg = preprocess_ppg(ppg, fs_ppg)
                rpeaks = detect_rpeaks_adaptive(ecg, fs_ecg)
                peaks = detect_ppg_peaks(ppg, fs_ppg)
                delays = pair_delays_ms_constrained(
                    rpeaks=rpeaks,
                    ppg=ppg,
                    ppg_peaks=peaks,
                    fs_ecg=fs_ecg,
                    fs_ppg=fs_ppg,
                )
                total_beats += int(delays.get("total_beats", 0))
                valid_beats += int(delays.get("valid_beats", 0))
                for reason, cnt in delays.get("invalid_reason_counts", {}).items():
                    invalid_reason_counts[reason] = invalid_reason_counts.get(reason, 0) + int(cnt)
                if np.isfinite(delays["r_to_foot_mean_ms"]):
                    all_r2f.extend(delays["r_to_foot_ms"].tolist())
                if np.isfinite(delays["r_to_peak_mean_ms"]):
                    all_r2p.extend(delays["r_to_peak_ms"].tolist())
                evaluated += 1
            except Exception:  # noqa: BLE001
                skipped += 1
                continue

    if evaluated == 0:
        return {
            "status": "skipped",
            "reason": "no usable ecg+ppg records for sync",
            "evaluated_records": 0,
            "skipped_records": skipped,
        }
    return {
        "status": "ok",
        "evaluated_records": evaluated,
        "skipped_records": skipped,
        "r_to_foot_mean_ms": float(np.mean(all_r2f)) if all_r2f else float("nan"),
        "r_to_peak_mean_ms": float(np.mean(all_r2p)) if all_r2p else float("nan"),
        "r_to_foot_std_ms": float(np.std(all_r2f)) if all_r2f else float("nan"),
        "r_to_peak_std_ms": float(np.std(all_r2p)) if all_r2p else float("nan"),
        "total_beats": int(total_beats),
        "valid_beats": int(valid_beats),
        "valid_ratio": float(valid_beats / total_beats) if total_beats > 0 else float("nan"),
        "invalid_reason_counts": invalid_reason_counts,
        "r_to_foot_values_ms": all_r2f[:2000],
        "r_to_peak_values_ms": all_r2p[:2000],
    }
