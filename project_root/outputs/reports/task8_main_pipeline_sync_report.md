# Task8 Main Pipeline Sync Report

## 1. 本次同步目标
- 将 Task8 修复后逻辑与结果并回主流水线。
- 刷新主入口指标、主报告与主汇总图，确保主流程不再引用旧 Task8 指标。
- 仅执行受影响最小链路，不做全量任务重跑。

## 2. 主入口与依赖链
- 主入口脚本：`src/run_pipeline.py`
- 任务调度：`src/iteration_controller.py`
- Task8 主评估：`src/evaluation/task8_ptt.py`
- Task8 特征逻辑：`src/features/ptt.py`（`pair_delays_ms_constrained`）
- 主报告生成：`src/reports/writers.py`
- Task8 主汇总图生成：`src/reports/figures.py` -> `generate_ptt_distribution(...)`

## 3. 代码并回情况
- 审计结果：主调用链已唯一指向修复后 Task8 路径，无并行旧 runner。
- 本次未新增分叉入口；主流程与专项逻辑一致。

## 4. 实际刷新文件
- 指标：
  - `outputs/metrics/best_metrics.json`
  - `outputs/metrics/round_00_metrics.json`
  - `outputs/metrics/round_01_metrics.json`
  - `outputs/metrics/round_02_metrics.json`
- 报告：
  - `outputs/reports/01_baseline_report.md`
  - `outputs/reports/03_final_validation_report.md`
  - `outputs/reports/task8_task3_special_report.md`（补充主线同步状态）
  - `README.md`（同步成果口径）
- 图表与索引：
  - `outputs/figures/ptt_pwtt_delay_distribution.png`
  - `outputs/figures/figure_index.json`
- 主指标总表：
  - `outputs/tables/main_metrics_snapshot.csv`

## 5. best_metrics.json 更新状态
- 已更新为修复后 Task8 指标：
  - `r_to_foot_mean_ms = 227.45376712328778`
  - `r_to_peak_mean_ms = 406.9212328767125`
  - `evaluated_records = 66`

## 6. 主报告更新状态
- 已更新。
- `outputs/reports/03_final_validation_report.md` 新增 `Task8 Mainline Sync Status`，明确修复后顺序关系与合规口径。
- `outputs/reports/01_baseline_report.md` 已同步主线 Task8 结论提示。

## 7. 旧值污染审计结果
- 主入口文件（best_metrics / baseline / final / figure_index）未发现旧 Task8 数值残留。
- 旧值仍保留在历史专项报告与历史对照表中（用于溯源），不再作为主流程结论来源。
- 未发现“主报告未清理干净”的残留问题。

## 8. 当前主流程 Task8 最终采用指标
- `R->foot mean = 227.45 ms`
- `R->peak mean = 406.92 ms`
- 顺序关系：`R->foot < R->peak`（PASS）
- 能力定位：可用于工程趋势分析与联调验证。

## 9. 对正式文档/答辩材料建议
- 可明确表述：Task8 联合时序链路已完成规则校验与主线重评估，顺序关系恢复正常。
- 建议使用“工程趋势验证”措辞，不夸大为临床结论。
- 对 PTT/PWTT 仅陈述趋势、稳定性与相关性验证结果；临床精度需后续真实硬件人体验证。
