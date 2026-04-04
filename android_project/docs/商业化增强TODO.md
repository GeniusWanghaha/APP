# 商业化增强 TODO（基于 V1.1 软件增强版）

更新时间：2026-04-01 20:26  
执行范围：优先完成软件侧可验证项；需真实硬件联调项保留待你后续连机验证。

## 使用约定
- 完成一项将 `- [ ]` 改为 `- [x]`
- 每项尽量附带可验证证据（测试名、构建命令、日志路径）

## A. 已确认基线
- [x] Android 主实现语言统一为 Kotlin（main/test 均为 Kotlin）
- [x] 软硬件接口统一为 `HardwareInterfaceRepository`
- [x] 微秒级时间戳字段 `baseTimestampMicros` 已保留
- [x] BLE 分包重组 + CRC + 状态包解析已就位
- [x] 单元测试可运行

## B. P0 采集链路与时间同步可信度
- [x] BLE 设备名/UUID/MTU 改为可配置（配置中心 + 工程模式页面）
- [x] 会话元数据落盘（SessionID、设备、采样率、起止时间、质量等级、丢帧）
- [x] 统一时间轴重建模块（ECG 固定周期 + PPG 相位/延时 + 抖动保护）
- [x] 短丢包显示补点 + 指标层剔除策略
- [x] 首页显示连接状态、会话状态、门控层级、丢帧计数
- [x] Session 事件日志标准化（按 SessionID 记录 LINK/PACKET_LOSS/QUALITY）

## C. P0 信号处理与 SQI 门控
- [x] ECG 链路：50Hz 陷波 + 高通 + 低通 + R 峰检测
- [x] PPG 链路：DC/AC 分离 + 带通 + 足点/峰值检测 + 红外红光联合
- [x] 统一 SQI 评分（状态位 + 波形统计 + 跨模态一致性）
- [x] 门控分层（基础输出 / 高阶输出 / 研究输出）
- [x] 质量不达标提示文案（接触差、丢包、干扰）

## D. P1 指标体系增强
- [x] ECG 指标：HR、RR、SDNN、RMSSD、pNN50
- [x] 异常提示：心动过速/过缓、节律不齐、房颤风险、疑似早搏（含置信度）
- [x] PPG 指标：PI、足点/峰值、上升时间、半高宽、反射指数
- [x] 联合指标：PAT、PTT、PWTT、心搏-脉搏一致性
- [x] 血压相关输出统一为“趋势参考”（禁止诊断性绝对值）
- [x] 指标输出层级已统一

## E. P1 AI 解释与报告
- [x] 结构化摘要包作为 AI 输入（指标 + 质量 + 风险）
- [x] Prompt 模板化与边界文案（明确非诊断）
- [x] 报告结构化（概述/风险/影响因素/建议/复测）
- [x] 问答模式支持“为何不输出某指标”等可解释问题
- [x] 报告与趋势加入追溯字段（sessionId/algorithmVersion/modelVersion）

## F. P1 数据、隐私、可追溯
- [x] 数据分层：实时缓存（内存）/会话层（measurement_sessions）/长期统计（detection_records）
- [x] 隐私授权开关与边界说明页面
- [x] 导出能力：会话 CSV/JSON（含事件标注）
- [x] 版本追溯字段持久化（算法版本、模型版本、输出层级）
- [x] 仅上传结构化摘要的策略文案已固化

## G. P2 工程化与交付
- [x] CI 流水线（lint + unit test + assemble）
- [x] 回归测试补齐（协议、重建、门控、报告边界）
- [x] 发布包策略（demo/contest flavor + 自动命名）
- [x] 体积巡检脚本（`tools/weekly_size_audit.ps1`）
- [ ] 功耗与热管理实测（需真实硬件）

## H. 软硬件联合调试（需真实硬件）
- [x] 固件状态位与 APP 提示映射文档（`docs/硬件接入说明.md`）
- [x] START/STOP/SET_CFG/GET_INFO/SELF_TEST 真机全链路验证（证据：`tools/ble_hw_smoke_test.py`、`docs/logs/hardware_smoke_20260401_202342.log`）
- [x] SYNC_MARK 真机命令闭环验证（证据：`tools/ble_hw_smoke_test.py`，已在数据帧检测到 `state_flags bit9`）
- [x] APP 真机 BLE 连接与实时数据帧联调通过（证据：`docs/logs/ui_after_debug5_20260402_002139.xml`、`docs/logs/phone_ble_debug5_20260402_002139.log`）
- [ ] 工程模式展示完整自检结果与错误码解释（需真机触发验证）
- [ ] PPG 固定延时标定流程（上电标定 + 会话内修正）实测闭环
- [x] 30~60 秒一键演示脚本真机验证（证据：`docs/logs/hardware_demo_35s_20260401_201530.log`）

## I. 目录清理与体积控制
- [x] 删除 `_docx_extract*` 临时目录
- [x] 删除 `android_project/.gradle`、`app/build`、`OPD/build` 历史构建缓存
- [x] `.gitignore` 已覆盖构建缓存
- [x] 新增每周体积巡检脚本并已生成报告（`dist/size_audit_*.csv`）
- [x] Gradle 用户缓存迁移到项目外（`D:\GradleUserHome`），`gradlew.bat` 默认不再写入项目目录

## 当前验收状态
- [x] 主实现语言统一为 Kotlin
- [x] 构建通过（demo/contest debug + contest release）
- [x] 核心单元测试通过
- [x] 分层与目录职责清晰（domain/application/data/infrastructure/presentation/ui）
- [x] 并发模型统一为 Kotlin coroutines
- [x] 空安全与错误模型统一（`HealthResult` + typed error）
- [ ] 真实硬件联调验收（待你连机后执行 H 章节）
