# Step1 Context Recheck

## Dataset Status Snapshot

| dataset | status | record_count | expected_record_count | note |
| --- | --- | --- | --- | --- |
| mitdb | ok | 48 | 48 |  |
| afdb | ok | 25 | 25 |  |
| ltafdb | incomplete | 49 | 84 | record_count=49 < expected_record_count=84 from RECORDS |
| qtdb | ok | 105 | 105 |  |
| nsrdb | ok | 18 | 18 |  |
| nstdb | ok | 15 | 15 |  |
| but_pdb | ok | 50 | 50 |  |
| but_ppg | ok | 96 | 96 |  |
| bidmc | ok | 53 | 53 |  |
| ptt_ppg | ok | 66 | 66 |  |
| ppg_dalia | missing | 0 | 0 | no files found under raw directory |
| apnea_ecg | ok | 86 | 86 |  |

## Metric Snapshot

- Task1 adaptive F1: 0.9713
- Task1 adaptive Sensitivity: 0.9482
- Task1 adaptive PPV: 0.9955
- Task1 adaptive localization error (ms): 7.40
- Task3 F1: 0.0000, AUROC: 0.3792
- Task8 R->foot mean (ms): 313.92
- Task8 R->peak mean (ms): 189.23

## Immediate Focus
- ??A: Task8 ????/?????????
- ??B: Task3 ??/??/?????????????