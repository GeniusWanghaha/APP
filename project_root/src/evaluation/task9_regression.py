from __future__ import annotations

import math


def run_task9(current_results: dict, previous_results: dict | None = None) -> dict:
    # Regression gate over key metrics from ECG/PPG tasks.
    task1_f1 = current_results.get("task1", {}).get("adaptive", {}).get("f1")
    task6_sqi = current_results.get("task6", {}).get("sqi_mean")

    checks = {
        "task1_f1_available": isinstance(task1_f1, (int, float)) and not math.isnan(task1_f1),
        "task6_sqi_available": isinstance(task6_sqi, (int, float)) and not math.isnan(task6_sqi),
    }
    regression_flags: list[str] = []
    if previous_results:
        prev_f1 = previous_results.get("task1", {}).get("adaptive", {}).get("f1")
        prev_sqi = previous_results.get("task6", {}).get("sqi_mean")
        if isinstance(prev_f1, (int, float)) and isinstance(task1_f1, (int, float)):
            if task1_f1 + 1e-6 < prev_f1:
                regression_flags.append("ecg_rpeak_f1_regression")
        if isinstance(prev_sqi, (int, float)) and isinstance(task6_sqi, (int, float)):
            if task6_sqi + 1e-6 < prev_sqi:
                regression_flags.append("ppg_sqi_regression")

    return {
        "status": "ok" if not regression_flags else "warn",
        "checks": checks,
        "regression_flags": regression_flags,
    }

