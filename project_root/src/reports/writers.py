from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd

from src.utils.pathing import ensure_dir


def _md_kv_table(payload: dict[str, Any]) -> str:
    lines = ["| key | value |", "| --- | --- |"]
    for key, value in payload.items():
        text = json.dumps(value, ensure_ascii=False) if isinstance(value, (dict, list)) else str(value)
        lines.append(f"| {key} | `{text}` |")
    return "\n".join(lines)


def write_dataset_summary_report(inventory_df: pd.DataFrame, output_path: Path) -> None:
    ensure_dir(output_path.parent)
    ok = int((inventory_df["status"] == "ok").sum())
    missing = int((inventory_df["status"].isin(["missing", "incomplete"])).sum())

    lines = [
        "# 00 Dataset Summary",
        "",
        f"- total datasets in catalog: **{len(inventory_df)}**",
        f"- ready datasets: **{ok}**",
        f"- missing/incomplete datasets: **{missing}**",
        "",
        "## Inventory Snapshot",
        "",
        "| dataset | phase | status | records | source |",
        "| --- | --- | --- | --- | --- |",
    ]
    for _, row in inventory_df.iterrows():
        lines.append(
            f"| {row['dataset_name']} | {row['phase']} | {row['status']} | {row['record_count']} | {row['source_url']} |"
        )

    lines.extend(["", "## Notes"])
    for _, row in inventory_df.iterrows():
        if row["notes"]:
            lines.append(f"- {row['dataset_name']}: {row['notes']}")

    output_path.write_text("\n".join(lines), encoding="utf-8")


def write_baseline_report(results: dict[str, Any], output_path: Path) -> None:
    ensure_dir(output_path.parent)
    lines = ["# 01 Baseline Report", ""]
    for task_name, payload in results.items():
        lines.extend([f"## {task_name}", ""])
        if isinstance(payload, dict):
            lines.append(_md_kv_table(payload))
        else:
            lines.append(f"- {payload}")
        lines.append("")
    output_path.write_text("\n".join(lines), encoding="utf-8")


def write_iteration_log(round_logs: list[dict[str, Any]], output_path: Path) -> None:
    ensure_dir(output_path.parent)
    lines = ["# 02 Iteration Log", ""]
    if not round_logs:
        lines.append("- no iteration rounds executed")
    else:
        for item in round_logs:
            lines.extend([f"## Round {item['round_id']}", "", _md_kv_table(item), ""])
    output_path.write_text("\n".join(lines), encoding="utf-8")


def write_final_validation_report(
    inventory_df: pd.DataFrame,
    best_result: dict[str, Any],
    stopping_reason: str,
    output_path: Path,
) -> None:
    ensure_dir(output_path.parent)

    ready_count = int((inventory_df["status"] == "ok").sum())
    total_count = int(len(inventory_df))

    lines = [
        "# 03 Final Validation Report",
        "",
        "## Project Goal",
        "- Validate ECG/PPG/sync algorithms with public datasets in a reproducible engineering workflow.",
        "",
        "## Task Definition",
        "- Task1 ECG R-peak/QRS",
        "- Task2 HR/HRV",
        "- Task3 AF screening",
        "- Task4 arrhythmia prompt baseline",
        "- Task5 QT/P-wave delineation gate",
        "- Task6 PPG HR/peak-foot/SQI",
        "- Task7 ECG/PPG derived respiration",
        "- Task8 ECG-PPG sync/PTT trend",
        "- Task9 anti-regression gate",
        "",
        "## Split Strategy",
        "- AF task uses GroupKFold by record/subject-id proxy (patient-wise intent).",
        "- Other baseline tasks are record-wise aggregate; strict subject-wise split remains a next-step item.",
        "",
        "## Evaluation Metrics",
        "- ECG: sensitivity, PPV, F1, localization error.",
        "- AF: sensitivity/specificity/precision/recall/F1/AUROC/confusion matrix.",
        "- PPG: HR proxy error, peak-foot success rate, SQI.",
        "- Respiration: MAE (bpm) for ECG-derived and PPG-derived respiration.",
        "- Sync/PTT: R-to-foot and R-to-peak delay mean/std.",
        "",
        "## Dataset Coverage",
        f"- Ready datasets: {ready_count} / {total_count}",
        "",
        "## Best Round Result Snapshot",
        "",
        "```json",
        json.dumps(best_result, ensure_ascii=False, indent=2),
        "```",
        "",
        "## Stop Condition",
        f"- {stopping_reason}",
        "",
        "## Hardware Mapping",
        "### 1) Directly validated by public datasets",
        "- ECG R-peak / HR / HRV / baseline rhythm prompts: mitdb, nsrdb, nstdb.",
        "- AF screening baseline generalization: afdb, ltafdb.",
        "- PPG peak/foot/SQI and motion robustness: but_ppg, ppg_dalia, bidmc.",
        "- ECG-PPG delay calculation chain: ptt_ppg.",
        "",
        "### 2) Indirect validation only",
        "- PVC/PAC prompt baseline can be validated on public labels, but hardware-specific noise transfer is not guaranteed.",
        "- QT/P-wave delineation pipeline can be validated, but gate thresholds still require device-side calibration.",
        "",
        "### 3) Trend validation only (not clinical claims)",
        "- PTT/PWTT and BP-related analysis are limited to trend/correlation engineering validation.",
        "",
        "### 4) Mapping to single-lead ECG + MAX30102",
        "- Direct transfer: preprocessing chain, R-peak detection, HR/HRV, PPG peak/foot, SQI framework, delay metrics.",
        "- Recalibration required: filter boundaries, detector thresholds, SQI bins, motion-artifact suppression parameters.",
        "- Must re-validate on self-collected data: AF/PVC alert thresholds, individual delay baseline, any BP trend mapping.",
        "",
        "## Failure Cases & Gaps",
        "- Some datasets may require manual acquisition or custom parsing due access/format differences.",
        "- Strict subject-wise split still needs better subject mapping for several tasks.",
        "",
        "## Best Config Snapshot",
        "- adaptive R-peak threshold scale is recorded in task1 result block.",
        "- full iteration logs are available in outputs/metrics/iteration_round_logs.json.",
        "",
        "## Next Real-Prototype Joint Debug Suggestions",
        "1. Collect 24h hardware sessions and rerun Task1/Task6/Task8 with rest/motion/contact-quality slices.",
        "2. Freeze SQI gate first, then enable AF/arrhythmia prompts, and validate with offline replay before live mode.",
        "3. Ingest self-collected data into the same manifest format for strict traceability/versioning.",
        "",
    ]
    output_path.write_text("\n".join(lines), encoding="utf-8")


def write_limitations_report(inventory_df: pd.DataFrame, output_path: Path) -> None:
    ensure_dir(output_path.parent)
    missing_rows = inventory_df[inventory_df["status"].isin(["missing", "incomplete"])]

    lines = [
        "# 04 Limitations And Next Steps",
        "",
        "## Current Limitations",
        "- Public dataset evaluation does not equal clinical validation.",
        "- PTT/PWTT analysis is for engineering trend/stability only.",
        "- Some datasets may require manual approval/download despite official URLs.",
        "",
        "## Datasets Requiring Follow-up",
    ]
    if missing_rows.empty:
        lines.append("- none")
    else:
        for _, row in missing_rows.iterrows():
            lines.append(f"- {row['dataset_name']}: status={row['status']}, note={row['notes']}")

    lines.extend(
        [
            "",
            "## Next Steps",
            "1. Complete remaining phase datasets.",
            "2. Run strict subject-wise evaluation for AF/arrhythmia tasks.",
            "3. Validate transfer on self-collected ESP32-S3 hardware sessions.",
        ]
    )
    output_path.write_text("\n".join(lines), encoding="utf-8")
