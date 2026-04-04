# 遗留文件审计

## A类：必须保留
- 原始数据 `datasets/raw/**`
- 主流程代码 `src/**`（排除缓存）
- 配置与入口 `configs/**`, `run_all.ps1`, `Makefile`, `requirements.txt`
- 主结果与关键专项 `outputs/metrics/best_metrics.json`, `outputs/reports/task8_task3_special_report.md`

## B类：建议归档但不删除
- 历史轮次快照 `outputs/metrics/round_*.json`
- 大体量中间表 `outputs/tables/task3_feature_matrix.csv` 等
- 专项同步中间报告 `outputs/reports/task8_sync_step*.md`

## C类：建议删除（已自动执行安全删除）
- `src/**/__pycache__/**` 与 `*.pyc`

## 执行结果
- 自动删除文件/目录数量: 0
- 未删除任何原始数据与主流程依赖文件。