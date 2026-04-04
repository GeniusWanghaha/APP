from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from .pathing import project_root


def load_yaml(path: Path | str) -> dict[str, Any]:
    path_obj = Path(path)
    if not path_obj.is_absolute():
        path_obj = project_root() / path_obj
    with path_obj.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)

