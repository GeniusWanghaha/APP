from __future__ import annotations

import numpy as np


def sample_entropy(x: np.ndarray, m: int = 2, r_scale: float = 0.2) -> float:
    if x.size < 20:
        return float("nan")
    x = np.asarray(x, dtype=float)
    r = r_scale * np.std(x)
    if r <= 1e-9:
        return 0.0

    def _phi(order: int) -> float:
        vectors = np.array([x[i : i + order] for i in range(len(x) - order + 1)])
        count = 0
        for i in range(len(vectors)):
            d = np.max(np.abs(vectors[i + 1 :] - vectors[i]), axis=1)
            count += int(np.sum(d <= r))
        return count

    b = _phi(m)
    a = _phi(m + 1)
    if b == 0 or a == 0:
        return float("nan")
    return float(-np.log(a / b))


def poincare(rr_ms: np.ndarray) -> tuple[float, float]:
    if rr_ms.size < 3:
        return float("nan"), float("nan")
    x1 = rr_ms[:-1]
    x2 = rr_ms[1:]
    diff = (x2 - x1) / np.sqrt(2.0)
    summ = (x2 + x1) / np.sqrt(2.0)
    sd1 = float(np.std(diff))
    sd2 = float(np.std(summ))
    return sd1, sd2


def extract_af_features(rr_ms: np.ndarray) -> dict[str, float]:
    if rr_ms.size == 0:
        return {
            "rr_mean": float("nan"),
            "rr_std": float("nan"),
            "rr_cv": float("nan"),
            "sampen": float("nan"),
            "sd1": float("nan"),
            "sd2": float("nan"),
            "sd1_sd2_ratio": float("nan"),
        }
    rr_mean = float(np.mean(rr_ms))
    rr_std = float(np.std(rr_ms))
    rr_cv = float(rr_std / rr_mean) if rr_mean > 0 else float("nan")
    sampen = sample_entropy(rr_ms)
    sd1, sd2 = poincare(rr_ms)
    ratio = float(sd1 / sd2) if sd2 and not np.isnan(sd2) and abs(sd2) > 1e-9 else float("nan")
    return {
        "rr_mean": rr_mean,
        "rr_std": rr_std,
        "rr_cv": rr_cv,
        "sampen": sampen,
        "sd1": sd1,
        "sd2": sd2,
        "sd1_sd2_ratio": ratio,
    }

