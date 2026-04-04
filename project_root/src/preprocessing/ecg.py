from __future__ import annotations

import numpy as np
import neurokit2 as nk
from scipy import signal


def remove_dc(ecg: np.ndarray) -> np.ndarray:
    return ecg - np.nanmean(ecg)


def baseline_wander_highpass(ecg: np.ndarray, fs: float, cutoff_hz: float = 0.5) -> np.ndarray:
    if ecg.size < 12 or fs <= 2.0:
        return ecg.astype(float)
    b, a = signal.butter(2, cutoff_hz / (fs / 2.0), btype="highpass")
    try:
        return signal.filtfilt(b, a, ecg)
    except ValueError:
        return ecg.astype(float)


def notch_filter(ecg: np.ndarray, fs: float, freq_hz: float = 50.0, q: float = 30.0) -> np.ndarray:
    if ecg.size < 12 or fs <= 2.0:
        return ecg.astype(float)
    if freq_hz >= (fs / 2.0) * 0.95:
        return ecg.astype(float)
    b, a = signal.iirnotch(w0=freq_hz / (fs / 2.0), Q=q)
    try:
        return signal.filtfilt(b, a, ecg)
    except ValueError:
        return ecg.astype(float)


def bandpass_filter(ecg: np.ndarray, fs: float, low_hz: float = 0.5, high_hz: float = 40.0) -> np.ndarray:
    if ecg.size < 25 or fs <= 2.0:
        return ecg.astype(float)
    nyq = fs / 2.0
    low = max(0.05, low_hz)
    high = min(high_hz, nyq * 0.95)
    if high <= low:
        return ecg.astype(float)
    b, a = signal.butter(3, [low / nyq, high / nyq], btype="band")
    try:
        return signal.filtfilt(b, a, ecg)
    except ValueError:
        return ecg.astype(float)


def preprocess_ecg(ecg: np.ndarray, fs: float) -> np.ndarray:
    if ecg.size == 0:
        return ecg.astype(float)
    clean = remove_dc(ecg)
    clean = baseline_wander_highpass(clean, fs=fs, cutoff_hz=0.5)
    if fs >= 90:
        clean = notch_filter(clean, fs=fs, freq_hz=50.0, q=25.0)
    clean = bandpass_filter(clean, fs=fs, low_hz=0.5, high_hz=min(40.0, fs / 2.2))
    return clean


def detect_rpeaks_classic(ecg: np.ndarray, fs: float) -> np.ndarray:
    _, info = nk.ecg_peaks(ecg, sampling_rate=fs, method="pantompkins1985")
    return np.asarray(info.get("ECG_R_Peaks", []), dtype=int)


def detect_rpeaks_adaptive(
    ecg: np.ndarray,
    fs: float,
    threshold_scale: float = 0.8,
    min_distance_sec: float = 0.25,
) -> np.ndarray:
    if ecg.size < 4:
        return np.array([], dtype=int)
    deriv = np.diff(ecg, prepend=ecg[0])
    sq = deriv**2
    mwi_window = max(1, int(0.15 * fs))
    kernel = np.ones(mwi_window) / mwi_window
    mwi = np.convolve(sq, kernel, mode="same")

    thresh = np.median(mwi) + float(threshold_scale) * np.std(mwi)
    min_distance = max(1, int(min_distance_sec * fs))
    peaks, _ = signal.find_peaks(mwi, height=thresh, distance=min_distance)

    # Search local maximum on ECG around each MWI peak for better localization
    rpeaks: list[int] = []
    radius = max(1, int(0.08 * fs))
    for p in peaks:
        left = max(0, p - radius)
        right = min(len(ecg), p + radius + 1)
        local = np.argmax(ecg[left:right])
        rpeaks.append(left + int(local))
    return np.asarray(sorted(set(rpeaks)), dtype=int)


def rpeak_sqi(rpeaks: np.ndarray, fs: float) -> float:
    if len(rpeaks) < 3:
        return 0.0
    rr = np.diff(rpeaks) / fs
    rr_ms = rr * 1000.0
    valid = (rr_ms > 300) & (rr_ms < 2000)
    if not np.any(valid):
        return 0.0
    rr_valid = rr_ms[valid]
    cv = np.std(rr_valid) / max(np.mean(rr_valid), 1e-6)
    score = 1.0 - min(cv, 1.0)
    return float(np.clip(score, 0.0, 1.0))
