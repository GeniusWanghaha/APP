# Task8 A4 Fix Log

## Trigger
- A1/A2/A3 showed systematic mismatch: most beats violate `R < foot < peak`, and previous pairing was independent nearest-after-R.

## Minimal Necessary Changes
### 1) Pairing logic refactor (highest priority)
- File: `src/features/ptt.py`
- Added `pair_delays_ms_constrained(...)` to enforce beat-consistent pairing.
- Pairing anchor changed to: **first systolic PPG peak in physiological R->peak window**.
- Foot is now derived per-peak from pre-peak local minimum window (instead of independent global trough stream).

### 2) Foot semantic correction
- File: `src/features/ptt.py`
- Added `_derive_feet_before_peaks(...)` to define foot as local minimum before each chosen peak (windowed).

### 3) Physiological hard constraints and outlier rejection
- Enforced during pairing:
  - `R->foot` in `[80, 400] ms`
  - `R->peak` in `[120, 500] ms`
  - `R < foot < peak` and `foot->peak > 0`
- Added per-record jump outlier filtering via median/MAD.

### 4) Task8 integration update
- File: `src/evaluation/task8_ptt.py`
- Replaced old `compute_delays_ms(...)` call with `pair_delays_ms_constrained(...)`.
- Added `total_beats`, `valid_beats`, `valid_ratio`, `invalid_reason_counts` in Task8 output for auditability.

## Why these changes are minimal and sufficient
- No model change, no global pipeline redesign.
- Only fixed Task8-specific semantics/pairing path that caused systematic order violations.
- Keeps ECG/PPG detectors unchanged; only pairing and beat-validity logic changed.