from __future__ import annotations

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from sklearn.metrics import precision_recall_curve, roc_curve

from src.evaluation.channel_utils import find_channel_by_keywords
from src.preprocessing.ecg import detect_rpeaks_adaptive, preprocess_ecg
from src.preprocessing.ppg import detect_ppg_foots, detect_ppg_peaks, preprocess_ppg
from src.utils.pathing import ensure_dir


def _savefig(path: Path) -> None:
    ensure_dir(path.parent)
    plt.tight_layout()
    plt.savefig(path, dpi=160)
    plt.close()


def generate_ecg_filter_rpeak_figure(loader_registry, output_dir: Path) -> str | None:
    records = loader_registry.list_records("mitdb")
    if not records:
        return None
    payload = loader_registry.load_record("mitdb", records[0])
    signals = payload["signals"]
    fs_map = payload["fs"]
    ecg_ch = find_channel_by_keywords(signals, ["ecg", "mlii", "ii", "v1"]) or next(iter(signals.keys()))
    ecg = np.asarray(signals[ecg_ch], dtype=float)
    fs = float(fs_map.get(ecg_ch, next(iter(fs_map.values()), 250.0)))
    n = min(len(ecg), int(fs * 10))
    raw = ecg[:n]
    clean = preprocess_ecg(raw, fs)
    peaks = detect_rpeaks_adaptive(clean, fs)
    peaks = peaks[peaks < n]

    plt.figure(figsize=(12, 5))
    t = np.arange(n) / fs
    plt.plot(t, raw, label="raw", alpha=0.45)
    plt.plot(t, clean, label="filtered", linewidth=1.2)
    if peaks.size:
        plt.scatter(peaks / fs, clean[peaks], color="red", s=12, label="R peaks")
    plt.xlabel("Time (s)")
    plt.ylabel("Amplitude")
    plt.title("ECG Filter + R-peak Example")
    plt.legend()
    out = output_dir / "ecg_filter_rpeak_example.png"
    _savefig(out)
    return str(out)


def generate_rpeak_only_figure(loader_registry, output_dir: Path) -> str | None:
    records = loader_registry.list_records("mitdb")
    if not records:
        return None
    payload = loader_registry.load_record("mitdb", records[0])
    signals = payload["signals"]
    fs_map = payload["fs"]
    ecg_ch = find_channel_by_keywords(signals, ["ecg", "mlii", "ii", "v1"]) or next(iter(signals.keys()))
    ecg = np.asarray(signals[ecg_ch], dtype=float)
    fs = float(fs_map.get(ecg_ch, next(iter(fs_map.values()), 250.0)))
    n = min(len(ecg), int(fs * 8))
    clean = preprocess_ecg(ecg[:n], fs)
    peaks = detect_rpeaks_adaptive(clean, fs)
    peaks = peaks[peaks < n]
    plt.figure(figsize=(12, 4))
    t = np.arange(n) / fs
    plt.plot(t, clean, label="ECG clean")
    if peaks.size:
        plt.scatter(peaks / fs, clean[peaks], s=16, color="crimson", label="R")
    plt.title("R-peak Detection Example")
    plt.xlabel("Time (s)")
    plt.ylabel("Amplitude")
    plt.legend()
    out = output_dir / "rpeak_detection_example.png"
    _savefig(out)
    return str(out)


def generate_ppg_peak_foot_figure(loader_registry, output_dir: Path) -> str | None:
    records = loader_registry.list_records("but_ppg")
    if not records:
        return None
    payload = loader_registry.load_record("but_ppg", records[0])
    signals = payload["signals"]
    fs_map = payload["fs"]
    if not signals:
        return None
    ppg_ch = find_channel_by_keywords(signals, ["ppg", "pleth", "ir", "red"]) or next(iter(signals.keys()))
    ppg = np.asarray(signals[ppg_ch], dtype=float)
    fs = float(fs_map.get(ppg_ch, next(iter(fs_map.values()), 100.0)))
    n = min(len(ppg), int(fs * 10))
    ppg = ppg[:n]
    clean = preprocess_ppg(ppg, fs)
    peaks = detect_ppg_peaks(clean, fs)
    feet = detect_ppg_foots(clean, fs)
    peaks = peaks[peaks < n]
    feet = feet[feet < n]

    plt.figure(figsize=(12, 5))
    t = np.arange(n) / fs
    plt.plot(t, clean, label="PPG clean")
    if peaks.size:
        plt.scatter(peaks / fs, clean[peaks], s=14, color="green", label="peaks")
    if feet.size:
        plt.scatter(feet / fs, clean[feet], s=14, color="orange", label="foots")
    plt.xlabel("Time (s)")
    plt.ylabel("Normalized amplitude")
    plt.title("PPG Peak/Foot Example")
    plt.legend()
    out = output_dir / "ppg_peak_foot_example.png"
    _savefig(out)
    return str(out)


def generate_iteration_figure(round_logs: list[dict], output_dir: Path) -> str | None:
    if not round_logs:
        return None
    rounds = [item["round_id"] for item in round_logs]
    scores = [item.get("score", np.nan) for item in round_logs]
    plt.figure(figsize=(8, 4))
    plt.plot(rounds, scores, marker="o")
    plt.xlabel("Round")
    plt.ylabel("Composite Score")
    plt.title("Iteration Score Trend")
    out = output_dir / "iteration_score_trend.png"
    _savefig(out)
    return str(out)


def generate_af_curves(best_result: dict, output_dir: Path) -> tuple[str | None, str | None]:
    task3 = best_result.get("task3", {})
    y_true = np.asarray(task3.get("y_true", []), dtype=int)
    y_prob = np.asarray(task3.get("y_prob", []), dtype=float)
    if y_true.size < 5 or y_prob.size != y_true.size or len(np.unique(y_true)) < 2:
        return None, None

    fpr, tpr, _ = roc_curve(y_true, y_prob)
    plt.figure(figsize=(5, 5))
    plt.plot(fpr, tpr, label="AF ROC")
    plt.plot([0, 1], [0, 1], "--", color="gray")
    plt.xlabel("FPR")
    plt.ylabel("TPR")
    plt.title("AF ROC Curve")
    plt.legend()
    roc_path = output_dir / "af_roc_curve.png"
    _savefig(roc_path)

    prec, rec, _ = precision_recall_curve(y_true, y_prob)
    plt.figure(figsize=(5, 5))
    plt.plot(rec, prec, label="AF PR")
    plt.xlabel("Recall")
    plt.ylabel("Precision")
    plt.title("AF PR Curve")
    plt.legend()
    pr_path = output_dir / "af_pr_curve.png"
    _savefig(pr_path)
    return str(roc_path), str(pr_path)


def generate_sqi_distribution(best_result: dict, output_dir: Path) -> str | None:
    sqi = np.asarray(best_result.get("task6", {}).get("sqi_values", []), dtype=float)
    if sqi.size == 0:
        return None
    plt.figure(figsize=(8, 4))
    plt.hist(sqi, bins=20, color="#2a9d8f", alpha=0.85)
    plt.xlabel("SQI")
    plt.ylabel("Count")
    plt.title("PPG SQI Distribution")
    out = output_dir / "ppg_sqi_distribution.png"
    _savefig(out)
    return str(out)


def generate_rr_error_figure(best_result: dict, output_dir: Path) -> str | None:
    ecg_err = np.asarray(best_result.get("task7", {}).get("ecg_resp_errors_bpm", []), dtype=float)
    ppg_err = np.asarray(best_result.get("task7", {}).get("ppg_resp_errors_bpm", []), dtype=float)
    if ecg_err.size == 0 and ppg_err.size == 0:
        return None
    plt.figure(figsize=(8, 4))
    if ecg_err.size:
        plt.hist(ecg_err, bins=20, alpha=0.6, label="ECG-derived RR error")
    if ppg_err.size:
        plt.hist(ppg_err, bins=20, alpha=0.6, label="PPG-derived RR error")
    plt.xlabel("Absolute Error (bpm)")
    plt.ylabel("Count")
    plt.title("Respiratory Rate Error Distribution")
    plt.legend()
    out = output_dir / "rr_estimation_error_distribution.png"
    _savefig(out)
    return str(out)


def generate_ptt_distribution(best_result: dict, output_dir: Path) -> str | None:
    r2f = np.asarray(best_result.get("task8", {}).get("r_to_foot_values_ms", []), dtype=float)
    r2p = np.asarray(best_result.get("task8", {}).get("r_to_peak_values_ms", []), dtype=float)
    if r2f.size == 0 and r2p.size == 0:
        return None
    plt.figure(figsize=(8, 4))
    if r2f.size:
        plt.hist(r2f, bins=30, alpha=0.65, label="R-to-foot")
    if r2p.size:
        plt.hist(r2p, bins=30, alpha=0.65, label="R-to-peak")
    plt.xlabel("Delay (ms)")
    plt.ylabel("Count")
    plt.title("PTT/PWTT Related Delay Distribution")
    plt.legend()
    out = output_dir / "ptt_pwtt_delay_distribution.png"
    _savefig(out)
    return str(out)


def generate_regression_result_figure(round_logs: list[dict], output_dir: Path) -> str | None:
    if not round_logs:
        return None
    rounds = [item["round_id"] for item in round_logs]
    reg_count = [len(item.get("regression_flags", [])) for item in round_logs]
    plt.figure(figsize=(8, 4))
    plt.bar(rounds, reg_count, color="#e76f51")
    plt.xlabel("Round")
    plt.ylabel("Regression Flags")
    plt.title("Regression Test Flags Per Iteration")
    out = output_dir / "regression_flags_by_round.png"
    _savefig(out)
    return str(out)
