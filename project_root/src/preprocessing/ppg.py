from __future__ import annotations

import numpy as np
from scipy import signal


def detrend_ppg(ppg: np.ndarray) -> np.ndarray:
    if ppg.size < 4:
        return ppg.astype(float)
    return signal.detrend(ppg, type="linear")


def bandpass_ppg(ppg: np.ndarray, fs: float, low_hz: float = 0.5, high_hz: float = 8.0) -> np.ndarray:
    if ppg.size < 25 or fs <= 2.0:
        return ppg.astype(float)
    nyq = fs / 2.0
    low = max(0.05, low_hz)
    high = min(high_hz, nyq * 0.95)
    if high <= low:
        return ppg.astype(float)
    b, a = signal.butter(3, [low / nyq, high / nyq], btype="band")
    try:
        return signal.filtfilt(b, a, ppg)
    except ValueError:
        return ppg.astype(float)


def suppress_outliers(ppg: np.ndarray, z: float = 5.0) -> np.ndarray:
    x = ppg.copy()
    mean = np.mean(x)
    std = np.std(x) + 1e-8
    mask = np.abs((x - mean) / std) > z
    if np.any(mask):
        x[mask] = np.median(x)
    return x


def normalize_ppg(ppg: np.ndarray) -> np.ndarray:
    std = np.std(ppg)
    if std < 1e-9:
        return ppg - np.mean(ppg)
    return (ppg - np.mean(ppg)) / std


def preprocess_ppg(ppg: np.ndarray, fs: float) -> np.ndarray:
    if ppg.size == 0:
        return ppg.astype(float)
    x = suppress_outliers(ppg)
    x = detrend_ppg(x)
    x = bandpass_ppg(x, fs)
    x = normalize_ppg(x)
    return x


def detect_ppg_peaks(ppg: np.ndarray, fs: float) -> np.ndarray:
    min_distance = max(1, int(0.35 * fs))
    prominence = 0.2 * np.std(ppg)
    peaks, _ = signal.find_peaks(ppg, distance=min_distance, prominence=prominence)
    return peaks.astype(int)


def detect_ppg_foots(ppg: np.ndarray, fs: float) -> np.ndarray:
    inv = -ppg
    min_distance = max(1, int(0.35 * fs))
    prominence = 0.1 * np.std(inv)
    feet, _ = signal.find_peaks(inv, distance=min_distance, prominence=prominence)
    return feet.astype(int)


def estimate_ppg_sqi(ppg: np.ndarray, peaks: np.ndarray, fs: float) -> float:
    if len(peaks) < 3:
        return 0.0
    rr = np.diff(peaks) / fs
    rr_valid = rr[(rr > 0.3) & (rr < 2.0)]
    if len(rr_valid) < 2:
        return 0.1
    periodicity = 1.0 - min(np.std(rr_valid) / max(np.mean(rr_valid), 1e-6), 1.0)
    band_energy = np.mean(ppg**2)
    spectral = np.abs(np.fft.rfft(ppg))
    if len(spectral) <= 1:
        return float(np.clip(periodicity, 0.0, 1.0))
    dom = np.max(spectral[1:]) / (np.sum(spectral[1:]) + 1e-9)
    score = 0.5 * periodicity + 0.25 * np.clip(dom * 10.0, 0.0, 1.0) + 0.25 * np.clip(band_energy, 0.0, 1.0)
    return float(np.clip(score, 0.0, 1.0))
