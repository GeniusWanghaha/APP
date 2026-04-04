# ESP32 工具链安装记录（Windows）

更新时间：2026-04-01

## 1. 安装目标
- 不占用当前项目目录（`D:\optoelectronic_design\APP`）
- 可直接编译/烧录 `OPD` 的 ESP32-S3 固件

## 2. 实际安装位置
- ESP-IDF 根目录：`D:\Espressif`
- IDF 版本：`v5.2.2`
- 工具链目录：`D:\Espressif\tools`
- 激活脚本：`C:\Espressif\tools\Microsoft.v5.2.2.PowerShell_profile.ps1`

## 3. 执行过的安装命令
```powershell
winget install --id Espressif.EIM-CLI --accept-source-agreements --accept-package-agreements --silent
```

```powershell
eim install --path D:\Espressif `
  --esp-idf-json-path D:\Espressif\tools `
  --tool-download-folder-name D:\Espressif\dist `
  --tool-install-folder-name D:\Espressif\tools `
  -t esp32s3 -i v5.2.2 -n true -r true -a true --cleanup true
```

## 4. 使用方式
```powershell
& "C:\Espressif\tools\Microsoft.v5.2.2.PowerShell_profile.ps1"
idf.py --version
```

## 5. 已验证能力
- `idf.py --version` -> `ESP-IDF v5.2.2`
- `idf.py set-target esp32s3` / `idf.py build` 通过
- `idf.py -p COM3 flash` 烧录通过

## 6. 常用命令
```powershell
# 在 OPD 工程目录
& "C:\Espressif\tools\Microsoft.v5.2.2.PowerShell_profile.ps1"
idf.py set-target esp32s3
idf.py build
idf.py -p COM3 flash
```
