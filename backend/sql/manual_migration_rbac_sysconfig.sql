USE blast_furnace;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(255) NOT NULL,
    role_code VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    permission_name VARCHAR(255) NOT NULL,
    permission_code VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(64) NOT NULL,
    path VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO roles (role_name, role_code, description)
VALUES
('管理员', 'ADMIN', '系统管理员'),
('普通用户', 'USER', '普通用户'),
('管理人员', 'MANAGER', '业务管理人员'),
('运维人员', 'OPERATOR', '系统运维人员')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    description = VALUES(description);

INSERT INTO permissions (parent_id, permission_name, permission_code, type, path)
VALUES
(NULL, '系统与运维', 'menu:system', 'menu', '/blast-furnace/system'),
(NULL, '角色管理', 'menu:system:role', 'menu', '/blast-furnace/system/role-management'),
(NULL, '系统设置', 'menu:system:config', 'menu', '/blast-furnace/system/system-settings'),
(NULL, '角色查看', 'system:role:read', 'button', NULL),
(NULL, '角色维护', 'system:role:write', 'button', NULL),
(NULL, '配置查看', 'system:config:read', 'button', NULL),
(NULL, '配置维护', 'system:config:write', 'button', NULL),
(NULL, '优化读权限', 'optimize:read', 'button', NULL),
(NULL, '优化写权限', 'optimize:write', 'button', NULL),
(NULL, '训练执行权限', 'optimize:train:execute', 'button', NULL),
(NULL, '部署执行权限', 'optimize:deploy:execute', 'button', NULL),
(NULL, '演化执行权限', 'optimize:evolution:execute', 'button', NULL),
(NULL, '对比读权限', 'comparison:read', 'button', NULL),
(NULL, '对比写权限', 'comparison:write', 'button', NULL),
(NULL, '报表读权限', 'report:read', 'button', NULL),
(NULL, '报表写权限', 'report:write', 'button', NULL),
(NULL, '可视化报表生成权限', 'visualization:report:generate', 'button', NULL)
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    parent_id = VALUES(parent_id),
    type = VALUES(type),
    path = VALUES(path);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN (
    'menu:system',
    'menu:system:role',
    'menu:system:config',
    'system:role:read',
    'system:role:write',
    'system:config:read',
    'system:config:write',
    'optimize:read',
    'optimize:write',
    'optimize:train:execute',
    'optimize:deploy:execute',
    'optimize:evolution:execute',
    'comparison:read',
    'comparison:write',
    'report:read',
    'report:write',
    'visualization:report:generate'
  );

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.role_code = 'MANAGER'
  AND p.permission_code IN (
    'optimize:read',
    'optimize:write',
    'optimize:train:execute',
    'optimize:deploy:execute',
    'optimize:evolution:execute',
    'comparison:read',
    'comparison:write',
    'report:read',
    'report:write',
    'visualization:report:generate'
  );

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.role_code = 'OPERATOR'
  AND p.permission_code IN (
    'optimize:read',
    'optimize:deploy:execute',
    'comparison:read',
    'report:read',
    'visualization:report:generate'
  );

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p
WHERE r.role_code = 'USER'
  AND p.permission_code IN (
    'optimize:read',
    'comparison:read',
    'report:read'
  );

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.role_code COLLATE utf8mb4_unicode_ci = u.role COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value VARCHAR(2048) NOT NULL,
    config_name VARCHAR(255) NOT NULL,
    config_group VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (config_key, config_value, config_name, config_group, description)
VALUES
('system_name', '高炉生产参数优化系统', '系统名称', 'SYSTEM_CONFIG', '用于页面展示的系统名称'),
('system_default_furnace_id', 'BF-001', '默认高炉编号', 'SYSTEM_CONFIG', '未指定高炉时使用的默认值'),
('system_timezone', 'Asia/Shanghai', '系统时区', 'SYSTEM_CONFIG', '用于时间展示与统计对齐'),
('system_ui_refresh_seconds', '10', '前端默认刷新间隔(秒)', 'SYSTEM_CONFIG', '用于前端轮询/刷新默认值'),
('evo_max_iterations', '30', '进化计算最大代数', 'ALGO_CONFIG', '限制进化算法的最高迭代次数'),
('evo_max_population', '40', '最大种群规模上限', 'ALGO_CONFIG', '限制种群规模上限'),
('alarm_push_interval', '60', '告警推送间隔(秒)', 'ALARM_CONFIG', '相同告警在规定时间内不重复推送'),
('alarm_global_enable', 'true', '开启全局系统告警', 'ALARM_CONFIG', '关闭后将停止WebSocket异常推送'),
('data_retention_days', '90', '生产数据保留天数', 'STORAGE_CONFIG', '超过此天数的热数据将被定时清理(0为不清理)')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    config_name = VALUES(config_name),
    config_group = VALUES(config_group),
    description = VALUES(description);
