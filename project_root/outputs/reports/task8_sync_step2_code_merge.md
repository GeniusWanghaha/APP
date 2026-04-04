# Task8 Sync Step2 Code Merge

## 检查范围
- `src/iteration_controller.py`
- `src/evaluation/task8_ptt.py`
- `src/features/ptt.py`
- 全仓库 `task8` / `run_task8` / `compute_delays_ms` / `pair_delays_ms_constrained` 引用扫描

## 结论
- 主流水线已唯一使用修复后路径：
  - `src/iteration_controller.py` -> `from src.evaluation.task8_ptt import run_task8`
  - `src/evaluation/task8_ptt.py` -> `from src.features.ptt import pair_delays_ms_constrained`
- 未发现并行旧版 `task8` runner、旧别名导入或旧 wrapper 被主入口调用。
- `src/features/ptt.py` 中的 `compute_delays_ms(...)` 仍存在，但当前仅为历史兼容函数，未被 `src/` 内任何主流程代码调用。

## 本步改动
- 本步**未修改代码**（0 处代码变更）。
- 原因：主调用链已指向修复后实现，不存在“专项用新逻辑、主流程仍走旧逻辑”的分叉风险。

## 风险说明
- 风险不在代码入口，而在主输出快照尚未刷新（`best_metrics.json` 与主报告/主图仍含旧 Task8 指标）。
- 下一步将执行最小必要重跑与聚合刷新，解决主输出污染问题。
