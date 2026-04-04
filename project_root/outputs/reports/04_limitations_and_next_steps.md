# 04 Limitations And Next Steps

## Current Limitations
- Public dataset evaluation does not equal clinical validation.
- PTT/PWTT analysis is for engineering trend/stability only.
- Some datasets may require manual approval/download despite official URLs.

## Datasets Requiring Follow-up
- ltafdb: status=incomplete, note=record_count=49 < expected_record_count=84 from RECORDS
- ppg_dalia: status=missing, note=no files found under raw directory

## Next Steps
1. Complete remaining phase datasets.
2. Run strict subject-wise evaluation for AF/arrhythmia tasks.
3. Validate transfer on self-collected ESP32-S3 hardware sessions.