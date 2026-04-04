from __future__ import annotations

import numpy as np
from sklearn.metrics import f1_score, precision_score, recall_score


NORMAL_SYMBOLS = {"N", "L", "R", "e", "j", ".", "/", "f"}


def run_task4(loader_registry, dataset_name: str = "mitdb", max_records: int = 12) -> dict:
    records = loader_registry.list_records(dataset_name)[:max_records]
    if not records:
        return {
            "status": "skipped",
            "reason": "no records available",
        }

    y_true = []
    y_pred = []
    evaluated = 0
    for record_id in records:
        try:
            payload = loader_registry.load_record(dataset_name, record_id)
            ann = payload.get("annotations", {}).get("atr")
            if not isinstance(ann, dict) or not ann.get("sample") or not ann.get("symbol"):
                continue
            samples = np.asarray(ann["sample"], dtype=int)
            symbols = ann["symbol"]
            if len(samples) < 5 or len(samples) != len(symbols):
                continue

            rr = np.diff(samples).astype(float)
            rr_med = np.median(rr)
            rr_flags = np.abs(rr - rr_med) > (0.2 * rr_med)
            pred_flags = np.concatenate([[False], rr_flags]).astype(int)
            true_flags = np.asarray([0 if s in NORMAL_SYMBOLS else 1 for s in symbols], dtype=int)

            y_true.extend(true_flags.tolist())
            y_pred.extend(pred_flags.tolist())
            evaluated += 1
        except Exception:  # noqa: BLE001
            continue

    if evaluated == 0:
        return {
            "status": "skipped",
            "reason": "no records with usable annotation symbols",
        }

    y_true_arr = np.asarray(y_true, dtype=int)
    y_pred_arr = np.asarray(y_pred, dtype=int)
    return {
        "status": "ok",
        "evaluated_records": evaluated,
        "precision": float(precision_score(y_true_arr, y_pred_arr, zero_division=0)),
        "recall": float(recall_score(y_true_arr, y_pred_arr, zero_division=0)),
        "f1": float(f1_score(y_true_arr, y_pred_arr, zero_division=0)),
    }
