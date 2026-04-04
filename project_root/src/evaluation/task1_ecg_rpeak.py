from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from src.evaluation.channel_utils import find_channel_by_keywords
from src.evaluation.metrics import classification_metrics, match_peaks_with_tolerance
from src.preprocessing.ecg import detect_rpeaks_adaptive, detect_rpeaks_classic, preprocess_ecg


@dataclass
class DetectorScores:
    tp: int = 0
    fp: int = 0
    fn: int = 0
    loc_errors_ms: list[float] | None = None

    def __post_init__(self) -> None:
        if self.loc_errors_ms is None:
            self.loc_errors_ms = []


def _reference_rpeaks(payload: dict) -> np.ndarray:
    annotations = payload.get("annotations", {})
    for key in ("atr", "qrs", "ecg", "ann"):
        ann = annotations.get(key)
        if isinstance(ann, dict) and ann.get("sample"):
            return np.asarray(ann["sample"], dtype=int)
    return np.array([], dtype=int)


def run_task1(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 12,
    adaptive_threshold_scale: float = 0.8,
    max_duration_sec: float = 300.0,
) -> dict:
    tolerance_ms = 100.0
    classic = DetectorScores()
    adaptive = DetectorScores()
    evaluated_records = 0
    skipped_records = 0

    for dataset_name in dataset_names:
        records = loader_registry.list_records(dataset_name)[:max_records_per_dataset]
        for record_id in records:
            try:
                payload = loader_registry.load_record(dataset_name, record_id)
                signal_map = payload["signals"]
                fs_map = payload["fs"]
                if not signal_map:
                    skipped_records += 1
                    continue

                ecg_ch = find_channel_by_keywords(signal_map, ["ecg", "mlii", "v1", "ii"]) or next(iter(signal_map.keys()))
                ecg = np.asarray(signal_map[ecg_ch], dtype=float)
                fs = float(fs_map.get(ecg_ch, next(iter(fs_map.values()), 250.0)))
                if ecg.size < fs * 10:
                    skipped_records += 1
                    continue
                max_len = int(fs * max_duration_sec)
                if max_len > 0 and ecg.size > max_len:
                    ecg = ecg[:max_len]
                ref = _reference_rpeaks(payload)
                if ref.size == 0:
                    skipped_records += 1
                    continue
                ref = ref[ref < ecg.size]
                if ref.size == 0:
                    skipped_records += 1
                    continue

                filtered = preprocess_ecg(ecg, fs=fs)
                pred_classic = detect_rpeaks_classic(filtered, fs=fs)
                pred_adaptive = detect_rpeaks_adaptive(filtered, fs=fs, threshold_scale=adaptive_threshold_scale)

                tol_samples = int((tolerance_ms / 1000.0) * fs)
                tp, fp, fn, loc = match_peaks_with_tolerance(ref, pred_classic, tol_samples)
                classic.tp += tp
                classic.fp += fp
                classic.fn += fn
                classic.loc_errors_ms.extend((loc * 1000.0 / fs).tolist())

                tp, fp, fn, loc = match_peaks_with_tolerance(ref, pred_adaptive, tol_samples)
                adaptive.tp += tp
                adaptive.fp += fp
                adaptive.fn += fn
                adaptive.loc_errors_ms.extend((loc * 1000.0 / fs).tolist())
                evaluated_records += 1
            except Exception:  # noqa: BLE001
                skipped_records += 1
                continue

    if evaluated_records == 0:
        return {
            "status": "skipped",
            "reason": "no record with usable ECG + reference annotation",
            "evaluated_records": 0,
            "skipped_records": skipped_records,
        }

    classic_metrics = classification_metrics(classic.tp, classic.fp, classic.fn)
    adaptive_metrics = classification_metrics(adaptive.tp, adaptive.fp, adaptive.fn)
    return {
        "status": "ok",
        "evaluated_records": evaluated_records,
        "skipped_records": skipped_records,
        "tolerance_ms": tolerance_ms,
        "classic": {
            **classic_metrics,
            "mean_abs_peak_localization_error_ms": float(np.mean(classic.loc_errors_ms))
            if classic.loc_errors_ms
            else float("nan"),
            "tp": classic.tp,
            "fp": classic.fp,
            "fn": classic.fn,
        },
        "adaptive": {
            **adaptive_metrics,
            "threshold_scale": adaptive_threshold_scale,
            "mean_abs_peak_localization_error_ms": float(np.mean(adaptive.loc_errors_ms))
            if adaptive.loc_errors_ms
            else float("nan"),
            "tp": adaptive.tp,
            "fp": adaptive.fp,
            "fn": adaptive.fn,
        },
    }
