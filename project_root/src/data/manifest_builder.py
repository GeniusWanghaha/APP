from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pandas as pd
import wfdb

from src.data.catalog import DatasetSpec
from src.utils.io_utils import write_df_csv, write_json


@dataclass
class ManifestEntry:
    dataset_name: str
    sample_id: str
    record_id: str
    subject_id: str | None
    task_tags: list[str]
    modality: str
    sampling_rate: float | None
    channels: list[str]
    label_types: list[str]
    duration_sec: float | None
    scene_activity: str | None
    source_url: str
    raw_file_paths: list[str]
    normalized_file_paths: list[str]
    checksum: str | None
    license_note: str
    notes: str


def _resolve_raw_dir(spec: DatasetSpec, raw_physionet_dir: Path, raw_uci_dir: Path) -> Path:
    if spec.source_type == "physionet":
        return raw_physionet_dir / spec.dataset_name
    return raw_uci_dir / spec.dataset_name


def _physionet_record_ids(root: Path) -> list[str]:
    return sorted(
        {
            p.relative_to(root).with_suffix("").as_posix()
            for p in root.rglob("*.hea")
        }
    )


def _extract_label_types(record_files: list[Path]) -> list[str]:
    label_exts = []
    for file_path in record_files:
        ext = file_path.suffix.lower().lstrip(".")
        if ext in {"atr", "qrs", "ecg", "ann", "st", "pu"}:
            label_exts.append(ext)
    return sorted(set(label_exts))


def _record_files(root: Path, record_id: str) -> list[Path]:
    prefix = root / Path(record_id)
    if prefix.parent.exists():
        return sorted(prefix.parent.glob(f"{prefix.name}.*"))
    return sorted(root.rglob(f"{Path(record_id).name}.*"))


def _manifest_entry_from_physionet(
    spec: DatasetSpec,
    root: Path,
    record_id: str,
    normalized_group_root: Path,
) -> ManifestEntry:
    files = _record_files(root, record_id)
    fs: float | None = None
    channels: list[str] = []
    duration: float | None = None
    notes = ""
    try:
        header = wfdb.rdheader(str((root / Path(record_id)).as_posix()))
        fs = float(header.fs) if header.fs is not None else None
        channels = list(header.sig_name or [])
        if fs and header.sig_len:
            duration = float(header.sig_len) / fs
    except Exception as exc:  # noqa: BLE001
        notes = f"header_read_failed: {type(exc).__name__}: {exc}"

    safe_record_id = record_id.replace("/", "_").replace("\\", "_")
    sample_id = f"{spec.dataset_name}__{safe_record_id}__full__0001"
    normalized_target = normalized_group_root / spec.normalized_group / f"{sample_id}.json"
    return ManifestEntry(
        dataset_name=spec.dataset_name,
        sample_id=sample_id,
        record_id=record_id,
        subject_id=record_id,
        task_tags=spec.task_tags,
        modality=spec.modality,
        sampling_rate=fs,
        channels=channels,
        label_types=_extract_label_types(files),
        duration_sec=duration,
        scene_activity=None,
        source_url=spec.source_url,
        raw_file_paths=[str(p) for p in files],
        normalized_file_paths=[str(normalized_target)],
        checksum=None,
        license_note=f"see official dataset license: {spec.source_url}",
        notes=notes,
    )


def _manifest_entry_from_uci(
    spec: DatasetSpec,
    root: Path,
    normalized_group_root: Path,
) -> list[ManifestEntry]:
    files = sorted(p for p in root.rglob("*") if p.is_file())
    if not files:
        return []
    record_id = "uci_export"
    sample_id = f"{spec.dataset_name}__{record_id}__full__0001"
    normalized_target = normalized_group_root / spec.normalized_group / f"{sample_id}.json"
    return [
        ManifestEntry(
            dataset_name=spec.dataset_name,
            sample_id=sample_id,
            record_id=record_id,
            subject_id=None,
            task_tags=spec.task_tags,
            modality=spec.modality,
            sampling_rate=None,
            channels=[],
            label_types=[],
            duration_sec=None,
            scene_activity="unknown",
            source_url=spec.source_url,
            raw_file_paths=[str(p) for p in files],
            normalized_file_paths=[str(normalized_target)],
            checksum=None,
            license_note=f"see official dataset license: {spec.source_url}",
            notes="UCI API export; raw waveform package may require manual official link download",
        )
    ]


def build_manifests(
    specs: list[DatasetSpec],
    raw_physionet_dir: Path,
    raw_uci_dir: Path,
    normalized_root: Path,
    manifest_dir: Path,
) -> pd.DataFrame:
    all_entries: list[ManifestEntry] = []
    manifest_columns = [
        "dataset_name",
        "sample_id",
        "record_id",
        "subject_id",
        "task_tags",
        "modality",
        "sampling_rate",
        "channels",
        "label_types",
        "duration_sec",
        "scene/activity",
        "source_url",
        "raw_file_paths",
        "normalized_file_paths",
        "checksum",
        "license_note",
        "notes",
    ]
    for spec in specs:
        root = _resolve_raw_dir(spec, raw_physionet_dir, raw_uci_dir)
        entries: list[ManifestEntry] = []
        if root.exists():
            if spec.source_type == "physionet":
                record_ids = _physionet_record_ids(root)
                entries = [
                    _manifest_entry_from_physionet(
                        spec=spec,
                        root=root,
                        record_id=record_id,
                        normalized_group_root=normalized_root,
                    )
                    for record_id in record_ids
                ]
            else:
                entries = _manifest_entry_from_uci(
                    spec=spec,
                    root=root,
                    normalized_group_root=normalized_root,
                )
        else:
            entries = []

        dataset_rows = [
            {
                "dataset_name": e.dataset_name,
                "sample_id": e.sample_id,
                "record_id": e.record_id,
                "subject_id": e.subject_id,
                "task_tags": json.dumps(e.task_tags, ensure_ascii=False),
                "modality": e.modality,
                "sampling_rate": e.sampling_rate,
                "channels": json.dumps(e.channels, ensure_ascii=False),
                "label_types": json.dumps(e.label_types, ensure_ascii=False),
                "duration_sec": e.duration_sec,
                "scene/activity": e.scene_activity,
                "source_url": e.source_url,
                "raw_file_paths": json.dumps(e.raw_file_paths, ensure_ascii=False),
                "normalized_file_paths": json.dumps(e.normalized_file_paths, ensure_ascii=False),
                "checksum": e.checksum,
                "license_note": e.license_note,
                "notes": e.notes,
            }
            for e in entries
        ]
        dataset_df = pd.DataFrame(dataset_rows, columns=manifest_columns)
        write_df_csv(manifest_dir / f"manifest_{spec.dataset_name}.csv", dataset_df)
        write_json(
            manifest_dir / f"manifest_{spec.dataset_name}.json",
            dataset_df.to_dict(orient="records"),
        )
        all_entries.extend(entries)

    all_df = pd.DataFrame(
        [
            {
                "dataset_name": e.dataset_name,
                "sample_id": e.sample_id,
                "record_id": e.record_id,
                "subject_id": e.subject_id,
                "task_tags": json.dumps(e.task_tags, ensure_ascii=False),
                "modality": e.modality,
                "sampling_rate": e.sampling_rate,
                "channels": json.dumps(e.channels, ensure_ascii=False),
                "label_types": json.dumps(e.label_types, ensure_ascii=False),
                "duration_sec": e.duration_sec,
                "scene/activity": e.scene_activity,
                "source_url": e.source_url,
                "raw_file_paths": json.dumps(e.raw_file_paths, ensure_ascii=False),
                "normalized_file_paths": json.dumps(e.normalized_file_paths, ensure_ascii=False),
                "checksum": e.checksum,
                "license_note": e.license_note,
                "notes": e.notes,
            }
            for e in all_entries
        ]
    )
    write_df_csv(manifest_dir / "manifest_all.csv", all_df)
    write_json(manifest_dir / "manifest_all.json", all_df.to_dict(orient="records"))
    return all_df
