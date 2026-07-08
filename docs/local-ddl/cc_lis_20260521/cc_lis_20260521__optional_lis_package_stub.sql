-- Optional test-only stub for Pre SQL in cc_lis_20260521.xml.
-- Run this only in a disposable local verification schema if task execution
-- fails because PKG_newBusinessDealDetail is absent.

CREATE OR REPLACE PACKAGE PKG_newBusinessDealDetail AS
    FUNCTION dealReturnResultInformatica(p_date VARCHAR2, p_type VARCHAR2)
        RETURN VARCHAR2;
END PKG_newBusinessDealDetail;
/

CREATE OR REPLACE PACKAGE BODY PKG_newBusinessDealDetail AS
    FUNCTION dealReturnResultInformatica(p_date VARCHAR2, p_type VARCHAR2)
        RETURN VARCHAR2
    IS
    BEGIN
        RETURN 'OK';
    END;
END PKG_newBusinessDealDetail;
/
