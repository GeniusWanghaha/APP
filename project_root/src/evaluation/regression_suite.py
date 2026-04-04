from __future__ import annotations

from dataclasses import dataclass


@dataclass
class RegressionDecision:
    accepted: bool
    reason: str
    improvement_percent: float
    regression_flags: list[str]


def compare_rounds(previous: dict, current: dict) -> RegressionDecision:
    if not previous:
        return RegressionDecision(
            accepted=True,
            reason="first round",
            improvement_percent=0.0,
            regression_flags=[],
        )

    regression_flags: list[str] = []
    prev_f1 = previous.get("task1", {}).get("adaptive", {}).get("f1")
    curr_f1 = current.get("task1", {}).get("adaptive", {}).get("f1")
    prev_ppg = previous.get("task6", {}).get("sqi_mean")
    curr_ppg = current.get("task6", {}).get("sqi_mean")

    improvement = 0.0
    if isinstance(prev_f1, (float, int)) and isinstance(curr_f1, (float, int)):
        if prev_f1 > 1e-9:
            improvement += ((curr_f1 - prev_f1) / prev_f1) * 100.0
        if curr_f1 + 1e-6 < prev_f1:
            regression_flags.append("task1_adaptive_f1_regression")

    if isinstance(prev_ppg, (float, int)) and isinstance(curr_ppg, (float, int)):
        if prev_ppg > 1e-9:
            improvement += ((curr_ppg - prev_ppg) / prev_ppg) * 100.0
        if curr_ppg + 1e-6 < prev_ppg:
            regression_flags.append("task6_sqi_regression")

    accepted = not regression_flags
    reason = "accepted" if accepted else "rejected_due_to_regression"
    return RegressionDecision(
        accepted=accepted,
        reason=reason,
        improvement_percent=float(improvement),
        regression_flags=regression_flags,
    )

