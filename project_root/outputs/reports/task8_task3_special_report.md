# Task8 + Task3 Special Report

## 1. Project Context
- Workspace: `d:/optoelectronic_design/APP/project_root`
- Dataset state used in this special run:
  - `ok`: mitdb, afdb, qtdb, nsrdb, nstdb, but_pdb, but_ppg, bidmc, ptt_ppg, apnea_ecg
  - `incomplete`: ltafdb (49/84)
  - `missing`: ppg_dalia
- Scope of this run: Task8 and Task3 only, no full-project rerun.

## 2. Existing Result Recheck
- Task8 (before fix): `R->foot=313.92 ms`, `R->peak=189.23 ms`, physiological order anomaly.
- Task3 (before fix): `F1=0.0000`, `AUROC=0.3792`, practically unusable.

## 2.1 Mainline Sync Status (Latest)
- Task8 fixed logic has been synced into mainline outputs.
- Mainline snapshot now uses:
  - `R->foot mean=227.45 ms`
  - `R->peak mean=406.92 ms`
- Mainline relation check: `R->foot < R->peak` (PASS).
- Statement boundary: engineering trend validation only, not a clinical diagnosis claim.

## 3. Task8 Root-cause Process
- A1 definition audit:
  - Found independent nearest-neighbor pairing for foot and peak.
  - No hard constraint `R < foot < peak`.
- A2 visual audit:
  - 9 figures (`sit/walk/run`, 3 each) showed frequent `R->foot > R->peak` mismatches.
- A3 rule check:
  - Total beats: 17003
  - Valid beats: 839 (4.93%)
  - Dominant failure: `order_violation_R_foot_peak`.

## 4. Task8 Before/After Comparison
- A4 fix (minimal required):
  - Pairing anchor changed to first physiological post-R peak.
  - Foot redefined as pre-peak local minimum for that same beat.
  - Enforced windows + order constraints + MAD jump rejection.
- Before:
  - `R->foot mean=311.47 ms`
  - `R->peak mean=190.09 ms`
- After:
  - `R->foot mean=227.45 ms`
  - `R->peak mean=406.92 ms`
- Order check: `R->foot < R->peak` now passes on overall statistics.

## 5. Task3 Root-cause Process
- B1 label/split audit:
  - Old pipeline was record-level weak labeling.
  - Rhythm `atr.aux_note` information was not used for AF labeling.
  - Task definition was misaligned with segment-level AF screening.
- B2 dataset rebuild:
  - 30s windows, 10s step, AF-ratio labeling, patient-wise split.
- B3 minimal traditional baselines:
  - Logistic Regression, Random Forest, XGBoost.
- B4 failure analysis:
  - FP mainly from irregular non-AF windows (PVC/PAC-like confusion).
  - FN mainly from transition/borderline windows.

## 6. Task3 Before/After Comparison
- Before: `F1=0.0000`, `AUROC=0.3792`
- After (best model: XGBoost):
  - `F1=0.7359`
  - `AUROC=0.9499`
  - `Recall=0.8472`
  - `Specificity=0.8965`
- B5 stop rule:
  - Target met (`AUROC>=0.75` and `F1>=0.60`), no forced complex model stacking.

## 7. Most Reliable Current Capability Boundary
- Reliable engineering capability:
  - ECG R-peak/HRV baseline,
  - PPG peak-foot/SQI baseline,
  - ECG+PPG delay pipeline (after Task8 fix),
  - Initial AF screening baseline on public datasets.
- Not allowed to claim:
  - Clinical diagnostic performance,
  - Clinical BP estimation from PTT/PWTT.

## 8. Remaining Unresolved Items
- `ltafdb` still incomplete (49/84), long-term AF generalization still under-covered.
- `ppg_dalia` still missing due official access/import limitation.
- AF errors remain around transition windows and non-AF irregular rhythm confusion.

## 9. Suggested Wording for Proposal/Defense
- Recommended:
  - "Initial AF screening capability validated on public datasets (engineering level)."
  - "ECG+PPG delay logic audited and corrected with physiological order consistency checks."
- Required disclaimers:
  - "Not a clinical diagnostic system."
  - "Needs validation on self-collected prototype data."

## Key Artifact Index
- `outputs/reports/task8_A1_definition_audit.md`
- `outputs/reports/task8_A2_visual_audit.md`
- `outputs/reports/task8_A3_rule_check.md`
- `outputs/reports/task8_A4_fix_log.md`
- `outputs/reports/task8_A5_re_evaluation.md`
- `outputs/reports/task3_B1_label_split_audit.md`
- `outputs/reports/task3_B2_dataset_rebuild.md`
- `outputs/reports/task3_B3_baseline_models.md`
- `outputs/reports/task3_B4_failure_analysis.md`
- `outputs/reports/task3_B5_iteration_log.md`
- `outputs/reports/task3_B6_final_summary.md`
