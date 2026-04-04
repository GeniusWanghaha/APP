from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from src.utils.config import load_yaml
from src.utils.pathing import project_root


@dataclass(frozen=True)
class DatasetSpec:
    dataset_name: str
    source_type: str
    source_url: str
    db_slug: str | None
    version: str
    phase: int
    task_tags: list[str]
    normalized_group: str
    modality: str
    uci_id: int | None = None

    @staticmethod
    def from_dict(payload: dict[str, Any]) -> "DatasetSpec":
        return DatasetSpec(
            dataset_name=payload["dataset_name"],
            source_type=payload["source_type"],
            source_url=payload["source_url"],
            db_slug=payload.get("db_slug"),
            version=payload["version"],
            phase=int(payload["phase"]),
            task_tags=list(payload.get("task_tags", [])),
            normalized_group=payload["normalized_group"],
            modality=payload["modality"],
            uci_id=payload.get("uci_id"),
        )


def load_catalog(path: Path | None = None) -> list[DatasetSpec]:
    cfg_path = path or (project_root() / "configs" / "datasets.yaml")
    raw = load_yaml(cfg_path)
    return [DatasetSpec.from_dict(item) for item in raw["datasets"]]

