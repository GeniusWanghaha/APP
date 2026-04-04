# 项目任务-算法映射

## 映射A：任务 -> 算法 -> 代码文件

| 任务 | 算法 | 关键函数 | 代码文件 |
| --- | --- | --- | --- |
| Task1 ECG R峰检测 | 经典 Pan-Tompkins(neurokit) + 自适应导数平方积分阈值检测 | run_task1 | detect_rpeaks_classic | detect_rpeaks_adaptive | src/evaluation/task1_ecg_rpeak.py | src/preprocessing/ecg.py |
| Task2 HR/HRV | R峰->RR间期->HRV统计(含离群RR清洗) | run_task2 | rr_intervals_ms | hrv_features | remove_outlier_rr | src/evaluation/task2_hrv.py | src/features/hrv.py |
| Task3 AF筛查(主线) | 弱标签 + RR特征 + RandomForest(GroupKFold) | run_task3 | build_af_samples | train_eval_af_baseline | src/evaluation/task3_af.py | src/models/af_baseline.py | src/features/af_features.py |
| Task3 AF筛查(专项当前最优) | 30s窗+10s步长 + AF比例标签 + RR不规则特征 + XGBoost | 见 task3_B* 专项产物 | 专项脚本/报告产物（不在主线 run_pipeline 调用） |
| Task4 异常搏提示 | RR偏离中位数阈值启发式 + 符号标签对比 | run_task4 | src/evaluation/task4_arrhythmia.py |
| Task5 P波/QT流程 | R峰SQI门控 + NeuroKit2 ecg_delineate(dwt) | run_task5 | rpeak_sqi | src/evaluation/task5_ecg_qt_pwave.py | src/preprocessing/ecg.py |
| Task6 PPG峰足/SQI/HR | PPG去趋势+带通+峰足检测 + 多因子SQI + 峰足HR自一致代理误差 | run_task6 | detect_ppg_peaks | detect_ppg_foots | estimate_ppg_sqi | src/evaluation/task6_ppg.py | src/preprocessing/ppg.py |
| Task7 呼吸率估计 | EDR(R幅调制) + PDR(PPG幅度调制) + Welch主频 | run_task7 | edr_from_rpeak_amplitude | pdr_from_ppg_amplitude | estimate_resp_rate_from_series | src/evaluation/task7_resp.py | src/features/respiration.py |
| Task8 ECG+PPG时序/PTT | 生理约束配对：先R后峰、峰前足点、窗口约束 + MAD跳变剔除 | run_task8 | pair_delays_ms_constrained | _derive_feet_before_peaks | src/evaluation/task8_ptt.py | src/features/ptt.py |
| Task9 回归门控 | 关键指标回退检测(task1_f1 + task6_sqi) | run_task9 | compare_rounds | src/evaluation/task9_regression.py | src/evaluation/regression_suite.py |

## 映射B：任务 -> 数据集 -> 标签 -> 评估

| 任务 | 数据集 | 标签/任务定义 | 评估方式 | 切分 |
| --- | --- | --- | --- | --- |
| Task1 ECG R峰检测 | mitdb, nsrdb, nstdb | WFDB atr/qrs 注释中的参考R峰 | Sensitivity/PPV/F1/定位误差 | record-wise (每库前N条) |
| Task2 HR/HRV | mitdb, nsrdb, afdb | 无显式分类标签，统计型任务 | mean HR/mean RR/SDNN/RMSSD/pNN50 | record-wise aggregate |
| Task3 AF筛查(主线) | afdb, ltafdb, mitdb | 旧版 weak label(symbol only) | Precision/Recall/F1/Specificity/AUROC | GroupKFold(record/subject proxy) |
| Task3 AF筛查(专项当前最优) | afdb, ltafdb(49/84), mitdb | AF ratio>=0.6; <=0.1 non-AF; 中间丢弃 | Patient-wise test + AUROC/F1/CM | patient-wise split |
| Task4 异常搏提示 | mitdb | atr symbol 正常/异常二值映射 | Precision/Recall/F1 | record-wise |
| Task5 P波/QT流程 | qtdb, but_pdb | 流程级成功率，无统一点级GT误差 | delineation_success_rate + mean_sqi | record-wise |
| Task6 PPG峰足/SQI/HR | but_ppg, ppg_dalia, bidmc (ppg_dalia当前缺失) | 无统一HR真值时采用proxy误差 | HR proxy MAE/SQI均值/峰足成功率 | record-wise |
| Task7 呼吸率估计 | bidmc, apnea_ecg | resp通道参考呼吸率 | ECG-derived/PPG-derived MAE | record-wise |
| Task8 ECG+PPG时序/PTT | ptt_ppg | 无临床标签，时延统计任务 | R->foot / R->peak 均值中位数方差 + rule validity | subject/scene统计 |
| Task9 回归门控 | 跨任务聚合 | 无 | regression_flags | 迭代轮次 |

## 映射C：任务 -> 最佳结果 -> 产出文件

| 任务 | 当前最佳结果 | 结果来源 | 状态 |
| --- | --- | --- | --- |
| Task1 ECG R峰检测 | adaptive F1=0.9713, loc_err=7.40ms | outputs/metrics/best_metrics.json | stable |
| Task2 HR/HRV | 流程可稳定产出，指标区间合理 | outputs/metrics/best_metrics.json | stable |
| Task3 AF筛查(主线) | F1=0.0000, AUROC=0.3792 | outputs/metrics/best_metrics.json | needs work (主线未并回专项改进) |
| Task3 AF筛查(专项当前最优) | F1=0.7359, AUROC=0.9499, Recall=0.8472, Specificity=0.8965 | outputs/tables/task3_baseline_model_compare.csv | outputs/reports/task3_B6_final_summary.md | provisional (专项有效，尚未并入主线best_metrics) |
| Task4 异常搏提示 | F1=0.6582, Recall=0.8901 | outputs/metrics/best_metrics.json | provisional |
| Task5 P波/QT流程 | success_rate=1.0000, mean_sqi=0.8618 | outputs/metrics/best_metrics.json | provisional |
| Task6 PPG峰足/SQI/HR | hr_proxy_mae=0.4352 bpm, sqi_mean=0.8378 | outputs/metrics/best_metrics.json | stable(工程基线) |
| Task7 呼吸率估计 | ECG MAE=2.84 bpm, PPG MAE=4.70 bpm | outputs/metrics/best_metrics.json | stable(工程趋势) |
| Task8 ECG+PPG时序/PTT | R->foot=227.45 ms, R->peak=406.92 ms | outputs/metrics/best_metrics.json | outputs/reports/task8_A5_re_evaluation.md | stable(已修复并回主线) |
| Task9 回归门控 | regression_flags=[] | outputs/metrics/best_metrics.json | outputs/metrics/iteration_round_logs.csv | stable |