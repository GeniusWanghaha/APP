# Task8 Sync Step3 Refresh Log

## 执行原则
- 仅重跑 Task8 受影响链路，不做全量任务重跑。
- 保持其他任务指标不变，只刷新 Task8 及其主入口聚合产物。

## 本步实际执行
1. 仅重跑 Task8 评估：
   - 调用 `src.evaluation.task8_ptt.run_task8(...)`
   - 数据集：`ptt_ppg`
   - 覆盖记录数：66（全可用记录）
2. 更新主指标快照：
   - 覆盖 `outputs/metrics/best_metrics.json` 的 `task8` 区块
3. 同步主链路历史轮次快照（避免旧值污染）：
   - `outputs/metrics/round_00_metrics.json`
   - `outputs/metrics/round_01_metrics.json`
   - `outputs/metrics/round_02_metrics.json`
   - 仅替换 `task8` 区块，其它任务保持原值
4. 刷新依赖 Task8 的主输出：
   - `outputs/reports/01_baseline_report.md`
   - `outputs/reports/03_final_validation_report.md`
   - `outputs/figures/ptt_pwtt_delay_distribution.png`
   - `outputs/figures/figure_index.json`

## 指标刷新结果
- 旧值：
  - `r_to_foot_mean_ms = 313.9160377358487`
  - `r_to_peak_mean_ms = 189.23106985832916`
- 新值：
  - `r_to_foot_mean_ms = 227.45376712328778`
  - `r_to_peak_mean_ms = 406.9212328767125`
- 记录数：
  - `evaluated_records = 66`

## 快照文件
- `outputs/metrics/task8_sync_refresh_snapshot.json`（本步刷新摘要）

## 结论
- 主入口 Task8 指标已从旧值切换为修复后值。
- 主报告与主汇总图已按新 Task8 指标重建，后续主流程读取不再落到旧 Task8 数值。
