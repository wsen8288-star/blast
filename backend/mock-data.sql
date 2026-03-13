-- 数据库模拟数据插入SQL脚本

-- 使用数据库
USE blast_furnace;

-- 1. 认证模块
-- 为用户表添加模拟数据
INSERT INTO users (username, password, email, role, enabled) VALUES 
('admin', '$2a$10$e1F4yMfZ2e6m8K8H9I7J6K7L8M9N8O7P6Q5R4S3T2U1V0W9X8Y7Z6A5B4C3D2E1F0', 'admin@example.com', 'ADMIN', TRUE),
('user', '$2a$10$e1F4yMfZ2e6m8K8H9I7J6K7L8M9N8O7P6Q5R4S3T2U1V0W9X8Y7Z6A5B4C3D2E1F0', 'user@example.com', 'USER', TRUE),
('manager', '$2a$10$e1F4yMfZ2e6m8K8H9I7J6K7L8M9N8O7P6Q5R4S3T2U1V0W9X8Y7Z6A5B4C3D2E1F0', 'manager@example.com', 'MANAGER', TRUE),
('operator', '$2a$10$e1F4yMfZ2e6m8K8H9I7J6K7L8M9N8O7P6Q5R4S3T2U1V0W9X8Y7Z6A5B4C3D2E1F0', 'operator@example.com', 'OPERATOR', TRUE)
ON DUPLICATE KEY UPDATE password = VALUES(password);

-- 2. 数据管理模块
-- 为数据表添加模拟数据
INSERT INTO data_records (data_content, submit_time, submit_user_id, status, description) VALUES 
('{"temperature": 1250, "pressure": 2.5, "flow_rate": 150, "composition": {"fe": 95, "c": 4.5, "si": 0.5}}', NOW() - INTERVAL 1 HOUR, 1, 'processed', '高炉生产数据记录'),
('{"temperature": 1260, "pressure": 2.6, "flow_rate": 155, "composition": {"fe": 94.8, "c": 4.6, "si": 0.6}}', NOW() - INTERVAL 2 HOUR, 2, 'processed', '高炉生产数据记录'),
('{"temperature": 1240, "pressure": 2.4, "flow_rate": 145, "composition": {"fe": 95.2, "c": 4.4, "si": 0.4}}', NOW() - INTERVAL 3 HOUR, 3, 'pending', '高炉生产数据记录'),
('{"temperature": 1270, "pressure": 2.7, "flow_rate": 160, "composition": {"fe": 94.5, "c": 4.7, "si": 0.8}}', NOW() - INTERVAL 4 HOUR, 4, 'rejected', '高炉生产数据记录')
ON DUPLICATE KEY UPDATE data_content = VALUES(data_content), status = VALUES(status);

-- 为文件表添加模拟数据
INSERT INTO files (file_name, file_path, file_size, upload_time, upload_user_id, file_type, status) VALUES 
('高炉生产数据.xlsx', '/uploads/2024/01/22/123456.xlsx', 1024000, NOW() - INTERVAL 1 HOUR, 1, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'processed'),
('高炉设备维护记录.pdf', '/uploads/2024/01/22/789012.pdf', 2048000, NOW() - INTERVAL 2 HOUR, 2, 'application/pdf', 'processed'),
('高炉工艺参数配置.json', '/uploads/2024/01/22/345678.json', 512000, NOW() - INTERVAL 3 HOUR, 3, 'application/json', 'uploaded'),
('高炉能耗分析报告.docx', '/uploads/2024/01/22/901234.docx', 1536000, NOW() - INTERVAL 4 HOUR, 4, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'uploaded')
ON DUPLICATE KEY UPDATE file_path = VALUES(file_path), status = VALUES(status);

-- 3. 存储设备模块
-- 为存储设备表添加模拟数据
INSERT INTO storage_devices (name, type, status, capacity, used, remaining, usage_percentage) VALUES 
('主存储设备', 'SSD', 'online', 500, 350, 150, 70),
('备份存储设备', 'HDD', 'online', 500, 300, 200, 60),
('归档存储设备', 'NAS', 'offline', 2000, 1000, 1000, 50),
('临时存储设备', 'SSD', 'maintenance', 250, 150, 100, 60),
('测试存储设备', 'HDD', 'online', 1000, 400, 600, 40)
ON DUPLICATE KEY UPDATE used = VALUES(used), remaining = VALUES(remaining), usage_percentage = VALUES(usage_percentage);

-- 4. 可视化分析模块
-- 为分析数据表添加模拟数据
INSERT INTO analysis_data (metric_name, metric_value, timestamp, device_id, status, description) VALUES 
('temperature', 1250, NOW() - INTERVAL 1 HOUR, 1, 'normal', '高炉温度数据'),
('temperature', 1260, NOW() - INTERVAL 2 HOUR, 1, 'normal', '高炉温度数据'),
('temperature', 1270, NOW() - INTERVAL 3 HOUR, 1, 'warning', '高炉温度数据'),
('temperature', 1280, NOW() - INTERVAL 4 HOUR, 1, 'error', '高炉温度数据'),
('pressure', 2.5, NOW() - INTERVAL 1 HOUR, 1, 'normal', '高炉压力数据'),
('pressure', 2.6, NOW() - INTERVAL 2 HOUR, 1, 'normal', '高炉压力数据'),
('pressure', 2.7, NOW() - INTERVAL 3 HOUR, 1, 'warning', '高炉压力数据'),
('pressure', 2.8, NOW() - INTERVAL 4 HOUR, 1, 'error', '高炉压力数据'),
('flow_rate', 150, NOW() - INTERVAL 1 HOUR, 2, 'normal', '高炉流量数据'),
('flow_rate', 155, NOW() - INTERVAL 2 HOUR, 2, 'normal', '高炉流量数据'),
('flow_rate', 160, NOW() - INTERVAL 3 HOUR, 2, 'warning', '高炉流量数据'),
('flow_rate', 165, NOW() - INTERVAL 4 HOUR, 2, 'error', '高炉流量数据')
ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value), status = VALUES(status);

-- 5. 优化模型模块
-- 为优化任务表添加模拟数据
INSERT INTO optimization_tasks (task_id, task_name, parameters, status, progress, start_time, end_time, created_by) VALUES 
('task-001', '高炉参数优化', '{"temperature": {"min": 1200, "max": 1300}, "pressure": {"min": 2.0, "max": 3.0}, "flow_rate": {"min": 140, "max": 170}}', 'completed', 100, NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 4 HOUR, 1),
('task-002', '能耗优化', '{"energy_consumption": {"target": 1000}, "production_rate": {"min": 90}}', 'running', 50, NOW() - INTERVAL 2 HOUR, NULL, 2),
('task-003', '产品质量优化', '{"fe_content": {"min": 95}, "c_content": {"target": 4.5}}', 'pending', 0, NULL, NULL, 3),
('task-004', '设备寿命优化', '{"maintenance_interval": {"target": 30}, "failure_rate": {"max": 0.01}}', 'failed', 20, NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 5 HOUR, 4)
ON DUPLICATE KEY UPDATE status = VALUES(status), progress = VALUES(progress);

-- 为优化结果表添加模拟数据
INSERT INTO optimization_results (task_id, result_data, validation_result, validation_message, saved_by) VALUES 
('task-001', '{"optimal_parameters": {"temperature": 1250, "pressure": 2.5, "flow_rate": 155}, "expected_improvement": 10}', TRUE, '优化结果验证成功', 1),
('task-004', '{"optimal_parameters": {"maintenance_interval": 25, "failure_rate": 0.008}, "expected_improvement": 5}', FALSE, '优化结果验证失败：设备参数超出范围', 4)
ON DUPLICATE KEY UPDATE result_data = VALUES(result_data), validation_result = VALUES(validation_result);

-- 6. 方案对比与预警模块
-- 为对比数据表添加模拟数据
INSERT INTO comparison_data (comparison_name, data_source_1, data_source_2, comparison_result, created_by) VALUES 
('方案1 vs 方案2', '{"name": "方案1", "parameters": {"temperature": 1250, "pressure": 2.5, "flow_rate": 155}}', '{"name": "方案2", "parameters": {"temperature": 1260, "pressure": 2.6, "flow_rate": 160}}', '{"winner": "方案1", "reason": "能耗更低，产品质量相当"}', 1),
('当前方案 vs 历史最佳', '{"name": "当前方案", "parameters": {"temperature": 1250, "pressure": 2.5, "flow_rate": 155}}', '{"name": "历史最佳", "parameters": {"temperature": 1240, "pressure": 2.4, "flow_rate": 150}}', '{"winner": "历史最佳", "reason": "能耗更低，产品质量相当"}', 2)
ON DUPLICATE KEY UPDATE comparison_result = VALUES(comparison_result);

-- 为预警设置表添加模拟数据
INSERT INTO warning_settings (metric_name, threshold_type, threshold_value, warning_level, enabled, updated_by) VALUES 
('temperature', 'max', '{"warning": 1270, "error": 1290}', 'warning', TRUE, 1),
('pressure', 'max', '{"warning": 2.7, "error": 2.9}', 'warning', TRUE, 1),
('flow_rate', 'max', '{"warning": 160, "error": 170}', 'warning', TRUE, 1),
('energy_consumption', 'max', '{"warning": 1100, "error": 1200}', 'warning', TRUE, 1),
('production_rate', 'min', '{"warning": 85, "error": 80}', 'warning', TRUE, 1)
ON DUPLICATE KEY UPDATE threshold_value = VALUES(threshold_value);

-- 为预警记录表添加模拟数据
INSERT INTO warning_records (warning_setting_id, metric_name, actual_value, threshold_value, warning_level, status, created_at, handled_at, handled_by, description) VALUES 
(1, 'temperature', 1275, '{"warning": 1270, "error": 1290}', 'warning', 'handled', NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 4 HOUR, 1, '高炉温度超出预警阈值'),
(2, 'pressure', 2.75, '{"warning": 2.7, "error": 2.9}', 'warning', 'unhandled', NOW() - INTERVAL 3 HOUR, NULL, NULL, '高炉压力超出预警阈值'),
(3, 'flow_rate', 165, '{"warning": 160, "error": 170}', 'warning', 'ignored', NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 1 HOUR, 2, '高炉流量超出预警阈值'),
(1, 'temperature', 1295, '{"warning": 1270, "error": 1290}', 'error', 'handled', NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 5 HOUR, 3, '高炉温度超出错误阈值')
ON DUPLICATE KEY UPDATE status = VALUES(status), handled_at = VALUES(handled_at), handled_by = VALUES(handled_by);

-- 7. 系统管理模块
-- 为系统设置表添加模拟数据
INSERT INTO system_settings (setting_key, setting_value, description, updated_by) VALUES 
('system.name', '{"value": "Blast Furnace System"}', '系统名称', 1),
('system.version', '{"value": "1.0.0"}', '系统版本', 1),
('system.timezone', '{"value": "Asia/Shanghai"}', '系统时区', 1),
('security.token.expiry', '{"value": 3600}', '令牌过期时间（秒）', 1),
('storage.max_size', '{"value": 1024000000}', '存储最大容量（字节）', 1),
('analysis.data.retention', '{"value": 30}', '分析数据保留天数', 1),
('optimization.timeout', '{"value": 3600}', '优化任务超时时间（秒）', 1)
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

-- 为操作日志表添加模拟数据
INSERT INTO operation_logs (user_id, action, resource, details, ip_address) VALUES 
(1, 'login', 'user', '{"username": "admin"}', '192.168.1.100'),
(1, 'create', 'data_record', '{"data_id": 1, "description": "高炉生产数据记录"}', '192.168.1.100'),
(2, 'login', 'user', '{"username": "user"}', '192.168.1.101'),
(2, 'update', 'storage_device', '{"device_id": 1, "status": "online"}', '192.168.1.101'),
(3, 'login', 'user', '{"username": "manager"}', '192.168.1.102'),
(3, 'delete', 'data_record', '{"data_id": 2}', '192.168.1.102'),
(4, 'login', 'user', '{"username": "operator"}', '192.168.1.103'),
(4, 'create', 'optimization_task', '{"task_id": "task-003"}', '192.168.1.103')
ON DUPLICATE KEY UPDATE details = VALUES(details);

-- 提交事务
COMMIT;

-- 完成
SELECT '模拟数据插入完成' AS message;