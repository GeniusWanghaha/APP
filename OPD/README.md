# 手机壳式 ECG+PPG 多模态生理信号采集终端固件

## 1. 方案理解

本固件严格按“硬件端极简、手机端重计算”的分工实现：

- ESP32-S3-N16R8 仅负责 ECG/PPG 原始采集、统一 1 MHz 时间基准、40 ms 帧封包、BLE NimBLE 传输、状态字上报与自检。
- MCU 端不做 HRV、房颤、SpO2 等复杂医学结论，不做高阶诊断推断，只保留原始数据质量与同步性。
- 手机端依据 `frame_id + t_base_us + 固定采样间隔 + ppg_phase_us` 重建统一时间轴，并完成滤波、特征提取、风险筛查与 AI 报告。

核心设计依据：

- 方案文档明确要求“高质量原始数据 + 统一时间轴 + 稳定 BLE 上传”。
- MAX30102 按本地数据手册寄存器实现：FIFO 32 深度、SpO2 模式双通道、400 sps、3 字节左对齐原始样本、FIFO 指针和溢出计数显式处理。
- AD8232 侧仅做 GPIO/ADC 采集与 lead-off 状态读取；`SDN` 极性做成可切换宏，默认按 `nSDN` 有效低实现。

## 2. 引脚定义

### AD8232

- `OUTPUT -> GPIO4 (ADC1_CH3)`
- `LO+    -> GPIO5`
- `LO-    -> GPIO6`
- `SDN    -> GPIO7`

### MAX30102

- `SCL -> GPIO17`
- `SDA -> GPIO18`
- `INT -> GPIO21`

### 约束说明

- 未占用 `GPIO0 / GPIO3 / GPIO45 / GPIO46` 作为业务 IO。
- ECG 模拟量走 `ADC1`，未使用 `ADC2`。
- MAX30102 I2C 和 INT 的内部上拉在代码中关闭，避免裸芯片 1.8V 场景下被 ESP32-S3 内部 3.3V 上拉误拉高。

## 3. MAX30102 裸芯片 vs 模块板

### 裸 MAX30102 芯片

- `VDD` 必须为 `1.8V`。
- `VLED+` 可为 `3.3V`。
- `SCL/SDA/INT` 若直连 ESP32-S3 3.3V IO，需要电平转换或至少采用 1.8V 侧上拉并确保 VIH 兼容。
- 代码层已假设 I2C/INT 为开漏兼容接口，不打开内部上拉。

### MAX30102 模块板

- 常见模块板通常已带稳压/电平兼容/上拉。
- 本驱动按照“寄存器兼容”实现，不绑定某家模块板的电源拓扑。
- 若模块板自带 4.7k 上拉到 3.3V，可直接使用；若上拉到 1.8V，则需确认 ESP32-S3 输入高电平裕量。

## 4. AD8232 `SDN` 极性说明

- 根目录 `Kconfig.projbuild` 提供 `APP_AD8232_SDN_ACTIVE_LOW` 与 `APP_AD8232_SDN_ACTIVE_HIGH` 二选一。
- 默认选择 `APP_AD8232_SDN_ACTIVE_LOW`，原因是本地 AD8232 板参考资料明确标注为 `nSDN`。
- 如果你使用的模块板把 `SDN` 做了板级反相，可在 `menuconfig` 中切到 `active high`，无需改驱动代码。

## 5. 统一时基与同步设计

- `components/timebase` 使用 `GPTimer` 建立 `1 MHz` 全局单调递增计数，分辨率 `1 us`。
- ECG 侧使用独立 `GPTimer` 周期节拍驱动，固定 `250 Hz` 采样。
- PPG 侧使用 MAX30102 `SpO2 mode` 双通道 `400 sps`，默认 `INT + FIFO` 批量读取；若 INT 超时则自动轮询 FIFO 兜底。
- ECG 帧时间基准定义为每 40 ms 帧中的第一个 ECG 样本时间，即 `t_base_us`。

手机端时间重建公式：

- `ECG(i) = t_base_us + i * 4000 us`
- `PPG(j) = t_base_us + ppg_phase_us + j * 2500 us`

其中：

- `ppg_phase_us`：手机端重建使用的跨模态相位偏移，默认 `1250 us`。
- `ppg_latency_us`：固件内部用于 PPG FIFO 批量读取时的事件时间回推估计，默认 `5000 us`。

## 6. MAX30102 默认寄存器策略

按本地 MAX30102 数据手册实现：

- `MODE = SpO2 mode (0x03)`，双 LED 双通道。
- `Sample rate = 400 sps`。
- `Pulse width = 215 us`。
- 选择 215 us 的理由：手册 Table 7 给出 215 us 对应 17-bit ADC 分辨率，同时仍保留 400 sps 采样余量；相比 411 us，更适合作为功耗、吞吐与动态范围之间的保守默认值。
- `ADC range = 16384 nA`，默认偏保守，优先防止不同手机壳光学结构下的直流饱和。
- `LED1/LED2 = 0x24`，约等效中低电流起始点，避免一上电就过亮导致反射饱和。
- `FIFO_A_FULL = 0x0F`，配合 almost-full interrupt 进行批量读。
- FIFO rollover 默认关闭，保留丢样可观测性。

如果现场测试发现 IR/Red 过小或过饱和，优先调：

- `APP_MAX30102_RED_LED_PA`
- `APP_MAX30102_IR_LED_PA`
- `ADC range`
- 手机壳内的遮光结构与按压程度

## 7. 40 ms 帧格式

单帧原始负载固定 132 字节：

1. `frame_id` `uint16_t`
2. `t_base_us` `uint64_t`
3. `ecg_count` `uint8_t = 10`
4. `ppg_count` `uint8_t = 16`
5. `state_flags` `uint16_t`
6. `ecg_raw[10]` `uint16_t`
7. `ppg_red[16]` `3 bytes * 16`
8. `ppg_ir[16]` `3 bytes * 16`
9. `crc16` `uint16_t`

CRC 选型：

- `CRC16 / CCITT-FALSE`
- Polynomial = `0x1021`
- Init = `0xFFFF`
- 不反射、不异或输出

因为 ATT MTU 不一定总是 247，数据特征值通知外层又增加了一个 8 字节分包头：

- `protocol_version`
- `message_type = 0xA1`
- `frame_id`
- `segment_index`
- `segment_count`
- `payload_len`

当 MTU 足够大时，`segment_count = 1`；否则手机端按 `frame_id + segment_index` 重组整帧后再做 CRC 校验。

## 8. `state_flags` 定义

- `bit0` `ecg_leads_off_any`
- `bit1` `ecg_lo_plus`
- `bit2` `ecg_lo_minus`
- `bit3` `ppg_fifo_overflow`
- `bit4` `ppg_int_timeout`
- `bit5` `adc_saturated`
- `bit6` `ble_backpressure`
- `bit7` `sensor_ready`
- `bit8` `finger_detected`
- 其余保留

其中 `bit3/4/5/6` 在每帧打包后会按“瞬时告警”清零，`lead-off / sensor-ready / finger-detected` 作为当前状态保留。

## 9. BLE 服务说明

自定义 GATT Service 包含 3 个特征值：

- `Data Notify Characteristic`
  - 仅 Notify
  - 发送分包后的二进制原始帧
- `Control Characteristic`
  - Read / Write / Write Without Response
  - 可读当前配置快照
  - 写入控制 opcode
- `Status Characteristic`
  - Read / Notify
  - 输出自检结果、状态位、计数器、高水位和当前配置

Control opcode：

- `0x01` start streaming
- `0x02` stop streaming
- `0x10` set LED PA: `[opcode, red_pa, ir_pa]`
- `0x11` set `ppg_phase_us`: `[opcode, int32_le]`
- `0x12` set `ppg_latency_us`: `[opcode, int32_le]`
- `0x13` set temperature enable: `[opcode, 0/1]`
- `0x14` set log level: `[opcode, 0..5]`
- `0x15` set PPG mode: `[opcode, 0 poll / 1 int]`

## 10. 启动自检

固件启动后执行：

- Timebase 初始化和单调性检查
- AD8232 GPIO / ADC 采样链路检查
- MAX30102 `PART_ID` 通信检查
- MAX30102 默认配置回读校验
- NimBLE 栈初始化检查

运行时持续监控：

- ECG lead-off
- MAX30102 FIFO overflow
- MAX30102 INT timeout
- I2C 读写失败计数
- ADC 饱和计数
- BLE backpressure / 丢帧
- ECG/PPG ringbuffer 高水位与丢样计数
- 帧序号连续性

## 11. 为什么 MCU 端不做复杂医学算法

这不是功能偷懒，而是系统边界刻意这样设计：

- 保真优先：原始 ECG/PPG 上传后，手机端可离线重复分析，避免 MCU 端先做不可逆处理。
- 风险隔离：复杂医学结论留在手机端和上层模型，更容易做版本升级、模型校准和合规边界控制。
- 算力分配更合理：ESP32-S3 负责确定性实时任务，手机负责重计算和 AI 报告。
- 答辩口径更稳：硬件端强调“采得准、传得稳、时基统一”，不在 MCU 端过度承诺诊断能力。

## 12. `ppg_phase_us / ppg_latency_us` 标定建议

### `ppg_phase_us`

用于手机端时间轴重建，建议这样标定：

1. 固定手机壳、手指接触和 ECG 电极位置。
2. 连续采集至少 30 秒 ECG+PPG 原始数据。
3. 手机端先按默认 `1250 us` 还原。
4. 观察多个心搏中 ECG R 峰到 PPG 上升沿/波脚的时间一致性。
5. 若所有 PPG 点整体偏早或偏晚，线性调整 `ppg_phase_us`，直到多拍对齐最稳定。

### `ppg_latency_us`

用于固件内部回推 FIFO 批量读取对应的样本时间，仅影响调试估计，不直接写入数据帧：

1. 保持 `INT + FIFO` 模式。
2. 观察同一批样本估计时间是否出现周期性前后跳动。
3. 若每次 FIFO 读取后估计时间整体滞后，可适当减小 `ppg_latency_us`；若偏早则增大。
4. 经验上先从 `5000 us` 起步，再根据实际批量到达时延微调。

## 13. 编译方式

推荐环境：ESP-IDF `5.2+`。

Windows 典型命令：

```powershell
C:\Espressif\frameworks\esp-idf-v5.2.x\export.bat
cd e:\OPD
idf.py set-target esp32s3
idf.py build
```

如果需要修改 `SDN` 极性、默认 LED 电流、默认 `ppg_phase_us` 等：

```powershell
idf.py menuconfig
```

当前这台机器未检测到已导出的 `idf.py / cmake / ninja`，因此我没有在本地完成最终编译；请在已正确 export 的 ESP-IDF 环境中执行以上命令。

## 14. 烧录方式

```powershell
idf.py -p COMx flash monitor
```

如果首次烧录：

- 确认 ESP32-S3-N16R8 进入下载模式
- USB-UART 驱动正常
- 串口波特率可以先用默认

## 15. BLE 联调与抓包

### 手机联调

- 先连接设备并协商较大 MTU。
- 订阅 `Data Notify` 与 `Status Notify`。
- 读取 `Control Characteristic` 获取当前默认配置。
- 写 `0x01` 启动流。
- 手机端按 `frame_id` 重组分包，再校验 CRC16。

### 抓包建议

- Android 可用 nRF Connect 先做功能联调。
- 若需要空口抓包，可使用支持 BLE sniffer 的适配器配合 Wireshark。
- 重点观察：MTU、通知速率、分包数、是否存在 backpressure 与 frame gap。

## 16. 工程结构

```text
main/
  app_main.c
  app_config.h
  app_tasks.c
  app_tasks.h
  board_pins.h
  packet_protocol.h
components/
  ecg_ad8232/
    CMakeLists.txt
    ad8232.c
    ad8232.h
  ppg_max30102/
    CMakeLists.txt
    max30102.c
    max30102.h
    max30102_regs.h
  timebase/
    CMakeLists.txt
    timebase.c
    timebase.h
  sampler/
    CMakeLists.txt
    ecg_sampler.c
    ecg_sampler.h
    ppg_sampler.c
    ppg_sampler.h
  ble_service/
    CMakeLists.txt
    ble_service.c
    ble_service.h
  state_monitor/
    CMakeLists.txt
    state_monitor.c
    state_monitor.h
  crc/
    CMakeLists.txt
    crc16.c
    crc16.h
CMakeLists.txt
Kconfig.projbuild
sdkconfig.defaults
README.md
```

## 17. 已知风险与调参项

- MAX30102 模块板差异很大，供电/上拉/电平兼容必须实板确认。
- `ADC range` 与 `LED PA` 需要结合你的手机壳光路结构和遮光条件调参。
- `ppg_phase_us` 只是默认初值，不是最终标定值。
- 当前 BLE 分包和重组逻辑假设手机端具备缓存能力；若手机端处理慢，需要进一步优化 APP 侧接收线程。
- ECG 侧默认不上复杂数字滤波，现场若噪声明显，应优先从电极结构、地参考、屏蔽和布线解决。

## 18. Kconfig 说明

本工程项目级 Kconfig 在 `Kconfig.projbuild`，主要暴露：

- BLE 设备名和首选 MTU
- ECG/PPG ringbuffer 容量
- AD8232 `SDN` 极性
- ADC 饱和阈值、lead-off 去抖参数
- MAX30102 默认 LED 电流
- `ppg_phase_us / ppg_latency_us`
- 是否启用温度读取
- 是否默认使用 INT 模式
- 状态通知周期与默认日志级别
