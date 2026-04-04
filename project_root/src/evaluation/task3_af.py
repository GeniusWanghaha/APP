from __future__ import annotations

from src.models.af_baseline import build_af_samples, train_eval_af_baseline


def run_task3(loader_registry, dataset_names: list[str], max_records_per_dataset: int = 12) -> dict:
    samples = build_af_samples(
        loader_registry=loader_registry,
        dataset_names=dataset_names,
        max_records_per_dataset=max_records_per_dataset,
    )
    result = train_eval_af_baseline(samples)
    result["dataset_names"] = dataset_names
    return result
