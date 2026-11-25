INSERT INTO user_permission (user_id, permission)
SELECT u.id, 'EDIT_PRICES'
FROM users u
WHERE regexp_replace(translate(lower(trim(u.name)), 'ãáâàéêíóôõúç', 'aaaaeeiooouc'), '\s+', ' ', 'g') IN ('beatriz', 'ana clara')
   OR regexp_replace(translate(lower(trim(u.login)), 'ãáâàéêíóôõúç', 'aaaaeeiooouc'), '\s+', ' ', 'g') IN ('beatriz', 'ana clara')
ON CONFLICT DO NOTHING;
