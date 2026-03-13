INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON UPPER(TRIM(u.role)) = UPPER(TRIM(r.role_code))
LEFT JOIN user_roles ur ON ur.user_id = u.id AND ur.role_id = r.id
WHERE u.role IS NOT NULL
  AND TRIM(u.role) <> ''
  AND ur.user_id IS NULL;
