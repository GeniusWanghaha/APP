from __future__ import annotations

import argparse
import datetime as dt
from pathlib import Path

import pandas as pd

from src.data.catalog import load_catalog
from src.data.downloader import download_datasets
from src.data.inventory import build_inventory, persist_inventory
from src.data.loaders import LoaderRegistry
from src.data.manifest_builder import build_manifests
from src.data.sanity_checks import run_dataset_sanity_checks
from src.iteration_controller import run_iteration_loop
from src.reports.writers import (
    write_baseline_report,
    write_dataset_summary_report,
    write_final_validation_report,
    write_iteration_log,
    write_limitations_report,
)
from src.reports.figures import (
    generate_af_curves,
    generate_ecg_filter_rpeak_figure,
    generate_iteration_figure,
    generate_ppg_peak_foot_figure,
    generate_ptt_distribution,
    generate_regression_result_figure,
    generate_rpeak_only_figure,
    generate_rr_error_figure,
    generate_sqi_distribution,
)
from src.utils.config import load_yaml
from src.utils.io_utils import write_df_csv, write_json
from src.utils.logging_utils import build_logger
from src.utils.pathing import ensure_dir, project_root, to_abs


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="ECG+PPG public-dataset validation pipeline")
    parser.add_argument("--phase", type=int, default=1, choices=[1, 2, 3], help="execution phase")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    root = project_root()
    cfg = load_yaml(root / "configs" / "pipeline.yaml")

    phase = args.phase
    cfg["phase_to_run"] = phase

    paths = cfg["paths"]
    raw_physionet = to_abs(paths["raw_physionet"])
    raw_uci = to_abs(paths["raw_uci"])
    manifest_dir = to_abs(paths["manifests"])
    log_dir = to_abs(paths["logs"])
    outputs_metrics = to_abs(paths["outputs_metrics"])
    outputs_reports = to_abs(paths["outputs_reports"])
    outputs_figures = to_abs(paths["outputs_figures"])
    normalized_root = root / "datasets" / "normalized"

    for path in [
        raw_physionet,
        raw_uci,
        manifest_dir,
        log_dir,
        outputs_metrics,
        outputs_reports,
        outputs_figures,
        normalized_root,
    ]:
        ensure_dir(path)

    run_tag = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    logger = build_logger("pipeline", log_dir / f"run_{run_tag}.log")
    logger.info("pipeline start | phase=%s", phase)

    specs = load_catalog()
    logger.info("loaded dataset specs: %s", len(specs))

    # Step 1: download datasets
    dl_results = download_datasets(
        specs=specs,
        raw_physionet_dir=raw_physionet,
        raw_uci_dir=raw_uci,
        log_dir=log_dir,
        phase=phase,
        force_redownload=bool(cfg.get("download", {}).get("force_redownload", False)),
    )
    dl_df = pd.DataFrame([r.__dict__ for r in dl_results])
    write_df_csv(manifest_dir / "download_results.csv", dl_df)
    write_json(manifest_dir / "download_results.json", dl_df.to_dict(orient="records"))
    logger.info(
        "download done | ok=%s failed=%s skipped=%s",
        int((dl_df["status"] == "ok").sum()),
        int((dl_df["status"] == "failed").sum()),
        int((dl_df["status"].str.startswith("skipped")).sum()),
    )

    # Step 2: inventory
    inventory_df = build_inventory(specs, raw_physionet, raw_uci)
    persist_inventory(
        inventory_df,
        csv_path=manifest_dir / "dataset_inventory.csv",
        md_path=manifest_dir / "dataset_inventory.md",
    )
    logger.info("inventory done")

    # Step 3: manifest
    manifest_all_df = build_manifests(
        specs=specs,
        raw_physionet_dir=raw_physionet,
        raw_uci_dir=raw_uci,
        normalized_root=normalized_root,
        manifest_dir=manifest_dir,
    )
    logger.info("manifest done | entries=%s", len(manifest_all_df))

    # Step 4: loaders and baseline/iteration
    loader_registry = LoaderRegistry(
        specs=specs,
        raw_physionet_dir=raw_physionet,
        raw_uci_dir=raw_uci,
    )

    sanity_df = run_dataset_sanity_checks(
        specs=specs,
        loader_registry=loader_registry,
        output_csv=manifest_dir / "dataset_sanity_checks.csv",
    )
    logger.info(
        "sanity checks done | ok=%s partial=%s failed=%s",
        int((sanity_df["status"] == "ok").sum()),
        int((sanity_df["status"] == "partial").sum()),
        int((sanity_df["status"] == "failed").sum()),
    )

    loop = run_iteration_loop(
        loader_registry=loader_registry,
        outputs_metrics_dir=outputs_metrics,
        max_iterations=int(cfg.get("max_iterations", 8)),
        min_improvement_percent=float(cfg.get("min_improvement_percent", 1.0)),
        stop_after_stagnation_rounds=int(cfg.get("stop_after_stagnation_rounds", 2)),
    )
    best_result = loop["best_result"]
    round_logs = loop["round_logs"]
    stopping_reason = loop["stopping_reason"]

    write_json(outputs_metrics / "best_metrics.json", best_result)
    write_json(outputs_metrics / "iteration_round_logs.json", round_logs)
    pd.DataFrame(round_logs).to_csv(outputs_metrics / "iteration_round_logs.csv", index=False, encoding="utf-8")
    logger.info("iteration done | best_round=%s stop=%s", loop["best_round_id"], stopping_reason)

    # Step 5: reports
    write_dataset_summary_report(
        inventory_df=inventory_df,
        output_path=outputs_reports / "00_dataset_summary.md",
    )
    write_baseline_report(
        results=best_result,
        output_path=outputs_reports / "01_baseline_report.md",
    )
    write_iteration_log(
        round_logs=round_logs,
        output_path=outputs_reports / "02_iteration_log.md",
    )
    write_final_validation_report(
        inventory_df=inventory_df,
        best_result=best_result,
        stopping_reason=stopping_reason,
        output_path=outputs_reports / "03_final_validation_report.md",
    )
    write_limitations_report(
        inventory_df=inventory_df,
        output_path=outputs_reports / "04_limitations_and_next_steps.md",
    )
    af_roc, af_pr = generate_af_curves(best_result, outputs_figures)
    figure_index = {
        "ecg_filter_rpeak_example": generate_ecg_filter_rpeak_figure(loader_registry, outputs_figures),
        "rpeak_detection_example": generate_rpeak_only_figure(loader_registry, outputs_figures),
        "ppg_peak_foot_example": generate_ppg_peak_foot_figure(loader_registry, outputs_figures),
        "af_roc_curve": af_roc,
        "af_pr_curve": af_pr,
        "ppg_sqi_distribution": generate_sqi_distribution(best_result, outputs_figures),
        "rr_estimation_error_distribution": generate_rr_error_figure(best_result, outputs_figures),
        "ptt_pwtt_delay_distribution": generate_ptt_distribution(best_result, outputs_figures),
        "iteration_score_trend": generate_iteration_figure(round_logs, outputs_figures),
        "regression_flags_by_round": generate_regression_result_figure(round_logs, outputs_figures),
    }
    write_json(outputs_figures / "figure_index.json", figure_index)
    logger.info("reports done")
    logger.info("pipeline finished")


if __name__ == "__main__":
    main()
