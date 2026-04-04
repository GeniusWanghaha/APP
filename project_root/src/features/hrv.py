from __future__ import annotations

import numpy as np


def rr_intervals_ms(rpeaks: np.ndarray, fs: float) -> np.ndarray:
    if len(rpeaks) < 2:
        return np.array([], dtype=float)
    return np.diff(rpeaks) * 1000.0 / fs


def hrv_features(rr_ms: np.ndarray) -> dict[str, float]:
    if rr_ms.size == 0:
        return {
            "mean_rr_ms": float("nan"),
            "mean_hr_bpm": float("nan"),
            "sdnn_ms": float("nan"),
            "rmssd_ms": float("nan"),
            "pnn50_percent": float("nan"),
        }
    diffs = np.diff(rr_ms)
    rmssd = np.sqrt(np.mean(diffs**2)) if diffs.size > 0 else float("nan")
    pnn50 = float(np.mean(np.abs(diffs) > 50.0) * 100.0) if diffs.size > 0 else float("nan")
    mean_rr = float(np.mean(rr_ms))
    return {
        "mean_rr_ms": mean_rr,
        "mean_hr_bpm": float(60000.0 / mean_rr) if mean_rr > 0 else float("nan"),
        "sdnn_ms": float(np.std(rr_ms)),
        "rmssd_ms": float(rmssd),
        "pnn50_percent": pnn50,
    }


def remove_outlier_rr(rr_ms: np.ndarray, low_ms: float = 320.0, high_ms: float = 2000.0) -> np.ndarray:
    return rr_ms[(rr_ms >= low_ms) & (rr_ms <= high_ms)]

