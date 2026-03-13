# 数据库表结构设计文档

## 1. 认证模块

### 1.1 用户表 (`users`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 用户ID |
| `username` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | 用户名 |
| `password` | `VARCHAR(255)` | `NOT NULL` | 密码（加密存储） |
| `email` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | 邮箱 |
| `role` | `VARCHAR(255)` | `NOT NULL` | 角色（ADMIN/USER） |
| `enabled` | `BOOLEAN` | `NOT NULL, DEFAULT TRUE` | 是否启用 |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |

## 2. 数据管理模块

### 2.1 数据表 (`data_records`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 数据ID |
| `data_content` | `JSON` | `NOT NULL` | 数据内容（JSON格式） |
| `submit_time` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 提交时间 |
| `submit_user_id` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 提交用户ID |
| `status` | `VARCHAR(255)` | `NOT NULL, DEFAULT 'pending'` | 数据状态（pending/processed/rejected） |
| `description` | `TEXT` | | 数据描述 |

### 2.2 文件表 (`files`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 文件ID |
| `file_name` | `VARCHAR(255)` | `NOT NULL` | 文件名 |
| `file_path` | `VARCHAR(255)` | `NOT NULL` | 文件存储路径 |
| `file_size` | `BIGINT` | `NOT NULL` | 文件大小（字节） |
| `upload_time` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 上传时间 |
| `upload_user_id` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 上传用户ID |
| `file_type` | `VARCHAR(255)` | `NOT NULL` | 文件类型 |
| `status` | `VARCHAR(255)` | `NOT NULL, DEFAULT 'uploaded'` | 文件状态（uploaded/processed） |

## 3. 存储设备模块

### 3.1 存储设备表 (`storage_devices`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 设备ID |
| `name` | `VARCHAR(255)` | `NOT NULL` | 设备名称 |
| `type` | `VARCHAR(255)` | `NOT NULL` | 设备类型（SSD/HDD/NAS） |
| `status` | `VARCHAR(255)` | `NOT NULL` | 设备状态（online/offline/maintenance） |
| `capacity` | `INT` | `NOT NULL` | 总容量（GB） |
| `used` | `INT` | `NOT NULL` | 已使用容量（GB） |
| `remaining` | `INT` | `NOT NULL` | 剩余容量（GB） |
| `usage_percentage` | `INT` | `NOT NULL` | 使用率（%） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |

## 4. 可视化分析模块

### 4.1 分析数据表 (`analysis_data`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 分析数据ID |
| `metric_name` | `VARCHAR(255)` | `NOT NULL` | 指标名称 |
| `metric_value` | `DOUBLE` | `NOT NULL` | 指标值 |
| `timestamp` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 数据时间戳 |
| `device_id` | `BIGINT` | `FOREIGN KEY REFERENCES storage_devices(id)` | 设备ID（可选） |
| `status` | `VARCHAR(255)` | `NOT NULL, DEFAULT 'normal'` | 数据状态（normal/warning/error） |
| `description` | `TEXT` | | 数据描述 |

## 5. 优化模型模块

### 5.1 优化任务表 (`optimization_tasks`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 任务ID |
| `task_id` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | 任务唯一标识 |
| `task_name` | `VARCHAR(255)` | `NOT NULL` | 任务名称 |
| `parameters` | `JSON` | `NOT NULL` | 优化参数（JSON格式） |
| `status` | `VARCHAR(255)` | `NOT NULL, DEFAULT 'pending'` | 任务状态（pending/running/completed/failed） |
| `progress` | `INT` | `NOT NULL, DEFAULT 0` | 任务进度（%） |
| `start_time` | `TIMESTAMP` | | 开始时间 |
| `end_time` | `TIMESTAMP` | | 结束时间 |
| `created_by` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 创建用户ID |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 创建时间 |

### 5.2 优化结果表 (`optimization_results`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 结果ID |
| `task_id` | `VARCHAR(255)` | `NOT NULL, FOREIGN KEY REFERENCES optimization_tasks(task_id)` | 任务唯一标识 |
| `result_data` | `JSON` | `NOT NULL` | 优化结果（JSON格式） |
| `validation_result` | `BOOLEAN` | | 验证结果（成功/失败） |
| `validation_message` | `TEXT` | | 验证消息 |
| `saved_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 保存时间 |
| `saved_by` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 保存用户ID |

## 6. 方案对比与预警模块

### 6.1 对比数据表 (`comparison_data`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 对比ID |
| `comparison_name` | `VARCHAR(255)` | `NOT NULL` | 对比名称 |
| `data_source_1` | `JSON` | `NOT NULL` | 数据源1（JSON格式） |
| `data_source_2` | `JSON` | `NOT NULL` | 数据源2（JSON格式） |
| `comparison_result` | `JSON` | `NOT NULL` | 对比结果（JSON格式） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `created_by` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 创建用户ID |

### 6.2 预警设置表 (`warning_settings`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 设置ID |
| `metric_name` | `VARCHAR(255)` | `NOT NULL` | 指标名称 |
| `threshold_type` | `VARCHAR(255)` | `NOT NULL` | 阈值类型（min/max/range） |
| `threshold_value` | `JSON` | `NOT NULL` | 阈值值（JSON格式） |
| `warning_level` | `VARCHAR(255)` | `NOT NULL, DEFAULT 'warning'` | 预警级别（info/warning/error） |
| `enabled` | `BOOLEAN` | `NOT NULL, DEFAULT TRUE` | 是否启用 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |
| `updated_by` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 更新用户ID |

### 6.3 预警记录表 (`warning_records`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 预警ID |
| `warning_setting_id` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES warning_settings(id)` | 预警设置ID |
| `metric_name` | `VARCHAR(255)` | `NOT NULL` | 指标名称 |
| `actual_value` | `DOUBLE` | `NOT NULL` | 实际值 |
| `threshold_value` | `JSON` | `NOT NULL` | 阈值值（JSON格式） |
| `warning_level` | `VARCHAR(255)` | `NOT NULL` | 预警级别（info/warning/error） |
| `status` | `VARCHAR(255)` | `NOT NULL, DEFAULT 'unhandled'` | 预警状态（unhandled/handled/ignored） |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `handled_at` | `TIMESTAMP` | | 处理时间 |
| `handled_by` | `BIGINT` | `FOREIGN KEY REFERENCES users(id)` | 处理用户ID |
| `description` | `TEXT` | | 预警描述 |

## 7. 系统管理模块

### 7.1 系统设置表 (`system_settings`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 设置ID |
| `setting_key` | `VARCHAR(255)` | `NOT NULL, UNIQUE` | 设置键 |
| `setting_value` | `JSON` | `NOT NULL` | 设置值（JSON格式） |
| `description` | `TEXT` | | 设置描述 |
| `updated_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |
| `updated_by` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 更新用户ID |

### 7.2 操作日志表 (`operation_logs`)

| 字段名 | 数据类型 | 约束 | 描述 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | `PRIMARY KEY, AUTO_INCREMENT` | 日志ID |
| `user_id` | `BIGINT` | `NOT NULL, FOREIGN KEY REFERENCES users(id)` | 用户ID |
| `action` | `VARCHAR(255)` | `NOT NULL` | 操作类型（login/logout/create/update/delete） |
| `resource` | `VARCHAR(255)` | `NOT NULL` | 操作资源（user/data/device） |
| `details` | `JSON` | `NOT NULL` | 操作详情（JSON格式） |
| `ip_address` | `VARCHAR(255)` | `NOT NULL` | 操作IP地址 |
| `created_at` | `TIMESTAMP` | `NOT NULL, DEFAULT CURRENT_TIMESTAMP` | 操作时间 |

## 8. 数据库关系图

```
+------------------+       +------------------+       +------------------+
|     users        |       |   data_records   |       |      files       |
+------------------+       +------------------+       +------------------+
| id (PK)          |<----+ | id (PK)          |       | id (PK)          |
| username         |     | | data_content     |       | file_name        |
| password         |     | | submit_time      |       | file_path        |
| email            |     | | submit_user_id (FK) |----+ | upload_user_id (FK) |
| role             |     | | status           |       | file_size        |
| enabled          |     | | description      |       | upload_time      |
| created_at       |     | +------------------+       | file_type        |
| updated_at       |     |                         | | status           |
+------------------+     |                         | +------------------+
                         |                         |
+------------------+     |                         | +------------------+
| storage_devices  |     |                         | | analysis_data    |
+------------------+     |                         | +------------------+
| id (PK)          |<----+-------------------------+ | id (PK)          |
| name             |       |                         | metric_name      |
| type             |       |                         | metric_value     |
| status           |       |                         | timestamp        |
| capacity         |       |                         | device_id (FK)   |
| used             |       |                         | status           |
| remaining        |       |                         | description      |
| usage_percentage |       |                         | +------------------+
| created_at       |       |                         |
| updated_at       |       |                         | +------------------+
+------------------+       |                         | | optimization_tasks |
                           |                         | +------------------+
+------------------+       |                         | | id (PK)          |
| warning_settings |       |                         | | task_id          |
+------------------+       |                         | | task_name        |
| id (PK)          |<-----+                         | | parameters       |
| metric_name      |       |                         | | status           |
| threshold_type   |       |                         | | progress         |
| threshold_value  |       |                         | | start_time       |
| warning_level    |       |                         | | end_time         |
| enabled          |       |                         | | created_by (FK)  |
| updated_at       |       |                         | | created_at       |
| updated_by (FK)  |-------+                         | +------------------+
+------------------+                                 |
                                                     | +------------------+
+------------------+                                 | | optimization_results |
| warning_records  |                                 | +------------------+
+------------------+                                 | | id (PK)          |
| id (PK)          |                                 | | task_id (FK)     |
| warning_setting_id (FK) |--------------------------+ | result_data      |
| metric_name      |                                 | | validation_result |
| actual_value     |                                 | | validation_message |
| threshold_value  |                                 | | saved_at         |
| warning_level    |                                 | | saved_by (FK)    |
| status           |                                 | +------------------+
| created_at       |                                 |
| handled_at       |                                 | +------------------+
| handled_by (FK)  |---------------------------------+ | comparison_data  |
| description      |                                 | +------------------+
+------------------+                                 | | id (PK)          |
                                                     | | comparison_name  |
+------------------+                                 | | data_source_1    |
| system_settings  |                                 | | data_source_2    |
+------------------+                                 | | comparison_result |
| id (PK)          |                                 | | created_at       |
| setting_key      |                                 | | created_by (FK)  |
| setting_value    |                                 | +------------------+
| description      |                                 |
| updated_at       |                                 | +------------------+
| updated_by (FK)  |---------------------------------+ | operation_logs   |
+------------------+                                 | +------------------+
                                                     | | id (PK)          |
                                                     | | user_id (FK)     |
                                                     | | action           |
                                                     | | resource         |
                                                     | | details          |
                                                     | | ip_address       |
                                                     | | created_at       |
                                                     | +------------------+
```

## 9. 总结

本数据库表结构设计文档基于后端项目的功能需求，设计了完整的数据库表结构，包括认证模块、数据管理模块、存储设备模块、可视化分析模块、优化模型模块、方案对比与预警模块和系统管理模块的表结构。这些表结构能够满足后端项目的所有功能需求，包括用户认证、数据管理、存储设备管理、可视化分析、优化模型、方案对比与预警和系统管理等功能。

数据库表结构设计遵循了以下原则：

1. **规范化原则**：表结构设计符合数据库规范化原则，避免数据冗余和不一致。
2. **完整性原则**：通过主键、外键、非空约束等保证数据的完整性。
3. **性能原则**：合理设计索引和表结构，提高数据库查询和操作的性能。
4. **可扩展性原则**：表结构设计具有良好的可扩展性，能够适应未来业务需求的变化。

本数据库表结构设计文档可以作为后端项目数据库开发的参考依据，确保数据库设计与后端代码的一致性，提高开发效率和代码质量。