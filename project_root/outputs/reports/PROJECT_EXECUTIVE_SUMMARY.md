# PROJECT_EXECUTIVE_SUMMARY

## 一页结论
- 项目已形成可复现公共数据验证流水线，任务覆盖 ECG/PPG/联合时序/回归门控。
- Task8 已完成专项修复并主线生效：`R->foot=227.45ms`，`R->peak=406.92ms`，关系正确。
- Task3 存在主线与专项双口径：主线旧值差，专项重建后可达 `F1=0.7359`、`AUROC=0.9499`。
- 数据覆盖 10/12：`ltafdb` incomplete，`ppg_dalia` missing。
- 当前可用于答辩的定位：工程验证与初步筛查，不是临床诊断系统。

## 当前最稳能力
- ECG R峰检测（Task1）
- HR/HRV统计（Task2）
- PPG峰足/SQI工程评估（Task6）
- ECG+PPG时序趋势特征（Task8）

## 关键风险
- Task3专项结果尚未完全合入主线自动聚合。
- 部分报告存在中文编码显示异常（终端查看时乱码风险）。
- PPG-DaLiA 缺失导致运动伪迹泛化证据不足。