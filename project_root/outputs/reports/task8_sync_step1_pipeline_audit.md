# Task8 Sync Step1 Pipeline Audit

## 审计目标
- 定位主流水线入口、Task8 调用链、主指标与主报告生成链路。
- 明确需要刷新和可能造成旧指标污染的文件。

## 主入口脚本路径
- `src/run_pipeline.py`

## Task8 当前调用位置
- `src/iteration_controller.py`
  - `run_all_tasks(...)` 中固定调用：`src.evaluation.task8_ptt.run_task8(...)`
- `src/evaluation/task8_ptt.py`
  - 当前已直接依赖 `src.features.ptt.pair_delays_ms_constrained(...)`
- 结论：主流程 Task8 执行路径已是“评估层 -> 新版 PTT 特征逻辑”，未发现并行旧版 Task8 runner。

## best_metrics / 总报告 / 总图生成逻辑
- `best_metrics.json` 生成点：`src/run_pipeline.py`（`write_json(outputs_metrics / "best_metrics.json", best_result)`）
- 任务汇总写入点：`src/iteration_controller.py`（`run_iteration_loop(...)` 返回 `best_result`）
- 主报告生成点：`src/reports/writers.py`（由 `run_pipeline.py` 调用）
  - `outputs/reports/01_baseline_report.md`
  - `outputs/reports/03_final_validation_report.md`
- 主汇总图生成点：`src/reports/figures.py`（由 `run_pipeline.py` 调用）
  - `outputs/figures/ptt_pwtt_delay_distribution.png`
  - `outputs/figures/figure_index.json`

## 需要刷新的主入口产物
- `outputs/metrics/best_metrics.json`
- `outputs/reports/01_baseline_report.md`
- `outputs/reports/03_final_validation_report.md`
- `outputs/figures/ptt_pwtt_delay_distribution.png`
- `outputs/figures/figure_index.json`
- （如存在主汇总表使用 best metrics 的派生文件）`outputs/tables/*summary*.csv`（本次会检索并按命中刷新）

## 可能造成旧指标污染的文件
- `outputs/metrics/round_00_metrics.json`、`outputs/metrics/round_01_metrics.json`、`outputs/metrics/round_02_metrics.json`
  - 这些是历史轮次快照，可能仍保存旧 Task8 值。
- `outputs/reports/03_final_validation_report.md`
  - 文件内嵌整段 `best_result` JSON，若未重写会保留旧值。
- `outputs/reports/01_baseline_report.md`
  - 直接由 `best_result` 展开，若未刷新会保留旧值。
- `outputs/reports/task8_task3_special_report.md`、`outputs/reports/iteration_log_task8_task3.md`
  - 历史专项文档内可保留旧值用于对比，但不应再作为主入口结论来源。

## 缓存/拼接风险结论
- 未发现额外缓存层；主入口结论以 `best_metrics.json` 为核心源。
- 主要风险不是“代码未切换”，而是“主入口聚合产物未刷新”。
- 下一步重点：最小链路重算 Task8 并回写主指标，再重建主报告和主汇总图。
