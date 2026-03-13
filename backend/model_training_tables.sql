-- 模型训练相关表创建SQL脚本

-- 使用数据库
USE blast_furnace;

-- 创建模型配置表
CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_name VARCHAR(255) NOT NULL,
    hidden_layers INT NOT NULL,
    neurons_per_layer VARCHAR(255) NOT NULL,
    activation_function VARCHAR(255) NOT NULL,
    loss_function VARCHAR(255) NOT NULL,
    optimizer VARCHAR(255) NOT NULL,
    dropout_rate DOUBLE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建模型训练表
CREATE TABLE IF NOT EXISTS model_training (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_type VARCHAR(255) NOT NULL,
    training_data VARCHAR(255) NOT NULL,
    epochs INT NOT NULL,
    batch_size INT NOT NULL,
    learning_rate DOUBLE NOT NULL,
    selected_features VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    progress INT NOT NULL,
    current_epoch INT NOT NULL,
    training_loss DOUBLE NOT NULL,
    validation_accuracy DOUBLE NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    model_config_id BIGINT,
    FOREIGN KEY (model_config_id) REFERENCES model_config(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建训练日志表
CREATE TABLE IF NOT EXISTS training_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    training_id BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message TEXT NOT NULL,
    log_level VARCHAR(255) NOT NULL,
    FOREIGN KEY (training_id) REFERENCES model_training(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建索引
CREATE INDEX idx_model_training_model_type ON model_training(model_type);
CREATE INDEX idx_model_training_status ON model_training(status);
CREATE INDEX idx_model_training_start_time ON model_training(start_time);
CREATE INDEX idx_training_log_training_id ON training_log(training_id);
CREATE INDEX idx_training_log_log_level ON training_log(log_level);

-- 提交事务
COMMIT;

-- 完成
SELECT '模型训练相关表创建完成' AS message;
