# 项目迭代轨迹

## 主流水线迭代
- Round 0: scale=0.8, decision=baseline, score=0.602749, improvement=0.0%
- Round 1: scale=0.7, decision=accepted, score=0.603028, improvement=0.0861062453284432%
- Round 2: scale=0.8, decision=rejected_due_to_regression, score=0.602749, improvement=-0.0860321662603018%

## 关键专项修复
- Task8: 从独立最近邻配对改为生理约束配对，修复 `R->foot < R->peak` 关系（见 task8_A1~A5）。
- Task3: 完成标签/切分重建与传统特征+XGBoost基线，专项指标显著提升（见 task3_B1~B6）。

## 回滚与保留策略
- 主线迭代中若触发 regression flag 会拒绝该轮（见 round_02）。
- 专项改进单独留痕，未自动覆盖所有主线历史报告。

## 当前最优配置来源
- Task1/2/4/5/6/7/8/9：以 `outputs/metrics/best_metrics.json` 为主。
- Task3：专项最优来源于 `outputs/tables/task3_baseline_model_compare.csv` 与 B6 报告。