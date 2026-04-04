from __future__ import annotations

import numpy as np


def find_channel_by_keywords(signal_map: dict[str, np.ndarray], keywords: list[str]) -> str | None:
    lower_map = {
        str(name).lower(): str(name)
        for name in signal_map.keys()
        if isinstance(name, str) and name.strip()
    }
    for key in keywords:
        key_l = key.lower()
        for low_name, original in lower_map.items():
            if key_l in low_name:
                return original
    # Fallback to first valid string key.
    for name in signal_map.keys():
        if isinstance(name, str) and name.strip():
            return name
    return None
