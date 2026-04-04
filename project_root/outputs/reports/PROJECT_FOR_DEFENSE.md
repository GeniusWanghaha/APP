# PROJECT_FOR_DEFENSE

## 建议答辩主线（可直接讲）
1. 工程可复现：数据下载、清单、loader、评估、报告全链路可追溯。
2. 核心能力：ECG R峰/HRV、PPG峰足/SQI、ECG+PPG时序。
3. 关键修复：Task8 从时序逻辑错误修到生理顺序正确。
4. AF口径：可讲“初步AF筛查基线”，并明确非临床。

## 推荐PPT图表
- outputs/figures/ecg_filter_rpeak_example.png
- outputs/figures/rpeak_detection_example.png
- outputs/figures/task3_baseline_roc.png
- outputs/figures/task3_baseline_pr.png
- outputs/figures/ppg_peak_foot_example.png
- outputs/figures/task8_delay_distribution_before_after.png
- outputs/figures/task8_subjectwise_delay_boxplot.png
- outputs/tables/master_best_results_table.csv

## 容易被问的问题与诚实回答
- 问：AF是不是临床级？答：不是，当前是工程级初步筛查。
- 问：Task8延时是不是同步误差？答：不是，是生理时延特征。
- 问：QT/P波是不是精度已验证？答：当前仅流程跑通，点级精度尚需统一验证。
- 问：为什么有Python？答：Python用于离线验证，手机端运行由Kotlin实现并做了数值对齐。