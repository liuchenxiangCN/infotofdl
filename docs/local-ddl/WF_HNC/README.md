# Local DDL for WF_HNC.XML

These files are generated from Informatica SOURCE/TARGET metadata.
Use them only in disposable local verification schemas.

Purpose: let FDL refresh source/target fields and verify field mapping in UI.

Generated SQL files:

- `WF_HNC__prip_155.sql`: prip_155, 1 tables
- `WF_HNC__lis_uat152.sql`: lis_uat152, 1 tables
- `WF_HNC__lis_uat152_sql_dependencies.sql`: lis_uat152, 1 SQL dependency tables
- `verify_tables.sql`: local Oracle column-count verification

Recommended UI verification sequence:

1. Run the SQL for each connection against the database/schema backing that FDL connection.
2. Recreate or reopen the generated FDL tasks.
3. Open each data sync node and refresh/check field mapping.
4. If task execution fails on missing package/function, use the optional stub only in a local test schema.

Local execution status on 2026-07-02:

- `LCCONT`: 105 columns confirmed in local Oracle SYSTEM schema.
- `LCINSURED`: 2 columns confirmed in local Oracle SYSTEM schema.
