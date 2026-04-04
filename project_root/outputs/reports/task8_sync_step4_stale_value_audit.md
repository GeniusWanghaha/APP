# Task8 Sync Step4 Stale Value Audit

## 审计范围与方法
- 递归检索目录：
  - `outputs/`
  - `outputs/reports/`
  - `outputs/tables/`
  - `outputs/metrics/`
- 重点检索旧值：
  - `311.47`
  - `190.09`
  - 旧语义关键词：`R->foot` / `R->peak`
- 同步检索新值传播：
  - `227.45`（含高精度 `227.45376712328778`）
  - `406.92`（含高精度 `406.9212328767125`）

## 主入口文件旧值检查结果
- 检查文件：
  - `outputs/metrics/best_metrics.json`
  - `outputs/reports/01_baseline_report.md`
  - `outputs/reports/03_final_validation_report.md`
  - `outputs/figures/figure_index.json`
- 结果：
  - 未命中旧值（`311.47` / `190.09` / 旧 Task8 均值高精度）
  - 新值已命中并一致：
    - `r_to_foot_mean_ms = 227.45376712328778`
    - `r_to_peak_mean_ms = 406.9212328767125`

## 全量命中分类
### A. 允许保留（历史专项/审计材料）
- `outputs/reports/task8_A5_re_evaluation.md`（包含 before/after 对照）
- `outputs/reports/task8_task3_special_report.md`（包含修复前后对照）
- `outputs/reports/task8_task3_step1_context.md`（专项上下文快照）
- `outputs/tables/task8_recomputed_metrics.csv`（包含 before/after 统计行）
- `outputs/reports/task8_sync_step4_stale_value_audit.md`（本审计文档自身包含检索模式与命中说明）

### B. 与 Task8 无关的数值碰撞（非污染）
- `outputs/tables/task3_feature_matrix.csv`
  - 命中的 `190.09*` 来自 Task3 特征值数值片段，不是 Task8 指标字段。

### C. 主入口残留（应清理）
- 未发现。

## 结论（按要求明确）
1. 旧值已从主入口清除：
   - 是，`best_metrics` / 主 baseline / 主 final report / 主 figure index 均无旧 Task8 指标。
2. 旧值保留位置：
   - 仅存在于历史专项报告与历史对照表（用于审计溯源），不再作为主流程结论来源。
3. 主报告是否仍有未清理残留：
   - 否，主入口文档已统一为修复后 Task8 指标。
