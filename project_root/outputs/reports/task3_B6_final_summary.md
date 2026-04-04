# Task3 B6 Final Summary

## Current Best Configuration
- Dataset: 30-second windows, 10-second step, patient-wise split.
- Label rule: AF ratio >= 0.60 as AF, <= 0.10 as non-AF, middle windows dropped as uncertain.
- Feature family: RR statistics, irregularity features, Poincare features, SQI/effective-peak ratio, ectopic ratio.
- Best model: `xgboost` (threshold = 0.575).

## Best Metrics (patient-wise test)
- AUROC: 0.9499
- F1: 0.7359
- Precision: 0.6505
- Recall (Sensitivity): 0.8472
- Specificity: 0.8965
- Confusion matrix: TN=2556, FP=295, FN=99, TP=549

## Data Footprint for This AF Run
- Total windows built: 21004
- Kept windows: 19597
- Dropped windows: 1407

## Remaining Major Error Sources
- False positives: non-AF irregular windows (high ectopic/high variability) can still mimic AF.
- False negatives: boundary-transition windows and low-AF-burden windows remain challenging.
- Coverage risk: `ltafdb` is still incomplete (49/84 records).

## Competition-level Wording Suggestion
- It is reasonable to claim an **initial engineering-level AF screening baseline** on public datasets.
- It is not acceptable to claim clinical-grade performance.
- Keep explicit disclaimer: external validation on self-collected hardware data is still required.

## Output References
- `outputs/tables/task3_baseline_model_compare.csv`
- `outputs/reports/task3_B3_baseline_models.md`
- `outputs/reports/task3_B4_failure_analysis.md`
