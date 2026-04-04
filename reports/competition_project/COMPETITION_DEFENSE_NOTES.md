# COMPETITION_DEFENSE_NOTES

## 推荐讲法
1. 先讲全系统三端协同，再讲任务结果，最后讲真实性边界。
2. 强调数据集验证是子系统，不能代表项目全部。
3. 强调软件端是正式系统组件，不是演示壳层。

## 必讲指标
- Task1 F1=0.9713
- Task6 SQI=0.8378
- Task8 R->foot=227.45ms, R->peak=406.92ms
- Task3 主线F1=0.0000 vs 专项F1=0.7359

## 推荐重点图
1. project_root/outputs/figures/ecg_filter_rpeak_example.png
2. project_root/outputs/figures/rpeak_detection_example.png
3. project_root/outputs/figures/task3_baseline_roc.png
4. project_root/outputs/figures/task3_baseline_pr.png
5. project_root/outputs/figures/ppg_peak_foot_example.png
6. project_root/outputs/figures/task8_delay_distribution_before_after.png
7. project_root/outputs/figures/task8_subjectwise_delay_boxplot.png

## 容易被问的问题
1. Q: 是不是只跑了数据集？A: 不是，数据集验证只是算法子系统。
2. Q: Task8 延时是不是同步误差？A: 不是，是生理时延特征。
3. Q: AF 能做诊断吗？A: 当前仅工程级初筛提示。
4. Q: QT/P波是否临床验证完成？A: 仅流程跑通，临床精度未完成验证。
