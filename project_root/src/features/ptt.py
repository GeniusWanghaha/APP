from __future__ import annotations

import numpy as np


def _derive_feet_before_peaks(
    ppg: np.ndarray,
    ppg_peaks: np.ndarray,
    fs_ppg: float,
    min_pre_peak_ms: float,
    max_pre_peak_ms: float,
) -> np.ndarray:
    if ppg.size == 0 or ppg_peaks.size == 0:
        return np.array([], dtype=int)
    min_offset = max(1, int((min_pre_peak_ms / 1000.0) * fs_ppg))
    max_offset = max(min_offset + 1, int((max_pre_peak_ms / 1000.0) * fs_ppg))
    feet: list[int] = []
    for peak in ppg_peaks.astype(int):
        left = max(0, peak - max_offset)
        right = max(left + 1, peak - min_offset)
        if right <= left:
            continue
        local = np.argmin(ppg[left:right])
        feet.append(left + int(local))
    if not feet:
        return np.array([], dtype=int)
    return np.asarray(feet, dtype=int)


def pair_delays_ms_constrained(
    rpeaks: np.ndarray,
    ppg: np.ndarray,
    ppg_peaks: np.ndarray,
    fs_ecg: float,
    fs_ppg: float,
    r2foot_window_ms: tuple[float, float] = (80.0, 400.0),
    r2peak_window_ms: tuple[float, float] = (120.0, 500.0),
    pre_peak_foot_window_ms: tuple[float, float] = (40.0, 350.0),
    jump_mad_scale: float = 3.0,
    jump_min_ms: float = 20.0,
) -> dict:
    r_times = np.asarray(rpeaks, dtype=float) / fs_ecg
    peak_idx = np.asarray(ppg_peaks, dtype=int)
    peak_times = peak_idx / fs_ppg
    foot_idx = _derive_feet_before_peaks(
        ppg=np.asarray(ppg, dtype=float),
        ppg_peaks=peak_idx,
        fs_ppg=fs_ppg,
        min_pre_peak_ms=pre_peak_foot_window_ms[0],
        max_pre_peak_ms=pre_peak_foot_window_ms[1],
    )
    foot_times = foot_idx / fs_ppg if foot_idx.size else np.array([], dtype=float)

    beats: list[dict] = []
    for beat_i, r_t in enumerate(r_times):
        reasons: list[str] = []
        # Pairing anchor: choose the first peak in physiological window after R.
        peak_mask = (peak_times > r_t + r2peak_window_ms[0] / 1000.0) & (
            peak_times < r_t + r2peak_window_ms[1] / 1000.0
        )
        if not np.any(peak_mask):
            reasons.append("missing_peak_in_r2peak_window")
            beats.append(
                {
                    "beat_idx": beat_i,
                    "r_time_sec": float(r_t),
                    "peak_time_sec": float("nan"),
                    "foot_time_sec": float("nan"),
                    "r_to_peak_ms": float("nan"),
                    "r_to_foot_ms": float("nan"),
                    "foot_to_peak_ms": float("nan"),
                    "valid": False,
                    "invalid_reasons": reasons,
                }
            )
            continue
        peak_pos = int(np.where(peak_mask)[0][0])
        peak_t = float(peak_times[peak_pos])

        foot_t = float("nan")
        if peak_pos < foot_times.size:
            foot_t = float(foot_times[peak_pos])
        else:
            reasons.append("missing_foot_for_peak")

        r2p = (peak_t - r_t) * 1000.0
        r2f = (foot_t - r_t) * 1000.0 if np.isfinite(foot_t) else float("nan")
        f2p = (peak_t - foot_t) * 1000.0 if np.isfinite(foot_t) else float("nan")

        if not np.isfinite(foot_t):
            reasons.append("invalid_foot_time")
        else:
            if not (r_t < foot_t < peak_t):
                reasons.append("order_violation_R_foot_peak")
            if not (r2foot_window_ms[0] <= r2f <= r2foot_window_ms[1]):
                reasons.append("r2foot_out_of_window")
            if not (f2p > 0.0):
                reasons.append("foot_to_peak_non_positive")

        if not (r2peak_window_ms[0] <= r2p <= r2peak_window_ms[1]):
            reasons.append("r2peak_out_of_window")

        beats.append(
            {
                "beat_idx": beat_i,
                "r_time_sec": float(r_t),
                "peak_time_sec": float(peak_t),
                "foot_time_sec": float(foot_t),
                "r_to_peak_ms": float(r2p),
                "r_to_foot_ms": float(r2f),
                "foot_to_peak_ms": float(f2p),
                "valid": len(reasons) == 0,
                "invalid_reasons": reasons,
            }
        )

    # Rule: adjacent beat delay should not jump too much.
    for delay_key, reason_name in [
        ("r_to_foot_ms", "delay_jump_r2foot"),
        ("r_to_peak_ms", "delay_jump_r2peak"),
    ]:
        valid_vals = np.asarray(
            [b[delay_key] for b in beats if b["valid"] and np.isfinite(b[delay_key])],
            dtype=float,
        )
        if valid_vals.size < 5:
            continue
        med = float(np.median(valid_vals))
        mad = float(np.median(np.abs(valid_vals - med)))
        threshold = max(jump_mad_scale * mad, jump_min_ms)
        for b in beats:
            if not np.isfinite(b[delay_key]):
                continue
            if abs(b[delay_key] - med) > threshold:
                b["valid"] = False
                if reason_name not in b["invalid_reasons"]:
                    b["invalid_reasons"].append(reason_name)

    valid_r2f = np.asarray(
        [b["r_to_foot_ms"] for b in beats if b["valid"] and np.isfinite(b["r_to_foot_ms"])],
        dtype=float,
    )
    valid_r2p = np.asarray(
        [b["r_to_peak_ms"] for b in beats if b["valid"] and np.isfinite(b["r_to_peak_ms"])],
        dtype=float,
    )
    invalid_reason_counts: dict[str, int] = {}
    for b in beats:
        if b["valid"]:
            continue
        for reason in b["invalid_reasons"]:
            invalid_reason_counts[reason] = invalid_reason_counts.get(reason, 0) + 1

    return {
        "beats": beats,
        "r_to_foot_ms": valid_r2f,
        "r_to_peak_ms": valid_r2p,
        "r_to_foot_mean_ms": float(np.mean(valid_r2f)) if valid_r2f.size else float("nan"),
        "r_to_peak_mean_ms": float(np.mean(valid_r2p)) if valid_r2p.size else float("nan"),
        "r_to_foot_std_ms": float(np.std(valid_r2f)) if valid_r2f.size else float("nan"),
        "r_to_peak_std_ms": float(np.std(valid_r2p)) if valid_r2p.size else float("nan"),
        "total_beats": int(len(beats)),
        "valid_beats": int(np.sum([1 for b in beats if b["valid"]])),
        "invalid_reason_counts": invalid_reason_counts,
    }


def compute_delays_ms(
    rpeaks: np.ndarray,
    ppg_feet: np.ndarray,
    ppg_peaks: np.ndarray,
    fs_ecg: float,
    fs_ppg: float,
    min_delay_ms: float = 80.0,
    max_delay_ms: float = 450.0,
) -> dict[str, np.ndarray]:
    # Legacy helper kept for compatibility with earlier reports/scripts.
    r_times = rpeaks / fs_ecg
    foot_times = ppg_feet / fs_ppg
    peak_times = ppg_peaks / fs_ppg

    rf: list[float] = []
    rp: list[float] = []
    for r in r_times:
        foot_after = foot_times[foot_times > r]
        peak_after = peak_times[peak_times > r]
        if foot_after.size:
            d = (foot_after[0] - r) * 1000.0
            if min_delay_ms <= d <= max_delay_ms:
                rf.append(d)
        if peak_after.size:
            d = (peak_after[0] - r) * 1000.0
            if min_delay_ms <= d <= max_delay_ms:
                rp.append(d)

    rf_arr = np.asarray(rf, dtype=float)
    rp_arr = np.asarray(rp, dtype=float)
    return {
        "r_to_foot_ms": rf_arr,
        "r_to_peak_ms": rp_arr,
        "r_to_foot_mean_ms": np.mean(rf_arr) if rf_arr.size else float("nan"),
        "r_to_peak_mean_ms": np.mean(rp_arr) if rp_arr.size else float("nan"),
        "r_to_foot_std_ms": np.std(rf_arr) if rf_arr.size else float("nan"),
        "r_to_peak_std_ms": np.std(rp_arr) if rp_arr.size else float("nan"),
    }
