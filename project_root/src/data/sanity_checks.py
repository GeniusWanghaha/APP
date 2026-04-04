from __future__ import annotations

import random
from pathlib import Path

import pandas as pd

from src.data.catalog import DatasetSpec
from src.data.loaders import LoaderRegistry
from src.utils.io_utils import write_df_csv


def run_dataset_sanity_checks(
    specs: list[DatasetSpec],
    loader_registry: LoaderRegistry,
    output_csv: Path,
    samples_per_dataset: int = 3,
    seed: int = 42,
) -> pd.DataFrame:
    random.seed(seed)
    rows: list[dict] = []
    for spec in specs:
        records = loader_registry.list_records(spec.dataset_name)
        if not records:
            rows.append(
                {
                    "dataset_name": spec.dataset_name,
                    "status": "no_records",
                    "checked_records": 0,
                    "ok_records": 0,
                    "error_records": 0,
                    "notes": "no records discovered by loader",
                }
            )
            continue
        chosen = records if len(records) <= samples_per_dataset else random.sample(records, samples_per_dataset)
        ok = 0
        err = 0
        error_messages: list[str] = []
        for record_id in chosen:
            try:
                payload = loader_registry.load_record(spec.dataset_name, record_id)
                if payload["signals"]:
                    ok += 1
                else:
                    err += 1
                    message = payload.get("meta", {}).get("error", "empty_signals")
                    error_messages.append(f"{record_id}:{message}")
            except Exception as exc:  # noqa: BLE001
                err += 1
                error_messages.append(f"{record_id}:{type(exc).__name__}:{exc}")
        rows.append(
            {
                "dataset_name": spec.dataset_name,
                "status": "ok" if err == 0 else ("partial" if ok > 0 else "failed"),
                "checked_records": len(chosen),
                "ok_records": ok,
                "error_records": err,
                "notes": "; ".join(error_messages[:5]),
            }
        )
    df = pd.DataFrame(rows)
    write_df_csv(output_csv, df)
    return df

