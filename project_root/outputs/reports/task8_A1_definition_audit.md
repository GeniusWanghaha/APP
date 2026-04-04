# Task8 A1 Definition Audit

## Audited Source Files
- `src/evaluation/task8_ptt.py`
- `src/features/ptt.py`
- `src/preprocessing/ppg.py`
- `src/preprocessing/ecg.py`

## Key Functions
- `run_task8(...)` in `task8_ptt.py`
- `compute_delays_ms(...)` in `features/ptt.py`
- `detect_ppg_peaks(...)` / `detect_ppg_foots(...)` in `preprocessing/ppg.py`
- `detect_rpeaks_adaptive(...)` in `preprocessing/ecg.py`

## Current Mathematical Definitions
- R-peak: from ECG adaptive detector (`detect_rpeaks_adaptive`), after ECG preprocessing.
- PPG peak: local maxima of filtered PPG using `find_peaks` with distance/prominence constraints.
- PPG foot (current): local minima of filtered PPG by applying `find_peaks` to `-ppg` (all trough candidates).
- Delay definitions in `compute_delays_ms`:
  - `R->foot`: first foot after each R (`foot_times > r`) within `[80, 450] ms`
  - `R->peak`: first peak after each R (`peak_times > r`) within `[80, 450] ms`

## Beat Pairing Logic (Current)
1. For each R, choose the first global foot occurring after R; independently choose the first global peak after R.
2. Foot and peak are *not enforced to belong to the same pulse beat*.
3. No explicit constraint `R < foot < peak` is checked during pairing.
4. No explicit constraint on `foot->peak` positive interval.
5. No robust outlier rejection on beat-level delay jump is applied in Task8 output stage.

## Cross-Beat/Nearest-Neighbor Risk
- Current logic is equivalent to independent nearest-after-R selection for foot and peak.
- This allows cross-beat mismatch: selected foot may come from a later trough while selected peak comes from an earlier systolic pulse.
- Therefore, aggregate means can violate expected physiological order (observed: `R->foot > R->peak`).

## Time Window / Phase / Signal-Semantics Checks
- Delay window uses same `[80, 450] ms` for both foot and peak; no separate physiological windows for each.
- Filtering uses zero-phase `filtfilt`, so major phase lag is unlikely to be the primary root cause.
- No explicit PPG inversion is performed, but foot as ?global trough? does not guarantee pulse onset semantic consistency.

## Top-3 Suspicious Points
1. **Independent pairing bug**: `R->foot` and `R->peak` are paired independently, not tied to the same beat.
2. **Foot semantic ambiguity**: foot detector returns troughs, potentially including post-systolic minima from previous/next beat cycles.
3. **No physiological ordering gate**: lack of hard rule `R < foot < peak` and no foot-peak consistency window.

## A1 Interim Conclusion
- Root-cause candidate is primarily pairing definition, then foot semantic definition.
- Next (A2/A3) should verify this by direct beat-level visualization and rule-failure statistics.