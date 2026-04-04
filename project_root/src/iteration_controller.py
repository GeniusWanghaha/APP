from __future__ import annotations

import math
from copy import deepcopy
from pathlib import Path
from typing import Any

from src.evaluation.regression_suite import compare_rounds
from src.evaluation.task1_ecg_rpeak import run_task1
from src.evaluation.task2_hrv import run_task2
from src.evaluation.task3_af import run_task3
from src.evaluation.task4_arrhythmia import run_task4
from src.evaluation.task5_ecg_qt_pwave import run_task5
from src.evaluation.task6_ppg import run_task6
from src.evaluation.task7_resp import run_task7
from src.evaluation.task8_ptt import run_task8
from src.evaluation.task9_regression import run_task9
from src.utils.io_utils import write_json


def run_all_tasks(
    loader_registry,
    adaptive_threshold_scale: float,
    previous_result: dict[str, Any] | None = None,
) -> dict[str, Any]:
    result = {
        "task1": run_task1(
            loader_registry=loader_registry,
            dataset_names=["mitdb", "nsrdb", "nstdb"],
            adaptive_threshold_scale=adaptive_threshold_scale,
        ),
        "task2": run_task2(
            loader_registry=loader_registry,
            dataset_names=["mitdb", "nsrdb", "afdb"],
        ),
        "task3": run_task3(
            loader_registry=loader_registry,
            dataset_names=["afdb", "ltafdb", "mitdb"],
        ),
        "task4": run_task4(
            loader_registry=loader_registry,
            dataset_name="mitdb",
        ),
        "task5": run_task5(
            loader_registry=loader_registry,
            dataset_names=["qtdb", "but_pdb"],
        ),
        "task6": run_task6(
            loader_registry=loader_registry,
            dataset_names=["but_ppg", "ppg_dalia", "bidmc"],
        ),
        "task7": run_task7(
            loader_registry=loader_registry,
            dataset_names=["bidmc", "apnea_ecg"],
        ),
        "task8": run_task8(
            loader_registry=loader_registry,
            dataset_names=["ptt_ppg"],
        ),
    }
    result["task9"] = run_task9(result, previous_result)
    return result


def _round_score(result: dict[str, Any]) -> float:
    task1_f1 = result.get("task1", {}).get("adaptive", {}).get("f1")
    task3_f1 = result.get("task3", {}).get("f1")
    task6_sqi = result.get("task6", {}).get("sqi_mean")
    values = [task1_f1, task3_f1, task6_sqi]
    valid = [float(v) for v in values if isinstance(v, (int, float)) and not math.isnan(v)]
    return float(sum(valid) / len(valid)) if valid else float("nan")


def run_iteration_loop(
    loader_registry,
    outputs_metrics_dir: Path,
    max_iterations: int,
    min_improvement_percent: float,
    stop_after_stagnation_rounds: int,
) -> dict[str, Any]:
    round_logs: list[dict[str, Any]] = []
    candidate_scales = [0.7, 0.8, 0.9, 1.0]

    best_result = run_all_tasks(loader_registry, adaptive_threshold_scale=0.8, previous_result=None)
    best_round_id = 0
    best_score = _round_score(best_result)
    write_json(outputs_metrics_dir / "round_00_metrics.json", best_result)
    round_logs.append(
        {
            "round_id": 0,
            "adaptive_threshold_scale": 0.8,
            "decision": "baseline",
            "score": best_score,
            "improvement_percent": 0.0,
            "regression_flags": [],
        }
    )

    no_gain_rounds = 0
    previous = deepcopy(best_result)
    stopping_reason = "max_iterations_reached"

    for round_id in range(1, max_iterations + 1):
        scale = candidate_scales[(round_id - 1) % len(candidate_scales)]
        current = run_all_tasks(loader_registry, adaptive_threshold_scale=scale, previous_result=previous)
        write_json(outputs_metrics_dir / f"round_{round_id:02d}_metrics.json", current)

        decision = compare_rounds(previous=previous, current=current)
        current_score = _round_score(current)
        improvement_percent = decision.improvement_percent

        accepted = decision.accepted
        if accepted and (
            (not math.isnan(current_score) and math.isnan(best_score))
            or (not math.isnan(current_score) and current_score > best_score)
        ):
            best_result = deepcopy(current)
            best_score = current_score
            best_round_id = round_id

        if accepted and improvement_percent >= min_improvement_percent:
            no_gain_rounds = 0
        else:
            no_gain_rounds += 1

        round_logs.append(
            {
                "round_id": round_id,
                "adaptive_threshold_scale": scale,
                "decision": decision.reason,
                "accepted": accepted,
                "score": current_score,
                "improvement_percent": improvement_percent,
                "regression_flags": decision.regression_flags,
            }
        )
        previous = deepcopy(current)

        if no_gain_rounds >= stop_after_stagnation_rounds:
            stopping_reason = (
                f"stopped_after_{stop_after_stagnation_rounds}_stagnation_rounds;"
                f" min_improvement_percent={min_improvement_percent}"
            )
            break

    return {
        "best_round_id": best_round_id,
        "best_result": best_result,
        "round_logs": round_logs,
        "stopping_reason": stopping_reason,
    }
