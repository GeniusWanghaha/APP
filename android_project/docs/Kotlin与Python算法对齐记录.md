# Kotlin 与 Python 算法对齐记录

更新时间：2026-04-04

## 1. 采集与输出策略
- 手机端已按 **60 秒采集窗口 + 批处理输出** 执行。
- 最终指标发布由 `BatchCardioAnalyzer` 统一产出，不再依赖实时链路作为发布前提。

## 2. 已对齐的核心指标/逻辑
- HR/HRV：`mean_rr`、`mean_hr`、`sdnn`、`rmssd`、`pnn50`
- AF 特征：`rr_cv`、`sample_entropy`、`sd1`、`sd2`、`sd1/sd2`
- PPG 质量：`sqi`
- ECG+PPG 时序：`r_to_foot`、`r_to_peak`、`rise_time`、合法规则约束与跳变守卫
- 呼吸率：ECG-derived / PPG-derived respiration
- 波形时程：QRS / QT / QTc / P 波可靠度

## 3. 本次新增对齐项
- 新增 `rrCv`、`sd1Sd2Ratio`、`pttValidBeatRatio` 到 Kotlin 批处理结果与 UI 展示。
- `beatPulseConsistency` 改为使用 Task8 约束后的有效 `r_to_foot` 序列计算，和 Python 约束逻辑一致。

## 4. 数值一致性校验
- Kotlin 校验导出：`android_project/app/src/test/java/com/photosentinel/health/infrastructure/signal/KotlinParityExportTest.kt`
- Python 对齐脚本：`project_root/src/utils/compare_kotlin_python_numeric_parity.py`
- 结果文件：
  - `project_root/outputs/metrics/kotlin_python_numeric_parity_summary.json`
  - `project_root/outputs/tables/kotlin_python_numeric_parity.csv`
  - `project_root/outputs/reports/kotlin_python_numeric_parity.md`

当前结果：`14/14` 指标匹配，`unmatched_metrics = 0`。

## 5. 说明
- Python 侧主要用于公开数据集验证与迭代，Kotlin 侧用于手机端真实运行。
- 本对齐确保“同公式同输入”的数学一致性；真实硬件全链路一致性仍需你后续真机联调继续验证。
