from __future__ import annotations

import numpy as np
from scipy import signal


def estimate_resp_rate_from_series(series: np.ndarray, fs: float) -> float:
    if series.size < fs * 10:
        return float("nan")
    freqs, power = signal.welch(series, fs=fs, nperseg=min(len(series), int(fs * 30)))
    band = (freqs >= 0.1) & (freqs <= 0.6)
    if not np.any(band):
        return float("nan")
    idx = np.argmax(power[band])
    resp_hz = freqs[band][idx]
    return float(resp_hz * 60.0)


def edr_from_rpeak_amplitude(ecg: np.ndarray, rpeaks: np.ndarray, fs: float) -> tuple[np.ndarray, float]:
    if len(rpeaks) < 4:
        return np.array([], dtype=float), 1.0
    amp = ecg[rpeaks]
    t = rpeaks / fs
    fs_interp = 4.0
    t_interp = np.arange(t[0], t[-1], 1.0 / fs_interp)
    edr = np.interp(t_interp, t, amp)
    return edr, fs_interp


def pdr_from_ppg_amplitude(ppg: np.ndarray, peaks: np.ndarray, fs: float) -> tuple[np.ndarray, float]:
    if len(peaks) < 4:
        return np.array([], dtype=float), 1.0
    amp = ppg[peaks]
    t = peaks / fs
    fs_interp = 4.0
    t_interp = np.arange(t[0], t[-1], 1.0 / fs_interp)
    pdr = np.interp(t_interp, t, amp)
    return pdr, fs_interp

