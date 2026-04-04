# Task8 A5 Re-evaluation

## Before vs After (Overall)

- Before R->foot mean/median/std: 311.47 / 318.00 / 64.15 ms (n=2000)
- Before R->peak mean/median/std: 190.09 / 170.00 / 74.81 ms (n=2000)
- After R->foot mean/median/std: 227.45 / 248.00 / 69.89 ms (n=1168)
- After R->peak mean/median/std: 406.92 / 422.00 / 67.96 ms (n=1168)

## Scene-level (After)
| scene | n_beats | r2foot_mean_ms | r2peak_mean_ms | r2foot_median_ms | r2peak_median_ms |
| --- | --- | --- | --- | --- | --- |
| run | 398 | 215.09 | 412.42 | 221.00 | 429.00 |
| sit | 43 | 170.70 | 300.70 | 132.00 | 232.00 |
| walk | 727 | 237.58 | 410.20 | 254.00 | 420.00 |

## Order Check
- Condition `R->foot < R->peak` on overall mean: **PASS**

## Outputs
- `outputs/tables/task8_recomputed_metrics.csv`
- `outputs/tables/task8_scene_stats_after.csv`
- `outputs/tables/task8_subject_stats_after.csv`
- `outputs/figures/task8_delay_distribution_before_after.png`
- `outputs/figures/task8_subjectwise_delay_boxplot.png`