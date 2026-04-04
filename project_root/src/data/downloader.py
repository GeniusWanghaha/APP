from __future__ import annotations

import traceback
import zipfile
from dataclasses import dataclass
from pathlib import Path

import pandas as pd
import requests
import wfdb
from ucimlrepo import fetch_ucirepo

from src.data.catalog import DatasetSpec
from src.utils.io_utils import write_json
from src.utils.pathing import ensure_dir


@dataclass
class DownloadResult:
    dataset_name: str
    source_type: str
    source_url: str
    status: str
    message: str
    local_path: str


def _physionet_target_dir(raw_physionet_dir: Path, dataset_name: str) -> Path:
    return ensure_dir(raw_physionet_dir / dataset_name)


def _uci_target_dir(raw_uci_dir: Path, dataset_name: str) -> Path:
    return ensure_dir(raw_uci_dir / dataset_name)


def _extract_zip_archive(zip_path: Path, target_dir: Path) -> None:
    target_dir = target_dir.resolve()
    with zipfile.ZipFile(zip_path, "r") as zf:
        for member in zf.namelist():
            member_target = (target_dir / member).resolve()
            if target_dir not in member_target.parents and member_target != target_dir:
                raise RuntimeError(f"unsafe zip member path: {member}")
        zf.extractall(target_dir)


def download_physionet_dataset(
    spec: DatasetSpec,
    raw_physionet_dir: Path,
    log_dir: Path,
    force_redownload: bool = False,
) -> DownloadResult:
    target_dir = _physionet_target_dir(raw_physionet_dir, spec.dataset_name)
    has_existing_data = any(target_dir.rglob("*.hea"))
    if has_existing_data and not force_redownload:
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="skipped_existing",
            message="existing .hea files found; skip redownload",
            local_path=str(target_dir),
        )

    try:
        wfdb.dl_database(
            db_dir=spec.db_slug or spec.dataset_name,
            dl_dir=str(target_dir),
            records="all",
        )
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="ok",
            message="wfdb.dl_database success",
            local_path=str(target_dir),
        )
    except Exception as exc:  # noqa: BLE001
        # Fallback for non-WFDB file projects on PhysioNet (e.g., BUT PPG).
        fallback = _download_physionet_zip_fallback(spec=spec, target_dir=target_dir)
        if fallback is not None:
            return fallback

        err = f"{type(exc).__name__}: {exc}"
        trace_path = ensure_dir(log_dir) / f"download_error_{spec.dataset_name}.log"
        trace_path.write_text(traceback.format_exc(), encoding="utf-8")
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="failed",
            message=f"{err}; see {trace_path.name}",
            local_path=str(target_dir),
        )


def _download_physionet_zip_fallback(spec: DatasetSpec, target_dir: Path) -> DownloadResult | None:
    url = spec.source_url.rstrip("/")
    # expected style: https://physionet.org/content/<slug>/<version>
    parts = url.split("/")
    if "content" not in parts:
        return None
    try:
        content_index = parts.index("content")
        slug = parts[content_index + 1]
        version = parts[content_index + 2]
    except Exception:  # noqa: BLE001
        return None

    zip_url = f"https://physionet.org/content/{slug}/get-zip/{version}/"
    zip_path = target_dir / f"{spec.dataset_name}_{version}.zip"
    try:
        with requests.get(zip_url, stream=True, timeout=300) as response:
            response.raise_for_status()
            with zip_path.open("wb") as f:
                for chunk in response.iter_content(chunk_size=1024 * 1024):
                    if chunk:
                        f.write(chunk)
        _extract_zip_archive(zip_path=zip_path, target_dir=target_dir)
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="ok",
            message=f"zip fallback success from {zip_url}; archive extracted",
            local_path=str(target_dir),
        )
    except Exception:  # noqa: BLE001
        return None


def download_uci_dataset(
    spec: DatasetSpec,
    raw_uci_dir: Path,
    log_dir: Path,
) -> DownloadResult:
    target_dir = _uci_target_dir(raw_uci_dir, spec.dataset_name)
    if any(target_dir.iterdir()):
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="skipped_existing",
            message="existing files found; skip redownload",
            local_path=str(target_dir),
        )

    if spec.uci_id is None:
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="failed",
            message="uci_id missing in dataset spec",
            local_path=str(target_dir),
        )

    try:
        dataset = fetch_ucirepo(id=spec.uci_id)
        if dataset.data.features is not None:
            features = dataset.data.features
            if isinstance(features, pd.DataFrame):
                features.to_csv(target_dir / "features.csv", index=False, encoding="utf-8")
        if dataset.data.targets is not None:
            targets = dataset.data.targets
            if isinstance(targets, pd.DataFrame):
                targets.to_csv(target_dir / "targets.csv", index=False, encoding="utf-8")
        metadata_payload = {
            "name": getattr(dataset.metadata, "name", None),
            "abstract": getattr(dataset.metadata, "abstract", None),
            "num_instances": getattr(dataset.metadata, "num_instances", None),
            "num_features": getattr(dataset.metadata, "num_features", None),
            "source_url": spec.source_url,
            "note": (
                "UCI loader exported tabular payload from official API. "
                "If raw wearable waveform files are needed, follow official external link "
                "from UCI page and place them under this folder manually."
            ),
        }
        write_json(target_dir / "metadata.json", metadata_payload)

        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="ok",
            message="ucimlrepo fetch success",
            local_path=str(target_dir),
        )
    except Exception as exc:  # noqa: BLE001
        err = f"{type(exc).__name__}: {exc}"
        trace_path = ensure_dir(log_dir) / f"download_error_{spec.dataset_name}.log"
        trace_path.write_text(traceback.format_exc(), encoding="utf-8")
        return DownloadResult(
            dataset_name=spec.dataset_name,
            source_type=spec.source_type,
            source_url=spec.source_url,
            status="failed",
            message=f"{err}; see {trace_path.name}",
            local_path=str(target_dir),
        )


def download_datasets(
    specs: list[DatasetSpec],
    raw_physionet_dir: Path,
    raw_uci_dir: Path,
    log_dir: Path,
    phase: int,
    force_redownload: bool = False,
) -> list[DownloadResult]:
    results: list[DownloadResult] = []
    for spec in specs:
        if spec.phase > phase:
            results.append(
                DownloadResult(
                    dataset_name=spec.dataset_name,
                    source_type=spec.source_type,
                    source_url=spec.source_url,
                    status="skipped_phase",
                    message=f"phase {spec.phase} > active phase {phase}",
                    local_path="",
                )
            )
            continue
        if spec.source_type == "physionet":
            results.append(
                download_physionet_dataset(
                    spec=spec,
                    raw_physionet_dir=raw_physionet_dir,
                    log_dir=log_dir,
                    force_redownload=force_redownload,
                )
            )
        elif spec.source_type == "uci":
            results.append(
                download_uci_dataset(
                    spec=spec,
                    raw_uci_dir=raw_uci_dir,
                    log_dir=log_dir,
                )
            )
        else:
            results.append(
                DownloadResult(
                    dataset_name=spec.dataset_name,
                    source_type=spec.source_type,
                    source_url=spec.source_url,
                    status="failed",
                    message=f"unsupported source_type={spec.source_type}",
                    local_path="",
                )
            )
    return results
