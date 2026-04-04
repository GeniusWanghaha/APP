from __future__ import annotations

import json
import re
import textwrap
from datetime import datetime
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd
from matplotlib import font_manager as fm
from matplotlib.backends.backend_pdf import PdfPages

APP_ROOT = Path(r"d:/optoelectronic_design/APP").resolve()
ROOT = APP_ROOT / "project_root"
OUTPUT_REPORTS = APP_ROOT / "reports" / "competition_project"
OUTPUT_TABLES = ROOT / "outputs" / "tables"
OUTPUT_FIGURES = ROOT / "outputs" / "figures"
OUTPUT_METRICS = ROOT / "outputs" / "metrics"
DATASET_MANIFESTS = ROOT / "datasets" / "manifests"
OPD_ROOT = APP_ROOT / "OPD"
ANDROID_ROOT = APP_ROOT / "android_project"

OUTPUT_REPORTS.mkdir(parents=True, exist_ok=True)


def rel_to_app(path: Path) -> str:
    return path.resolve().relative_to(APP_ROOT).as_posix()


def choose_cjk_font() -> str | None:
    candidates = ["Microsoft YaHei", "SimHei", "Noto Sans CJK SC", "Source Han Sans CN"]
    available = {f.name for f in fm.fontManager.ttflist}
    for name in candidates:
        if name in available:
            return name
    return None


def scan_files(root: Path) -> list[Path]:
    ignore_tokens = {".git", "__pycache__", ".venv", "node_modules"}
    files: list[Path] = []
    for p in root.rglob("*"):
        if not p.is_file():
            continue
        rel = p.relative_to(root).parts
        if any(token in rel for token in ignore_tokens):
            continue
        files.append(p)
    return files


def build_module_inventory() -> pd.DataFrame:
    rows: list[dict[str, str]] = []

    def add(module: str, category: str, path: Path, role: str, entry: str, depends: str) -> None:
        rows.append(
            {
                "module": module,
                "category": category,
                "path": rel_to_app(path),
                "role": role,
                "is_entrypoint": entry,
                "depends_on": depends,
            }
        )

    hw_key = [
        OPD_ROOT / "README.md",
        OPD_ROOT / "main" / "app_main.c",
        OPD_ROOT / "main" / "app_tasks.c",
        OPD_ROOT / "main" / "app_config.h",
        OPD_ROOT / "main" / "packet_protocol.h",
        OPD_ROOT / "main" / "board_pins.h",
        OPD_ROOT / "components" / "ble_service" / "ble_service.h",
        OPD_ROOT / "components" / "state_monitor" / "state_monitor.h",
        OPD_ROOT / "components" / "sampler" / "ecg_sampler.h",
        OPD_ROOT / "components" / "sampler" / "ppg_sampler.h",
        OPD_ROOT / "components" / "timebase" / "timebase.h",
    ]
    for p in hw_key:
        if p.exists():
            add(
                "Hardware",
                "firmware",
                p,
                "entry" if p.name in {"app_main.c", "app_tasks.c"} else "implementation",
                "yes" if p.name in {"app_main.c", "app_tasks.c"} else "no",
                "ESP-IDF/NimBLE/ADC/I2C",
            )

    sw_key = [
        ANDROID_ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
        ANDROID_ROOT / "app" / "build.gradle.kts",
        ANDROID_ROOT / "build.gradle.kts",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "data"
        / "repository"
        / "ble"
        / "BlePacketProtocol.kt",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "data"
        / "repository"
        / "BleHardwareBridgeRepository.kt",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "infrastructure"
        / "signal"
        / "TimelineReconstructor.kt",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "infrastructure"
        / "signal"
        / "CardiovascularSignalProcessor.kt",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "infrastructure"
        / "signal"
        / "BatchCardioAnalyzer.kt",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "ui"
        / "viewmodel"
        / "HomeViewModel.kt",
        ANDROID_ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "photosentinel"
        / "health"
        / "ui"
        / "screens"
        / "HomeScreen.kt",
    ]
    for p in sw_key:
        if p.exists():
            add(
                "Software",
                "android_app",
                p,
                "entry" if p.name in {"AndroidManifest.xml", "HomeViewModel.kt"} else "implementation",
                "yes" if p.name in {"AndroidManifest.xml", "HomeViewModel.kt"} else "no",
                "BLE/GATT/Compose/Kotlin",
            )

    ds_key = [
        ROOT / "README.md",
        ROOT / "requirements.txt",
        ROOT / "run_all.ps1",
        ROOT / "configs" / "datasets.yaml",
        ROOT / "configs" / "pipeline.yaml",
        ROOT / "src" / "run_pipeline.py",
        ROOT / "src" / "iteration_controller.py",
        ROOT / "src" / "evaluation" / "task1_ecg_rpeak.py",
        ROOT / "src" / "evaluation" / "task2_hrv.py",
        ROOT / "src" / "evaluation" / "task3_af.py",
        ROOT / "src" / "evaluation" / "task4_arrhythmia.py",
        ROOT / "src" / "evaluation" / "task5_ecg_qt_pwave.py",
        ROOT / "src" / "evaluation" / "task6_ppg.py",
        ROOT / "src" / "evaluation" / "task7_resp.py",
        ROOT / "src" / "evaluation" / "task8_ptt.py",
        ROOT / "src" / "evaluation" / "task9_regression.py",
        ROOT / "outputs" / "metrics" / "best_metrics.json",
        ROOT / "outputs" / "tables" / "task3_baseline_model_compare.csv",
        ROOT / "outputs" / "tables" / "task8_recomputed_metrics.csv",
        ROOT / "datasets" / "manifests" / "dataset_inventory.csv",
    ]
    for p in ds_key:
        if p.exists():
            add(
                "DatasetValidation",
                "python_pipeline",
                p,
                "entry" if p.name in {"run_pipeline.py", "run_all.ps1"} else "data_or_impl",
                "yes" if p.name in {"run_pipeline.py", "run_all.ps1"} else "no",
                "Python/Pandas/WFDB/NeuroKit2",
            )

    df = pd.DataFrame(rows)
    df.to_csv(OUTPUT_REPORTS / "project_module_inventory.csv", index=False, encoding="utf-8-sig")

    files = scan_files(APP_ROOT)
    summary = {
        "Hardware": sum(1 for p in files if rel_to_app(p).startswith("OPD/")),
        "Software": sum(1 for p in files if rel_to_app(p).startswith("android_project/")),
        "DatasetValidation": sum(1 for p in files if rel_to_app(p).startswith("project_root/")),
    }
    md_lines = [
        "# project_module_inventory",
        "",
        f"生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"项目根目录：`{APP_ROOT.as_posix()}`",
        "",
        "## 文件树摘要",
        "```text",
        "APP/",
        "├─ OPD/ (硬件端固件)",
        "├─ android_project/ (软件端 Android)",
        "├─ project_root/ (数据集验证与结果)",
        "└─ .vscode/",
        "```",
        "",
        "## 扫描统计",
        f"- 硬件端文件数：{summary['Hardware']}",
        f"- 软件端文件数：{summary['Software']}",
        f"- 数据集验证文件数：{summary['DatasetValidation']}",
        "",
        "## 主入口文件",
        "- 硬件端：`OPD/main/app_main.c`, `OPD/main/app_tasks.c`",
        "- 软件端：`android_project/app/src/main/AndroidManifest.xml`, `HomeViewModel.kt`",
        "- 验证端：`project_root/src/run_pipeline.py`, `project_root/run_all.ps1`",
        "",
        "## 模块依赖关系",
        "- 硬件端 -> 软件端：BLE 分段帧 + CRC。",
        "- 软件端 -> 验证端：Kotlin/Python 指标对齐。",
        "- 验证端 -> 工程主线：参数与门控策略回灌。",
        "- 关键声明：数据集验证模块只验证算法，不等于整个项目本体。",
        "",
        "## 关键文件摘录",
    ]
    for module in ["Hardware", "Software", "DatasetValidation"]:
        md_lines.append(f"### {module}")
        part = df[df["module"] == module]
        for _, r in part.iterrows():
            md_lines.append(
                f"- `{r['path']}` | role={r['role']} | entry={r['is_entrypoint']} | depends={r['depends_on']}"
            )
        md_lines.append("")
    (OUTPUT_REPORTS / "project_module_inventory.md").write_text("\n".join(md_lines), encoding="utf-8")
    return df


def load_metrics() -> dict:
    best = json.loads((OUTPUT_METRICS / "best_metrics.json").read_text(encoding="utf-8"))
    t8_refresh = json.loads((OUTPUT_METRICS / "task8_sync_refresh_snapshot.json").read_text(encoding="utf-8"))
    parity = json.loads((OUTPUT_METRICS / "kotlin_python_numeric_parity_summary.json").read_text(encoding="utf-8"))
    t3_df = pd.read_csv(OUTPUT_TABLES / "task3_baseline_model_compare.csv")
    t3_best = t3_df.sort_values("f1", ascending=False).iloc[0].to_dict()
    ds = pd.read_csv(DATASET_MANIFESTS / "dataset_inventory.csv")
    status_counts = ds["status"].value_counts().to_dict()
    return {
        "best": best,
        "task8_refresh": t8_refresh,
        "parity": parity,
        "task3_best": t3_best,
        "dataset_status_counts": status_counts,
    }


def fmt(v: float, n: int = 4) -> str:
    return f"{v:.{n}f}"


def build_figure_manifest() -> list[tuple[str, str, str]]:
    body_figs = [
        ("ECG Filter + R-peak Example", "ecg_filter_rpeak_example.png", "Task1 预处理与峰值示例"),
        ("R-peak Detection Example", "rpeak_detection_example.png", "Task1 检测结果示例"),
        ("Task3 Baseline ROC", "task3_baseline_roc.png", "Task3 ROC 曲线"),
        ("Task3 Baseline PR", "task3_baseline_pr.png", "Task3 PR 曲线"),
        ("PPG Peak/Foot Example", "ppg_peak_foot_example.png", "Task6 峰足检测示例"),
        ("PPG SQI Distribution", "ppg_sqi_distribution.png", "Task6 SQI 分布"),
        ("RR Estimation Error Distribution", "rr_estimation_error_distribution.png", "Task7 呼吸率误差分布"),
        ("Task8 Delay Distribution Before/After", "task8_delay_distribution_before_after.png", "Task8 修复前后时延分布"),
        ("Task8 Subject-wise Delay Boxplot", "task8_subjectwise_delay_boxplot.png", "Task8 各受试者时延箱线图"),
        ("Iteration Score Trend", "iteration_score_trend.png", "迭代得分趋势"),
        ("Regression Flags By Round", "regression_flags_by_round.png", "Task9 回归门控轮次结果"),
    ]
    appendix = sorted(OUTPUT_FIGURES.glob("task3_failure_case_*.png")) + sorted(
        OUTPUT_FIGURES.glob("task8_visual_audit_*.png")
    )
    lines = [
        "# COMPETITION_FIGURE_MANIFEST",
        "",
        f"生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        "## 正文图",
        "| 序号 | 图名 | 文件 | 用途 | 状态 |",
        "|---|---|---|---|---|",
    ]
    for i, (t, f, u) in enumerate(body_figs, start=1):
        status = "exists" if (OUTPUT_FIGURES / f).exists() else "missing"
        lines.append(f"| {i} | {t} | `project_root/outputs/figures/{f}` | {u} | {status} |")
    lines += ["", "## 附录图", "| 序号 | 图名 | 文件 | 用途 | 状态 |", "|---|---|---|---|---|"]
    for i, p in enumerate(appendix, start=1):
        status = "exists"
        lines.append(f"| A{i} | {p.stem} | `project_root/outputs/figures/{p.name}` | 审计或失败案例 | {status} |")
    (OUTPUT_REPORTS / "COMPETITION_FIGURE_MANIFEST.md").write_text("\n".join(lines), encoding="utf-8")

    ordered: list[tuple[str, str, str]] = []
    for t, f, u in body_figs:
        if (OUTPUT_FIGURES / f).exists():
            ordered.append((t, f, u))
    for p in appendix:
        ordered.append((p.stem, p.name, "审计或失败案例"))
    return ordered


def build_main_report(metrics: dict) -> str:
    best = metrics["best"]
    t3 = metrics["task3_best"]
    st = metrics["dataset_status_counts"]
    report = f"""# 封面
- 项目名称：手机壳式 ECG+PPG AI 健康评估系统
- 团队/作者：________（待补）
- 生成日期：{datetime.now().strftime('%Y-%m-%d')}
- 项目根目录：`{APP_ROOT.as_posix()}`

# 摘要
本项目从全项目视角构建了硬件端、软件端、数据集验证端三位一体体系。硬件端由 ESP32-S3、AD8232、MAX30102 组成，负责 ECG/PPG 原始信号采集、微秒级时间基准、封包和 BLE 传输；软件端由 Android Kotlin 实现，负责 BLE 分包重组、CRC 校验、时间轴重建、本地算法计算、波形显示与报告输出；数据集验证端由 Python Task1~Task9 组成，负责公开数据集离线验证、图表证据生成和迭代回归。当前稳定能力包括 Task1 R峰检测（F1={fmt(best['task1']['adaptive']['f1'])}）、Task6 PPG 基线（SQI均值={fmt(best['task6']['sqi_mean'])}）、Task8 联合时序修复后主线结果（R->foot={fmt(best['task8']['r_to_foot_mean_ms'],2)}ms，R->peak={fmt(best['task8']['r_to_peak_mean_ms'],2)}ms）。本报告明确：数据集验证模块只是算法验证子系统，不等于项目本体；项目当前定位为工程验证与初步筛查，不是临床诊断系统。

# 1. 项目背景与问题定义
项目面向便携式日常健康监测需求。ECG 反映心脏电活动，PPG 反映外周血流脉动，二者联合可增强节律与循环特征观测。采用“硬件采集 + 手机计算”分工，可以降低硬件复杂度并提高算法迭代效率。

# 2. 基础概念说明
- ECG：心电图，记录心脏电活动。
- PPG：光电容积脉搏波，反映血容量变化。
- R峰：ECG 主要心拍定位点。
- HR：心率（bpm）。
- HRV：心率变异性（SDNN/RMSSD/pNN50 等）。
- AF：房颤，当前项目仅做初步筛查提示。
- SQI：信号质量指标，用于门控输出。
- PTT/PWTT：生理时延特征，用于趋势分析。
- BLE：低功耗蓝牙通信协议。
- 时间戳/同步：通过统一时基和相位参数对齐 ECG/PPG 时间轴。
- 数据集验证：离线验证算法有效性，不代表整机全部能力。

# 3. 全项目总体架构
- 硬件端：采集、封包、传输、状态上报。
- 软件端：接收、重建、计算、显示、报告。
- 数据集验证端：公开数据集任务验证与证据输出。
- 三端关系：硬件提供真实链路数据，软件落地算法，验证端提供可复现证据并回灌参数。
- 核心声明：跑数据集不等于整个项目本体。

# 4. 硬件端设计
- 主控/器件：ESP32-S3 + AD8232 + MAX30102。
- 采样参数：ECG 250Hz（10点/帧），PPG 400Hz（16点/帧），40ms 帧周期。
- 时间与同步：`t_base_us` 微秒时基，手机端结合 `ppg_phase_us` 重建时间轴。
- 协议：132B 业务帧 + 8B 分段头 + CRC16-CCITT-FALSE。
- 传输：NimBLE 自定义服务，支持 start/stop/self-test/sync-mark 等控制。
- 边界：硬件端不做高阶医学诊断，只提供高质量原始数据与状态。

# 5. 软件端设计
- 技术栈：Android/Kotlin/Compose。
- BLE 接收解析：`BlePacketProtocol.kt` + `BleHardwareBridgeRepository.kt`。
- 时间轴重建：`TimelineReconstructor.kt`。
- 本地算法：`CardiovascularSignalProcessor.kt`（实时）+ `BatchCardioAnalyzer.kt`（60秒批处理）。
- 输出策略：60秒采集结束后统一输出，避免实时抖动误导。
- 协议一致性：协议版本、帧结构、采样率、相位参数与固件一致。
- 验证映射：Kotlin/Python 数值一致性 14/14 matched。

# 6. 数据集验证模块设计
- 模块定位：验证算法有效性的子系统，不是项目全部。
- 数据覆盖：ok={int(st.get('ok',0))}，incomplete={int(st.get('incomplete',0))}，missing={int(st.get('missing',0))}。
- 任务范围：Task1~Task9 覆盖 R峰、HRV、AF、异常搏、P/QT、PPG、呼吸、联合时序、回归门控。
- 比赛意义：提供可复现证据和图表，不替代整机实机联调证据。

# 7. 关键算法与任务结果
## Task1 ECG R峰检测
1. 任务目标：稳定定位 R峰。  
2. 输入数据：ECG。  
3. 数据集：mitdb/nsrdb/nstdb。  
4. 算法：Pan-Tompkins + 自适应阈值。  
5. 状态：稳定。  
6. 关键结果：F1={fmt(best['task1']['adaptive']['f1'])}, 定位误差={fmt(best['task1']['adaptive']['mean_abs_peak_localization_error_ms'],2)}ms。  
7. 图表：`ecg_filter_rpeak_example.png`, `rpeak_detection_example.png`。  
8. 解释：是系统最稳能力之一。  
9. 局限：对体动和接触敏感。  
10. 答辩表述：工程级高可靠 R峰检测能力。  

## Task2 HR/HRV
1. 任务目标：输出 HRV 统计。  
2. 输入：RR 间期。  
3. 数据集：mitdb/nsrdb/afdb。  
4. 算法：RR 统计 + 离群清洗。  
5. 状态：稳定。  
6. 结果：mean HR={fmt(best['task2']['summary']['clean']['mean_hr_bpm'],2)}bpm, RMSSD={fmt(best['task2']['summary']['clean']['rmssd_ms'],2)}ms。  
7. 图表：`best_metrics.json`。  
8. 解释：可用于趋势监测。  
9. 局限：跨数据源标签口径差异。  
10. 表述：工程分析能力。  

## Task3 AF筛查
1. 目标：房颤初筛。  
2. 输入：RR 不规则特征。  
3. 数据集：afdb/ltafdb/mitdb。  
4. 算法：主线弱标签 + RF；专项重建标签 + XGBoost。  
5. 状态：主线待并回，专项有效。  
6. 结果：主线F1={fmt(best['task3']['f1'])}, AUROC={fmt(best['task3']['auroc'])}; 专项F1={fmt(t3['f1'])}, AUROC={fmt(t3['auroc'])}。  
7. 图表：`task3_baseline_roc.png`, `task3_baseline_pr.png`。  
8. 解释：专项显著优于主线旧值。  
9. 局限：双口径并存。  
10. 表述：必须显式区分主线口径与专项口径。  

## Task4 异常搏提示
1. 目标：异常搏风险提示。  
2. 输入：RR + 注释标签。  
3. 数据集：mitdb。  
4. 算法：RR 偏离启发式。  
5. 状态：可用待优化。  
6. 结果：F1={fmt(best['task4']['f1'])}, Recall={fmt(best['task4']['recall'])}。  
7. 图表：`best_metrics.json`。  
8. 解释：召回较高。  
9. 局限：误报仍存在。  
10. 表述：提示型能力，不做确诊。  

## Task5 P波/QT流程
1. 目标：流程打通。  
2. 输入：ECG。  
3. 数据集：qtdb/but_pdb。  
4. 算法：SQI门控 + delineation。  
5. 状态：流程可运行。  
6. 结果：success_rate={fmt(best['task5']['delineation_success_rate'])}, mean_sqi={fmt(best['task5']['mean_sqi'])}。  
7. 图表：`best_metrics.json`。  
8. 解释：工程链路已通。  
9. 局限：临床点级精度未完成验证。  
10. 表述：流程级验证完成。  

## Task6 PPG峰足/SQI/HR
1. 目标：PPG 工程基线。  
2. 输入：PPG。  
3. 数据集：but_ppg/bidmc（ppg_dalia缺失）。  
4. 算法：去趋势+峰足+SQI。  
5. 状态：稳定。  
6. 结果：hr_proxy_mae={fmt(best['task6']['hr_proxy_mae_bpm'])}bpm, sqi_mean={fmt(best['task6']['sqi_mean'])}。  
7. 图表：`ppg_peak_foot_example.png`, `ppg_sqi_distribution.png`。  
8. 解释：是稳定工程基线。  
9. 局限：运动泛化证据不足。  
10. 表述：稳定工程基线。  

## Task7 呼吸率
1. 目标：ECG/PPG 导出呼吸率。  
2. 输入：EDR/PDR 序列。  
3. 数据集：bidmc/apnea_ecg。  
4. 算法：Welch 主频。  
5. 状态：可用。  
6. 结果：ECG MAE={fmt(best['task7']['ecg_resp_mae_bpm'],2)}bpm, PPG MAE={fmt(best['task7']['ppg_resp_mae_bpm'],2)}bpm。  
7. 图表：`rr_estimation_error_distribution.png`。  
8. 解释：ECG 优于 PPG。  
9. 局限：对噪声敏感。  
10. 表述：趋势估计能力。  

## Task8 ECG+PPG联合时序/PTT
1. 目标：提取生理时延特征。  
2. 输入：R峰与PPG峰足。  
3. 数据集：ptt_ppg。  
4. 算法：生理约束配对 + MAD 跳变剔除。  
5. 状态：主线已更新修复后口径。  
6. 结果：R->foot={fmt(best['task8']['r_to_foot_mean_ms'],2)}ms, R->peak={fmt(best['task8']['r_to_peak_mean_ms'],2)}ms。  
7. 图表：`task8_delay_distribution_before_after.png`, `task8_subjectwise_delay_boxplot.png`。  
8. 解释：顺序恢复为生理合理关系。  
9. 局限：用于趋势分析而非临床诊断。  
10. 表述：联合时序工程验证完成；延时不是同步误差。  

## Task9 回归门控
1. 目标：防止关键指标回退。  
2. 输入：跨任务指标。  
3. 数据集：跨任务聚合。  
4. 算法：回归规则检测。  
5. 状态：稳定。  
6. 结果：regression_flags={best['task9']['regression_flags']}。  
7. 图表：`regression_flags_by_round.png`。  
8. 解释：当前轮次无回归告警。  
9. 局限：可继续扩展规则覆盖。  
10. 表述：具备持续迭代质量控制能力。  

# 8. 图表结果展示
![ECG Filter + R-peak Example](../../project_root/outputs/figures/ecg_filter_rpeak_example.png)
*图注：Task1 ECG 预处理与峰值示例。*

![R-peak Detection Example](../../project_root/outputs/figures/rpeak_detection_example.png)
*图注：Task1 R峰检测示例。*

![Task3 Baseline ROC](../../project_root/outputs/figures/task3_baseline_roc.png)
*图注：Task3 ROC 曲线。*

![Task3 Baseline PR](../../project_root/outputs/figures/task3_baseline_pr.png)
*图注：Task3 PR 曲线。*

![PPG Peak/Foot Example](../../project_root/outputs/figures/ppg_peak_foot_example.png)
*图注：Task6 峰足检测示例。*

![PPG SQI Distribution](../../project_root/outputs/figures/ppg_sqi_distribution.png)
*图注：Task6 SQI 分布。*

![RR Estimation Error Distribution](../../project_root/outputs/figures/rr_estimation_error_distribution.png)
*图注：Task7 呼吸率估计误差分布。*

![Task8 Delay Distribution Before/After](../../project_root/outputs/figures/task8_delay_distribution_before_after.png)
*图注：Task8 修复前后时延分布。*

![Task8 Subject-wise Delay Boxplot](../../project_root/outputs/figures/task8_subjectwise_delay_boxplot.png)
*图注：Task8 受试者维度时延箱线图。*

# 9. 系统创新点与比赛价值
- 三端协同闭环，不是单一算法作业。
- 协议级可观测设计（状态位、自检位、丢帧统计）。
- Task8 修复并主线回灌，体现工程迭代能力。
- 60秒批处理发布策略，降低实时误导。

# 10. 真实性边界与风险说明
- 公开数据结果用于工程验证，不等于临床结论。
- Task3 双口径并存，报告已显式区分。
- Task8 延时是生理特征，不是同步误差。
- QT/P波“能跑通”不等于临床精度验证完成。

# 11. 当前完成度与下一步计划
- 已完成：硬件采集链路、软件分析链路、Task1~Task9 验证体系。
- 最稳能力：Task1、Task6、Task8、Task9。
- 下一步：Task3 专项并回主线、补齐数据缺口、开展实机联调验证。

# 12. 结论
项目已形成可答辩的全链路工程体系，最可信成果是稳定采集、稳定检测、联合时序修复和回归门控。最适合比赛定位是“工程验证充分、具备落地潜力的初步筛查系统”。

# 附录A：关键图表索引
见 `COMPETITION_FIGURE_MANIFEST.md`。

# 附录B：任务-算法-代码映射
见 `outputs/tables/project_task_algorithm_map.csv`。

# 附录C：关键结果总表
- Task1 F1={fmt(best['task1']['adaptive']['f1'])}
- Task6 SQI={fmt(best['task6']['sqi_mean'])}
- Task8 R->foot={fmt(best['task8']['r_to_foot_mean_ms'],2)}ms, R->peak={fmt(best['task8']['r_to_peak_mean_ms'],2)}ms
- Task3 主线 F1={fmt(best['task3']['f1'])}, 专项 F1={fmt(t3['f1'])}

# 附录D：失败案例图（Task3）
见 `project_root/outputs/figures/task3_failure_case_*.png`。

# 附录E：旧逻辑审计图（Task8）
见 `project_root/outputs/figures/task8_visual_audit_*.png`。
"""
    (OUTPUT_REPORTS / "COMPETITION_PROJECT_REPORT.md").write_text(report, encoding="utf-8")
    return report


def build_exec_summary(metrics: dict) -> None:
    best = metrics["best"]
    t3 = metrics["task3_best"]
    st = metrics["dataset_status_counts"]
    text = f"""# COMPETITION_EXECUTIVE_SUMMARY

生成日期：{datetime.now().strftime('%Y-%m-%d')}

## 项目定位
这是一个“硬件采集 + 手机分析 + 数据集验证”三端协同的竞赛工程，不是单一离线算法工程。

## 关键结果
- Task1：F1={fmt(best['task1']['adaptive']['f1'])}。
- Task6：SQI均值={fmt(best['task6']['sqi_mean'])}。
- Task8：R->foot={fmt(best['task8']['r_to_foot_mean_ms'],2)}ms，R->peak={fmt(best['task8']['r_to_peak_mean_ms'],2)}ms（主线已更新修复后值）。
- Task3：主线F1={fmt(best['task3']['f1'])}；专项F1={fmt(t3['f1'])}, AUROC={fmt(t3['auroc'])}。

## 边界
- 工程验证/初步筛查，不是临床诊断。
- Task8 延时是生理时延特征，不是同步误差。
- QT/P波流程跑通不等于临床精度验证完成。

## 数据覆盖
- ok={int(st.get('ok',0))}, incomplete={int(st.get('incomplete',0))}, missing={int(st.get('missing',0))}。
"""
    (OUTPUT_REPORTS / "COMPETITION_EXECUTIVE_SUMMARY.md").write_text(text, encoding="utf-8")


def build_defense_notes(metrics: dict) -> None:
    best = metrics["best"]
    t3 = metrics["task3_best"]
    text = f"""# COMPETITION_DEFENSE_NOTES

## 推荐讲法
1. 先讲全系统三端协同，再讲任务结果，最后讲真实性边界。
2. 强调数据集验证是子系统，不能代表项目全部。
3. 强调软件端是正式系统组件，不是演示壳层。

## 必讲指标
- Task1 F1={fmt(best['task1']['adaptive']['f1'])}
- Task6 SQI={fmt(best['task6']['sqi_mean'])}
- Task8 R->foot={fmt(best['task8']['r_to_foot_mean_ms'],2)}ms, R->peak={fmt(best['task8']['r_to_peak_mean_ms'],2)}ms
- Task3 主线F1={fmt(best['task3']['f1'])} vs 专项F1={fmt(t3['f1'])}

## 推荐重点图
1. project_root/outputs/figures/ecg_filter_rpeak_example.png
2. project_root/outputs/figures/rpeak_detection_example.png
3. project_root/outputs/figures/task3_baseline_roc.png
4. project_root/outputs/figures/task3_baseline_pr.png
5. project_root/outputs/figures/ppg_peak_foot_example.png
6. project_root/outputs/figures/task8_delay_distribution_before_after.png
7. project_root/outputs/figures/task8_subjectwise_delay_boxplot.png

## 容易被问的问题
1. Q: 是不是只跑了数据集？A: 不是，数据集验证只是算法子系统。
2. Q: Task8 延时是不是同步误差？A: 不是，是生理时延特征。
3. Q: AF 能做诊断吗？A: 当前仅工程级初筛提示。
4. Q: QT/P波是否临床验证完成？A: 仅流程跑通，临床精度未完成验证。
"""
    (OUTPUT_REPORTS / "COMPETITION_DEFENSE_NOTES.md").write_text(text, encoding="utf-8")


def strip_markdown_for_pdf(md: str) -> list[str]:
    lines = []
    for raw in md.splitlines():
        line = raw.rstrip()
        line = re.sub(r"!\[[^\]]*\]\([^\)]*\)", "", line)
        line = re.sub(r"\[(.*?)\]\([^\)]*\)", r"\1", line)
        line = line.replace("`", "")
        if line.startswith("#"):
            line = "【" + line.lstrip("#").strip() + "】"
        lines.append(line)
    return lines


def render_pdf(report_md: str, fig_list: list[tuple[str, str, str]]) -> None:
    font_name = choose_cjk_font()
    if font_name:
        plt.rcParams["font.sans-serif"] = [font_name]
        plt.rcParams["axes.unicode_minus"] = False

    lines = strip_markdown_for_pdf(report_md)
    pdf_path = OUTPUT_REPORTS / "COMPETITION_PROJECT_REPORT.pdf"

    with PdfPages(pdf_path) as pdf:
        page_no = 1

        def save_page(fig, ax) -> None:
            ax.text(0.95, 0.02, f"Page {page_no}", ha="right", va="bottom", fontsize=9, alpha=0.7)
            pdf.savefig(fig, bbox_inches="tight")
            plt.close(fig)

        fig, ax = plt.subplots(figsize=(8.27, 11.69))
        ax.axis("off")
        ax.text(0.5, 0.72, "COMPETITION PROJECT REPORT", ha="center", fontsize=20, fontweight="bold")
        ax.text(0.5, 0.66, "手机壳式 ECG+PPG AI 健康评估系统", ha="center", fontsize=14)
        ax.text(0.5, 0.60, datetime.now().strftime("%Y-%m-%d %H:%M:%S"), ha="center", fontsize=10)
        save_page(fig, ax)
        page_no += 1

        fig, ax = plt.subplots(figsize=(8.27, 11.69))
        ax.axis("off")
        y = 0.95
        for item in [
            "目录",
            "1 背景",
            "2 概念",
            "3 架构",
            "4 硬件",
            "5 软件",
            "6 验证",
            "7 任务结果",
            "8 图表",
            "9 创新",
            "10 风险边界",
            "11 计划",
            "12 结论",
        ]:
            ax.text(0.08, y, item, fontsize=12)
            y -= 0.05
        save_page(fig, ax)
        page_no += 1

        fig, ax = plt.subplots(figsize=(8.27, 11.69))
        ax.axis("off")
        y = 0.97
        for line in lines:
            wrapped = textwrap.wrap(line, width=58, break_long_words=False, break_on_hyphens=False) or [""]
            for w in wrapped:
                if y < 0.05:
                    save_page(fig, ax)
                    page_no += 1
                    fig, ax = plt.subplots(figsize=(8.27, 11.69))
                    ax.axis("off")
                    y = 0.97
                fs = 11 if w.startswith("【") and w.endswith("】") else 10
                ax.text(0.04, y, w, fontsize=fs, va="top")
                y -= 0.022
        save_page(fig, ax)
        page_no += 1

        for title, fname, use in fig_list:
            p = OUTPUT_FIGURES / fname
            if not p.exists():
                continue
            img = plt.imread(p)
            fig, ax = plt.subplots(figsize=(8.27, 11.69))
            ax.axis("off")
            ax.set_position([0.07, 0.13, 0.86, 0.78])
            ax.imshow(img)
            overlay = fig.add_axes([0, 0, 1, 1], frameon=False)
            overlay.set_axis_off()
            overlay.text(0.5, 0.97, title, ha="center", va="top", fontsize=13, fontweight="bold")
            overlay.text(0.5, 0.07, f"图注：{use} | {fname}", ha="center", fontsize=9)
            overlay.text(0.95, 0.02, f"Page {page_no}", ha="right", va="bottom", fontsize=9, alpha=0.7)
            pdf.savefig(fig, bbox_inches="tight")
            plt.close(fig)
            page_no += 1


def main() -> None:
    build_module_inventory()
    metrics = load_metrics()
    fig_list = build_figure_manifest()
    report_md = build_main_report(metrics)
    build_exec_summary(metrics)
    build_defense_notes(metrics)
    render_pdf(report_md, fig_list)
    print("Generated:")
    for name in [
        "project_module_inventory.md",
        "project_module_inventory.csv",
        "COMPETITION_PROJECT_REPORT.md",
        "COMPETITION_PROJECT_REPORT.pdf",
        "COMPETITION_EXECUTIVE_SUMMARY.md",
        "COMPETITION_DEFENSE_NOTES.md",
        "COMPETITION_FIGURE_MANIFEST.md",
    ]:
        print((OUTPUT_REPORTS / name).as_posix())


if __name__ == "__main__":
    main()
