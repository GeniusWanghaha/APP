# project_root（数据集验证子系统）

`project_root` 是 APP 全项目中的“公开数据集离线验证模块”，用于验证算法有效性与生成可复现证据。

它不是整个比赛项目本体；完整项目说明请看：
- `../README.md`（APP 全项目 README）

## 本模块职责
- 公开数据集下载、清单化、完整性检查
- Task1~Task9 离线评估
- 指标 / 图表 / 报告产出
- Kotlin-Python 数值一致性校验

## 目录
```text
project_root/
├─ configs/
├─ datasets/          # 本地数据（默认不上传）
├─ src/
├─ outputs/
├─ run_all.ps1
└─ requirements.txt
```

## 快速运行
```powershell
cd D:\optoelectronic_design\APP\project_root
python -m venv env\.venv
.\env\.venv\Scripts\python -m pip install -r requirements.txt
.\env\.venv\Scripts\python -m src.run_pipeline --phase 1
```

## 关键边界
- 本模块用于工程验证与算法对比，不可直接外推为临床诊断结论。
- 结果解读需结合硬件端与手机端联调证据。
