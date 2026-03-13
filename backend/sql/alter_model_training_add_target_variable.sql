ALTER TABLE model_training ADD COLUMN target_variable VARCHAR(64) NULL;

UPDATE model_training SET target_variable = 'productionRate' WHERE target_variable IS NULL OR target_variable = '';
