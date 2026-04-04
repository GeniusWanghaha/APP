# PROJECT_MASTER_REPORT

- 生成时间: 2026-04-03 15:38:46
- 审计范围: `D:/optoelectronic_design/APP/project_root` 全量代码与产物

## 第1章 项目概览
- 项目目标：构建手机壳式 ECG+PPG 算法验证与工程化评估流水线，覆盖 Task1~Task9。
- 硬件形态映射：ESP32-S3 + AD8232(单导联ECG) + MAX30102(PPG) + 手机端算法。
- 当前工程边界：公共数据集工程验证为主，临床级结论不成立。
- 已完成：数据下载/清单/loader/9个任务评估框架/专项修复(Task8+Task3)/图表输出。
- 未完成：ppg_dalia缺失、ltafdb不完整、Task3专项尚未完全并回主线聚合。

## 第2章 项目目录与代码结构说明
- 主入口：`src/run_pipeline.py`
- 迭代控制：`src/iteration_controller.py`
- 任务实现：`src/evaluation/task1~task9_*.py`
- 特征与预处理：`src/preprocessing/*`, `src/features/*`, `src/models/af_baseline.py`
- 报告与图表：`src/reports/writers.py`, `src/reports/figures.py`
- 数据资产：`datasets/raw|normalized|manifests|logs`

## 第3章 数据资产总览
详表见 `outputs/tables/dataset_overview_master.csv`。

### mitdb
- 状态: ok
- 模态: ECG
- 任务映射: ECG_RPEAK,ECG_HRV,ECG_ARRHYTHMIA
- 记录数: 48 / 期望 48
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### afdb
- 状态: ok
- 模态: ECG
- 任务映射: ECG_AF,ECG_HRV
- 记录数: 25 / 期望 25
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### ltafdb
- 状态: incomplete
- 模态: ECG
- 任务映射: ECG_AF
- 记录数: 49 / 期望 84
- 是否参与主评估: True
- 说明: record_count=49 < expected_record_count=84 from RECORDS

### qtdb
- 状态: ok
- 模态: ECG
- 任务映射: ECG_QT_PWAVE
- 记录数: 105 / 期望 105
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### nsrdb
- 状态: ok
- 模态: ECG
- 任务映射: ECG_RPEAK,ECG_HRV
- 记录数: 18 / 期望 18
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### nstdb
- 状态: ok
- 模态: ECG
- 任务映射: ECG_RPEAK,ECG_ARRHYTHMIA
- 记录数: 15 / 期望 15
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### but_pdb
- 状态: ok
- 模态: ECG
- 任务映射: ECG_QT_PWAVE
- 记录数: 50 / 期望 50
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### but_ppg
- 状态: ok
- 模态: PPG
- 任务映射: PPG_HR,PPG_SQI
- 记录数: 96 / 期望 96
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### bidmc
- 状态: ok
- 模态: ECG+PPG+RESP
- 任务映射: PPG_RESP,ECG_RESP,PPG_HR
- 记录数: 53 / 期望 53
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### ptt_ppg
- 状态: ok
- 模态: ECG+PPG
- 任务映射: ECG_PPG_SYNC,ECG_PPG_PTT
- 记录数: 66 / 期望 66
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

### ppg_dalia
- 状态: missing
- 模态: PPG+ACC
- 任务映射: PPG_HR,PPG_MOTION,PPG_SQI
- 记录数: 0 / 期望 0
- 是否参与主评估: True
- 说明: 原始数据目录为空/未成功获取

### apnea_ecg
- 状态: ok
- 模态: ECG+RESP
- 任务映射: ECG_RESP
- 记录数: 86 / 期望 86
- 是否参与主评估: True
- 说明: 已参与或可参与当前评估

## 第4章 各任务详细算法说明
任务-算法-代码映射详见 `outputs/reports/project_task_algorithm_map.md` 与 `outputs/tables/project_task_algorithm_map.csv`。

### Task1 ECG R峰检测
- 输入信号与数据集：mitdb, nsrdb, nstdb。
- 预处理/检测逻辑：经典 Pan-Tompkins(neurokit) + 自适应导数平方积分阈值检测。
- 关键代码：src/evaluation/task1_ecg_rpeak.py | src/preprocessing/ecg.py。
- 关键函数：run_task1 | detect_rpeaks_classic | detect_rpeaks_adaptive。
- 标签与评估：WFDB atr/qrs 注释中的参考R峰；Sensitivity/PPV/F1/定位误差。
- 切分策略：record-wise (每库前N条)。
- 当前结果：adaptive F1=0.9713, loc_err=7.40ms。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=stable。

### Task2 HR/HRV
- 输入信号与数据集：mitdb, nsrdb, afdb。
- 预处理/检测逻辑：R峰->RR间期->HRV统计(含离群RR清洗)。
- 关键代码：src/evaluation/task2_hrv.py | src/features/hrv.py。
- 关键函数：run_task2 | rr_intervals_ms | hrv_features | remove_outlier_rr。
- 标签与评估：无显式分类标签，统计型任务；mean HR/mean RR/SDNN/RMSSD/pNN50。
- 切分策略：record-wise aggregate。
- 当前结果：流程可稳定产出，指标区间合理。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=stable。

### Task3 AF筛查(主线)
- 输入信号与数据集：afdb, ltafdb, mitdb。
- 预处理/检测逻辑：弱标签 + RR特征 + RandomForest(GroupKFold)。
- 关键代码：src/evaluation/task3_af.py | src/models/af_baseline.py | src/features/af_features.py。
- 关键函数：run_task3 | build_af_samples | train_eval_af_baseline。
- 标签与评估：旧版 weak label(symbol only)；Precision/Recall/F1/Specificity/AUROC。
- 切分策略：GroupKFold(record/subject proxy)。
- 当前结果：F1=0.0000, AUROC=0.3792。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=needs work (主线未并回专项改进)。

### Task3 AF筛查(专项当前最优)
- 输入信号与数据集：afdb, ltafdb(49/84), mitdb。
- 预处理/检测逻辑：30s窗+10s步长 + AF比例标签 + RR不规则特征 + XGBoost。
- 关键代码：专项脚本/报告产物（不在主线 run_pipeline 调用）。
- 关键函数：见 task3_B* 专项产物。
- 标签与评估：AF ratio>=0.6; <=0.1 non-AF; 中间丢弃；Patient-wise test + AUROC/F1/CM。
- 切分策略：patient-wise split。
- 当前结果：F1=0.7359, AUROC=0.9499, Recall=0.8472, Specificity=0.8965。
- 结果来源：outputs/tables/task3_baseline_model_compare.csv | outputs/reports/task3_B6_final_summary.md。
- 局限：状态=provisional (专项有效，尚未并入主线best_metrics)。

### Task4 异常搏提示
- 输入信号与数据集：mitdb。
- 预处理/检测逻辑：RR偏离中位数阈值启发式 + 符号标签对比。
- 关键代码：src/evaluation/task4_arrhythmia.py。
- 关键函数：run_task4。
- 标签与评估：atr symbol 正常/异常二值映射；Precision/Recall/F1。
- 切分策略：record-wise。
- 当前结果：F1=0.6582, Recall=0.8901。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=provisional。

### Task5 P波/QT流程
- 输入信号与数据集：qtdb, but_pdb。
- 预处理/检测逻辑：R峰SQI门控 + NeuroKit2 ecg_delineate(dwt)。
- 关键代码：src/evaluation/task5_ecg_qt_pwave.py | src/preprocessing/ecg.py。
- 关键函数：run_task5 | rpeak_sqi。
- 标签与评估：流程级成功率，无统一点级GT误差；delineation_success_rate + mean_sqi。
- 切分策略：record-wise。
- 当前结果：success_rate=1.0000, mean_sqi=0.8618。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=provisional。

### Task6 PPG峰足/SQI/HR
- 输入信号与数据集：but_ppg, ppg_dalia, bidmc (ppg_dalia当前缺失)。
- 预处理/检测逻辑：PPG去趋势+带通+峰足检测 + 多因子SQI + 峰足HR自一致代理误差。
- 关键代码：src/evaluation/task6_ppg.py | src/preprocessing/ppg.py。
- 关键函数：run_task6 | detect_ppg_peaks | detect_ppg_foots | estimate_ppg_sqi。
- 标签与评估：无统一HR真值时采用proxy误差；HR proxy MAE/SQI均值/峰足成功率。
- 切分策略：record-wise。
- 当前结果：hr_proxy_mae=0.4352 bpm, sqi_mean=0.8378。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=stable(工程基线)。

### Task7 呼吸率估计
- 输入信号与数据集：bidmc, apnea_ecg。
- 预处理/检测逻辑：EDR(R幅调制) + PDR(PPG幅度调制) + Welch主频。
- 关键代码：src/evaluation/task7_resp.py | src/features/respiration.py。
- 关键函数：run_task7 | edr_from_rpeak_amplitude | pdr_from_ppg_amplitude | estimate_resp_rate_from_series。
- 标签与评估：resp通道参考呼吸率；ECG-derived/PPG-derived MAE。
- 切分策略：record-wise。
- 当前结果：ECG MAE=2.84 bpm, PPG MAE=4.70 bpm。
- 结果来源：outputs/metrics/best_metrics.json。
- 局限：状态=stable(工程趋势)。

### Task8 ECG+PPG时序/PTT
- 输入信号与数据集：ptt_ppg。
- 预处理/检测逻辑：生理约束配对：先R后峰、峰前足点、窗口约束 + MAD跳变剔除。
- 关键代码：src/evaluation/task8_ptt.py | src/features/ptt.py。
- 关键函数：run_task8 | pair_delays_ms_constrained | _derive_feet_before_peaks。
- 标签与评估：无临床标签，时延统计任务；R->foot / R->peak 均值中位数方差 + rule validity。
- 切分策略：subject/scene统计。
- 当前结果：R->foot=227.45 ms, R->peak=406.92 ms。
- 结果来源：outputs/metrics/best_metrics.json | outputs/reports/task8_A5_re_evaluation.md。
- 局限：状态=stable(已修复并回主线)。

### Task9 回归门控
- 输入信号与数据集：跨任务聚合。
- 预处理/检测逻辑：关键指标回退检测(task1_f1 + task6_sqi)。
- 关键代码：src/evaluation/task9_regression.py | src/evaluation/regression_suite.py。
- 关键函数：run_task9 | compare_rounds。
- 标签与评估：无；regression_flags。
- 切分策略：迭代轮次。
- 当前结果：regression_flags=[]。
- 结果来源：outputs/metrics/best_metrics.json | outputs/metrics/iteration_round_logs.csv。
- 局限：状态=stable。

## 第5章 当前最佳结果总表
详表见 `outputs/tables/master_best_results_table.csv`。

| 任务 | 指标 | 当前最优值 | 状态 | 可用于正式方案/答辩 |
| --- | --- | --- | --- | --- |
| Task1 ECG R峰 | F1(adaptive) | 0.9713 | stable | yes |
| Task2 HRV | RMSSD(clean, ms) | 91.67 | stable | yes |
| Task3 AF(主线) | F1 | 0.0000 | needs work | no |
| Task3 AF(专项最优) | F1 / AUROC | 0.7359 / 0.9499 | provisional | yes_with_disclaimer |
| Task4 异常搏 | F1 | 0.6582 | provisional | yes_with_disclaimer |
| Task5 QT/P波 | delineation_success_rate | 1.0000 | provisional | yes_with_disclaimer |
| Task6 PPG | HR proxy MAE(bpm) | 0.4352 | stable | yes |
| Task7 呼吸率 | ECG MAE / PPG MAE(bpm) | 2.84 / 4.70 | stable(趋势) | yes_with_disclaimer |
| Task8 ECG+PPG时序 | R->foot / R->peak mean(ms) | 227.45 / 406.92 | stable | yes_with_disclaimer |
| Task9 回归门控 | regression_flags | [] | stable | yes |

## 第6章 图表与证据索引
- 详见 `outputs/tables/figure_table_index.csv` 与 `outputs/reports/figure_table_index.md`。

## 第7章 历史迭代轨迹
- 详见 `outputs/reports/project_iteration_history.md`。

## 第8章 遗留文件与清理建议
- 详见 `outputs/reports/file_cleanup_audit.md` 与 `outputs/tables/file_cleanup_candidates.csv`。

## 第9章 安全删除执行记录
- 详见 `outputs/tables/file_cleanup_delete_list.csv` 与 `outputs/reports/file_cleanup_execution_log.md`。

## 第10章 当前能力边界与正式表述建议
- 可稳定表述：Task1/2/6/8 的工程能力；Task3可表述为“初步AF筛查”但必须加非临床声明。
- 不可夸大：无袖带血压精度、临床诊断、QT/P波临床级时程精度。
- Task8 的 R->foot/R->peak 是生理时延特征，不是同步误差。
- Task5 success_rate=1.0 仅表示流程可跑通，不代表临床精度已验证。

## 关键冲突与过时项审计结论
- `outputs/metrics/best_metrics.json` 中 Task8 已是修复后新值（227.45 / 406.92）。
- 主线 Task3 仍是旧弱标签结果（F1=0/AUROC=0.3792），专项改进结果保存在 task3_B* 与对应CSV。
- `01_baseline_report.md`、`03_final_validation_report.md` 对 Task3 仍引用主线旧值，答辩时应同时给出专项口径并声明并回状态。