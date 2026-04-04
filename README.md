# 手机壳式 ECG+PPG AI 健康评估系统（APP 全项目）

## 项目定位
这是一个完整比赛工程，不是单一算法脚本。

项目由三端组成：
- 硬件端（`OPD/`）：ESP32-S3 固件，负责 ECG/PPG 采集、时间基准、BLE 封包与状态上报。
- 软件端（`android_project/`）：Android Kotlin，负责 BLE 重组解析、本地计算、UI 展示与报告输出。
- 数据集验证端（`project_root/`）：Python Task1~Task9，负责公开数据离线验证与证据图表生成。

请特别注意：
- **数据集验证端只是算法验证子系统，不等于整个项目本体。**
- 比赛答辩应强调“三端协同的系统工程价值”。

## 项目解决的问题
- 在便携形态下稳定采集 ECG + PPG。
- 在手机端实现可解释的指标输出（HR/HRV、AF 风险提示、SQI、联合时序特征等）。
- 通过公开数据验证形成可复现、可审计的算法证据链。

## 当前关键进展（基于现有结果）
- Task1（ECG R 峰）：`F1(adaptive)=0.9713`
- Task6（PPG 峰足/SQI）：`sqi_mean=0.8378`
- Task8（联合时序，主线修复后）：
  - `R->foot=227.4538 ms`
  - `R->peak=406.9212 ms`
- Task3（AF）当前存在双口径：
  - 主线：`F1=0.0000`, `AUROC=0.3792`
  - 专项最优：`F1=0.7359`, `AUROC=0.9499`

## 能力边界（答辩必须保留）
- 当前定位是工程验证 / 初步筛查，不是临床诊断系统。
- Task8 延时是生理时延特征，不是同步误差。
- Task5（QT/P 波）流程跑通不等于临床精度已验证。

## 目录结构
```text
APP/
├─ OPD/                        # 硬件固件（ESP-IDF）
├─ android_project/            # Android 软件端
├─ project_root/               # Python 数据集验证子系统
├─ reports/competition_project # 比赛报告与审计文档（主目录）
└─ README.md                   # 本文件（全项目入口）
```

## 运行方式

### 1) 硬件端（OPD）
```powershell
cd D:\optoelectronic_design\APP\OPD
idf.py set-target esp32s3
idf.py build
idf.py -p COMx flash monitor
```

### 2) 软件端（Android）
```powershell
cd D:\optoelectronic_design\APP\android_project
.\gradlew.bat assembleDebug
```

### 3) 数据集验证端（Python）
```powershell
cd D:\optoelectronic_design\APP\project_root
python -m venv env\.venv
.\env\.venv\Scripts\python -m pip install -r requirements.txt
.\env\.venv\Scripts\python -m src.run_pipeline --phase 1
```

## 数据集与 GitHub 上传策略
- `project_root/datasets/` 体积较大，默认不上传 GitHub。
- 已在仓库级 `.gitignore`（`APP/.gitignore`）和子项目 `.gitignore`（`project_root/.gitignore`）中忽略。
- 若历史上已被 Git 跟踪，请在仓库根目录执行：
```bash
git rm -r --cached project_root/datasets
```
- 然后提交 `.gitignore` 变更。

## 推荐阅读
- `reports/competition_project/COMPETITION_PROJECT_REPORT.md`
- `reports/competition_project/COMPETITION_DEFENSE_NOTES.md`
- `reports/competition_project/consistency_audit.md`
- `reports/competition_project/cleanup_log.md`
