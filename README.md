# CH341PARDemo – Android USB 控制 AD9833 & MCP41010

本示例展示如何在 Android 平板/手机上通过 WCH CH341 USB 转接器同时驱动两款常见的外设：

- **AD9833**：直接数字合成芯片，可输出正弦/三角/方波等波形；
- **MCP41010**：8 位数字电位器，用于调节输出幅度。

整个控制过程完全基于 **GPIO bit-bang**，不依赖 `CH34xStreamSPI4` 等存在问题的 SPI 封装，因而可以稳定运行在 CH341 官方提供的库之上。

---

## 功能特性

- **AD9833 控制**
  - 设置输出频率（默认 25 MHz 晶振，自动换算 28-bit 频率字）
  - 切换波形：正弦、三角、两种方波、关闭输出
  - 默认初始化为 1 kHz 正弦波，支持 FREQ0 通道

- **MCP41010 控制**
  - SeekBar 0~255 调节电阻值（归一化幅度）
  - “写入电位器”按钮一键输出

- **安全可靠**
  - 所有 SPI 时序均由 GPIO 手动驱动，可按需调整 CPOL/CPHA
  - 与逻辑分析仪核对过空闲状态、片选电平、位序
  - 控制逻辑在单线程后台执行，不阻塞 UI

---

## 硬件准备

1. 支持 USB OTG 的 Android 设备
2. WCH CH341/CH347 USB 转接器（Android 端需授权 USB 权限）
3. AD9833 模块 + MCP41010 模块
4. 接线（默认）：
   - D0 → AD9833 FSYNC
   - D3 → AD9833/MCP41010 SCLK
   - D5 → AD9833/MCP41010 MOSI
   - D1 → MCP41010 CS
   - GND → 所有外设共地

> 如需更改片选或时钟映射，可在 `Ad9833Controller` / `Mcp41010Controller` 中调整常量。

---

## 工程结构
```
app/
└── src/main/java/cn/wch/ch341pardemo/
├── MainActivity.java # UI + 控制入口
├── controller/
│ ├── Ad9833Controller.java # AD9833 bit-bang 实现
│ └── Mcp41010Controller.java # MCP41010 bit-bang 实现
└── util/ # 其他工具类
└── src/main/res/layout/
├── activity_main.xml # 主界面 layout
├── ad9833_item.xml # AD9833 控制面板
└── mcp41010_item.xml # MCP41010 面板
```

---

## 构建与运行

1. 克隆仓库并使用 Android Studio 打开项目；
2. 连接实际的 CH341 + AD9833/MCP41010 硬件；
3. 运行或安装 APK，首次使用时需授予 USB 权限。

---

## 使用说明

- **打开设备**：接入硬件后点击主界面底部“打开设备”，若成功会初始化 AD9833（1 kHz 正弦）和 MCP41010（255）。
- **设置频率**：在 AD9833 面板输入 Hz（如 `1000`），点击“设置”即可更新。
- **切换波形**：选择正弦 / 三角 / 方波 / 关闭。
- **调节幅度**：拖动 MCP41010 SeekBar（0~255），再点击“写入电位器”。

逻辑分析仪建议设置 `CPOL=1, CPHA=0（AD9833）` 与 `CPOL=0, CPHA=0（MCP41010）`，MSB First，16-bit 帧，可验证时序正确性。

---

## 常见问题

| 现象 | 解决方案 |
| --- | --- |
| 打开设备失败 | 确认 OTG 已启用、已授权 USB 权限、线路稳固 |
| 频率无变化 | 检查 SPI 模式是否匹配、FSYNC 与硬件接线是否一致 |
| 示波器显示杂波 | 确保逻辑分析仪或示波器与 CH341 共地，探头夹在 MOSI/SCLK/CS 上 |

---

## 致谢

- WCH 官方 CH341 Android SDK
- AD9833 / MCP41010 数据手册与社区资料
- Windows 版 `pych341` 工具带来的时序参考

如需扩展功能（相位控制、EEPROM 保存、可配置 IO 映射等），欢迎 fork 后继续改进。```
