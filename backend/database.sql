-- 数据库表结构创建SQL脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS blast_furnace CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE blast_furnace;

-- 1. 认证模块
-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 数据管理模块
-- 创建数据表
CREATE TABLE IF NOT EXISTS data_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    data_content JSON NOT NULL,
    submit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submit_user_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'pending',
    description TEXT,
    FOREIGN KEY (submit_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建文件表
CREATE TABLE IF NOT EXISTS files (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    upload_user_id BIGINT NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'uploaded',
    FOREIGN KEY (upload_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. 存储设备模块
-- 创建存储设备表
CREATE TABLE IF NOT EXISTS storage_devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    used INT NOT NULL,
    remaining INT NOT NULL,
    usage_percentage INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 可视化分析模块
-- 创建分析数据表
CREATE TABLE IF NOT EXISTS analysis_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DOUBLE NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_id BIGINT,
    status VARCHAR(255) NOT NULL DEFAULT 'normal',
    description TEXT,
    FOREIGN KEY (device_id) REFERENCES storage_devices(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 优化模型模块
-- 创建优化任务表
CREATE TABLE IF NOT EXISTS optimization_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    task_name VARCHAR(255) NOT NULL,
    parameters JSON NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'pending',
    progress INT NOT NULL DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建优化结果表
CREATE TABLE IF NOT EXISTS optimization_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(255) NOT NULL,
    result_data JSON NOT NULL,
    validation_result BOOLEAN,
    validation_message TEXT,
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    saved_by BIGINT NOT NULL,
    FOREIGN KEY (task_id) REFERENCES optimization_tasks(task_id) ON DELETE CASCADE,
    FOREIGN KEY (saved_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 方案对比与预警模块
-- 创建对比数据表
CREATE TABLE IF NOT EXISTS comparison_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comparison_name VARCHAR(255) NOT NULL,
    data_source_1 JSON NOT NULL,
    data_source_2 JSON NOT NULL,
    comparison_result JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT NOT NULL,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建预警设置表
CREATE TABLE IF NOT EXISTS warning_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    metric_name VARCHAR(255) NOT NULL,
    threshold_type VARCHAR(255) NOT NULL,
    threshold_value JSON NOT NULL,
    warning_level VARCHAR(255) NOT NULL DEFAULT 'warning',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建预警记录表
CREATE TABLE IF NOT EXISTS warning_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    warning_setting_id BIGINT NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    actual_value DOUBLE NOT NULL,
    threshold_value JSON NOT NULL,
    warning_level VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'unhandled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP,
    handled_by BIGINT,
    description TEXT,
    FOREIGN KEY (warning_setting_id) REFERENCES warning_settings(id) ON DELETE CASCADE,
    FOREIGN KEY (handled_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 系统管理模块
-- 创建系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    setting_key VARCHAR(255) NOT NULL UNIQUE,
    setting_value JSON NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建操作日志表
CREATE TABLE IF NOT EXISTS operation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action VARCHAR(255) NOT NULL,
    resource VARCHAR(255) NOT NULL,
    details JSON NOT NULL,
    ip_address VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建索引
-- 用户表索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- 数据表索引
CREATE INDEX idx_data_records_submit_user_id ON data_records(submit_user_id);
CREATE INDEX idx_data_records_status ON data_records(status);

-- 文件表索引
CREATE INDEX idx_files_upload_user_id ON files(upload_user_id);
CREATE INDEX idx_files_status ON files(status);

-- 存储设备表索引
CREATE INDEX idx_storage_devices_status ON storage_devices(status);
CREATE INDEX idx_storage_devices_type ON storage_devices(type);

-- 分析数据表索引
CREATE INDEX idx_analysis_data_metric_name ON analysis_data(metric_name);
CREATE INDEX idx_analysis_data_device_id ON analysis_data(device_id);
CREATE INDEX idx_analysis_data_timestamp ON analysis_data(timestamp);

-- 优化任务表索引
CREATE INDEX idx_optimization_tasks_task_id ON optimization_tasks(task_id);
CREATE INDEX idx_optimization_tasks_status ON optimization_tasks(status);
CREATE INDEX idx_optimization_tasks_created_by ON optimization_tasks(created_by);

-- 优化结果表索引
CREATE INDEX idx_optimization_results_task_id ON optimization_results(task_id);
CREATE INDEX idx_optimization_results_saved_by ON optimization_results(saved_by);

-- 对比数据表索引
CREATE INDEX idx_comparison_data_created_by ON comparison_data(created_by);

-- 预警设置表索引
CREATE INDEX idx_warning_settings_metric_name ON warning_settings(metric_name);
CREATE INDEX idx_warning_settings_enabled ON warning_settings(enabled);

-- 预警记录表索引
CREATE INDEX idx_warning_records_warning_setting_id ON warning_records(warning_setting_id);
CREATE INDEX idx_warning_records_status ON warning_records(status);
CREATE INDEX idx_warning_records_created_at ON warning_records(created_at);

-- 系统设置表索引
CREATE INDEX idx_system_settings_setting_key ON system_settings(setting_key);

-- 操作日志表索引
CREATE INDEX idx_operation_logs_user_id ON operation_logs(user_id);
CREATE INDEX idx_operation_logs_action ON operation_logs(action);
CREATE INDEX idx_operation_logs_resource ON operation_logs(resource);
CREATE INDEX idx_operation_logs_created_at ON operation_logs(created_at);

-- 初始化数据
-- 初始化用户表（默认管理员用户）
INSERT INTO users (username, password, email, role, enabled) VALUES 
('admin', '$2a$10$e1F4yMfZ2e6m8K8H9I7J6K7L8M9N8O7P6Q5R4S3T2U1V0W9X8Y7Z6A5B4C3D2E1F0', 'admin@example.com', 'ADMIN', TRUE),
('user', '$2a$10$e1F4yMfZ2e6m8K8H9I7J6K7L8M9N8O7P6Q5R4S3T2U1V0W9X8Y7Z6A5B4C3D2E1F0', 'user@example.com', 'USER', TRUE)
ON DUPLICATE KEY UPDATE password = VALUES(password);

-- 初始化存储设备表
INSERT INTO storage_devices (name, type, status, capacity, used, remaining, usage_percentage) VALUES 
('主存储设备', 'SSD', 'online', 500, 350, 150, 70),
('备份存储设备', 'HDD', 'online', 500, 300, 200, 60),
('归档存储设备', 'NAS', 'offline', 2000, 1000, 1000, 50)
ON DUPLICATE KEY UPDATE status = VALUES(status), used = VALUES(used), remaining = VALUES(remaining), usage_percentage = VALUES(usage_percentage);

-- 初始化系统设置表
INSERT INTO system_settings (setting_key, setting_value, description, updated_by) VALUES 
('system.name', '{"value": "Blast Furnace System"}', '系统名称', 1),
('system.version', '{"value": "1.0.0"}', '系统版本', 1),
('system.timezone', '{"value": "Asia/Shanghai"}', '系统时区', 1),
('security.token.expiry', '{"value": 3600}', '令牌过期时间（秒）', 1)
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_by = VALUES(updated_by);

-- 初始化预警设置表
INSERT INTO warning_settings (metric_name, threshold_type, threshold_value, warning_level, enabled, updated_by) VALUES 
('storage.usage', 'max', '{"value": 80}', 'warning', TRUE, 1),
('storage.usage', 'max', '{"value": 90}', 'error', TRUE, 1),
('system.cpu', 'max', '{"value": 80}', 'warning', TRUE, 1),
('system.memory', 'max', '{"value": 80}', 'warning', TRUE, 1)
ON DUPLICATE KEY UPDATE threshold_value = VALUES(threshold_value), updated_by = VALUES(updated_by);

-- 提交事务
COMMIT;

-- 完成
SELECT '数据库表结构创建完成' AS message;