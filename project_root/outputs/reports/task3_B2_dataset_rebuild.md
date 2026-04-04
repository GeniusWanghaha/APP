# Task3 B2 Dataset Rebuild

## Rebuild Strategy
- Segment length: 30s
- Step: 10s
- Label rule: AF ratio >= 0.6 => AF, <= 0.1 => non-AF, else uncertain/drop
- Patient-wise split: GroupShuffleSplit by `subject_id` (train/val/test).
- Engineering cap for this iteration: first 1800s per record for controllable runtime.

## Core Stats
- Total windows: 21004
- Kept windows: 19597
- Dropped windows: 1407 (6.70%)
- AF windows: 4123
- non-AF windows: 15474

## Dataset Contribution (kept only)
| dataset | kept_windows | af_windows | non_af_windows | subjects |
| --- | --- | --- | --- | --- |
| afdb | 4073 | 872 | 3201 | 23 |
| ltafdb | 6981 | 3251 | 3730 | 46 |
| mitdb | 8543 | 0 | 8543 | 48 |

## Split Stats (kept only)
| split | windows | af_windows | non_af_windows | subjects |
| --- | --- | --- | --- | --- |
| test | 3613 | 648 | 2965 | 15 |
| train | 12944 | 2369 | 10575 | 65 |
| val | 3040 | 1106 | 1934 | 14 |

## Output Files
- `outputs/tables/task3_rebuilt_segments.csv`
- `outputs/tables/task3_rebuilt_dataset_stats.csv`