SET @db_name = DATABASE();

DROP TEMPORARY TABLE IF EXISTS candidate_tables;
CREATE TEMPORARY TABLE candidate_tables (
  table_name VARCHAR(128) PRIMARY KEY
);

INSERT INTO candidate_tables(table_name) VALUES
('analysis_data'),
('comparison_data'),
('data_records'),
('files'),
('optimization_results'),
('optimization_tasks'),
('warning_records'),
('warning_settings'),
('system_settings'),
('operation_logs');

SELECT
  c.table_name,
  t.engine,
  t.table_rows,
  t.create_time,
  t.update_time,
  t.table_collation
FROM candidate_tables c
LEFT JOIN information_schema.tables t
  ON t.table_schema = @db_name
 AND t.table_name = c.table_name
ORDER BY c.table_name;

SELECT
  c.table_name,
  COALESCE(in_fk.inbound_fk_count, 0) AS inbound_fk_count,
  COALESCE(out_fk.outbound_fk_count, 0) AS outbound_fk_count
FROM candidate_tables c
LEFT JOIN (
  SELECT referenced_table_name AS table_name, COUNT(*) AS inbound_fk_count
  FROM information_schema.key_column_usage
  WHERE table_schema = @db_name
    AND referenced_table_name IS NOT NULL
  GROUP BY referenced_table_name
) in_fk ON in_fk.table_name = c.table_name
LEFT JOIN (
  SELECT table_name, COUNT(*) AS outbound_fk_count
  FROM information_schema.key_column_usage
  WHERE table_schema = @db_name
    AND referenced_table_name IS NOT NULL
  GROUP BY table_name
) out_fk ON out_fk.table_name = c.table_name
ORDER BY c.table_name;

SELECT
  c.table_name,
  COUNT(vtu.view_name) AS dependent_view_count
FROM candidate_tables c
LEFT JOIN information_schema.view_table_usage vtu
  ON vtu.table_schema = @db_name
 AND vtu.table_name = c.table_name
GROUP BY c.table_name
ORDER BY c.table_name;

SELECT
  c.table_name,
  COUNT(tr.trigger_name) AS trigger_count
FROM candidate_tables c
LEFT JOIN information_schema.triggers tr
  ON tr.event_object_schema = @db_name
 AND tr.event_object_table = c.table_name
GROUP BY c.table_name
ORDER BY c.table_name;

SELECT
  CONCAT('SELECT ''', c.table_name, ''' AS table_name, COUNT(*) AS exact_rows FROM `', @db_name, '`.`', c.table_name, '`;') AS exact_count_sql
FROM candidate_tables c
JOIN information_schema.tables t
  ON t.table_schema = @db_name
 AND t.table_name = c.table_name
ORDER BY c.table_name;

SELECT
  CONCAT('RENAME TABLE `', @db_name, '`.`', c.table_name, '` TO `', @db_name, '`.`', c.table_name, '_archived_', DATE_FORMAT(NOW(), '%Y%m%d'), '`;') AS archive_sql
FROM candidate_tables c
JOIN information_schema.tables t
  ON t.table_schema = @db_name
 AND t.table_name = c.table_name
ORDER BY c.table_name;

SELECT
  CONCAT('DROP TABLE `', @db_name, '`.`', c.table_name, '`;') AS drop_sql
FROM candidate_tables c
JOIN information_schema.tables t
  ON t.table_schema = @db_name
 AND t.table_name = c.table_name
ORDER BY c.table_name;
