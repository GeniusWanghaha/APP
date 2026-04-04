from __future__ import annotations

import csv
import json
from pathlib import Path

import numpy as np


def compute_python_metrics() -> dict[str, float]:
    rr_ms = np.asarray(
        [
            780.0,
            810.0,
            795.0,
            830.0,
            770.0,
            760.0,
            820.0,
            805.0,
            790.0,
            815.0,
            785.0,
            800.0,
        ],
        dtype=float,
    )
    ptt_ms = np.asarray(
        [
            218.0,
            224.0,
            221.0,
            229.0,
            216.0,
            214.0,
            226.0,
            223.0,
            220.0,
            227.0,
            219.0,
            222.0,
        ],
        dtype=float,
    )
    pat_ms = np.asarray(
        [
            392.0,
            401.0,
            398.0,
            407.0,
            390.0,
            387.0,
            403.0,
            400.0,
            396.0,
            405.0,
            394.0,
            399.0,
        ],
        dtype=float,
    )

    mean_rr = float(np.mean(rr_ms))
    mean_hr = float(60000.0 / mean_rr) if mean_rr > 0 else float("nan")
    sdnn = float(np.std(rr_ms))
    rr_cv = float(sdnn / mean_rr) if mean_rr > 0 else float("nan")
    diffs = np.diff(rr_ms)
    rmssd = float(np.sqrt(np.mean(diffs**2)))
    pnn50 = float(np.mean(np.abs(diffs) > 50.0) * 100.0)

    rr_x1 = rr_ms[:-1]
    rr_x2 = rr_ms[1:]
    sd1 = float(np.std((rr_x2 - rr_x1) / np.sqrt(2.0)))
    sd2 = float(np.std((rr_x2 + rr_x1) / np.sqrt(2.0)))
    sd1_sd2_ratio = float(sd1 / sd2) if sd2 > 1e-9 else float("nan")

    ptt_mean = float(np.mean(ptt_ms))
    pat_mean = float(np.mean(pat_ms))
    rise_time_mean = float(np.mean(pat_ms - ptt_ms))
    rr_cv_for_consistency = float(sdnn / max(mean_rr, 1.0))
    ptt_cv = float(np.std(ptt_ms) / max(ptt_mean, 1.0))
    beat_pulse_consistency = float(np.clip(1.0 - abs(rr_cv_for_consistency - ptt_cv) * 4.0, 0.0, 1.0))
    arrhythmia_index = float(np.clip((sdnn / mean_rr) * 100.0, 0.0, 100.0))

    return {
        "mean_rr_ms": mean_rr,
        "mean_hr_bpm": mean_hr,
        "sdnn_ms": sdnn,
        "rr_cv": rr_cv,
        "rmssd_ms": rmssd,
        "pnn50_percent": pnn50,
        "sd1_ms": sd1,
        "sd2_ms": sd2,
        "sd1_sd2_ratio": sd1_sd2_ratio,
        "ptt_mean_ms": ptt_mean,
        "pat_mean_ms": pat_mean,
        "rise_time_mean_ms": rise_time_mean,
        "beat_pulse_consistency": beat_pulse_consistency,
        "arrhythmia_index": arrhythmia_index,
    }


def read_kotlin_metrics(path: Path) -> dict[str, float]:
    rows = list(csv.DictReader(path.open("r", encoding="utf-8")))
    out: dict[str, float] = {}
    for row in rows:
        metric = row.get("metric")
        value = row.get("value")
        if metric is None or value is None:
            continue
        out[metric] = float(value)
    return out


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    kotlin_metrics_path = root / "outputs" / "metrics" / "kotlin_parity_metrics.csv"
    if not kotlin_metrics_path.exists():
        raise FileNotFoundError(
            f"Missing {kotlin_metrics_path}. Run Kotlin unit test KotlinParityExportTest first."
        )

    python_metrics = compute_python_metrics()
    kotlin_metrics = read_kotlin_metrics(kotlin_metrics_path)

    table_path = root / "outputs" / "tables" / "kotlin_python_numeric_parity.csv"
    report_path = root / "outputs" / "reports" / "kotlin_python_numeric_parity.md"
    summary_json_path = root / "outputs" / "metrics" / "kotlin_python_numeric_parity_summary.json"

    table_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    summary_json_path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = ["metric", "kotlin_value", "python_value", "abs_diff", "matched"]
    rows: list[dict[str, str]] = []
    matched_count = 0
    for metric in sorted(python_metrics.keys()):
        py_val = python_metrics[metric]
        kt_val = kotlin_metrics.get(metric, float("nan"))
        abs_diff = abs(kt_val - py_val) if np.isfinite(kt_val) and np.isfinite(py_val) else float("nan")
        matched = bool(np.isfinite(abs_diff) and abs_diff <= 1e-9)
        if matched:
            matched_count += 1
        rows.append(
            {
                "metric": metric,
                "kotlin_value": f"{kt_val:.12f}" if np.isfinite(kt_val) else "nan",
                "python_value": f"{py_val:.12f}" if np.isfinite(py_val) else "nan",
                "abs_diff": f"{abs_diff:.12f}" if np.isfinite(abs_diff) else "nan",
                "matched": "yes" if matched else "no",
            }
        )

    with table_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    summary = {
        "total_metrics": len(rows),
        "matched_metrics": matched_count,
        "unmatched_metrics": len(rows) - matched_count,
        "kotlin_metrics_file": str(kotlin_metrics_path),
        "table_file": str(table_path),
    }
    summary_json_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    report = f"""# Kotlin-Python 数值对齐报告
生成时间：2026-04-03

## 结论
- 对齐指标数：{matched_count}/{len(rows)}
- 未对齐指标数：{len(rows) - matched_count}
- 对齐公差：`abs_diff <= 1e-9`

## 说明
- 本报告基于共享夹具（固定 RR/PTT/PAT 序列）进行公式级数值一致性校验。
- 这属于“同输入下数学一致性”验证，不等同于真实传感器全链路逐样本一致性。
- 详细明细见：`outputs/tables/kotlin_python_numeric_parity.csv`
"""
    report_path.write_text(report, encoding="utf-8")
    print(f"written: {table_path}")
    print(f"written: {report_path}")
    print(f"written: {summary_json_path}")


if __name__ == "__main__":
    main()
