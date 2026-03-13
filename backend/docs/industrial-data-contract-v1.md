# 工业数据契约 v1

## 目标
- 统一采集模拟、预处理、预警、可视化、模型训练的数据口径
- 让“同一条数据”在各模块中使用同一字段名、单位与范围

## 参数基线

| canonical key | 中文名称 | 单位 | 硬限制最小 | 硬限制最大 | 预警最小 | 预警最大 |
|---|---|---:|---:|---:|---:|---:|
| temperature | 温度 | ℃ | 1100 | 1400 | 1150 | 1380 |
| pressure | 压力 | kPa | 100 | 300 | 120 | 280 |
| windVolume | 风量 | m³/h | 3000 | 6000 | 3300 | 5700 |
| coalInjection | 喷煤量 | kg/t | 100 | 220 | 110 | 210 |
| materialHeight | 料面高度 | m | 2 | 6 | 2.3 | 5.7 |
| gasFlow | 煤气流量 | m³/h | 2000 | 5000 | 2300 | 4700 |
| oxygenLevel | 氧气含量 | % | 18 | 25 | 18.5 | 24.5 |
| productionRate | 生产率 | t/h | 20 | 80 | 25 | 75 |
| energyConsumption | 能耗 | kgce/t | 800 | 2000 | 900 | 1800 |
| hotMetalTemperature | 铁水温度 | ℃ | 1420 | 1560 | 1440 | 1540 |
| siliconContent | 硅含量 | % | 0.1 | 1.0 | 0.2 | 0.8 |

## 映射规则
- 上传字段先做 canonical 映射
- 不认识的字段不参与核心算法
- 支持中英文别名，例如：
  - 温度/炉温/furnaceTemperature → temperature
  - 风量 → windVolume
  - 氧气含量/oxygenContent → oxygenLevel

## 质量规则
- 哨兵缺失值：`9999`、`9999.0`、`9999.00`
- 哨兵值在缺失值处理前统一转 `null`
- 严重越界：超出硬限制区间
- 预警：落在预警带之外但仍在硬限制内

