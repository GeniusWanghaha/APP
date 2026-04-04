from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import wfdb

from src.data.catalog import DatasetSpec


@dataclass
class LoadedRecord:
    signals: dict[str, np.ndarray]
    fs: dict[str, float]
    annotations: dict[str, Any]
    meta: dict[str, Any]


class BaseLoader:
    def __init__(self, spec: DatasetSpec, raw_root: Path):
        self.spec = spec
        self.raw_root = raw_root

    def list_records(self) -> list[str]:
        raise NotImplementedError

    def load_record(self, record_id: str, target_modalities: list[str] | None = None) -> LoadedRecord:
        raise NotImplementedError

    def get_sampling_rate(self, record_id: str) -> float | None:
        loaded = self.load_record(record_id=record_id)
        first = next(iter(loaded.fs.values()), None)
        return first

    def get_subject_id(self, record_id: str) -> str | None:
        return record_id


class PhysioNetLoader(BaseLoader):
    def list_records(self) -> list[str]:
        return sorted(
            {
                p.relative_to(self.raw_root).with_suffix("").as_posix()
                for p in self.raw_root.rglob("*.hea")
            }
        )

    def _record_prefix(self, record_id: str) -> str:
        path = (self.raw_root / Path(record_id)).as_posix()
        return str(path)

    def load_record(self, record_id: str, target_modalities: list[str] | None = None) -> LoadedRecord:
        try:
            record = wfdb.rdrecord(self._record_prefix(record_id))
        except Exception as exc:  # noqa: BLE001
            return LoadedRecord(
                signals={},
                fs={},
                annotations={},
                meta={
                    "dataset_name": self.spec.dataset_name,
                    "record_id": record_id,
                    "source_url": self.spec.source_url,
                    "error": f"{type(exc).__name__}: {exc}",
                },
            )
        raw_signal_names = list(record.sig_name)
        signal_names = [
            (name.strip() if isinstance(name, str) and name.strip() else f"ch_{idx}")
            for idx, name in enumerate(raw_signal_names)
        ]
        signal_matrix = record.p_signal
        if signal_matrix is None and record.d_signal is not None:
            signal_matrix = record.d_signal.astype(float)
        if signal_matrix is None:
            return LoadedRecord(
                signals={},
                fs={},
                annotations={},
                meta={
                    "dataset_name": self.spec.dataset_name,
                    "record_id": record_id,
                    "source_url": self.spec.source_url,
                    "error": "signal_matrix_missing",
                },
            )
        signals = {
            ch: signal_matrix[:, idx].astype(float)
            for idx, ch in enumerate(signal_names)
        }
        fs_map = {ch: float(record.fs) for ch in signal_names}

        annotations: dict[str, Any] = {}
        for ext in ("atr", "qrs", "ecg", "ann"):
            try:
                ann = wfdb.rdann(self._record_prefix(record_id), extension=ext)
                annotations[ext] = {
                    "sample": ann.sample.tolist(),
                    "symbol": ann.symbol,
                }
                break
            except Exception:  # noqa: BLE001
                continue

        return LoadedRecord(
            signals=signals,
            fs=fs_map,
            annotations=annotations,
            meta={
                "dataset_name": self.spec.dataset_name,
                "record_id": record_id,
                "source_url": self.spec.source_url,
                "modality": self.spec.modality,
            },
        )


class UciLoader(BaseLoader):
    def list_records(self) -> list[str]:
        if not self.raw_root.exists():
            return []
        has_features = (self.raw_root / "features.csv").exists()
        return ["uci_export"] if has_features else []

    def load_record(self, record_id: str, target_modalities: list[str] | None = None) -> LoadedRecord:
        features_path = self.raw_root / "features.csv"
        targets_path = self.raw_root / "targets.csv"
        if not features_path.exists():
            raise FileNotFoundError(f"{features_path} not found")

        features_df = pd.read_csv(features_path)
        targets_df = pd.read_csv(targets_path) if targets_path.exists() else pd.DataFrame()
        annotations: dict[str, Any] = {}
        if not targets_df.empty:
            annotations["targets"] = targets_df.to_dict(orient="list")
        metadata_path = self.raw_root / "metadata.json"
        metadata = {}
        if metadata_path.exists():
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        return LoadedRecord(
            signals={"features": features_df.to_numpy(dtype=float)},
            fs={},
            annotations=annotations,
            meta={
                "dataset_name": self.spec.dataset_name,
                "record_id": record_id,
                "source_url": self.spec.source_url,
                "metadata": metadata,
                "warning": "UCI export may not be raw waveform data.",
            },
        )


class LoaderRegistry:
    def __init__(
        self,
        specs: list[DatasetSpec],
        raw_physionet_dir: Path,
        raw_uci_dir: Path,
    ):
        self._loaders: dict[str, BaseLoader] = {}
        for spec in specs:
            if spec.source_type == "physionet":
                loader = PhysioNetLoader(spec=spec, raw_root=raw_physionet_dir / spec.dataset_name)
            else:
                loader = UciLoader(spec=spec, raw_root=raw_uci_dir / spec.dataset_name)
            self._loaders[spec.dataset_name] = loader

    def list_records(self, dataset_name: str) -> list[str]:
        return self._loaders[dataset_name].list_records()

    def load_record(
        self,
        dataset_name: str,
        record_id: str,
        target_modalities: list[str] | None = None,
    ) -> dict[str, Any]:
        loaded = self._loaders[dataset_name].load_record(record_id, target_modalities)
        return {
            "signals": loaded.signals,
            "fs": loaded.fs,
            "annotations": loaded.annotations,
            "meta": loaded.meta,
        }

    def get_sampling_rate(self, dataset_name: str, record_id: str) -> float | None:
        return self._loaders[dataset_name].get_sampling_rate(record_id)

    def get_subject_id(self, dataset_name: str, record_id: str) -> str | None:
        return self._loaders[dataset_name].get_subject_id(record_id)

    def get_labels(self, dataset_name: str, record_id: str) -> dict[str, Any]:
        loaded = self._loaders[dataset_name].load_record(record_id)
        return loaded.annotations

    def segment_record(
        self,
        dataset_name: str,
        record_id: str,
        start_sec: float,
        duration_sec: float,
    ) -> dict[str, Any]:
        loaded = self._loaders[dataset_name].load_record(record_id)
        if not loaded.signals:
            return {
                "signals": {},
                "fs": loaded.fs,
                "annotations": loaded.annotations,
                "meta": loaded.meta,
            }
        segmented: dict[str, np.ndarray] = {}
        for ch, sig in loaded.signals.items():
            fs = loaded.fs.get(ch)
            if fs is None or fs <= 0:
                continue
            start_idx = max(0, int(start_sec * fs))
            end_idx = min(len(sig), int((start_sec + duration_sec) * fs))
            segmented[ch] = sig[start_idx:end_idx]
        return {
            "signals": segmented,
            "fs": loaded.fs,
            "annotations": loaded.annotations,
            "meta": loaded.meta,
        }
