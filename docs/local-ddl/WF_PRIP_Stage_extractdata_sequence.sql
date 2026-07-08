-- Local-only test fixture for WF_PRIP_Stage.XML / s_M_PRIP_ExtractData.
-- The Informatica source qualifier references ExtractDataLog_SEQ.Nextval.
-- Create the sequence in the local schema backing the FDL connection name prip_155.
-- INFA_ORACLE_NEXTVAL is a generic local helper used when FDL nests Oracle
-- source SQL under a downstream SparkSqlNode; Oracle does not allow NEXTVAL
-- directly in that nested query shape.

CREATE OR REPLACE FUNCTION INFA_ORACLE_NEXTVAL(sequence_name IN VARCHAR2)
RETURN NUMBER AUTHID DEFINER
IS
    next_value NUMBER;
BEGIN
    EXECUTE IMMEDIATE 'SELECT ' || DBMS_ASSERT.SQL_OBJECT_NAME(sequence_name) || '.Nextval FROM dual'
        INTO next_value;
    RETURN next_value;
END;
/

CREATE SEQUENCE ExtractDataLog_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

SELECT ExtractDataLog_SEQ.Nextval AS serialno FROM dual;

SELECT INFA_ORACLE_NEXTVAL('ExtractDataLog_SEQ') AS serialno FROM dual;
