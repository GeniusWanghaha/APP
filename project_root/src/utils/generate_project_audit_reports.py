from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

import pandas as pd
import yaml


ROOT = Path("d:/optoelectronic_design/APP/project_root").resolve()
OUTPUTS = ROOT / "outputs"
REPORTS = OUTPUTS / "reports"
TABLES = OUTPUTS / "tables"
FIGURES = OUTPUTS / "figures"
METRICS = OUTPUTS / "metrics"


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _safe_read_text(path: Path, limit: int = 2_000_000) -> str:
    try:
        if path.stat().st_size > limit:
            return ""
        return path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return ""


def _now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _to_rel(path: Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def _mkdirs() -> None:
    REPORTS.mkdir(parents=True, exist_ok=True)
    TABLES.mkdir(parents=True, exist_ok=True)


def build_file_inventory() -> pd.DataFrame:
    text_ext = {
        ".py",
        ".md",
        ".json",
        ".csv",
        ".yaml",
        ".yml",
        ".txt",
        ".ps1",
        ".sh",
        ".bat",
        ".xml",
        ".toml",
        ".ini",
    }
    files = sorted([p for p in ROOT.rglob("*") if p.is_file()])

    corpus_parts = []
    for p in files:
        rel = _to_rel(p)
        if rel.startswith("env/"):
            continue
        if p.suffix.lower() in text_ext:
            content = _safe_read_text(p)
            if content:
                corpus_parts.append(content)
    corpus = "\n".join(corpus_parts)

    rows: list[dict[str, Any]] = []
    for p in files:
        rel = _to_rel(p)
        stat = p.stat()
        ext = p.suffix.lower()
        parts = set(rel.split("/"))

        in_env = rel.startswith("env/")
        is_main_flow = (
            (rel.startswith("src/") and "__pycache__" not in parts and ext == ".py")
            or rel in {"README.md", "requirements.txt", "Makefile", "run_all.ps1"}
            or rel.startswith("configs/")
        )
        is_intermediate = (
            rel.startswith("outputs/")
            or rel.startswith("datasets/normalized/")
            or rel.startswith("datasets/logs/")
            or "__pycache__" in parts
            or ext == ".pyc"
            or rel.startswith("env/")
        )
        is_possibly_expired = (
            "__pycache__" in parts
            or ext == ".pyc"
            or "tmp" in p.name.lower()
            or "temp" in p.name.lower()
            or "debug" in p.name.lower()
            or (rel.startswith("outputs/metrics/round_") and rel.endswith("_metrics.json"))
            or rel.endswith("task8_sync_refresh_snapshot.json")
        )

        if in_env:
            referenced = "n/a"
        else:
            rel_hit = corpus.count(rel)
            name_hit = corpus.count(p.name)
            referenced = "yes" if (rel_hit > 1 or name_hit > 1) else "no"

        rows.append(
            {
                "file_path": rel,
                "file_type": ext if ext else "no_ext",
                "size_bytes": stat.st_size,
                "last_modified": datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M:%S"),
                "is_main_flow_file": bool(is_main_flow),
                "is_intermediate_artifact": bool(is_intermediate),
                "is_possibly_expired": bool(is_possibly_expired),
                "is_referenced_by_other_files": referenced,
                "notes": "env/venv" if in_env else "",
            }
        )
    df = pd.DataFrame(rows)
    out = REPORTS / "project_file_inventory.csv"
    df.to_csv(out, index=False, encoding="utf-8-sig")
    return df


def build_task_algorithm_map(best_metrics: dict[str, Any]) -> pd.DataFrame:
    task_rows = [
        {
            "task": "Task1 ECG R峰检测",
            "algorithm": "经典 Pan-Tompkins(neurokit) + 自适应导数平方积分阈值检测",
            "core_code_files": "src/evaluation/task1_ecg_rpeak.py | src/preprocessing/ecg.py",
            "key_functions": "run_task1 | detect_rpeaks_classic | detect_rpeaks_adaptive",
            "datasets": "mitdb, nsrdb, nstdb",
            "labels": "WFDB atr/qrs 注释中的参考R峰",
            "evaluation": "Sensitivity/PPV/F1/定位误差",
            "split": "record-wise (每库前N条)",
            "best_result": f"adaptive F1={best_metrics['task1']['adaptive']['f1']:.4f}, loc_err={best_metrics['task1']['adaptive']['mean_abs_peak_localization_error_ms']:.2f}ms",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "stable",
        },
        {
            "task": "Task2 HR/HRV",
            "algorithm": "R峰->RR间期->HRV统计(含离群RR清洗)",
            "core_code_files": "src/evaluation/task2_hrv.py | src/features/hrv.py",
            "key_functions": "run_task2 | rr_intervals_ms | hrv_features | remove_outlier_rr",
            "datasets": "mitdb, nsrdb, afdb",
            "labels": "无显式分类标签，统计型任务",
            "evaluation": "mean HR/mean RR/SDNN/RMSSD/pNN50",
            "split": "record-wise aggregate",
            "best_result": "流程可稳定产出，指标区间合理",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "stable",
        },
        {
            "task": "Task3 AF筛查(主线)",
            "algorithm": "弱标签 + RR特征 + RandomForest(GroupKFold)",
            "core_code_files": "src/evaluation/task3_af.py | src/models/af_baseline.py | src/features/af_features.py",
            "key_functions": "run_task3 | build_af_samples | train_eval_af_baseline",
            "datasets": "afdb, ltafdb, mitdb",
            "labels": "旧版 weak label(symbol only)",
            "evaluation": "Precision/Recall/F1/Specificity/AUROC",
            "split": "GroupKFold(record/subject proxy)",
            "best_result": f"F1={best_metrics['task3']['f1']:.4f}, AUROC={best_metrics['task3']['auroc']:.4f}",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "needs work (主线未并回专项改进)",
        },
        {
            "task": "Task3 AF筛查(专项当前最优)",
            "algorithm": "30s窗+10s步长 + AF比例标签 + RR不规则特征 + XGBoost",
            "core_code_files": "专项脚本/报告产物（不在主线 run_pipeline 调用）",
            "key_functions": "见 task3_B* 专项产物",
            "datasets": "afdb, ltafdb(49/84), mitdb",
            "labels": "AF ratio>=0.6; <=0.1 non-AF; 中间丢弃",
            "evaluation": "Patient-wise test + AUROC/F1/CM",
            "split": "patient-wise split",
            "best_result": "F1=0.7359, AUROC=0.9499, Recall=0.8472, Specificity=0.8965",
            "result_source": "outputs/tables/task3_baseline_model_compare.csv | outputs/reports/task3_B6_final_summary.md",
            "status": "provisional (专项有效，尚未并入主线best_metrics)",
        },
        {
            "task": "Task4 异常搏提示",
            "algorithm": "RR偏离中位数阈值启发式 + 符号标签对比",
            "core_code_files": "src/evaluation/task4_arrhythmia.py",
            "key_functions": "run_task4",
            "datasets": "mitdb",
            "labels": "atr symbol 正常/异常二值映射",
            "evaluation": "Precision/Recall/F1",
            "split": "record-wise",
            "best_result": f"F1={best_metrics['task4']['f1']:.4f}, Recall={best_metrics['task4']['recall']:.4f}",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "provisional",
        },
        {
            "task": "Task5 P波/QT流程",
            "algorithm": "R峰SQI门控 + NeuroKit2 ecg_delineate(dwt)",
            "core_code_files": "src/evaluation/task5_ecg_qt_pwave.py | src/preprocessing/ecg.py",
            "key_functions": "run_task5 | rpeak_sqi",
            "datasets": "qtdb, but_pdb",
            "labels": "流程级成功率，无统一点级GT误差",
            "evaluation": "delineation_success_rate + mean_sqi",
            "split": "record-wise",
            "best_result": f"success_rate={best_metrics['task5']['delineation_success_rate']:.4f}, mean_sqi={best_metrics['task5']['mean_sqi']:.4f}",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "provisional",
        },
        {
            "task": "Task6 PPG峰足/SQI/HR",
            "algorithm": "PPG去趋势+带通+峰足检测 + 多因子SQI + 峰足HR自一致代理误差",
            "core_code_files": "src/evaluation/task6_ppg.py | src/preprocessing/ppg.py",
            "key_functions": "run_task6 | detect_ppg_peaks | detect_ppg_foots | estimate_ppg_sqi",
            "datasets": "but_ppg, ppg_dalia, bidmc (ppg_dalia当前缺失)",
            "labels": "无统一HR真值时采用proxy误差",
            "evaluation": "HR proxy MAE/SQI均值/峰足成功率",
            "split": "record-wise",
            "best_result": f"hr_proxy_mae={best_metrics['task6']['hr_proxy_mae_bpm']:.4f} bpm, sqi_mean={best_metrics['task6']['sqi_mean']:.4f}",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "stable(工程基线)",
        },
        {
            "task": "Task7 呼吸率估计",
            "algorithm": "EDR(R幅调制) + PDR(PPG幅度调制) + Welch主频",
            "core_code_files": "src/evaluation/task7_resp.py | src/features/respiration.py",
            "key_functions": "run_task7 | edr_from_rpeak_amplitude | pdr_from_ppg_amplitude | estimate_resp_rate_from_series",
            "datasets": "bidmc, apnea_ecg",
            "labels": "resp通道参考呼吸率",
            "evaluation": "ECG-derived/PPG-derived MAE",
            "split": "record-wise",
            "best_result": f"ECG MAE={best_metrics['task7']['ecg_resp_mae_bpm']:.2f} bpm, PPG MAE={best_metrics['task7']['ppg_resp_mae_bpm']:.2f} bpm",
            "result_source": "outputs/metrics/best_metrics.json",
            "status": "stable(工程趋势)",
        },
        {
            "task": "Task8 ECG+PPG时序/PTT",
            "algorithm": "生理约束配对：先R后峰、峰前足点、窗口约束 + MAD跳变剔除",
            "core_code_files": "src/evaluation/task8_ptt.py | src/features/ptt.py",
            "key_functions": "run_task8 | pair_delays_ms_constrained | _derive_feet_before_peaks",
            "datasets": "ptt_ppg",
            "labels": "无临床标签，时延统计任务",
            "evaluation": "R->foot / R->peak 均值中位数方差 + rule validity",
            "split": "subject/scene统计",
            "best_result": f"R->foot={best_metrics['task8']['r_to_foot_mean_ms']:.2f} ms, R->peak={best_metrics['task8']['r_to_peak_mean_ms']:.2f} ms",
            "result_source": "outputs/metrics/best_metrics.json | outputs/reports/task8_A5_re_evaluation.md",
            "status": "stable(已修复并回主线)",
        },
        {
            "task": "Task9 回归门控",
            "algorithm": "关键指标回退检测(task1_f1 + task6_sqi)",
            "core_code_files": "src/evaluation/task9_regression.py | src/evaluation/regression_suite.py",
            "key_functions": "run_task9 | compare_rounds",
            "datasets": "跨任务聚合",
            "labels": "无",
            "evaluation": "regression_flags",
            "split": "迭代轮次",
            "best_result": f"regression_flags={best_metrics['task9']['regression_flags']}",
            "result_source": "outputs/metrics/best_metrics.json | outputs/metrics/iteration_round_logs.csv",
            "status": "stable",
        },
    ]
    df = pd.DataFrame(task_rows)
    df.to_csv(TABLES / "project_task_algorithm_map.csv", index=False, encoding="utf-8-sig")

    md_lines = [
        "# 项目任务-算法映射",
        "",
        "## 映射A：任务 -> 算法 -> 代码文件",
        "",
        "| 任务 | 算法 | 关键函数 | 代码文件 |",
        "| --- | --- | --- | --- |",
    ]
    for r in task_rows:
        md_lines.append(f"| {r['task']} | {r['algorithm']} | {r['key_functions']} | {r['core_code_files']} |")
    md_lines += [
        "",
        "## 映射B：任务 -> 数据集 -> 标签 -> 评估",
        "",
        "| 任务 | 数据集 | 标签/任务定义 | 评估方式 | 切分 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for r in task_rows:
        md_lines.append(f"| {r['task']} | {r['datasets']} | {r['labels']} | {r['evaluation']} | {r['split']} |")
    md_lines += [
        "",
        "## 映射C：任务 -> 最佳结果 -> 产出文件",
        "",
        "| 任务 | 当前最佳结果 | 结果来源 | 状态 |",
        "| --- | --- | --- | --- |",
    ]
    for r in task_rows:
        md_lines.append(f"| {r['task']} | {r['best_result']} | {r['result_source']} | {r['status']} |")
    (REPORTS / "project_task_algorithm_map.md").write_text("\n".join(md_lines), encoding="utf-8")
    return df


def build_dataset_overview(inventory_df: pd.DataFrame) -> pd.DataFrame:
    datasets_cfg = yaml.safe_load((ROOT / "configs" / "datasets.yaml").read_text(encoding="utf-8"))
    cfg_map = {d["dataset_name"]: d for d in datasets_cfg.get("datasets", [])}
    main_eval_used = {
        "mitdb",
        "nsrdb",
        "nstdb",
        "afdb",
        "ltafdb",
        "qtdb",
        "but_pdb",
        "but_ppg",
        "ppg_dalia",
        "bidmc",
        "apnea_ecg",
        "ptt_ppg",
    }
    rows = []
    for _, row in inventory_df.iterrows():
        name = row["dataset_name"]
        cfg = cfg_map.get(name, {})
        status = row["status"]
        reason = ""
        if status == "missing":
            reason = "原始数据目录为空/未成功获取"
        elif status == "incomplete":
            reason = row["notes"] or "记录不完整"
        else:
            reason = "已参与或可参与当前评估"
        rows.append(
            {
                "dataset_name": name,
                "abbrev": name,
                "status": status,
                "source_url": row["source_url"],
                "modality": cfg.get("modality", ""),
                "phase": cfg.get("phase", row.get("phase")),
                "task_tags": ",".join(cfg.get("task_tags", [])),
                "record_count": row["record_count"],
                "expected_record_count": row.get("expected_record_count", ""),
                "used_in_current_main_eval": name in main_eval_used,
                "actually_has_data": status in {"ok", "incomplete"},
                "reason_if_not_fully_used": reason,
            }
        )
    df = pd.DataFrame(rows)
    df.to_csv(TABLES / "dataset_overview_master.csv", index=False, encoding="utf-8-sig")
    return df


def build_master_best_results_table(best_metrics: dict[str, Any]) -> pd.DataFrame:
    rows = [
        {
            "task": "Task1 ECG R峰",
            "metric_name": "F1(adaptive)",
            "best_value": f"{best_metrics['task1']['adaptive']['f1']:.4f}",
            "dataset": "mitdb/nsrdb/nstdb",
            "algorithm_version": "adaptive_threshold_scale=0.7",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "stable",
            "for_formal_plan_or_defense": "yes",
            "notes": "工程级公共数据验证",
        },
        {
            "task": "Task2 HRV",
            "metric_name": "RMSSD(clean, ms)",
            "best_value": f"{best_metrics['task2']['summary']['clean']['rmssd_ms']:.2f}",
            "dataset": "mitdb/nsrdb/afdb",
            "algorithm_version": "RR清洗规则: 320-2000ms",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "stable",
            "for_formal_plan_or_defense": "yes",
            "notes": "HRV仅在足够长度+节律稳定条件下解释",
        },
        {
            "task": "Task3 AF(主线)",
            "metric_name": "F1",
            "best_value": f"{best_metrics['task3']['f1']:.4f}",
            "dataset": "afdb/ltafdb/mitdb",
            "algorithm_version": "weak-label RF baseline",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "needs work",
            "for_formal_plan_or_defense": "no",
            "notes": "主线尚未并回专项修复",
        },
        {
            "task": "Task3 AF(专项最优)",
            "metric_name": "F1 / AUROC",
            "best_value": "0.7359 / 0.9499",
            "dataset": "afdb/ltafdb/mitdb(30s窗)",
            "algorithm_version": "XGBoost + patient-wise",
            "source_file": "outputs/tables/task3_baseline_model_compare.csv",
            "current_status": "provisional",
            "for_formal_plan_or_defense": "yes_with_disclaimer",
            "notes": "可表述为初步AF筛查，不可表述临床诊断",
        },
        {
            "task": "Task4 异常搏",
            "metric_name": "F1",
            "best_value": f"{best_metrics['task4']['f1']:.4f}",
            "dataset": "mitdb",
            "algorithm_version": "RR偏离启发式",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "provisional",
            "for_formal_plan_or_defense": "yes_with_disclaimer",
            "notes": "召回高，精度仍有提升空间",
        },
        {
            "task": "Task5 QT/P波",
            "metric_name": "delineation_success_rate",
            "best_value": f"{best_metrics['task5']['delineation_success_rate']:.4f}",
            "dataset": "qtdb/but_pdb",
            "algorithm_version": "SQI门控 + nk.ecg_delineate",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "provisional",
            "for_formal_plan_or_defense": "yes_with_disclaimer",
            "notes": "流程跑通不等于时程精度临床级",
        },
        {
            "task": "Task6 PPG",
            "metric_name": "HR proxy MAE(bpm)",
            "best_value": f"{best_metrics['task6']['hr_proxy_mae_bpm']:.4f}",
            "dataset": "but_ppg/bidmc",
            "algorithm_version": "peak-foot + SQI",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "stable",
            "for_formal_plan_or_defense": "yes",
            "notes": "当前为工程proxy误差",
        },
        {
            "task": "Task7 呼吸率",
            "metric_name": "ECG MAE / PPG MAE(bpm)",
            "best_value": f"{best_metrics['task7']['ecg_resp_mae_bpm']:.2f} / {best_metrics['task7']['ppg_resp_mae_bpm']:.2f}",
            "dataset": "bidmc/apnea_ecg",
            "algorithm_version": "EDR/PDR + PSD",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "stable(趋势)",
            "for_formal_plan_or_defense": "yes_with_disclaimer",
            "notes": "工程趋势估计，不作诊断",
        },
        {
            "task": "Task8 ECG+PPG时序",
            "metric_name": "R->foot / R->peak mean(ms)",
            "best_value": f"{best_metrics['task8']['r_to_foot_mean_ms']:.2f} / {best_metrics['task8']['r_to_peak_mean_ms']:.2f}",
            "dataset": "ptt_ppg",
            "algorithm_version": "constrained pairing fix",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "stable",
            "for_formal_plan_or_defense": "yes_with_disclaimer",
            "notes": "生理时延特征；趋势验证，不作临床诊断",
        },
        {
            "task": "Task9 回归门控",
            "metric_name": "regression_flags",
            "best_value": str(best_metrics["task9"]["regression_flags"]),
            "dataset": "跨任务",
            "algorithm_version": "rule-based gate",
            "source_file": "outputs/metrics/best_metrics.json",
            "current_status": "stable",
            "for_formal_plan_or_defense": "yes",
            "notes": "当前无回退标记",
        },
    ]
    df = pd.DataFrame(rows)
    df.to_csv(TABLES / "master_best_results_table.csv", index=False, encoding="utf-8-sig")
    return df


def _map_task_by_name(name: str) -> str:
    lower = name.lower()
    if "task1" in lower or "rpeak" in lower or "ecg_filter" in lower:
        return "Task1"
    if "task2" in lower or "hrv" in lower:
        return "Task2"
    if "task3" in lower or "af_" in lower:
        return "Task3"
    if "task4" in lower or "arrhythmia" in lower:
        return "Task4"
    if "task5" in lower or "qt" in lower or "pwave" in lower:
        return "Task5"
    if "task6" in lower or "ppg" in lower or "sqi" in lower:
        return "Task6"
    if "task7" in lower or "resp" in lower or "rr_estimation" in lower:
        return "Task7"
    if "task8" in lower or "ptt" in lower:
        return "Task8"
    if "task9" in lower or "regression" in lower:
        return "Task9"
    if "kotlin_python" in lower:
        return "Kotlin-Python一致性"
    return "通用"


def build_figure_table_index() -> pd.DataFrame:
    rows = []
    for folder, kind in [(FIGURES, "figure"), (TABLES, "table")]:
        for p in sorted(folder.rglob("*")):
            if not p.is_file():
                continue
            rel = _to_rel(p)
            task = _map_task_by_name(p.name)
            recommended = p.name in {
                "ecg_filter_rpeak_example.png",
                "rpeak_detection_example.png",
                "task3_baseline_roc.png",
                "task3_baseline_pr.png",
                "task8_delay_distribution_before_after.png",
                "task8_subjectwise_delay_boxplot.png",
                "ppg_peak_foot_example.png",
                "ppg_sqi_distribution.png",
                "rr_estimation_error_distribution.png",
                "ptt_pwtt_delay_distribution.png",
                "master_best_results_table.csv",
                "dataset_overview_master.csv",
                "task3_baseline_model_compare.csv",
                "task8_recomputed_metrics.csv",
            }
            rows.append(
                {
                    "artifact_type": kind,
                    "file_path": rel,
                    "task": task,
                    "description": f"{task}相关{kind}",
                    "recommended_for_defense": "yes" if recommended else "optional",
                }
            )
    df = pd.DataFrame(rows)
    df.to_csv(TABLES / "figure_table_index.csv", index=False, encoding="utf-8-sig")

    md = ["# 图表与表格索引", "", "| 类型 | 路径 | 任务 | 建议答辩使用 |", "| --- | --- | --- | --- |"]
    for _, r in df.iterrows():
        md.append(f"| {r['artifact_type']} | {r['file_path']} | {r['task']} | {r['recommended_for_defense']} |")
    md.extend(
        [
            "",
            "## 推荐答辩图表清单",
            "- outputs/figures/ecg_filter_rpeak_example.png",
            "- outputs/figures/rpeak_detection_example.png",
            "- outputs/figures/task3_baseline_roc.png",
            "- outputs/figures/task3_baseline_pr.png",
            "- outputs/figures/ppg_peak_foot_example.png",
            "- outputs/figures/ppg_sqi_distribution.png",
            "- outputs/figures/task8_delay_distribution_before_after.png",
            "- outputs/figures/task8_subjectwise_delay_boxplot.png",
            "- outputs/tables/master_best_results_table.csv",
        ]
    )
    (REPORTS / "figure_table_index.md").write_text("\n".join(md), encoding="utf-8")
    return df


@dataclass
class CleanupCandidate:
    file_path: str
    category: str
    reason: str
    safe_auto_delete: bool


def build_cleanup_and_execute(inventory_df: pd.DataFrame) -> tuple[pd.DataFrame, list[str]]:
    keep_prefix = {"datasets/raw/", "datasets/manifests/", "src/", "configs/"}
    keep_exact = {
        "README.md",
        "requirements.txt",
        "Makefile",
        "run_all.ps1",
        "outputs/metrics/best_metrics.json",
        "outputs/reports/03_final_validation_report.md",
        "outputs/reports/task8_task3_special_report.md",
        "outputs/reports/task8_A5_re_evaluation.md",
        "outputs/reports/task3_B6_final_summary.md",
    }
    candidates: list[CleanupCandidate] = []

    for _, r in inventory_df.iterrows():
        p = r["file_path"]
        lower = p.lower()
        if p.startswith("env/"):
            # 虚拟环境仅做审计，不自动清理，避免影响依赖可用性
            continue
        is_keep = (p in keep_exact) or any(p.startswith(prefix) for prefix in keep_prefix)
        if is_keep and "__pycache__" not in lower and not lower.endswith(".pyc"):
            continue

        if "__pycache__" in lower or lower.endswith(".pyc"):
            candidates.append(CleanupCandidate(p, "C_建议删除", "Python缓存文件", True))
        elif p.startswith("outputs/metrics/round_") and p.endswith("_metrics.json"):
            candidates.append(CleanupCandidate(p, "B_建议归档", "历史轮次快照，追溯可用", False))
        elif p.startswith("outputs/tables/task3_feature_matrix.csv") or p.startswith("outputs/tables/task3_rebuilt_segments.csv"):
            candidates.append(CleanupCandidate(p, "B_建议归档", "大体量中间产物，保留追溯", False))
        elif p.startswith("outputs/reports/task8_sync_step"):
            candidates.append(CleanupCandidate(p, "B_建议归档", "主线同步过程记录", False))
        elif p.startswith("outputs/reports/task8_task3_step1_context.md"):
            candidates.append(CleanupCandidate(p, "B_建议归档", "专项中间上下文记录", False))

    df = pd.DataFrame([c.__dict__ for c in candidates]).drop_duplicates(subset=["file_path"])
    df.to_csv(TABLES / "file_cleanup_candidates.csv", index=False, encoding="utf-8-sig")

    delete_df = df[(df["category"] == "C_建议删除") & (df["safe_auto_delete"] == True)].copy()
    delete_df.to_csv(TABLES / "file_cleanup_delete_list.csv", index=False, encoding="utf-8-sig")

    deleted = []
    for p in delete_df["file_path"].tolist():
        abs_p = ROOT / p
        if abs_p.exists() and abs_p.is_file():
            abs_p.unlink()
            deleted.append(p)

    for d in sorted((ROOT / "src").rglob("__pycache__"), reverse=True):
        try:
            if d.is_dir() and not any(d.iterdir()):
                d.rmdir()
                deleted.append(_to_rel(d))
        except Exception:
            pass

    log_lines = [
        "# 文件清理执行日志",
        "",
        f"- 执行时间: {_now_str()}",
        f"- 删除数量: {len(deleted)}",
        "",
        "## 删除明细",
    ]
    if deleted:
        for p in deleted:
            log_lines.append(f"- {p} | 原因: Python缓存文件")
    else:
        log_lines.append("- 无自动删除项（全部保守保留）")
    (REPORTS / "file_cleanup_execution_log.md").write_text("\n".join(log_lines), encoding="utf-8")

    audit_lines = [
        "# 遗留文件审计",
        "",
        "## A类：必须保留",
        "- 原始数据 `datasets/raw/**`",
        "- 主流程代码 `src/**`（排除缓存）",
        "- 配置与入口 `configs/**`, `run_all.ps1`, `Makefile`, `requirements.txt`",
        "- 主结果与关键专项 `outputs/metrics/best_metrics.json`, `outputs/reports/task8_task3_special_report.md`",
        "",
        "## B类：建议归档但不删除",
        "- 历史轮次快照 `outputs/metrics/round_*.json`",
        "- 大体量中间表 `outputs/tables/task3_feature_matrix.csv` 等",
        "- 专项同步中间报告 `outputs/reports/task8_sync_step*.md`",
        "",
        "## C类：建议删除（已自动执行安全删除）",
        "- `src/**/__pycache__/**` 与 `*.pyc`",
        "",
        "## 执行结果",
        f"- 自动删除文件/目录数量: {len(deleted)}",
        "- 未删除任何原始数据与主流程依赖文件。",
    ]
    (REPORTS / "file_cleanup_audit.md").write_text("\n".join(audit_lines), encoding="utf-8")
    return df, deleted


def build_iteration_history() -> None:
    logs_csv = METRICS / "iteration_round_logs.csv"
    rows = []
    if logs_csv.exists():
        rows = pd.read_csv(logs_csv).to_dict(orient="records")
    lines = ["# 项目迭代轨迹", "", "## 主流水线迭代"]
    if rows:
        for r in rows:
            lines.append(
                f"- Round {int(r['round_id'])}: scale={r.get('adaptive_threshold_scale')}, decision={r.get('decision')}, score={r.get('score'):.6f}, improvement={r.get('improvement_percent')}%"
            )
    else:
        lines.append("- 未找到 iteration_round_logs.csv")
    lines += [
        "",
        "## 关键专项修复",
        "- Task8: 从独立最近邻配对改为生理约束配对，修复 `R->foot < R->peak` 关系（见 task8_A1~A5）。",
        "- Task3: 完成标签/切分重建与传统特征+XGBoost基线，专项指标显著提升（见 task3_B1~B6）。",
        "",
        "## 回滚与保留策略",
        "- 主线迭代中若触发 regression flag 会拒绝该轮（见 round_02）。",
        "- 专项改进单独留痕，未自动覆盖所有主线历史报告。",
        "",
        "## 当前最优配置来源",
        "- Task1/2/4/5/6/7/8/9：以 `outputs/metrics/best_metrics.json` 为主。",
        "- Task3：专项最优来源于 `outputs/tables/task3_baseline_model_compare.csv` 与 B6 报告。",
    ]
    (REPORTS / "project_iteration_history.md").write_text("\n".join(lines), encoding="utf-8")


def build_registry() -> None:
    key_files = [
        "README.md",
        "requirements.txt",
        "Makefile",
        "run_all.ps1",
        "configs/datasets.yaml",
        "configs/pipeline.yaml",
        "datasets/manifests/dataset_inventory.csv",
        "outputs/metrics/best_metrics.json",
        "outputs/reports/PROJECT_MASTER_REPORT.md",
        "outputs/reports/PROJECT_EXECUTIVE_SUMMARY.md",
        "outputs/reports/PROJECT_FOR_DEFENSE.md",
        "outputs/reports/PROJECT_ALGORITHM_APPENDIX.md",
        "outputs/tables/master_best_results_table.csv",
        "outputs/tables/dataset_overview_master.csv",
        "outputs/tables/project_task_algorithm_map.csv",
        "outputs/tables/figure_table_index.csv",
    ]
    reg_rows = []
    for rel in key_files:
        reg_rows.append(
            {
                "file_path": rel,
                "exists": (ROOT / rel).exists(),
                "category": "核心交付",
                "description": "项目审计后建议长期保留",
            }
        )
    for p in [
        "outputs/reports/task8_task3_special_report.md",
        "outputs/reports/task8_A5_re_evaluation.md",
        "outputs/reports/task3_B6_final_summary.md",
        "outputs/reports/kotlin_python_numeric_parity.md",
    ]:
        reg_rows.append(
            {
                "file_path": p,
                "exists": (ROOT / p).exists(),
                "category": "专项证据",
                "description": "关键专项证据材料",
            }
        )
    pd.DataFrame(reg_rows).to_csv(TABLES / "current_artifact_registry.csv", index=False, encoding="utf-8-sig")


def build_master_reports(
    inventory_df: pd.DataFrame,
    dataset_df: pd.DataFrame,
    task_map_df: pd.DataFrame,
    best_table: pd.DataFrame,
) -> None:
    lines = [
        "# PROJECT_MASTER_REPORT",
        "",
        f"- 生成时间: {_now_str()}",
        f"- 审计范围: `{ROOT.as_posix()}` 全量代码与产物",
        "",
        "## 第1章 项目概览",
        "- 项目目标：构建手机壳式 ECG+PPG 算法验证与工程化评估流水线，覆盖 Task1~Task9。",
        "- 硬件形态映射：ESP32-S3 + AD8232(单导联ECG) + MAX30102(PPG) + 手机端算法。",
        "- 当前工程边界：公共数据集工程验证为主，临床级结论不成立。",
        "- 已完成：数据下载/清单/loader/9个任务评估框架/专项修复(Task8+Task3)/图表输出。",
        "- 未完成：ppg_dalia缺失、ltafdb不完整、Task3专项尚未完全并回主线聚合。",
        "",
        "## 第2章 项目目录与代码结构说明",
        "- 主入口：`src/run_pipeline.py`",
        "- 迭代控制：`src/iteration_controller.py`",
        "- 任务实现：`src/evaluation/task1~task9_*.py`",
        "- 特征与预处理：`src/preprocessing/*`, `src/features/*`, `src/models/af_baseline.py`",
        "- 报告与图表：`src/reports/writers.py`, `src/reports/figures.py`",
        "- 数据资产：`datasets/raw|normalized|manifests|logs`",
        "",
        "## 第3章 数据资产总览",
        "详表见 `outputs/tables/dataset_overview_master.csv`。",
        "",
    ]
    for _, r in dataset_df.iterrows():
        lines += [
            f"### {r['dataset_name']}",
            f"- 状态: {r['status']}",
            f"- 模态: {r['modality']}",
            f"- 任务映射: {r['task_tags']}",
            f"- 记录数: {r['record_count']} / 期望 {r['expected_record_count']}",
            f"- 是否参与主评估: {r['used_in_current_main_eval']}",
            f"- 说明: {r['reason_if_not_fully_used']}",
            "",
        ]
    lines += [
        "## 第4章 各任务详细算法说明",
        "任务-算法-代码映射详见 `outputs/reports/project_task_algorithm_map.md` 与 `outputs/tables/project_task_algorithm_map.csv`。",
        "",
    ]
    for _, r in task_map_df.iterrows():
        lines += [
            f"### {r['task']}",
            f"- 输入信号与数据集：{r['datasets']}。",
            f"- 预处理/检测逻辑：{r['algorithm']}。",
            f"- 关键代码：{r['core_code_files']}。",
            f"- 关键函数：{r['key_functions']}。",
            f"- 标签与评估：{r['labels']}；{r['evaluation']}。",
            f"- 切分策略：{r['split']}。",
            f"- 当前结果：{r['best_result']}。",
            f"- 结果来源：{r['result_source']}。",
            f"- 局限：状态={r['status']}。",
            "",
        ]
    lines += [
        "## 第5章 当前最佳结果总表",
        "详表见 `outputs/tables/master_best_results_table.csv`。",
        "",
        "| 任务 | 指标 | 当前最优值 | 状态 | 可用于正式方案/答辩 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for _, r in best_table.iterrows():
        lines.append(
            f"| {r['task']} | {r['metric_name']} | {r['best_value']} | {r['current_status']} | {r['for_formal_plan_or_defense']} |"
        )
    lines += [
        "",
        "## 第6章 图表与证据索引",
        "- 详见 `outputs/tables/figure_table_index.csv` 与 `outputs/reports/figure_table_index.md`。",
        "",
        "## 第7章 历史迭代轨迹",
        "- 详见 `outputs/reports/project_iteration_history.md`。",
        "",
        "## 第8章 遗留文件与清理建议",
        "- 详见 `outputs/reports/file_cleanup_audit.md` 与 `outputs/tables/file_cleanup_candidates.csv`。",
        "",
        "## 第9章 安全删除执行记录",
        "- 详见 `outputs/tables/file_cleanup_delete_list.csv` 与 `outputs/reports/file_cleanup_execution_log.md`。",
        "",
        "## 第10章 当前能力边界与正式表述建议",
        "- 可稳定表述：Task1/2/6/8 的工程能力；Task3可表述为“初步AF筛查”但必须加非临床声明。",
        "- 不可夸大：无袖带血压精度、临床诊断、QT/P波临床级时程精度。",
        "- Task8 的 R->foot/R->peak 是生理时延特征，不是同步误差。",
        "- Task5 success_rate=1.0 仅表示流程可跑通，不代表临床精度已验证。",
        "",
        "## 关键冲突与过时项审计结论",
        "- `outputs/metrics/best_metrics.json` 中 Task8 已是修复后新值（227.45 / 406.92）。",
        "- 主线 Task3 仍是旧弱标签结果（F1=0/AUROC=0.3792），专项改进结果保存在 task3_B* 与对应CSV。",
        "- `01_baseline_report.md`、`03_final_validation_report.md` 对 Task3 仍引用主线旧值，答辩时应同时给出专项口径并声明并回状态。",
    ]
    (REPORTS / "PROJECT_MASTER_REPORT.md").write_text("\n".join(lines), encoding="utf-8")

    executive = [
        "# PROJECT_EXECUTIVE_SUMMARY",
        "",
        "## 一页结论",
        "- 项目已形成可复现公共数据验证流水线，任务覆盖 ECG/PPG/联合时序/回归门控。",
        "- Task8 已完成专项修复并主线生效：`R->foot=227.45ms`，`R->peak=406.92ms`，关系正确。",
        "- Task3 存在主线与专项双口径：主线旧值差，专项重建后可达 `F1=0.7359`、`AUROC=0.9499`。",
        "- 数据覆盖 10/12：`ltafdb` incomplete，`ppg_dalia` missing。",
        "- 当前可用于答辩的定位：工程验证与初步筛查，不是临床诊断系统。",
        "",
        "## 当前最稳能力",
        "- ECG R峰检测（Task1）",
        "- HR/HRV统计（Task2）",
        "- PPG峰足/SQI工程评估（Task6）",
        "- ECG+PPG时序趋势特征（Task8）",
        "",
        "## 关键风险",
        "- Task3专项结果尚未完全合入主线自动聚合。",
        "- 部分报告存在中文编码显示异常（终端查看时乱码风险）。",
        "- PPG-DaLiA 缺失导致运动伪迹泛化证据不足。",
    ]
    (REPORTS / "PROJECT_EXECUTIVE_SUMMARY.md").write_text("\n".join(executive), encoding="utf-8")

    defense = [
        "# PROJECT_FOR_DEFENSE",
        "",
        "## 建议答辩主线（可直接讲）",
        "1. 工程可复现：数据下载、清单、loader、评估、报告全链路可追溯。",
        "2. 核心能力：ECG R峰/HRV、PPG峰足/SQI、ECG+PPG时序。",
        "3. 关键修复：Task8 从时序逻辑错误修到生理顺序正确。",
        "4. AF口径：可讲“初步AF筛查基线”，并明确非临床。",
        "",
        "## 推荐PPT图表",
        "- outputs/figures/ecg_filter_rpeak_example.png",
        "- outputs/figures/rpeak_detection_example.png",
        "- outputs/figures/task3_baseline_roc.png",
        "- outputs/figures/task3_baseline_pr.png",
        "- outputs/figures/ppg_peak_foot_example.png",
        "- outputs/figures/task8_delay_distribution_before_after.png",
        "- outputs/figures/task8_subjectwise_delay_boxplot.png",
        "- outputs/tables/master_best_results_table.csv",
        "",
        "## 容易被问的问题与诚实回答",
        "- 问：AF是不是临床级？答：不是，当前是工程级初步筛查。",
        "- 问：Task8延时是不是同步误差？答：不是，是生理时延特征。",
        "- 问：QT/P波是不是精度已验证？答：当前仅流程跑通，点级精度尚需统一验证。",
        "- 问：为什么有Python？答：Python用于离线验证，手机端运行由Kotlin实现并做了数值对齐。",
    ]
    (REPORTS / "PROJECT_FOR_DEFENSE.md").write_text("\n".join(defense), encoding="utf-8")

    appendix = [
        "# PROJECT_ALGORITHM_APPENDIX",
        "",
        "## Task1",
        "- 文件: src/preprocessing/ecg.py, src/evaluation/task1_ecg_rpeak.py",
        "- 函数: preprocess_ecg, detect_rpeaks_classic, detect_rpeaks_adaptive",
        "- 参数: highpass=0.5Hz, notch=50Hz, bandpass=0.5-40Hz, tolerance=100ms",
        "",
        "## Task2",
        "- 文件: src/features/hrv.py, src/evaluation/task2_hrv.py",
        "- 函数: rr_intervals_ms, hrv_features, remove_outlier_rr",
        "",
        "## Task3",
        "- 文件: src/models/af_baseline.py, src/features/af_features.py",
        "- 函数: weak_label_from_annotations, build_af_samples, train_eval_af_baseline",
        "",
        "## Task4",
        "- 文件: src/evaluation/task4_arrhythmia.py",
        "- 逻辑: RR偏离中位数20%判定异常",
        "",
        "## Task5",
        "- 文件: src/evaluation/task5_ecg_qt_pwave.py",
        "- 函数: nk.ecg_delineate(method='dwt'), rpeak_sqi",
        "",
        "## Task6",
        "- 文件: src/preprocessing/ppg.py, src/evaluation/task6_ppg.py",
        "- 峰检测: distance=0.35s, prominence=0.2*std",
        "- 足点检测: 在 -ppg 上峰值检测，prominence=0.1*std",
        "",
        "## Task7",
        "- 文件: src/features/respiration.py, src/evaluation/task7_resp.py",
        "- 方法: EDR/PDR幅度调制 + Welch主频",
        "",
        "## Task8",
        "- 文件: src/features/ptt.py, src/evaluation/task8_ptt.py",
        "- 核心: pair_delays_ms_constrained, 强制 R<foot<peak + 窗口约束 + MAD",
        "",
        "## Task9",
        "- 文件: src/evaluation/task9_regression.py, src/evaluation/regression_suite.py",
        "- 规则: 关键指标回退触发拦截",
    ]
    (REPORTS / "PROJECT_ALGORITHM_APPENDIX.md").write_text("\n".join(appendix), encoding="utf-8")


def main() -> None:
    _mkdirs()
    inventory_df = build_file_inventory()
    dataset_inventory_df = pd.read_csv(ROOT / "datasets" / "manifests" / "dataset_inventory.csv")
    best_metrics = _read_json(ROOT / "outputs" / "metrics" / "best_metrics.json")

    task_map_df = build_task_algorithm_map(best_metrics)
    dataset_df = build_dataset_overview(dataset_inventory_df)
    best_table_df = build_master_best_results_table(best_metrics)
    build_figure_table_index()
    build_iteration_history()
    build_cleanup_and_execute(inventory_df)
    build_registry()
    build_master_reports(dataset_inventory_df, dataset_df, task_map_df, best_table_df)
    print("Project audit artifacts generated.")


if __name__ == "__main__":
    main()
