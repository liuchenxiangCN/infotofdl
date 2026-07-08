SELECT
    c.table_name,
    COUNT(*) AS column_count
FROM user_tab_columns c
WHERE c.table_name IN (
    'CC_CUSTOMER_INFO_LIS',
    'CC_ANSWER_INFO_LIS',
    'CC_CALLCENTERRESULT',
    'CC_CALLCENTERRESULT1',
    'CC_NB_FEEDBACK_INFO'
)
GROUP BY c.table_name
ORDER BY c.table_name;
