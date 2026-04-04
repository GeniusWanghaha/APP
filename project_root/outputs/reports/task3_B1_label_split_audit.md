# Task3 B1 Label/Split Audit

## Direct Findings
- Current AF task is **record-level weak labeling**, not segment-level AF detection.
- Current label extraction only checks annotation `symbol` for `AFIB/AFL/f/F`.
- Rhythm information in `atr.aux_note` (key AF source in AFDB/LTAFDB) is not used in the old pipeline.
- Current sample size is very small and heavily imbalanced.

## Answers to Required Questions
1. Label source: `weak_label_from_annotations` using annotation `symbol` only.
2. AF mapping: positive if symbol includes `AFIB/AFL/f/F`.
3. Granularity: record-level classification.
4. Window/step/label-threshold: not defined in old Task3 path.
5. Split strategy: GroupKFold on record_id proxy (patient-wise intent).
6. Leakage check: no direct leakage found in old setup.
7. Class imbalance: severe.
8. Label inversion: no direct inversion evidence, but clear missing-label risk.
9. Time-axis mismatch: old design is full-record level, so segment alignment is not implemented.

## Most Likely Root Cause of F1=0
- **Systematic task-definition mismatch + weak/missing AF labels** in the old record-level pipeline.

## Output Files
- `outputs/tables/task3_label_audit_summary.csv`
- `outputs/tables/task3_label_probe_records.csv`
