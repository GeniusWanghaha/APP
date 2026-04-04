from __future__ import annotations

import json
import textwrap
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd
from matplotlib import font_manager as fm
from matplotlib.backends.backend_pdf import PdfPages


ROOT = Path("d:/optoelectronic_design/APP/project_root").resolve()
REPORTS = ROOT / "outputs" / "reports"
TABLES = ROOT / "outputs" / "tables"
METRICS = ROOT / "outputs" / "metrics"
MANIFESTS = ROOT / "datasets" / "manifests"


def choose_cjk_font() -> str | None:
    candidates = [
        "Microsoft YaHei",
        "SimHei",
        "Noto Sans CJK SC",
        "Source Han Sans CN",
    ]
    available = {f.name for f in fm.fontManager.ttflist}
    for name in candidates:
        if name in available:
            return name
    return None


def build_markdown() -> str:
    inventory = pd.read_csv(MANIFESTS / "dataset_inventory.csv")
    best = json.loads((METRICS / "best_metrics.json").read_text(encoding="utf-8"))
    task3_compare = pd.read_csv(TABLES / "task3_baseline_model_compare.csv")
    task8_recomputed = pd.read_csv(TABLES / "task8_recomputed_metrics.csv")

    status_counts = inventory["status"].value_counts().to_dict()
    ok_count = int(status_counts.get("ok", 0))
    incomplete_count = int(status_counts.get("incomplete", 0))
    missing_count = int(status_counts.get("missing", 0))

    task3_best_row = task3_compare.sort_values("f1", ascending=False).iloc[0]
    task8_after = task8_recomputed[
        (task8_recomputed["stage"] == "after") & (task8_recomputed["scope"] == "overall")
    ]
    task8_foot = float(task8_after[task8_after["metric"] == "r_to_foot_ms"]["mean"].iloc[0])
    task8_peak = float(task8_after[task8_after["metric"] == "r_to_peak_ms"]["mean"].iloc[0])

    md = f"""# 手机壳式 ECG+PPG AI 健康评估系统完整总报告

生成时间：{pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S")}
项目根目录：`d:/optoelectronic_design/APP/project_root`

## 1. 项目概览

本项目面向“ESP32-S3 + AD8232（三电极单导联 ECG）+ MAX30102（PPG）+ 手机端算法”的工程化落地，目标是构建可复现的数据验证流水线，并将可迁移算法同步到手机端 Kotlin 实现。

当前整体完成状态：
- 已完成公开数据流水线（下载、清单、标准化读取、任务评估、报告输出）。
- 已完成 Task8（ECG+PPG 时序）专项修复并并回主线。
- 已完成 Task3（AF）专项修复与基线重建（专项结果有效）。
- 手机端已改为“1分钟采集后统一出指标”的输出策略，避免实时虚假波动数据。

## 2. 数据资产与覆盖情况

数据集统计（来自 `datasets/manifests/dataset_inventory.csv`）：
- `ok`：{ok_count}
- `incomplete`：{incomplete_count}
- `missing`：{missing_count}

当前状态明细：
- 可用：mitdb、afdb、qtdb、nsrdb、nstdb、but_pdb、but_ppg、bidmc、ptt_ppg、apnea_ecg
- 不完整：ltafdb（49/84）
- 缺失：ppg_dalia

工程结论：核心 ECG/PPG/联合时序任务已具备验证数据基础，但 AF 的长期泛化和 PPG 运动场景仍建议后续补齐数据。

## 3. 任务级算法与结果总览

### Task1 ECG R峰检测
- 算法：经典 Pan-Tompkins + 自适应导数平方积分阈值检测
- 代码：`src/evaluation/task1_ecg_rpeak.py`、`src/preprocessing/ecg.py`
- 主线结果：
  - F1（adaptive）= {best['task1']['adaptive']['f1']:.4f}
  - Sensitivity = {best['task1']['adaptive']['sensitivity']:.4f}
  - PPV = {best['task1']['adaptive']['ppv']:.4f}
  - 定位误差 = {best['task1']['adaptive']['mean_abs_peak_localization_error_ms']:.2f} ms

### Task2 HR / HRV
- 算法：R峰 -> RR间期 -> HRV统计（含离群RR清洗）
- 代码：`src/evaluation/task2_hrv.py`、`src/features/hrv.py`
- 主线结果（clean）：
  - mean HR = {best['task2']['summary']['clean']['mean_hr_bpm']:.2f} bpm
  - mean RR = {best['task2']['summary']['clean']['mean_rr_ms']:.2f} ms
  - SDNN = {best['task2']['summary']['clean']['sdnn_ms']:.2f} ms
  - RMSSD = {best['task2']['summary']['clean']['rmssd_ms']:.2f} ms
  - pNN50 = {best['task2']['summary']['clean']['pnn50_percent']:.2f} %

### Task3 AF筛查（必须区分双口径）
- 主线口径（`best_metrics.json`）：
  - F1 = {best['task3']['f1']:.4f}
  - AUROC = {best['task3']['auroc']:.4f}
  - 说明：该结果不可用于正式对外展示
- 专项修复口径（`task3_baseline_model_compare.csv` 最优）：
  - 最优模型：{task3_best_row['model']}
  - F1 = {task3_best_row['f1']:.4f}
  - AUROC = {task3_best_row['auroc']:.4f}
  - Recall = {task3_best_row['recall']:.4f}
  - Specificity = {task3_best_row['specificity']:.4f}
  - 说明：可表述为“初步AF筛查能力（工程级）”，不可表述为临床诊断

### Task4 异常搏/早搏提示
- 算法：RR偏离中位数启发式 + 注释标签对比
- 主线结果：
  - F1 = {best['task4']['f1']:.4f}
  - Recall = {best['task4']['recall']:.4f}
  - Precision = {best['task4']['precision']:.4f}
- 结论：召回较高，精度仍有优化空间

### Task5 P波/QT流程
- 算法：SQI门控 + NeuroKit2 delineation
- 主线结果：
  - delineation_success_rate = {best['task5']['delineation_success_rate']:.4f}
  - mean_sqi = {best['task5']['mean_sqi']:.4f}
- 结论：流程可跑通，不等于临床级时程精度已验证

### Task6 PPG峰足/SQI/HR
- 算法：PPG去趋势+带通+峰足检测+多因素SQI
- 主线结果：
  - hr_proxy_mae = {best['task6']['hr_proxy_mae_bpm']:.4f} bpm
  - sqi_mean = {best['task6']['sqi_mean']:.4f}
  - 峰足成功率 = {best['task6']['peak_foot_success_rate']:.4f}

### Task7 ECG/PPG导出呼吸率
- 算法：EDR/PDR + PSD主频估计
- 主线结果：
  - ECG-derived respiration MAE = {best['task7']['ecg_resp_mae_bpm']:.2f} bpm
  - PPG-derived respiration MAE = {best['task7']['ppg_resp_mae_bpm']:.2f} bpm
- 结论：ECG端优于PPG端，当前适合工程趋势监测口径

### Task8 ECG+PPG联合时序/PTT
- 修复前异常：R->foot > R->peak（不符合常规生理顺序）
- 修复后（专项重评估）：
  - R->foot = {task8_foot:.2f} ms
  - R->peak = {task8_peak:.2f} ms
  - 顺序恢复：R->foot < R->peak
- 代码：`src/features/ptt.py`、`src/evaluation/task8_ptt.py`
- 结论：可用于“趋势分析与工程验证”，不可作临床诊断宣称

### Task9 回归测试
- 机制：关键任务指标回退门控
- 主线结果：regression_flags = {best['task9']['regression_flags']}

## 4. 手机端与算法端映射结论

- Python侧作用：公开数据离线验证、算法迭代、证据产出。
- Kotlin侧作用：手机端实时/准实时执行（真实产品链路）。
- 当前状态：已建立 Kotlin 与 Python 数值一致性对照（见 `kotlin_python_numeric_parity` 相关产物）。
- 工程建议：后续所有外部展示指标以“Kotlin落地版本 + 离线复现验证”双重口径输出。

## 5. 图表证据与答辩推荐

推荐优先用于答辩的图表：
- `outputs/figures/ecg_filter_rpeak_example.png`
- `outputs/figures/rpeak_detection_example.png`
- `outputs/figures/task3_baseline_roc.png`
- `outputs/figures/task3_baseline_pr.png`
- `outputs/figures/ppg_peak_foot_example.png`
- `outputs/figures/task8_delay_distribution_before_after.png`
- `outputs/figures/task8_subjectwise_delay_boxplot.png`

核心总表：
- `outputs/tables/master_best_results_table.csv`
- `outputs/tables/dataset_overview_master.csv`
- `outputs/tables/project_task_algorithm_map.csv`

## 6. 当前能力边界（必须在正式材料中保留）

可以写进正式方案/答辩：
- ECG R峰/HRV基础能力
- PPG峰足检测与SQI工程能力
- ECG+PPG时序特征链路修复完成并具备趋势分析价值
- AF初步筛查（工程级、风险提示级）

不能夸大的内容：
- 不能宣称临床级 AF 诊断
- 不能宣称无袖带血压“精准估计”
- 不能把公开数据集结果直接表述为临床结论
- QT/P波“流程跑通”不能等价为“精度临床验证完成”

## 7. 后续收敛建议

1. 将 Task3 专项最优流程正式并回主线自动聚合，统一唯一口径。
2. 补齐 ppg_dalia 与完整 ltafdb，增强泛化与运动场景可信度。
3. 用自采硬件数据做二次标定（滤波参数、阈值、SQI分档、告警阈值）。
4. 维持“1分钟采集后统一出报告”的手机端交互策略，减少瞬时噪声误导。

---

附：本完整报告对应文件
- Markdown：`outputs/reports/PROJECT_FULL_REPORT.md`
- PDF：`outputs/reports/PROJECT_FULL_REPORT.pdf`
"""
    return md


def markdown_to_pdf(markdown_text: str, pdf_path: Path) -> None:
    font_name = choose_cjk_font()
    if font_name:
        plt.rcParams["font.sans-serif"] = [font_name]
        plt.rcParams["axes.unicode_minus"] = False

    lines_raw = markdown_text.splitlines()
    lines_wrapped: list[str] = []
    for line in lines_raw:
        if line.strip() == "":
            lines_wrapped.append("")
            continue
        # 保留标题层级符号，按宽度换行
        wrapped = textwrap.wrap(line, width=48, break_long_words=False, break_on_hyphens=False)
        if not wrapped:
            lines_wrapped.append("")
        else:
            lines_wrapped.extend(wrapped)

    pdf_path.parent.mkdir(parents=True, exist_ok=True)
    with PdfPages(pdf_path) as pdf:
        fig = None
        ax = None
        y = 0.96
        line_h = 0.022

        def new_page() -> tuple[Any, Any]:
            f, a = plt.subplots(figsize=(8.27, 11.69))  # A4
            a.axis("off")
            return f, a

        fig, ax = new_page()
        for line in lines_wrapped:
            if y < 0.04:
                pdf.savefig(fig, bbox_inches="tight")
                plt.close(fig)
                fig, ax = new_page()
                y = 0.96
            ax.text(0.03, y, line, fontsize=10.5, va="top", ha="left", family="sans-serif")
            y -= line_h

        if fig is not None:
            pdf.savefig(fig, bbox_inches="tight")
            plt.close(fig)


def main() -> None:
    md = build_markdown()
    md_path = REPORTS / "PROJECT_FULL_REPORT.md"
    pdf_path = REPORTS / "PROJECT_FULL_REPORT.pdf"
    md_path.write_text(md, encoding="utf-8")
    markdown_to_pdf(md, pdf_path)
    print(f"Markdown generated: {md_path}")
    print(f"PDF generated: {pdf_path}")


if __name__ == "__main__":
    main()

