# Task3 B5 Iteration Log

## Decision
- stop_by_target_reached (AUROC>=0.75 and F1>=0.60)

## Baseline Checkpoint
- best model: xgboost
- F1: 0.7359
- AUROC: 0.9499
- precision/recall/specificity: 0.6505/0.8472/0.8965

## Why no further forced iteration now
- Current objective for B5 is to recover a usable baseline without over-design.
- Baseline already meets stop criterion; additional complexity now may reduce interpretability and increase overfit risk.
- Recommended next step is external validation on self-collected hardware data and stricter cross-dataset holdout stress tests.

## Output
- `outputs/tables/task3_iteration_compare.csv`