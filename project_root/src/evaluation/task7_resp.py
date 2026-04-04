from __future__ import annotations

import numpy as np

from src.evaluation.channel_utils import find_channel_by_keywords
from src.features.respiration import (
    edr_from_rpeak_amplitude,
    estimate_resp_rate_from_series,
    pdr_from_ppg_amplitude,
)
from src.preprocessing.ecg import detect_rpeaks_adaptive, preprocess_ecg
from src.preprocessing.ppg import detect_ppg_peaks, preprocess_ppg


def run_task7(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 12,
    max_duration_sec: float = 300.0,
) -> dict:
    errs_ecg = []
    errs_ppg = []
    evaluated = 0
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
                resp_ch = find_channel_by_keywords(signals, ["resp", "breath", "thorax", "abd"])
                ecg_ch = find_channel_by_keywords(signals, ["ecg", "mlii", "ii", "v1"])
                ppg_ch = find_channel_by_keywords(signals, ["ppg", "pleth", "ir", "red"])
                if resp_ch is None or (ecg_ch is None and ppg_ch is None):
                    skipped += 1
                    continue

                resp = np.asarray(signals[resp_ch], dtype=float)
                fs_resp = float(fs_map.get(resp_ch, next(iter(fs_map.values()), 100.0)))
                max_len_resp = int(fs_resp * max_duration_sec)
                if max_len_resp > 0 and resp.size > max_len_resp:
                    resp = resp[:max_len_resp]
                rr_ref = estimate_resp_rate_from_series(resp, fs_resp)
                if not np.isfinite(rr_ref):
                    skipped += 1
                    continue

                if ecg_ch is not None:
                    ecg = np.asarray(signals[ecg_ch], dtype=float)
                    fs_ecg = float(fs_map.get(ecg_ch, fs_resp))
                    max_len_ecg = int(fs_ecg * max_duration_sec)
                    if max_len_ecg > 0 and ecg.size > max_len_ecg:
                        ecg = ecg[:max_len_ecg]
                    ecg = preprocess_ecg(ecg, fs_ecg)
                    rpeaks = detect_rpeaks_adaptive(ecg, fs_ecg)
                    edr, edr_fs = edr_from_rpeak_amplitude(ecg, rpeaks, fs_ecg)
                    rr_edr = estimate_resp_rate_from_series(edr, edr_fs)
                    if np.isfinite(rr_edr):
                        errs_ecg.append(abs(rr_edr - rr_ref))

                if ppg_ch is not None:
                    ppg = np.asarray(signals[ppg_ch], dtype=float)
                    fs_ppg = float(fs_map.get(ppg_ch, fs_resp))
                    max_len_ppg = int(fs_ppg * max_duration_sec)
                    if max_len_ppg > 0 and ppg.size > max_len_ppg:
                        ppg = ppg[:max_len_ppg]
                    ppg = preprocess_ppg(ppg, fs_ppg)
                    peaks = detect_ppg_peaks(ppg, fs_ppg)
                    pdr, pdr_fs = pdr_from_ppg_amplitude(ppg, peaks, fs_ppg)
                    rr_pdr = estimate_resp_rate_from_series(pdr, pdr_fs)
                    if np.isfinite(rr_pdr):
                        errs_ppg.append(abs(rr_pdr - rr_ref))

                evaluated += 1
            except Exception:  # noqa: BLE001
                skipped += 1
                continue

    if evaluated == 0:
        return {
            "status": "skipped",
            "reason": "no usable respiration reference records",
            "evaluated_records": 0,
            "skipped_records": skipped,
        }
    return {
        "status": "ok",
        "evaluated_records": evaluated,
        "skipped_records": skipped,
        "ecg_resp_mae_bpm": float(np.mean(errs_ecg)) if errs_ecg else float("nan"),
        "ppg_resp_mae_bpm": float(np.mean(errs_ppg)) if errs_ppg else float("nan"),
        "ecg_resp_errors_bpm": errs_ecg[:1000],
        "ppg_resp_errors_bpm": errs_ppg[:1000],
    }
