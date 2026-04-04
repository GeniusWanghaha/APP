from __future__ import annotations

import numpy as np


def match_peaks_with_tolerance(
    ref: np.ndarray,
    pred: np.ndarray,
    tolerance_samples: int,
) -> tuple[int, int, int, np.ndarray]:
    if ref.size == 0 and pred.size == 0:
        return 0, 0, 0, np.array([], dtype=float)
    if ref.size == 0:
        return 0, int(pred.size), 0, np.array([], dtype=float)
    if pred.size == 0:
        return 0, 0, int(ref.size), np.array([], dtype=float)

    ref_used = np.zeros(ref.size, dtype=bool)
    tp = 0
    loc_errors = []
    for p in pred:
        diff = np.abs(ref - p)
        idx = int(np.argmin(diff))
        if diff[idx] <= tolerance_samples and not ref_used[idx]:
            ref_used[idx] = True
            tp += 1
            loc_errors.append(float(diff[idx]))
    fp = int(pred.size - tp)
    fn = int(ref.size - tp)
    return tp, fp, fn, np.asarray(loc_errors, dtype=float)


def classification_metrics(tp: int, fp: int, fn: int) -> dict[str, float]:
    sensitivity = tp / max(tp + fn, 1)
    ppv = tp / max(tp + fp, 1)
    f1 = 2 * sensitivity * ppv / max(sensitivity + ppv, 1e-12)
    return {
        "sensitivity": float(sensitivity),
        "ppv": float(ppv),
        "f1": float(f1),
    }

