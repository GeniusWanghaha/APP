# consistency_audit

生成时间：2026-04-04
扫描根目录：`D:\optoelectronic_design\APP`

## 0. 审计范围（全项目）
- 硬件端：`OPD/`
- 软件端：`android_project/`
- 数据集验证端：`project_root/`
- 项目报告目录：`reports/competition_project/`

本次审计按“完整比赛项目”执行，不把 `project_root` 视为唯一主体。

## 1. 已同步（代码/指标/文档一致）

### 1.1 硬件端与软件端协议一致
- 固件协议定义：
  - `OPD/main/app_config.h`
  - `OPD/main/packet_protocol.h`
- 手机端解析实现：
  - `android_project/app/src/main/java/com/photosentinel/health/data/repository/ble/BlePacketProtocol.kt`
- 已核对一致项：
  - protocol version：`0x01`
  - message type：`0xA1`
  - frame payload bytes：`132`
  - segment header bytes：`8`
  - ECG samples/frame：`10`
  - PPG samples/frame：`16`
- 结论：硬件封包与手机端解包参数一致，协议层无明显口径漂移。

### 1.2 验证端主线指标与主报告一致（Task1/Task6/Task8）
- 指标源：`project_root/outputs/metrics/best_metrics.json`
- 同步文档：
  - `project_root/outputs/reports/01_baseline_report.md`
  - `project_root/outputs/reports/03_final_validation_report.md`
- 关键值：
  - Task1 F1(adaptive)=`0.97130765368338`
  - Task6 sqi_mean=`0.837774846652732`
  - Task8 R->foot=`227.45376712328778` ms, R->peak=`406.9212328767125` ms

### 1.3 Kotlin-Python 数值对齐链路有结果文件
- 文档：`android_project/docs/Kotlin与Python算法对齐记录.md`
- 结果文件：
  - `project_root/outputs/metrics/kotlin_python_numeric_parity_summary.json`
  - `project_root/outputs/tables/kotlin_python_numeric_parity.csv`
  - `project_root/outputs/reports/kotlin_python_numeric_parity.md`
- 结论：对齐产物存在，链路可追溯。

## 2. 未同步/冲突项（需显式管理）

### 2.1 Task3 主线口径 vs 专项口径冲突（核心）
- 主线（`best_metrics.json`）：`F1=0.0000`, `AUROC=0.379166...`
- 专项（`outputs/tables/task3_baseline_model_compare.csv`）：`F1=0.7359`, `AUROC=0.9499`
- 影响：
  - 自动主报告（`01_baseline_report.md`、`03_final_validation_report.md`）仅体现主线旧口径。
  - 比赛报告若不显式标注“双口径”，会出现“代码与结论认知冲突”。

### 2.2 图表命名双轨并存
- 自动图：`af_roc_curve.png` / `af_pr_curve.png` / `ptt_pwtt_delay_distribution.png`
- 专项图：`task3_baseline_roc.png` / `task3_baseline_pr.png` / `task8_delay_distribution_before_after.png`
- 影响：README/答辩稿引用时易混用口径。

## 3. 旧值残留/旧口径残留
- Task8 旧值（313.916 / 189.231）仍存在于过程日志：
  - `project_root/outputs/reports/task8_sync_step3_refresh_log.md`
- 说明：该文件属于修复审计证据，可保留，但不应作为当前主结论来源。

## 4. 报告与代码一致性评估
- 协议代码层（OPD <-> Android）一致。
- Task8 主线结果（代码 -> 指标 -> 主报告）一致。
- Task3 存在“主线未并回专项”的结构性不一致，属于当前项目状态而非计算错误。

## 5. 文件处置建议

### 5.1 应更新
- `reports/competition_project/COMPETITION_PROJECT_REPORT.md`（后续版本建议加“Task3 主线/专项并回状态”独立小节，避免评委误读）。
- `project_root/outputs/reports/figure_table_index.md`（建议在表中新增“主线图/专项图”标识列）。

### 5.2 应归档（保留）
- `project_root/outputs/reports/task8_sync_step*.md`
- `project_root/outputs/reports/task8_task3_step1_context.md`
- `project_root/outputs/reports/task3_B*.md`

### 5.3 已删除（本次清理执行）
- `project_root/consistency_audit.md`（重复）
- `project_root/cleanup_log.md`（重复）
- `project_root/reports/competition_project/`（重复）
- `android_project/app/build/`（构建缓存）
- `android_project/docs/_docx_extract_v11_ui/`（docx 提取中间目录）
- `android_project/docs/word_v11_extracted.txt`（提取快照）

## 6. Git 与数据集上传策略核查
- 已处理：
  - `project_root/.gitignore` 中已忽略 `datasets/`
  - 新增 `APP/.gitignore`，已忽略 `project_root/datasets/` 及构建缓存
- 未执行项：
  - 当前 `APP` 目录未检测到 `.git`（非 Git 仓库）
  - 因此无法执行 `git rm -r --cached project_root/datasets`
  - 待接入 Git 后执行该命令即可

## 7. 建议人工确认项
- `android_project/dist/`：可能是分发产物，是否删除需按发布策略确认。
- `OPD/sdkconfig.old`：可能是硬件调参回滚点，是否删除需硬件负责人确认。
- Task3 专项何时并回主线自动聚合（影响对外唯一口径）。
