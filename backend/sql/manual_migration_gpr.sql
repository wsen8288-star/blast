ALTER TABLE model_config ADD COLUMN gpr_length_scale DOUBLE NULL;
ALTER TABLE model_config ADD COLUMN gpr_noise_variance DOUBLE NULL;

DELETE FROM model_training
WHERE model_type NOT IN ('neural_network', 'random_forest', 'gradient_boosting', 'gpr');
