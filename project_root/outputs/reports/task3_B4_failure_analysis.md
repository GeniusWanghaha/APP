# Task3 B4 Failure Case Analysis

- Model analyzed: `xgboost`

## Case Counts
| case_type | n | mean_prob | mean_sdnn | mean_rmssd | mean_sampen | mean_ectopic | mean_sqi | mean_af_ratio |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| FN | 5 | 0.000 | 97.00 | 129.99 | 0.471 | 0.052 | 0.960 | 1.000 |
| FP | 5 | 0.997 | 196.70 | 283.45 | 2.045 | 0.357 | 0.921 | 0.000 |
| TN | 5 | 0.000 | 49.67 | 39.78 | 1.253 | 0.000 | 0.987 | 0.000 |
| TP | 5 | 1.000 | 95.16 | 124.56 | 2.357 | 0.292 | 0.940 | 1.000 |

## Interpretation
- TP windows: usually show strong irregularity signatures (high sample entropy / RMSSD / turning behavior).
- FP windows: often high-variability non-AF rhythm with elevated ectopic-like pattern; likely PVC/PAC confusion.
- FN windows: often boundary/transition cases or AF windows with weaker short-term irregularity expression.
- TN windows: stable RR structure and low ectopic profile.

## Output Files
- `outputs/tables/task3_failure_case_table.csv`
- `outputs/tables/task3_failure_case_summary.csv`
- `outputs/figures/task3_failure_case_*.png`
