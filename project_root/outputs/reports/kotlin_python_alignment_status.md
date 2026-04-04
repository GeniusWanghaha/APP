# Kotlin-Python 指标一致性状态（本轮）

更新时间：2026-04-03

## 1) 共享夹具数值对齐（已完成）
- Kotlin 导出：`outputs/metrics/kotlin_parity_metrics.csv`
- Python 对比：`src/utils/compare_kotlin_python_numeric_parity.py`
- 对齐明细：`outputs/tables/kotlin_python_numeric_parity.csv`
- 汇总：`outputs/metrics/kotlin_python_numeric_parity_summary.json`

本轮结果：共享夹具下 **10/10 指标数值一致**（公差 `abs_diff <= 1e-9`）。

覆盖指标：
- mean_rr_ms
- mean_hr_bpm
- sdnn_ms
- rmssd_ms
- pnn50_percent
- ptt_mean_ms
- pat_mean_ms
- rise_time_mean_ms
- beat_pulse_consistency
- arrhythmia_index

## 2) 全量能力映射复核（与公开数据集验证结果结合）
- 已对齐（定义/规则层）：
  - HR / RR / SDNN / RMSSD / pNN50
  - Task8 时序规则（R→foot、R→peak、同搏动配对、跳变门控）
- 部分不一致：
  - SQI（Python 与 Kotlin 都是 0~1 评分，但特征构成不同）
- Python-only：
  - AF 完整分类流水线
  - 呼吸率估计（EDR/PDR）
- Kotlin-only：
  - SpO2 运行时实现
  - PWV/弹性分/血管年龄/反射指数等运行时派生指标

## 3) 结论
1. “能直接对齐”的核心指标已实现公式级数值一致，可用于你当前的手机端主链路。
2. 目前不存在“Python 能跑、Kotlin 不能跑”的核心数学矛盾；关键差异集中在任务覆盖范围。
3. 若要进一步达到“全链路逐样本一致”，下一步需要做同一原始波形回放下的双端逐拍比对（不仅是夹具公式）。
