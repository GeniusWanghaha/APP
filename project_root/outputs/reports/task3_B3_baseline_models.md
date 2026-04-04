# Task3 B3 Baseline Models

## Feature Set
- mean_rr_ms
- std_rr_ms
- sdnn_ms
- rmssd_ms
- pnn50
- sample_entropy
- turning_point_ratio
- sd1
- sd2
- sd1_sd2_ratio
- effective_rpeak_ratio
- ectopic_ratio
- sqi_proxy

## Data Split
- Train windows: 12250
- Val windows: 2696
- Test windows: 3499
- Test class balance: pos=648, neg=2851

## Model Comparison (test)
| model | threshold | precision | recall | specificity | f1 | auroc | tn | fp | fn | tp |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| xgboost | 0.575 | 0.6505 | 0.8472 | 0.8965 | 0.7359 | 0.9499 | 2556 | 295 | 99 | 549 |
| random_forest | 0.675 | 0.6501 | 0.7052 | 0.9137 | 0.6765 | 0.9297 | 2605 | 246 | 191 | 457 |
| logistic_regression | 0.700 | 0.5903 | 0.7114 | 0.8878 | 0.6452 | 0.9193 | 2531 | 320 | 187 | 461 |

- Best baseline by F1: `xgboost` (F1=0.7359, AUROC=0.9499)

## Output Files
- `outputs/tables/task3_baseline_model_compare.csv`
- `outputs/tables/task3_baseline_predictions.csv`
- `outputs/tables/task3_feature_matrix.csv`
- `outputs/figures/task3_baseline_roc.png`
- `outputs/figures/task3_baseline_pr.png`