from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GroupKFold

from src.features.af_features import extract_af_features
from src.features.hrv import rr_intervals_ms
from src.preprocessing.ecg import detect_rpeaks_adaptive, preprocess_ecg


@dataclass
class AfSample:
    subject_id: str
    label: int
    feature: dict[str, float]


def weak_label_from_annotations(annotations: dict) -> int:
    # Weak label fallback:
    # if annotation symbols include known AF markers, assign 1.
    for ann in annotations.values():
        symbols = ann.get("symbol", []) if isinstance(ann, dict) else []
        for symbol in symbols:
            if symbol in {"AFIB", "AFL", "f", "F"}:
                return 1
    return 0


def build_af_samples(
    loader_registry,
    dataset_names: list[str],
    max_records_per_dataset: int = 12,
    max_duration_sec: float = 300.0,
) -> list[AfSample]:
    samples: list[AfSample] = []
    for dataset_name in dataset_names:
        records = loader_registry.list_records(dataset_name)[:max_records_per_dataset]
        for record_id in records:
            try:
                payload = loader_registry.load_record(dataset_name, record_id)
                signal_map = payload["signals"]
                fs_map = payload["fs"]
                if not signal_map:
                    continue
                ch = next(iter(signal_map.keys()))
                ecg = np.asarray(signal_map[ch], dtype=float)
                fs = float(next(iter(fs_map.values()), 250.0))
                if ecg.size < fs * 30:
                    continue
                max_len = int(fs * max_duration_sec)
                if max_len > 0 and ecg.size > max_len:
                    ecg = ecg[:max_len]
                ecg = preprocess_ecg(ecg, fs)
                rpeaks = detect_rpeaks_adaptive(ecg, fs)
                rr = rr_intervals_ms(rpeaks, fs)
                feature = extract_af_features(rr)
                label = weak_label_from_annotations(payload.get("annotations", {}))
                samples.append(
                    AfSample(
                        subject_id=str(payload.get("meta", {}).get("record_id", record_id)),
                        label=label,
                        feature=feature,
                    )
                )
            except Exception:  # noqa: BLE001
                continue
    return samples


def train_eval_af_baseline(samples: list[AfSample]) -> dict:
    if len(samples) < 10:
        return {
            "status": "skipped",
            "reason": "insufficient samples",
            "sample_count": len(samples),
        }

    df = pd.DataFrame([s.feature for s in samples])
    df["label"] = [s.label for s in samples]
    df["group"] = [s.subject_id for s in samples]
    df = df.replace([np.inf, -np.inf], np.nan).dropna()
    if df.empty or df["label"].nunique() < 2:
        return {
            "status": "skipped",
            "reason": "insufficient valid AF labels after cleaning",
            "sample_count": len(df),
        }

    X = df.drop(columns=["label", "group"]).to_numpy(dtype=float)
    y = df["label"].to_numpy(dtype=int)
    groups = df["group"].to_numpy()

    gkf = GroupKFold(n_splits=min(5, max(2, len(np.unique(groups)))))
    fold_metrics = []
    all_y_true = []
    all_y_pred = []
    all_y_prob = []
    for train_idx, test_idx in gkf.split(X, y, groups):
        model = RandomForestClassifier(
            n_estimators=200,
            random_state=42,
            class_weight="balanced_subsample",
            max_depth=6,
        )
        model.fit(X[train_idx], y[train_idx])
        y_prob = model.predict_proba(X[test_idx])[:, 1]
        y_pred = (y_prob >= 0.5).astype(int)
        y_true = y[test_idx]

        fold_metrics.append(
            {
                "precision": float(precision_score(y_true, y_pred, zero_division=0)),
                "recall": float(recall_score(y_true, y_pred, zero_division=0)),
                "f1": float(f1_score(y_true, y_pred, zero_division=0)),
            }
        )
        all_y_true.extend(y_true.tolist())
        all_y_pred.extend(y_pred.tolist())
        all_y_prob.extend(y_prob.tolist())

    y_true_arr = np.asarray(all_y_true, dtype=int)
    y_pred_arr = np.asarray(all_y_pred, dtype=int)
    y_prob_arr = np.asarray(all_y_prob, dtype=float)
    cm = confusion_matrix(y_true_arr, y_pred_arr).tolist()

    result = {
        "status": "ok",
        "sample_count": int(len(df)),
        "positive_count": int(np.sum(y_true_arr == 1)),
        "negative_count": int(np.sum(y_true_arr == 0)),
        "precision": float(precision_score(y_true_arr, y_pred_arr, zero_division=0)),
        "recall": float(recall_score(y_true_arr, y_pred_arr, zero_division=0)),
        "f1": float(f1_score(y_true_arr, y_pred_arr, zero_division=0)),
        "specificity": float(np.sum((y_true_arr == 0) & (y_pred_arr == 0)) / max(np.sum(y_true_arr == 0), 1)),
        "auroc": float(roc_auc_score(y_true_arr, y_prob_arr)) if len(np.unique(y_true_arr)) > 1 else float("nan"),
        "confusion_matrix": cm,
        "fold_metrics": fold_metrics,
        "y_true": y_true_arr.tolist(),
        "y_prob": y_prob_arr.tolist(),
    }
    return result
