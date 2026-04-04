# project_module_inventory

生成时间：2026-04-04 15:21:25
项目根目录：`D:/optoelectronic_design/APP`

## 文件树摘要
```text
APP/
├─ OPD/ (硬件端固件)
├─ android_project/ (软件端 Android)
├─ project_root/ (数据集验证与结果)
└─ .vscode/
```

## 扫描统计
- 硬件端文件数：42
- 软件端文件数：1894
- 数据集验证文件数：2786

## 主入口文件
- 硬件端：`OPD/main/app_main.c`, `OPD/main/app_tasks.c`
- 软件端：`android_project/app/src/main/AndroidManifest.xml`, `HomeViewModel.kt`
- 验证端：`project_root/src/run_pipeline.py`, `project_root/run_all.ps1`

## 模块依赖关系
- 硬件端 -> 软件端：BLE 分段帧 + CRC。
- 软件端 -> 验证端：Kotlin/Python 指标对齐。
- 验证端 -> 工程主线：参数与门控策略回灌。
- 关键声明：数据集验证模块只验证算法，不等于整个项目本体。

## 关键文件摘录
### Hardware
- `OPD/README.md` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/main/app_main.c` | role=entry | entry=yes | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/main/app_tasks.c` | role=entry | entry=yes | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/main/app_config.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/main/packet_protocol.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/main/board_pins.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/components/ble_service/ble_service.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/components/state_monitor/state_monitor.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/components/sampler/ecg_sampler.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/components/sampler/ppg_sampler.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C
- `OPD/components/timebase/timebase.h` | role=implementation | entry=no | depends=ESP-IDF/NimBLE/ADC/I2C

### Software
- `android_project/app/src/main/AndroidManifest.xml` | role=entry | entry=yes | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/build.gradle.kts` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/build.gradle.kts` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/data/repository/ble/BlePacketProtocol.kt` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/data/repository/BleHardwareBridgeRepository.kt` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/infrastructure/signal/TimelineReconstructor.kt` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/infrastructure/signal/CardiovascularSignalProcessor.kt` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/infrastructure/signal/BatchCardioAnalyzer.kt` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/ui/viewmodel/HomeViewModel.kt` | role=entry | entry=yes | depends=BLE/GATT/Compose/Kotlin
- `android_project/app/src/main/java/com/photosentinel/health/ui/screens/HomeScreen.kt` | role=implementation | entry=no | depends=BLE/GATT/Compose/Kotlin

### DatasetValidation
- `project_root/README.md` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/requirements.txt` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/run_all.ps1` | role=entry | entry=yes | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/configs/datasets.yaml` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/configs/pipeline.yaml` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/run_pipeline.py` | role=entry | entry=yes | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/iteration_controller.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task1_ecg_rpeak.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task2_hrv.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task3_af.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task4_arrhythmia.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task5_ecg_qt_pwave.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task6_ppg.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task7_resp.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task8_ptt.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/src/evaluation/task9_regression.py` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/outputs/metrics/best_metrics.json` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/outputs/tables/task3_baseline_model_compare.csv` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/outputs/tables/task8_recomputed_metrics.csv` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
- `project_root/datasets/manifests/dataset_inventory.csv` | role=data_or_impl | entry=no | depends=Python/Pandas/WFDB/NeuroKit2
