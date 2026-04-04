# Task8 A3 Rule Check

## Rules
1. `t_R < t_foot < t_peak`
2. `R->foot` in `[80, 400] ms`
3. `R->peak` in `[120, 500] ms`
4. `foot->peak > 0`
5. Delay jump outlier check (per-record median/MAD)

## Summary
- Total beats: 17003
- Valid beats: 839
- Invalid beats: 16164
- Valid ratio: 0.049

## Failure Reason Breakdown
| reason | count | ratio_over_total_beats |
| --- | --- | --- |
| order_violation_R_foot_peak | 14726 | 0.8661 |
| foot_to_peak_non_positive | 14726 | 0.8661 |
| delay_jump_r2peak | 5017 | 0.2951 |
| delay_jump_r2foot | 3671 | 0.2159 |
| r2foot_out_of_range | 2904 | 0.1708 |
| r2peak_out_of_range | 2141 | 0.1259 |
| missing_foot_or_peak | 31 | 0.0018 |

- Most frequent failed rule: `order_violation_R_foot_peak`

## Output Files
- `outputs/tables/task8_rule_check_summary.csv`
- `outputs/tables/task8_rule_check_details.csv`
- `outputs/tables/task8_rule_check_reason_breakdown.csv`