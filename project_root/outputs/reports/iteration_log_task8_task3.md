# Iteration Log (Task8 + Task3)

## Step1 Context Recheck
- Read dataset_inventory.csv and best_metrics.json
- Confirmed Task8 delay order anomaly and Task3 AF baseline failure

## A1 Definition Audit
- Audited Task8 definitions and pairing logic
- Identified independent pairing as most likely root cause

## A2 Visual Audit
- Generated 9 audit figures for sit/walk/run
- Mean bad-order ratio: 0.889

## A3 Rule Check
- total_beats=17003, valid=839, invalid=16164
- top_failed_rule=order_violation_R_foot_peak

## A4 Task8 Fix Applied
- Updated src/features/ptt.py: new constrained beat pairing and peak-anchored foot derivation
- Updated src/evaluation/task8_ptt.py: switched to new pairing output and added validity counters

## A5 Task8 Re-evaluation
- after_r2foot_mean=227.45 ms
- after_r2peak_mean=406.92 ms
- order_check=PASS

## B1 Label/Split Audit
- Confirmed current Task3 is record-level weak labeling and ignores atr.aux_note rhythm labels
- Current sample imbalance: pos=4, neg=30

## B2 Dataset Rebuild
- total_windows=21004, kept=19597, dropped=1407
- AF=4123, non_AF=15474, drop_ratio=0.067

## B3 Baseline Models
- repaired record_id mapping for leading-zero records before feature extraction
- best_model=xgboost, F1=0.7359, AUROC=0.9499
- test_n=3499, pos=648, neg=2851

## B4 Failure Case Analysis
- model=xgboost, selected_cases=20
- FN: n=5, mean_prob=0.000
- FP: n=5, mean_prob=0.997
- TN: n=5, mean_prob=0.000
- TP: n=5, mean_prob=1.000

## B5 Iteration Strategy
- stop_by_target_reached (AUROC>=0.75 and F1>=0.60)
- baseline best: model=xgboost, F1=0.7359, AUROC=0.9499

## B6 AF Final Summary
- best_model=xgboost, F1=0.7359, AUROC=0.9499
- recommendation: engineering-level AF screening claim, not clinical claim

## Final Merge Report
- Generated consolidated task8_task3_special_report.md

## Task8 Integration Check
- Executed run_task8 after fix on 66 records and saved outputs/metrics/task8_after_fix_metrics.json

