ALTER TABLE anomaly_records
  ADD COLUMN handler_content VARCHAR(500) NULL,
  ADD COLUMN handler_user BIGINT NULL;

ALTER TABLE anomaly_records
  MODIFY COLUMN status INT NOT NULL;

ALTER TABLE anomaly_thresholds
  ADD COLUMN tip_offset_pct DOUBLE NULL,
  ADD COLUMN warning_offset_pct DOUBLE NULL,
  ADD COLUMN severe_offset_pct DOUBLE NULL;
