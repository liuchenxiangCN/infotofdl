SELECT
    c.table_name,
    COUNT(*) AS column_count
FROM user_tab_columns c
WHERE c.table_name IN (
    'LCCONT',
    'LCINSURED'
)
GROUP BY c.table_name
ORDER BY c.table_name;
