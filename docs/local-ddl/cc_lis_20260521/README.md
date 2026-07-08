# Local DDL for cc_lis_20260521.xml

These files are generated from Informatica SOURCE/TARGET metadata.
Use them only in disposable local verification schemas.

Purpose: let FDL refresh source/target fields and verify field mapping in UI.

Generated SQL files:

- `cc_lis_20260521__cc_133_uat_rw.sql`: cc_133_uat_rw, 4 tables
- `cc_lis_20260521__lis_190_uat_newcrm_rw.sql`: lis_190_uat_newcrm_rw, 4 tables
- `cc_lis_20260521__optional_lis_package_stub.sql`: optional package stub for local execution only

Recommended UI verification sequence:

1. Run the SQL for each connection against the database/schema backing that FDL connection.
2. Recreate or reopen the generated FDL tasks.
3. Open each data sync node and refresh/check field mapping.
4. If task execution fails on missing package/function, use the optional stub only in a local test schema.
