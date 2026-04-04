from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path

import pandas as pd
import wfdb

from src.data.catalog import DatasetSpec
from src.utils.io_utils import write_df_csv


@dataclass
class InventoryRow:
    dataset_name: str
    source_type: str
    phase: int
    source_url: str
    raw_root: str
    status: str
    record_count: int
    header_count: int
    annotation_count: int
    file_count: int
    expected_record_count: int
    notes: str


def _resolve_raw_dir(spec: DatasetSpec, raw_physionet_dir: Path, raw_uci_dir: Path) -> Path:
    if spec.source_type == "physionet":
        return raw_physionet_dir / spec.dataset_name
    return raw_uci_dir / spec.dataset_name


def _expected_record_count(root: Path) -> int:
    counts: list[int] = []
    for records_file in root.rglob("RECORDS"):
        try:
            lines = [line.strip() for line in records_file.read_text(encoding="utf-8", errors="ignore").splitlines()]
            lines = [line for line in lines if line]
            if lines:
                counts.append(len(lines))
        except Exception:  # noqa: BLE001
            continue
    return max(counts) if counts else 0


def _expected_record_count_from_wfdb(spec: DatasetSpec) -> int:
    if spec.source_type != "physionet" or not spec.db_slug:
        return 0
    try:
        return len(wfdb.get_record_list(spec.db_slug))
    except Exception:  # noqa: BLE001
        return 0


def build_inventory(
    specs: list[DatasetSpec],
    raw_physionet_dir: Path,
    raw_uci_dir: Path,
) -> pd.DataFrame:
    rows: list[InventoryRow] = []
    for spec in specs:
        root = _resolve_raw_dir(spec, raw_physionet_dir, raw_uci_dir)
        if not root.exists():
            rows.append(
                InventoryRow(
                    dataset_name=spec.dataset_name,
                    source_type=spec.source_type,
                    phase=spec.phase,
                    source_url=spec.source_url,
                    raw_root=str(root),
                    status="missing",
                    record_count=0,
                    header_count=0,
                    annotation_count=0,
                    file_count=0,
                    expected_record_count=0,
                    notes="raw directory missing",
                )
            )
            continue

        all_files = [p for p in root.rglob("*") if p.is_file()]
        headers = list(root.rglob("*.hea"))
        anns = [p for p in all_files if p.suffix in {".atr", ".qrs", ".ecg", ".ann"}]
        record_ids = {
            p.relative_to(root).with_suffix("").as_posix()
            for p in headers
        }
        expected_records_local = _expected_record_count(root)
        expected_records_remote = _expected_record_count_from_wfdb(spec)
        expected_records = max(expected_records_local, expected_records_remote)

        status = "ok" if all_files else "missing"
        notes = "" if all_files else "no files found under raw directory"
        if spec.source_type == "physionet" and all_files and not headers:
            status = "incomplete"
            notes = "no .hea files found; data may be incomplete"
        if spec.source_type == "physionet" and expected_records > 0 and len(record_ids) < expected_records:
            status = "incomplete"
            notes = f"record_count={len(record_ids)} < expected_record_count={expected_records} from RECORDS"

        rows.append(
            InventoryRow(
                dataset_name=spec.dataset_name,
                source_type=spec.source_type,
                phase=spec.phase,
                source_url=spec.source_url,
                raw_root=str(root),
                status=status,
                record_count=len(record_ids),
                header_count=len(headers),
                annotation_count=len(anns),
                file_count=len(all_files),
                expected_record_count=expected_records,
                notes=notes,
            )
        )
    return pd.DataFrame([asdict(row) for row in rows])


def write_inventory_markdown(df: pd.DataFrame, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    lines.append("# Dataset Inventory")
    lines.append("")
    lines.append(f"- total datasets: {len(df)}")
    lines.append(f"- ok datasets: {(df['status'] == 'ok').sum()}")
    lines.append(f"- missing/incomplete datasets: {(df['status'].isin(['missing', 'incomplete'])).sum()}")
    lines.append("")
    lines.append("| dataset | source | phase | status | records | expected_records | headers | annotations | files | raw_root |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
    for _, row in df.iterrows():
        lines.append(
            "| {dataset_name} | {source_type} | {phase} | {status} | {record_count} | {expected_record_count} | "
            "{header_count} | {annotation_count} | {file_count} | {raw_root} |".format(**row.to_dict())
        )
    lines.append("")
    lines.append("## Notes")
    for _, row in df.iterrows():
        if row["notes"]:
            lines.append(f"- {row['dataset_name']}: {row['notes']}")
    path.write_text("\n".join(lines), encoding="utf-8")


def persist_inventory(df: pd.DataFrame, csv_path: Path, md_path: Path) -> None:
    write_df_csv(csv_path, df)
    write_inventory_markdown(df, md_path)
