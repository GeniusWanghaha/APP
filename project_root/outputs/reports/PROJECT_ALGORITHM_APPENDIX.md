# PROJECT_ALGORITHM_APPENDIX

## Task1
- 文件: src/preprocessing/ecg.py, src/evaluation/task1_ecg_rpeak.py
- 函数: preprocess_ecg, detect_rpeaks_classic, detect_rpeaks_adaptive
- 参数: highpass=0.5Hz, notch=50Hz, bandpass=0.5-40Hz, tolerance=100ms

## Task2
- 文件: src/features/hrv.py, src/evaluation/task2_hrv.py
- 函数: rr_intervals_ms, hrv_features, remove_outlier_rr

## Task3
- 文件: src/models/af_baseline.py, src/features/af_features.py
- 函数: weak_label_from_annotations, build_af_samples, train_eval_af_baseline

## Task4
- 文件: src/evaluation/task4_arrhythmia.py
- 逻辑: RR偏离中位数20%判定异常

## Task5
- 文件: src/evaluation/task5_ecg_qt_pwave.py
- 函数: nk.ecg_delineate(method='dwt'), rpeak_sqi

## Task6
- 文件: src/preprocessing/ppg.py, src/evaluation/task6_ppg.py
- 峰检测: distance=0.35s, prominence=0.2*std
- 足点检测: 在 -ppg 上峰值检测，prominence=0.1*std

## Task7
- 文件: src/features/respiration.py, src/evaluation/task7_resp.py
- 方法: EDR/PDR幅度调制 + Welch主频

## Task8
- 文件: src/features/ptt.py, src/evaluation/task8_ptt.py
- 核心: pair_delays_ms_constrained, 强制 R<foot<peak + 窗口约束 + MAD

## Task9
- 文件: src/evaluation/task9_regression.py, src/evaluation/regression_suite.py
- 规则: 关键指标回退触发拦截